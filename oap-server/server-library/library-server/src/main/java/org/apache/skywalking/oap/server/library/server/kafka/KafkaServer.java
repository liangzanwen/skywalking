package org.apache.skywalking.oap.server.library.server.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.skywalking.oap.server.library.server.Server;
import org.apache.skywalking.oap.server.library.server.ServerException;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author caoyixiong
 */
public class KafkaServer implements Server {

    private final String brokers;
    private final String topic;
    private CopyOnWriteArrayList<KafkaHandler> kafkaHandlers = new CopyOnWriteArrayList<>();

    private Consumer<String, byte[]> consumer;

    public KafkaServer(String brokers, String topic) {
        this.brokers = brokers;
        this.topic = topic;
    }

    @Override
    public String hostPort() {
        return null;
    }

    @Override
    public String serverClassify() {
        return "kafka";
    }

    public void addHandler(KafkaHandler kafkaHandler) {
        kafkaHandlers.add(kafkaHandler);
    }

    @Override
    public void initialize() {
        Properties props = new Properties();
        props.put("bootstrap.servers", brokers);
        props.put("group.id", "test_group");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", 1000);
        props.put("session.timeout.ms", 120000);
        props.put("max.poll.interval.ms", 600000);
        props.put("max.poll.records", 100);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));
    }

    @Override
    public void start() throws ServerException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(1000);
                    for (KafkaHandler kafkaHandler : kafkaHandlers) {
                        kafkaHandler.doConsumer(records);
                    }
                }
            }
        }).start();
    }

    @Override
    public boolean isSSLOpen() {
        return false;
    }

    @Override
    public boolean isStatusEqual(Server target) {
        return equals(target);
    }
}
