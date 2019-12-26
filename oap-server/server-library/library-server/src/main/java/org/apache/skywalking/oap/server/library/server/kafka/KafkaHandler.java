package org.apache.skywalking.oap.server.library.server.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.skywalking.oap.server.library.server.ServerHandler;

/**
 * @author caoyixiong
 */
public interface KafkaHandler extends ServerHandler {
    void doConsumer(ConsumerRecords<String, byte[]> records);
}
