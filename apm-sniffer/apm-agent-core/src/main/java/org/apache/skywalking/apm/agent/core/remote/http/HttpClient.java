/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.remote.http;

import com.google.gson.Gson;
import io.netty.util.internal.ThreadLocalRandom;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.apache.skywalking.apm.util.StringUtil;

import java.io.IOException;

/**
 * @author caoyixiong
 */
public enum HttpClient {
    INSTANCE;
    private CloseableHttpClient closeableHttpClient;
    private Gson gson;

    HttpClient() {
        closeableHttpClient = HttpClients.createDefault();
        gson = new Gson();
    }

    public String execute(String path, Object data) throws IOException {
        HttpPost httpPost = new HttpPost(getIpPort() + Constants.PATH_SEPARATOR + path);
        httpPost.setEntity(new StringEntity(gson.toJson(data)));
        CloseableHttpResponse response = closeableHttpClient.execute(httpPost);
        HttpEntity httpEntity = response.getEntity();
        return EntityUtils.toString(httpEntity);
    }

    public String getIpPort() {
        if (!StringUtil.isEmpty(Config.Collector.BACKEND_ADDRESS)) {
            String[] ipPorts = Config.Collector.BACKEND_ADDRESS.split(",");
            if (ipPorts.length == 0) {
                return null;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return ipPorts[random.nextInt(ipPorts.length - 1)];
        }
        return null;
    }
}
