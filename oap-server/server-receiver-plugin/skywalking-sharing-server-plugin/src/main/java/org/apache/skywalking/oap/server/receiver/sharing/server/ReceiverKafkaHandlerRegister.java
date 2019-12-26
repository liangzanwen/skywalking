package org.apache.skywalking.oap.server.receiver.sharing.server;

import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.server.kafka.KafkaHandler;
import org.apache.skywalking.oap.server.library.server.kafka.KafkaServer;

/**
 * @author caoyixiong
 */
public class ReceiverKafkaHandlerRegister implements Service {

    private KafkaServer kafkaServer;

    ReceiverKafkaHandlerRegister(KafkaServer kafkaServer) {
        this.kafkaServer = kafkaServer;
    }

    public void addHandler(KafkaHandler kafkaHandler) {
        kafkaServer.addHandler(kafkaHandler);
    }
}
