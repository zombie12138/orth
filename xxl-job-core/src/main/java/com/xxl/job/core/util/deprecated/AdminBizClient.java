package com.xxl.job.core.util.deprecated;

import java.util.List;

import com.xxl.job.core.openapi.AdminBiz;
import com.xxl.job.core.openapi.model.CallbackRequest;
import com.xxl.job.core.openapi.model.RegistryRequest;
import com.xxl.tool.response.Response;

/**
 * Deprecated HTTP client for admin API operations.
 *
 * <p>This class has been moved to deprecated utilities and is no longer maintained. It provided
 * HTTP-based communication with the orth admin server for callback and registry operations.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use the xxl-tool
 *     library's HTTP client utilities instead. For admin API calls, use the standard OpenAPI client
 *     implementations provided by the framework.
 * @author xuxueli 2017-07-28 22:14:52
 */
@Deprecated
public class AdminBizClient implements AdminBiz {

    public AdminBizClient() {}

    public AdminBizClient(String addressUrl, String accessToken, int timeout) {
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
    public Response<String> callback(List<CallbackRequest> callbackRequestList) {
        throw new UnsupportedOperationException(
                "AdminBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }

    @Override
    public Response<String> registry(RegistryRequest registryRequest) {
        throw new UnsupportedOperationException(
                "AdminBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }

    @Override
    public Response<String> registryRemove(RegistryRequest registryRequest) {
        throw new UnsupportedOperationException(
                "AdminBizClient is deprecated. Use xxl-tool HTTP client instead.");
    }
}
