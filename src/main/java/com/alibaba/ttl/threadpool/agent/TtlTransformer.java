package com.alibaba.ttl.threadpool.agent;

import com.alibaba.ttl.TtlCallable;
import com.alibaba.ttl.TtlRunnable;

import java.io.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

/**
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @since 0.9.0
 */
public class TtlTransformer implements ClassFileTransformer {
    private static final Logger logger = Logger.getLogger(TtlTransformer.class.getName());

    private static final String TTL_RUNNABLE_CLASS_NAME = TtlRunnable.class.getName();
    private static final String TTL_CALLABLE_CLASS_NAME = TtlCallable.class.getName();

    private static final String RUNNABLE_CLASS_NAME = "java.lang.Runnable";
    private static final String CALLABLE_CLASS_NAME = "java.util.concurrent.Callable";
    private static final String TIMER_TASK_CLASS_NAME = "java.util.TimerTask";

    private static Set<String> EXECUTOR_CLASS_NAMES = new HashSet<String>();

    static {
        EXECUTOR_CLASS_NAMES.add("java.util.concurrent.ThreadPoolExecutor");
        EXECUTOR_CLASS_NAMES.add("java.util.concurrent.ScheduledThreadPoolExecutor");
    }

    private static final byte[] EMPTY_BYTE_ARRAY = {};

    /**
     * 返回包装后新类的字节码
     * @param loader
     * @param classFile
     * @param classBeingRedefined
     * @param protectionDomain
     * @param classFileBuffer
     * @return
     * @throws IllegalClassFormatException
     */
    @Override
    public byte[] transform(ClassLoader loader, String classFile, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classFileBuffer) throws IllegalClassFormatException {
        try {
            // Lambda has no class file, no need to transform, just return.
            if (classFile == null) {
                return EMPTY_BYTE_ARRAY;
            }

            final String className = toClassName(classFile);
            if (EXECUTOR_CLASS_NAMES.contains(className)) {
                logger.info("Transforming class " + className);
                CtClass clazz = getCtClass(classFileBuffer, loader);

                for (CtMethod method : clazz.getDeclaredMethods()) {
                    updateMethod(clazz, method);
                }

                byte[] bytes = clazz.toBytecode();
                writeClass(className,bytes);
                return bytes;
            } else if (TIMER_TASK_CLASS_NAME.equals(className)) {
                CtClass clazz = getCtClass(classFileBuffer, loader);
                while (true) {
                    String name = clazz.getSuperclass().getName();
                    if (Object.class.getName().equals(name)) {
                        break;
                    }
                    if (TIMER_TASK_CLASS_NAME.equals(name)) {
                        logger.info("Transforming class " + className);
                        // FIXME add code here
                        return EMPTY_BYTE_ARRAY;
                    }
                }
            }
        } catch (Throwable t) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            t.printStackTrace(printWriter);
            String msg = "Fail to transform class " + classFile + ", cause: " + stringWriter.toString();
            logger.severe(msg);
            throw new IllegalStateException(msg, t);
        }
        return EMPTY_BYTE_ARRAY;
    }

    private static String toClassName(String classFile) {
        return classFile.replace('/', '.');
    }

    private static CtClass getCtClass(byte[] classFileBuffer, ClassLoader classLoader) throws IOException {
        ClassPool classPool = new ClassPool(true);
        if (classLoader == null) {
            classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
        } else {
            classPool.appendClassPath(new LoaderClassPath(classLoader));
        }

        CtClass clazz = classPool.makeClass(new ByteArrayInputStream(classFileBuffer), false);
        clazz.defrost();
        return clazz;
    }

    private static void updateMethod(CtClass clazz, CtMethod method) throws NotFoundException, CannotCompileException {
        if (method.getDeclaringClass() != clazz) {
            return;
        }
        final int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
            return;
        }

        CtClass[] parameterTypes = method.getParameterTypes();
        StringBuilder insertCode = new StringBuilder();
        for (int i = 0; i < parameterTypes.length; i++) {
            CtClass paraType = parameterTypes[i];
            if (RUNNABLE_CLASS_NAME.equals(paraType.getName())) {
                String code = String.format("$%d = %s.get($%d, false, true);", i + 1, TTL_RUNNABLE_CLASS_NAME, i + 1);
                logger.info("insert code before method " + method + " of class " + method.getDeclaringClass().getName() + ": " + code);
                insertCode.append(code);
            } else if (CALLABLE_CLASS_NAME.equals(paraType.getName())) {
                String code = String.format("$%d = %s.get($%d, false, true);", i + 1, TTL_CALLABLE_CLASS_NAME, i + 1);
                logger.info("insert code before method " + method + " of class " + method.getDeclaringClass().getName() + ": " + code);
                insertCode.append(code);
            }
        }
        if (insertCode.length() > 0) {
            method.insertBefore(insertCode.toString());
        }
    }


    public static void writeClass(String className,byte[] bytes) throws IOException {
        Boolean generateClass = Boolean.valueOf(System.getProperty("generateClass","false"));
        if (!generateClass)
            return;
        String generateClassPath = System.getProperty("generateClassPath", ".");
        String filePath = generateClassPath + File.separator + className + ".class";
        File file = new File(filePath);
        RandomAccessFile randomAccessFile = null;
        FileChannel channel = null;
        try {
            randomAccessFile = new RandomAccessFile(file,"rw");
            channel = randomAccessFile.getChannel();
            ByteBuffer byteBuffer =  ByteBuffer.wrap(bytes);
            channel.write(byteBuffer);
            logger.info("transform "+className+"-> filePath :" + filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            if (null != randomAccessFile)
            randomAccessFile.close();
            if (null != channel)
            channel.close();
        }
    }
}
