package com.abyss.orth.admin.test.util;

import com.abyss.orth.core.constant.ExecutorBlockStrategyEnum;
import com.abyss.orth.core.glue.GlueTypeEnum;
import com.abyss.orth.core.openapi.model.TriggerRequest;

/**
 * Fixture for creating TriggerRequest test data.
 *
 * <p>Provides factory methods for common trigger parameter scenarios.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * TriggerRequest param = TriggerRequestFixture.createBeanTrigger(1, "testHandler");
 * TriggerRequest scriptParam = TriggerRequestFixture.createScriptTrigger(2, "echo 'test'");
 * }</pre>
 */
public class TriggerRequestFixture {

    /**
     * Create a BEAN glue type trigger param.
     *
     * @param jobId job ID
     * @param executorHandler handler name
     * @return trigger param
     */
    public static TriggerRequest createBeanTrigger(int jobId, String executorHandler) {
        return createTrigger(jobId, GlueTypeEnum.BEAN, executorHandler, "");
    }

    /**
     * Create a GLUE_GROOVY trigger param.
     *
     * @param jobId job ID
     * @param glueSource Groovy code
     * @return trigger param
     */
    public static TriggerRequest createGroovyTrigger(int jobId, String glueSource) {
        return createTrigger(jobId, GlueTypeEnum.GLUE_GROOVY, null, glueSource);
    }

    /**
     * Create a GLUE_SHELL script trigger param.
     *
     * @param jobId job ID
     * @param script shell script content
     * @return trigger param
     */
    public static TriggerRequest createScriptTrigger(int jobId, String script) {
        return createTrigger(jobId, GlueTypeEnum.GLUE_SHELL, null, script);
    }

    /**
     * Create a generic trigger param with all options.
     *
     * @param jobId job ID
     * @param glueType glue type
     * @param executorHandler handler name (for BEAN type)
     * @param glueSource code source (for GLUE types)
     * @return trigger param
     */
    public static TriggerRequest createTrigger(
            int jobId, GlueTypeEnum glueType, String executorHandler, String glueSource) {
        TriggerRequest param = new TriggerRequest();
        param.setJobId(jobId);
        param.setExecutorHandler(executorHandler);
        param.setExecutorParams("");
        param.setExecutorBlockStrategy(ExecutorBlockStrategyEnum.SERIAL_EXECUTION.name());
        param.setExecutorTimeout(0);
        param.setLogId(1L);
        param.setLogDateTime(System.currentTimeMillis());
        param.setGlueType(glueType.name());
        param.setGlueSource(glueSource);
        param.setGlueUpdatetime(System.currentTimeMillis());
        param.setBroadcastIndex(0);
        param.setBroadcastTotal(1);
        return param;
    }
}
