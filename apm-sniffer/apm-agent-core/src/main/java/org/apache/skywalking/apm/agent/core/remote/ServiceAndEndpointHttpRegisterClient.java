package org.apache.skywalking.apm.agent.core.remote;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.*;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.OSUtil;
import org.apache.skywalking.apm.agent.core.remote.http.HttpClient;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author caoyixiong
 */
@OverrideImplementor(ServiceAndEndpointRegisterClient.class)
public class ServiceAndEndpointHttpRegisterClient extends ServiceAndEndpointRegisterClient {
    private static final ILog logger = LogManager.getLogger(ServiceAndEndpointRegisterClient.class);

    private static final String SERVICE_REGISTER_PATH = "/service/register";
    private static final String SERVICE_INSTANCE_REGISTER_PATH = "/serviceInstance/register";
    private static final String SERVICE_INSTANCE_PING_PATH = "/serviceInstance/ping";

    private static final String SERVICE_NAME = "sn";
    private static final String SERVICE_ID = "si";

    private static final String INSTANCE_UUID = "iu";
    private static final String REGISTER_TIME = "rt";
    private static final String INSTANCE_ID = "ii";
    private static final String INSTANCE_PROPERTIES = "ips";

    private static final String HEARTBEAT_TIME = "ht";
    private static final String INSTANCE_COMMAND = "ic";

    private static String AGENT_INSTANCE_UUID;

    private volatile ScheduledFuture<?> applicationRegisterFuture;
    private volatile long coolDownStartTime = -1;
    private Gson gson = new Gson();

    @Override
    public void prepare() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);

        AGENT_INSTANCE_UUID = StringUtil.isEmpty(Config.Agent.INSTANCE_UUID) ? UUID.randomUUID().toString()
                .replaceAll("-", "") : Config.Agent.INSTANCE_UUID;
    }

    @Override
    public void boot() throws Throwable {
        applicationRegisterFuture = Executors
                .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("ServiceAndEndpointRegisterClient"))
                .scheduleAtFixedRate(new RunnableWithExceptionProtection(this, new RunnableWithExceptionProtection.CallbackWhenException() {
                    @Override
                    public void handle(Throwable t) {
                        logger.error("unexpected exception.", t);
                    }
                }), 0, Config.Collector.APP_AND_SERVICE_REGISTER_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {
    }

    @Override
    public void shutdown() throws Throwable {
        applicationRegisterFuture.cancel(true);
    }

    @Override
    public void run() {

        if (coolDownStartTime > 0) {
            final long coolDownDurationInMillis = TimeUnit.MINUTES.toMillis(Config.Agent.COOL_DOWN_THRESHOLD);
            if (System.currentTimeMillis() - coolDownStartTime < coolDownDurationInMillis) {
                logger.warn("The agent is cooling down, won't register itself");
                return;
            } else {
                logger.warn("The agent is re-registering itself to backend");
            }
        }
        coolDownStartTime = -1;

        boolean shouldTry = true;
        while (shouldTry) {
            shouldTry = false;
            try {
                if (RemoteDownstreamConfig.Agent.SERVICE_ID == DictionaryUtil.nullValue()) {
                    HttpPost httpPost = new HttpPost(SERVICE_REGISTER_PATH);
                    httpPost.setEntity(new StringEntity(gson.toJson(Lists.newArrayList(Config.Agent.SERVICE_NAME))));

                    JsonArray jsonElements = gson.fromJson(HttpClient.INSTANCE.execute(httpPost), JsonArray.class);
                    if (jsonElements != null && jsonElements.size() > 0) {
                        for (JsonElement jsonElement : jsonElements) {
                            JsonObject jsonObject = jsonElement.getAsJsonObject();
                            String serviceName = jsonObject.get(SERVICE_NAME).getAsString();
                            int serviceId = jsonObject.get(SERVICE_ID).getAsInt();

                            if (Config.Agent.SERVICE_NAME.equals(serviceName)) {
                                RemoteDownstreamConfig.Agent.SERVICE_ID = serviceId;
                                shouldTry = true;
                            }
                        }
                    }
                } else {
                    if (RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID == DictionaryUtil.nullValue()) {

                        HttpPost httpPost = new HttpPost(SERVICE_INSTANCE_REGISTER_PATH);
                        httpPost.setEntity(new StringEntity(gson.toJson(Lists.newArrayList(Config.Agent.SERVICE_NAME))));
                        JsonArray jsonArray = new JsonArray();
                        JsonObject mapping = new JsonObject();
                        jsonArray.add(mapping);

                        mapping.addProperty(SERVICE_ID, RemoteDownstreamConfig.Agent.SERVICE_ID);
                        mapping.addProperty(INSTANCE_UUID, AGENT_INSTANCE_UUID);
                        mapping.addProperty(REGISTER_TIME, System.currentTimeMillis());
                        mapping.addProperty(INSTANCE_PROPERTIES, gson.toJson(OSUtil.buildOSInfo()));

                        JsonArray response = gson.fromJson(HttpClient.INSTANCE.execute(httpPost), JsonArray.class);
                        for (JsonElement serviceInstance : response) {
                            String agentInstanceUUID = serviceInstance.getAsJsonObject().get(INSTANCE_UUID).getAsString();
                            if (AGENT_INSTANCE_UUID.equals(agentInstanceUUID)) {
                                int serviceInstanceId = serviceInstance.getAsJsonObject().get(INSTANCE_ID).getAsInt();
                                if (serviceInstanceId != DictionaryUtil.nullValue()) {
                                    RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID = serviceInstanceId;
                                    RemoteDownstreamConfig.Agent.INSTANCE_REGISTERED_TIME = System.currentTimeMillis();
                                }
                            }
                        }
                    } else {
                        HttpPost httpPost = new HttpPost(SERVICE_INSTANCE_PING_PATH);
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty(INSTANCE_ID, RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID);
                        jsonObject.addProperty(HEARTBEAT_TIME, System.currentTimeMillis());
                        jsonObject.addProperty(INSTANCE_UUID, AGENT_INSTANCE_UUID);

                        httpPost.setEntity(new StringEntity(gson.toJson(jsonObject)));
                        JsonObject response = gson.fromJson(HttpClient.INSTANCE.execute(httpPost), JsonObject.class);

                        final Commands commands = gson.fromJson(response.get(INSTANCE_COMMAND).getAsString(), Commands.class);
                        ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);

                        NetworkAddressHttpDictionary.INSTANCE.syncRemoteDictionary();
                        EndpointNameHttpDictionary.INSTANCE.syncRemoteDictionary();
                    }
                }
            } catch (Throwable t) {
                logger.error(t, "ServiceAndEndpointRegisterClient execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }

    }

    public void coolDown() {
        this.coolDownStartTime = System.currentTimeMillis();
    }
}
