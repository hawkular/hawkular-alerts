/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerter.kafka;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class KafkaAlerterTest {

    @Ignore
    @Test
    public void listenDataFromKafkaTopic() throws Exception {
        Trigger kafkaTrigger = new Trigger("test-tenant","kafka-trigger-id", "kafka-trigger-name");
        kafkaTrigger.addContext("kafka.bootstrap.servers", "localhost:9092");
        kafkaTrigger.addContext("kafka.group.id", "kafka-trigger-group");
        kafkaTrigger.addContext("kafka.key.deserializer", StringDeserializer.class.getName());
        kafkaTrigger.addContext("kafka.value.deserializer", StringDeserializer.class.getName());
        kafkaTrigger.addContext("topic", "my-topic");

        KafkaQuery kafkaQuery = new KafkaQuery(null, kafkaTrigger);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(kafkaQuery);

        int seconds = 100;
        for (int i = 0; i < seconds; i++) {
            System.out.println("Second: " + i);
            Thread.sleep(1000);
        }

        kafkaQuery.shutdown();
    }

    @Ignore
    @Test
    public void listenEventFromKafkaTopic() throws Exception {
        Trigger kafkaTrigger = new Trigger("test-tenant","kafka-trigger-id", "kafka-trigger-name");
        kafkaTrigger.addContext("kafka.bootstrap.servers", "localhost:9092");
        kafkaTrigger.addContext("kafka.group.id", "kafka-trigger-group");
        kafkaTrigger.addContext("kafka.key.deserializer", StringDeserializer.class.getName());
        kafkaTrigger.addContext("kafka.value.deserializer", StringDeserializer.class.getName());
        kafkaTrigger.addContext("topic", "my-topic");
        kafkaTrigger.addContext("mapping", "origin:dataId,payload:text");

        KafkaQuery kafkaQuery = new KafkaQuery(null, kafkaTrigger);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.submit(kafkaQuery);

        int seconds = 100;
        for (int i = 0; i < seconds; i++) {
            System.out.println("Second: " + i);
            Thread.sleep(1000);
        }

        kafkaQuery.shutdown();
    }

}
