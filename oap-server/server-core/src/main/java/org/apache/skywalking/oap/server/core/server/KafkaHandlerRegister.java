package org.apache.skywalking.oap.server.core.server;

import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.server.kafka.KafkaHandler;

/**
 * @author caoyixiong
 */
public interface KafkaHandlerRegister extends Service {
    void addHandler(KafkaHandler kafkaHandler);
}
