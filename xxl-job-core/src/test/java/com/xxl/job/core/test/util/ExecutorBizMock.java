package com.xxl.job.core.test.util;

import com.xxl.job.core.openapi.ExecutorBiz;
import com.xxl.job.core.openapi.model.IdleBeatRequest;
import com.xxl.job.core.openapi.model.KillRequest;
import com.xxl.job.core.openapi.model.LogRequest;
import com.xxl.job.core.openapi.model.LogResult;
import com.xxl.job.core.openapi.model.TriggerRequest;
import com.xxl.tool.response.Response;

/**
 * Mock implementation of ExecutorBiz for testing.
 *
 * <p>Provides configurable responses for all executor operations.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ExecutorBiz executorBiz = ExecutorBizMock.success();
 * Response response = executorBiz.run(triggerRequest);
 * }</pre>
 */
public class ExecutorBizMock implements ExecutorBiz {

    private final boolean shouldSucceed;
    private final String failureMessage;

    private ExecutorBizMock(boolean shouldSucceed, String failureMessage) {
        this.shouldSucceed = shouldSucceed;
        this.failureMessage = failureMessage;
    }

    public static ExecutorBiz success() {
        return new ExecutorBizMock(true, null);
    }

    public static ExecutorBiz failure(String message) {
        return new ExecutorBizMock(false, message);
    }

    @Override
    public Response<String> beat() {
        return shouldSucceed ? Response.ofSuccess("beat success") : Response.ofFail(failureMessage);
    }

    @Override
    public Response<String> idleBeat(IdleBeatRequest request) {
        return shouldSucceed
                ? Response.ofSuccess("idle beat success")
                : Response.ofFail(failureMessage);
    }

    @Override
    public Response<String> run(TriggerRequest request) {
        return shouldSucceed ? Response.ofSuccess("run success") : Response.ofFail(failureMessage);
    }

    @Override
    public Response<String> kill(KillRequest request) {
        return shouldSucceed ? Response.ofSuccess("kill success") : Response.ofFail(failureMessage);
    }

    @Override
    public Response<LogResult> log(LogRequest request) {
        if (shouldSucceed) {
            LogResult logResult = new LogResult();
            logResult.setFromLineNum(request.getFromLineNum());
            logResult.setToLineNum(request.getFromLineNum() + 100);
            logResult.setLogContent("Test log content");
            logResult.setEnd(true);
            return Response.ofSuccess(logResult);
        } else {
            return Response.ofFail(failureMessage);
        }
    }
}
