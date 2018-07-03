package com.alibaba.ttl.threadpool.agent;


import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;


/**
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @see <a href="http://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html">The mechanism for instrumentation</a>
 * @since 0.9.0
 *
 *
 * -Xbootclasspath/p:F:\transmittable-thread-local-2.3.0-SNAPSHOT.jar -javaagent:F:\transmittable-thread-local-2.3.0-SNAPSHOT.jar -DgenerateClass=true -DgenerateClassPath="F:\Idea workspace\transmittable-thread-local\src\main\java\com\alibaba\ttl\threadpool\agent\transformer"
 *
 *
 *  -Xbootclasspath/p:F:\transmittable-thread-local-2.3.0-SNAPSHOT.jar -javaagent:F:\transmittable-thread-local-2.3.0-SNAPSHOT.jar
 */
public final class TtlAgent {
    private static final Logger logger = Logger.getLogger(TtlAgent.class.getName());
    
    private TtlAgent() {
    	throw new InstantiationError( "Must not instantiate this class" );
    }
    
    public static void premain(String agentArgs, Instrumentation inst) {
        logger.info("[TtlAgent.premain] begin, agentArgs: " + agentArgs);
        install(agentArgs, inst);
    }

    /**
     * 在instrument中安装 类的 Transformer。 Transformer 在初始化类之前，利用className得到类的字节码。然后classloader 加载其字节码为对象。
     *
     * @param agentArgs
     * @param inst
     */
    static void install(String agentArgs, Instrumentation inst) {
        logger.info("[TtlAgent.install] agentArgs: " + agentArgs + ", Instrumentation: " + inst);

        ClassFileTransformer transformer = new TtlTransformer();
        inst.addTransformer(transformer, true);

        logger.info("[TtlAgent.install] addTransformer success.");
    }
}
