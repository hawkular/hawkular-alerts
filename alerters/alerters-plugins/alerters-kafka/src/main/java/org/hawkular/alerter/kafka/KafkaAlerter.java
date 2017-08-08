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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.hawkular.alerts.alerters.api.Alerter;
import org.hawkular.alerts.alerters.api.AlerterPlugin;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerKey;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.DistributedEvent;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;

/**
 * This is the main class of the Kafka Alerter.
 *
 * The Kafka Alerter will listen for triggers tagged with "Kafka" tag. The Alerter will create a consumer to a Kafka Topic
 * with the config provider into the Trigger context. The Alerter will convert Kafka records into Hawkular Data or Events
 * and send them into the Alerting engine.
 *
 * The Kafka Alerter uses the following conventions for trigger tags and context:
 *
 * <pre>
 *
 * - [Required]    trigger.tags["Kafka"] = "<value reserved for future uses>"
 *
 *   An "Kafka" tag is required for the alerter to detect this trigger will listen to a Kafka topic.
 *   Value is not necessary, it can be used as a description, it is reserved for future uses.
 *
 *   i.e.   trigger.tags["Kafka"] = ""                          // Empty value is valid
 *          trigger.tags["Kafka"] = "OpenShift Kafka System"    // It can be used as description
 *
 * - [Required]    trigger.context["kafka.*"] = "<kafka native properties>"
 *
 *   Kafka Consumer properties are prefixed with "kafka." in the Trigger context.
 *   kafka.key.deserializer and kafka.value.deserializer point to StringDeserializer as default if not present.
 *
 *   i.e.   kafkaTrigger.addContext("kafka.bootstrap.servers", "localhost:9092");
 *          kafkaTrigger.addContext("kafka.group.id", "kafka-trigger-group");
 *          kafkaTrigger.addContext("kafka.key.deserializer", StringDeserializer.class.getName());
 *          kafkaTrigger.addContext("kafka.value.deserializer", StringDeserializer.class.getName());
 *
 *   See the KafkaConsumer reference for more info
 *
 *   https://kafka.apache.org/0100/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html
 *
 * - [Required]    trigger.context["topic"] = "<kafka topic to listen>"
 *
 *   It defines the Kafka Topic a trigger will listen. In the current version only a single topic for Trigger is allowed.
 *
 * - [Optional]    trigger.context["poll_timeout"] = "<kafka consumer poll timeout>"
 *
 *   It defines the poll timeout in ms for the Kafka Consumer. By default it takes 1 second.
 *
 * - [Optional]    trigger.context["mapping"] = "<mapping_expression>"
 *
 *   By default, Kafka records are directly mapped into a Data object, with the following mapping
 *
 *          Kafka record timestamp -> Data timestamp
 *          Kafka record value     -> Data value
 *          Kafka Topic            -> Data id (dataId referenced on conditions)
 *
 *   Optionally, a Kafka record can be mapped into an Event. To enable this, a "mapping" expression must be present in
 *   Trigger context.
 *
 *   Kafka Alerter expects a json record value.
 *
 *   A mapping expressions defines how to convert an Kafka json record into a Hawkular Event:
 *
 *   <mapping_expression> ::= <mapping> | <mapping> "," <mapping_expression>
 *   <mapping> ::= <kafka_record_field> [ "|" "'" <DEFAULT_VALUE> "'" ] ":" <hawkular_event_field>
 *   <hawkular_event_field> ::= "id" | "ctime" | "dataSource" | "dataId" | "category" | "text" | "context" | "tags"
 *
 * - [Optional]    trigger.context["timestamp_pattern"] = "<date and time pattern>"
 *
 *   By default, an Event ctime field is mapped directly from a kafka record timestamp value. This can be optionally
 *   overwritten to use a json field of the Kafka record value.
 *
 *   This property defines a time pattern to parse ctime field. It must follow supported formats of
 *   {@link java.time.format.DateTimeFormatter}.
 *
 * </pre>
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Alerter(name = "kafka")
public class KafkaAlerter implements AlerterPlugin {
    private static final MsgLogger log = MsgLogging.getMsgLogger(KafkaAlerter.class);

    private static final String KAFKA_ALERTER = "hawkular-alerts.kafka-alerter";
    private static final String KAFKA_ALERTER_ENV = "KAFKA_ALERTER";
    private static final String KAFKA_ALERTER_DEFAULT = "true";
    private boolean kafkaAlerter;

    private static final String ALERTER_NAME = "Kafka";
    private static final String KAFKA_EXECUTOR_NAME = "KafkaConsumer-";

    private Map<TriggerKey, Trigger> activeTriggers = new ConcurrentHashMap<>();

    private Map<TriggerKey, KafkaQuery> kafkaQueries = new ConcurrentHashMap<>();

    private DefinitionsService definitions;

    private AlertsService alerts;

    private ExecutorService executor;
    private ExecutorService kafkaExecutor;

    @Override
    public void init(DefinitionsService definitions, AlertsService alerts, ExecutorService executor) {
        if (definitions == null || alerts == null || executor == null) {
            throw new IllegalStateException("KafkaAlerter Alerter cannot connect with Hawkular Alerting");
        }
        this.definitions = definitions;
        this.alerts = alerts;
        this.executor = executor;
        kafkaAlerter = Boolean.parseBoolean(HawkularProperties.getProperty(KAFKA_ALERTER,
                KAFKA_ALERTER_ENV, KAFKA_ALERTER_DEFAULT));

        if (kafkaAlerter) {
            kafkaExecutor = Executors.newCachedThreadPool(new KafkaConsumerThreadFactory());
            this.definitions.registerDistributedListener(events -> refresh(events));
            initialRefresh();
        }
    }

    @Override
    public void stop() {
        if (kafkaQueries != null) {
            kafkaQueries.values().stream().forEach(q -> q.shutdown());
        }
    }

    private void initialRefresh() {
        try {
            Collection<Trigger> triggers = definitions.getAllTriggersByTag(ALERTER_NAME, "*");
            triggers.stream().forEach(trigger -> activeTriggers.put(new TriggerKey(trigger.getTenantId(), trigger.getId()), trigger));
            update();
        } catch (Exception e) {
            log.error("Failed to fetch Triggers for external conditions.", e);
        }
    }

    private synchronized void update() {
        final Set<TriggerKey> existingKeys = kafkaQueries.keySet();
        final Set<TriggerKey> activeKeys = activeTriggers.keySet();

        Set<TriggerKey> newKeys = new HashSet<>();
        Set<TriggerKey> canceledKeys = new HashSet<>();

        Set<TriggerKey> updatedKeys = new HashSet<>(activeKeys);
        updatedKeys.retainAll(activeKeys);

        activeKeys.stream().filter(key -> !existingKeys.contains(key)).forEach(key -> newKeys.add(key));
        existingKeys.stream().filter(key -> !activeKeys.contains(key)).forEach(key -> canceledKeys.add(key));

        log.debugf("newKeys %s", newKeys);
        log.debugf("updatedKeys %s", updatedKeys);
        log.debugf("canceledKeys %s", canceledKeys);

        canceledKeys.stream().forEach(key -> {
            KafkaQuery canceled = kafkaQueries.remove(key);
            if (canceled != null) {
                canceled.shutdown();
            }
        });
        updatedKeys.stream().forEach(key -> {
            KafkaQuery updated = kafkaQueries.remove(key);
            if (updated != null) {
                updated.shutdown();
            }
        });

        newKeys.addAll(updatedKeys);

        for (TriggerKey key : newKeys) {
            Trigger trigger = activeTriggers.get(key);
            KafkaQuery query = new KafkaQuery(alerts, trigger);
            kafkaQueries.put(key, query);
            kafkaExecutor.submit(query);
        }
    }

    private void refresh(Set<DistributedEvent> distEvents) {
        log.debugf("Events received %s", distEvents);
        executor.submit(() -> {
            try {
                for (DistributedEvent distEvent : distEvents) {
                    TriggerKey triggerKey = new TriggerKey(distEvent.getTenantId(), distEvent.getTriggerId());
                    switch (distEvent.getOperation()) {
                        case REMOVE:
                            activeTriggers.remove(triggerKey);
                            break;
                        case ADD:
                            if (activeTriggers.containsKey(triggerKey)) {
                                break;
                            }
                        case UPDATE:
                            Trigger trigger = definitions.getTrigger(distEvent.getTenantId(), distEvent.getTriggerId());
                            if (trigger != null && trigger.getTags().containsKey(ALERTER_NAME)) {
                                if (!trigger.isLoadable()) {
                                    activeTriggers.remove(triggerKey);
                                    break;
                                } else {
                                    activeTriggers.put(triggerKey, trigger);
                                }
                            }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch Triggers for external conditions.", e);
            }
            update();
        });
    }

    public static class KafkaConsumerThreadFactory implements ThreadFactory {
        private int count = 0;

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, KAFKA_EXECUTOR_NAME + (++count));
        }
    }
}
