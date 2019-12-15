package org.apache.skywalking.apm.agent.core.remote.http;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * @author caoyixiong
 */
public enum HttpClient {
    INSTANCE;
    private CloseableHttpClient closeableHttpClient;

    HttpClient() {
        closeableHttpClient = HttpClients.createDefault();
    }

    public String execute(HttpUriRequest httpUriRequest) throws IOException {
        CloseableHttpResponse response = closeableHttpClient.execute(httpUriRequest);
        HttpEntity httpEntity = response.getEntity();
        return EntityUtils.toString(httpEntity);
    }
}
