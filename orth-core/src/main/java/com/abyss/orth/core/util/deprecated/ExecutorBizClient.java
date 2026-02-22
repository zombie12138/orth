package com.abyss.orth.core.util.deprecated;

import com.abyss.orth.core.openapi.ExecutorBiz;
import com.abyss.orth.core.openapi.model.*;
import com.xxl.tool.response.Response;

/**
 * Deprecated HTTP client for executor API operations.
 *
 * <p>This class provided HTTP-based communication with orth job executors for triggering jobs,
 * checking idle status, killing jobs, and retrieving logs. It has been superseded by more modern
 * HTTP client implementations.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use the xxl-tool
 *     library's HTTP client utilities instead. For executor API calls, use the standard OpenAPI
 *     client implementations provided by the framework.
 * @author xuxueli 2017-07-28 22:14:52
 */
@Deprecated
public class ExecutorBizClient implements ExecutorBiz {

    public ExecutorBizClient() {}

    public ExecutorBizClient(String addressUrl, String accessToken, int timeout) {
        this.addressUrl = addressUrl;
        this.accessToken = accessToken;
        this.timeout = timeout;

        // valid
        if (!this.addressUrl.endsWith("/")) {
            this.addressUrl = this.addressUrl + "/";
        }
        if (!(this.timeout >= 1 && this.timeout <= 10)) {
            this.timeout = 3;
        }
    }

    private String addressUrl;
    private String accessToken;
    private int timeout;

    @Override
    public Response<String> beat() {
        throw new UnsupportedOperationException(
                "ExecutorBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }

    @Override
    public Response<String> idleBeat(IdleBeatRequest idleBeatRequest) {
        throw new UnsupportedOperationException(
                "ExecutorBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }

    @Override
    public Response<String> run(TriggerRequest triggerRequest) {
        throw new UnsupportedOperationException(
                "ExecutorBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }

    @Override
    public Response<String> kill(KillRequest killRequest) {
        throw new UnsupportedOperationException(
                "ExecutorBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }

    @Override
    public Response<LogResult> log(LogRequest logRequest) {
        throw new UnsupportedOperationException(
                "ExecutorBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }
}
