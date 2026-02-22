package com.abyss.orth.core.util.deprecated;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xxl.tool.gson.GsonTool;
import com.xxl.tool.response.Response;

/**
 * Deprecated HTTP remoting utilities for orth job communication.
 *
 * <p>This utility class provided low-level HTTP POST operations with JSON body support, SSL trust
 * configuration, and access token authentication. It used HttpURLConnection for HTTP communication.
 *
 * @deprecated This utility is deprecated and will be removed in a future version. Use the xxl-tool
 *     library's HTTP client utilities (com.xxl.tool.net.HttpTool) instead, which provide modern
 *     HTTP client functionality with better connection pooling, timeout handling, and SSL
 *     configuration. Consider using Spring RestTemplate or OkHttp for production code.
 * @author xuxueli 2018-11-25 00:55:31
 */
@Deprecated
public class OrthJobRemotingUtil {
    private static Logger logger = LoggerFactory.getLogger(OrthJobRemotingUtil.class);
    public static final String XXL_JOB_ACCESS_TOKEN = "Orth-ACCESS-TOKEN";

    // trust-https start
    private static void trustAllHosts(HttpsURLConnection connection) {
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory newFactory = sc.getSocketFactory();

            connection.setSSLSocketFactory(newFactory);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        connection.setHostnameVerifier(
                new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
    }

    private static final TrustManager[] trustAllCerts =
            new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[] {};
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {}
                }
            };

    // trust-https end

    /**
     * post
     *
     * @param url
     * @param accessToken
     * @param timeout by second
     * @param requestObj
     * @param returnTargClassOfT
     * @return
     */
    public static Response postBody(
            String url,
            String accessToken,
            int timeout,
            Object requestObj,
            Class returnTargClassOfT) {
        HttpURLConnection connection = null;
        BufferedReader bufferedReader = null;
        DataOutputStream dataOutputStream = null;
        try {
            // connection
            URL realUrl = new URL(url);
            connection = (HttpURLConnection) realUrl.openConnection();

            // trust-https
            boolean useHttps = url.startsWith("https");
            if (useHttps) {
                HttpsURLConnection https = (HttpsURLConnection) connection;
                trustAllHosts(https);
            }

            // connection setting
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setReadTimeout(timeout * 1000);
            connection.setConnectTimeout(timeout * 1000);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept-Charset", "application/json;charset=UTF-8");

            if (accessToken != null && !accessToken.trim().isEmpty()) {
                connection.setRequestProperty(XXL_JOB_ACCESS_TOKEN, accessToken);
            }

            // do connection
            connection.connect();

            // write requestBody
            if (requestObj != null) {
                String requestBody = GsonTool.toJson(requestObj);

                dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.write(requestBody.getBytes("UTF-8"));
                dataOutputStream.flush();
                dataOutputStream.close();
            }

            // valid StatusCode
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                return Response.ofFail(
                        "orth remoting fail, StatusCode("
                                + statusCode
                                + ") invalid. for url : "
                                + url);
            }

            // result
            bufferedReader =
                    new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            String resultJson = result.toString();

            // parse returnT
            try {
                Response returnT =
                        GsonTool.fromJson(resultJson, Response.class, returnTargClassOfT);
                return returnT;
            } catch (Exception e) {
                logger.error(
                        "orth remoting (url="
                                + url
                                + ") response content invalid("
                                + resultJson
                                + ").",
                        e);
                return Response.ofFail(
                        "orth remoting (url="
                                + url
                                + ") response content invalid("
                                + resultJson
                                + ").");
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.ofFail("orth remoting error(" + e.getMessage() + "), for url : " + url);
        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e2) {
                logger.error(e2.getMessage(), e2);
            }
        }
    }
}
