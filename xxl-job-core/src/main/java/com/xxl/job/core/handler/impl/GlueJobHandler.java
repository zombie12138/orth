package com.xxl.job.core.handler.impl;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.IJobHandler;

/**
 * Glue-based job handler for dynamically compiled Groovy code.
 *
 * <p>Wraps a dynamically compiled job handler and logs the glue version (update timestamp) before
 * each execution. The admin console compiles Groovy code into handlers at runtime.
 */
public class GlueJobHandler extends IJobHandler {

    private final long glueUpdatetime;
    private final IJobHandler jobHandler;

    /**
     * Constructs a glue job handler.
     *
     * @param jobHandler the dynamically compiled handler instance
     * @param glueUpdatetime the glue code update timestamp (version identifier)
     */
    public GlueJobHandler(IJobHandler jobHandler, long glueUpdatetime) {
        this.jobHandler = jobHandler;
        this.glueUpdatetime = glueUpdatetime;
    }

    public long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    @Override
    public void execute() throws Exception {
        XxlJobHelper.log("----------- glue.version: {} -----------", glueUpdatetime);
        jobHandler.execute();
    }

    @Override
    public void init() throws Exception {
        jobHandler.init();
    }

    @Override
    public void destroy() throws Exception {
        jobHandler.destroy();
    }
}
