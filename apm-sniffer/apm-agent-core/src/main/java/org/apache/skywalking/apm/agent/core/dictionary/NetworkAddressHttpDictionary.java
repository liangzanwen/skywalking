package org.apache.skywalking.apm.agent.core.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.util.internal.ConcurrentSet;
import org.apache.skywalking.apm.agent.core.remote.http.HttpClient;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.skywalking.apm.agent.core.conf.Config.Dictionary.SERVICE_CODE_BUFFER_SIZE;

/**
 * @author caoyixiong
 */
public enum NetworkAddressHttpDictionary {
    INSTANCE;
    private static final String NETWORK_ADDRESS_REGISTER_PATH = "/v6/networkAddress/register";

    private static final String NETWORK_ADDRESS = "na";
    private static final String NETWORK_ADDRESS_ID = "nai";

    private Map<String, Integer> serviceDictionary = new ConcurrentHashMap<String, Integer>();
    private Set<String> unRegisterServices = new ConcurrentSet<String>();
    private Gson gson = new Gson();

    public PossibleFound find(String networkAddress) {
        Integer applicationId = serviceDictionary.get(networkAddress);
        if (applicationId != null) {
            return new Found(applicationId);
        } else {
            if (serviceDictionary.size() + unRegisterServices.size() < SERVICE_CODE_BUFFER_SIZE) {
                unRegisterServices.add(networkAddress);
            }
            return new NotFound();
        }
    }

    public void syncRemoteDictionary() throws IOException {
        if (unRegisterServices.size() > 0) {
            String response = HttpClient.INSTANCE.execute(NETWORK_ADDRESS_REGISTER_PATH, unRegisterServices);
            JsonArray array = gson.fromJson(response, JsonArray.class);

            if (array != null && array.size() > 0) {
                for (JsonElement element : array) {
                    JsonObject object = element.getAsJsonObject();
                    unRegisterServices.remove(object.get(NETWORK_ADDRESS).getAsString());
                    serviceDictionary.put(object.get(NETWORK_ADDRESS).getAsString(), object.get(NETWORK_ADDRESS_ID).getAsInt());
                }
            }
        }
    }

    public void clear() {
        this.serviceDictionary.clear();
    }
}

