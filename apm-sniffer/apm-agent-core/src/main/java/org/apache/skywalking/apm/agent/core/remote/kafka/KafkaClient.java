package org.apache.skywalking.apm.agent.core.remote.kafka;

import com.google.gson.Gson;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.*;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.apm.network.language.agent.*;

import java.util.Properties;

/**
 * @author caoyixiong
 * @Date: 2019/9/22
 */
public class KafkaClient {
    private static final ILog logger = LogManager.getLogger(KafkaClient.class);
    private Gson gson = new Gson();
    private Producer<String, byte[]> producer;
    private String topic;

    public KafkaClient() {
        if (StringUtil.isEmpty(Config.Collector.KAFKA_BROKERS) || StringUtil.isEmpty(Config.Collector.KAFKA_TOPIC)) {
            return;
        }
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, Config.Collector.KAFKA_BROKERS);
        properties.put(ProducerConfig.RETRIES_CONFIG, 3);
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG, 16 * 1024);
        properties.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 32 * 1024 * 1024);
        properties.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 10 * 1024 * 1024);
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        producer = new KafkaProducer<String, byte[]>(properties);
        this.topic = Config.Collector.KAFKA_TOPIC;
    }

    public void send(UpstreamSegment upstreamSegment) {
        producer.send(new ProducerRecord<String, byte[]>(this.topic, upstreamSegment.toByteArray()), new KafkaCallBack(upstreamSegment));
    }

    public void close() {
        producer.close();
    }

    class KafkaCallBack implements Callback {
        private final UpstreamSegment upstreamSegment;

        public KafkaCallBack(UpstreamSegment upstreamSegment) {
            this.upstreamSegment = upstreamSegment;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if (exception == null) {
                // send success
                logger.error("trace segment send success" + gson.toJson(upstreamSegment.getGlobalTraceIdsList()));
            } else {
                logger.error("trace segment send failure");
            }
        }
    }
}
