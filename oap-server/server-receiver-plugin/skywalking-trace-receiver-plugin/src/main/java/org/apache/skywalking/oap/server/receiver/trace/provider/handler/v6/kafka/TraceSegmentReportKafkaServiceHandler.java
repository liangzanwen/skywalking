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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParseV2;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentSource;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Properties;

/**
 * @author caoyixiong
 */
public class TraceSegmentReportKafkaServiceHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentReportKafkaServiceHandler.class);

    private final SegmentParseV2.Producer segmentProducer;
    private HistogramMetrics histogram;
    private Consumer<String, byte[]> consumer;

    public TraceSegmentReportKafkaServiceHandler(SegmentParseV2.Producer segmentProducer, ModuleManager moduleManager) {
        this.segmentProducer = segmentProducer;
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric("trace_grpc_v6_in_latency", "The process latency of service mesh telemetry",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);

        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092");
        props.put("group.id", "test_group");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", 1000);
        props.put("session.timeout.ms", 120000);
        props.put("max.poll.interval.ms", 600000);
        props.put("max.poll.records", 100);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("test1"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                doConsumer();
            }
        }).start();
    }

    private void doConsumer() {
        logger.info("begin try to receive kafka message");

        while (true) {
            ConsumerRecords<String, byte[]> records = consumer.poll(1000L);

            for (ConsumerRecord<String, byte[]> record : records) {
                HistogramMetrics.Timer timer = histogram.createTimer();
                try {
                    segmentProducer.send(UpstreamSegment.parseFrom(record.value()), SegmentSource.Agent);
                } catch (InvalidProtocolBufferException e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    timer.finish();
                }
            }
        }
    }
}
