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

import static org.hawkular.alerts.api.model.event.EventField.DATAID;
import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.event.EventField;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class KafkaQuery implements Runnable {
    private static final MsgLogger log = MsgLogging.getMsgLogger(KafkaQuery.class);

    public static final String TOPIC = "topic";
    public static final String MAPPING = "mapping";
    private static final String SOURCE = "source";
    private static final String TIMESTAMP_PATTERN = "timestamp_pattern";
    private static final String KEY_DESERIALIZER = "key.deserializer";
    private static final String VALUE_DESERIALIZER = "value.deserializer";
    private static final String POLL_TIMEOUT = "poll_timeout";
    public static final int POLL_TIMEOUT_DEFAULT = 1000;

    private static final DateTimeFormatter[] DEFAULT_DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    };

    AlertsService alerts;
    Properties consumerProperties;
    String topic;
    long pollTimeout;
    boolean running;

    boolean mappingData = true;
    private Map<String, EventField> mappings = new HashMap<>();
    String tenantId;
    String definedPattern;

    KafkaConsumer<String, String> consumer;

    public KafkaQuery(AlertsService alerts, Trigger trigger) {
        this.alerts = alerts;
        running = true;
        if (trigger == null || isEmpty(trigger.getTenantId()) || isEmpty(trigger.getContext())) {
            log.warnf("Found an empty Kafka Trigger %s", trigger);
            running = false;
            return;
        }
        tenantId = trigger.getTenantId();
        topic = trigger.getContext().get(TOPIC);
        if (isEmpty(topic)) {
            log.warnf("Found a Kafka Trigger without topic in context %s", trigger);
            running = false;
            return;
        }
        pollTimeout = POLL_TIMEOUT_DEFAULT;
        if (trigger.getContext().get(POLL_TIMEOUT) != null) {
            try {
                pollTimeout = Long.valueOf(trigger.getContext().get(POLL_TIMEOUT));
            } catch (Exception e) {
                log.warnf("Invalid poll_timeout value on Kafka Trigger %s", trigger);
            }
        }
        consumerProperties = new Properties();
        trigger.getContext().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("kafka."))
                .forEach(entry -> consumerProperties.put(entry.getKey().substring(6), entry.getValue()));
        // Default deserializers are handy to reduce verbosity on trigger definitions
        if (!consumerProperties.containsKey(KEY_DESERIALIZER)) {
            consumerProperties.put(KEY_DESERIALIZER, StringDeserializer.class.getName());
        }
        if (!consumerProperties.containsKey(VALUE_DESERIALIZER)) {
            consumerProperties.put(VALUE_DESERIALIZER, StringDeserializer.class.getName());
        }
        if (consumerProperties.isEmpty()) {
            log.warnf("Found a Kafka Trigger without kafka consumer properties %s", trigger);
            running = false;
            return;
        }
        if (trigger.getContext().get(MAPPING) != null) {
            mappingData = false;
            definedPattern = trigger.getContext().get(TIMESTAMP_PATTERN);
            try {
                parseMap(trigger.getContext());
            } catch (Exception e) {
                log.errorf("Error fetching mapping on Kafka Trigger %s", trigger, e);
                running = false;
                return;
            }
        }
    }

    @Override
    public void run() {
        if (running) {
            try {
                log.debugf("Starting Kafka Consumer %s %s ", topic, consumerProperties);
                consumer = new KafkaConsumer<>(consumerProperties);
                consumer.subscribe(Arrays.asList(topic));
                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
                    for (ConsumerRecord<String, String> record : records) {
                        if (mappingData) {
                            Data data = new Data(tenantId, null, topic, record.timestamp(), record.value(), null);
                            log.debugf("Data %s", data);
                            if (alerts != null) {
                                alerts.sendData(Arrays.asList(data));
                            }
                        } else {
                            try {
                                Map<String, Object> json = JsonUtil.fromJson(record.value(), Map.class);
                                Event newEvent = new Event();
                                newEvent.setTenantId(tenantId);
                                newEvent.setCtime(record.timestamp());
                                parseEvent(newEvent, json);
                                log.infof("Event %s", newEvent);
                                if (alerts != null) {
                                    alerts.sendEvents(Arrays.asList(newEvent));
                                }
                            } catch (Exception e) {
                                log.errorf("Error parsing an event from Kafka Alerter %s", record.value(), e);
                            }
                        }
                    }
                }
            } catch (WakeupException e) {
                // This is caught when consumer is stopped
            } catch (Exception e) {
                log.error("Unexpected error on Kafka Alerter", e);
            }
        }
        log.debugf("Finished");
    }

    public void shutdown() {
        log.debugf("Shutting down");
        running = false;
    }

    protected void parseMap(Map<String, String> context) throws Exception {
        if (!mappingData) {
            String rawMap = context.get(MAPPING);
            if (rawMap == null) {
                throw new IllegalStateException("mapping must be not null");
            }
            String[] rawMappings = rawMap.split(",");
            for (String rawMapping : rawMappings) {
                String[] fields = rawMapping.trim().split(":");
                if (fields.length == 2) {
                    EventField eventField = EventField.fromString(fields[1].trim());
                    if (eventField == null) {
                        log.warnf("Skipping invalid mapping [%s]", rawMapping);
                    } else {
                        mappings.put(fields[0].trim(), eventField);
                    }
                } else {
                    log.warnf("Skipping invalid mapping [%s]", rawMapping);
                }
            }
            if (!mappings.values().contains(DATAID)) {
                throw new IllegalStateException("Mapping [" + rawMap + "] does not include dataId");
            }
        }
    }

    protected void parseEvent(Event newEvent, Map<String, Object> payload) {
        newEvent.getContext().put(SOURCE, JsonUtil.toJson(payload));
        for (Map.Entry<String, EventField> entry : mappings.entrySet()) {
            switch (entry.getValue()) {
                case ID:
                    newEvent.setId(getField(payload, entry.getKey()));
                    break;
                case CTIME:
                    newEvent.setCtime(parseTimestamp(getField(payload, entry.getKey())));
                    break;
                case DATAID:
                    newEvent.setDataId(getField(payload, entry.getKey()));
                    break;
                case DATASOURCE:
                    newEvent.setDataSource(getField(payload, entry.getKey()));
                    break;
                case CATEGORY:
                    newEvent.setCategory(getField(payload, entry.getKey()));
                    break;
                case TEXT:
                    newEvent.setText(getField(payload, entry.getKey()));
                    break;
                case CONTEXT:
                    newEvent.getContext().put(entry.getKey(), getField(payload, entry.getKey()));
                    break;
                case TAGS:
                    newEvent.getTags().put(entry.getKey(), getField(payload, entry.getKey()));
                    break;
            }
        }
        if (newEvent.getId() == null) {
            newEvent.setId(UUID.randomUUID().toString());
        }
    }

    protected String getField(Map<String, Object> source, String name) {
        if (source == null || name == null) {
            return null;
        }
        if (name.charAt(0) == '\'' && name.charAt(name.length() - 1) == '\'') {
            return name.substring(1, name.length() - 1);
        }
        String[] names = name.split("\\|");
        String defaultValue = "";
        if (names.length > 1) {
            if (names[1].charAt(0) == '\'' && names[1].charAt(names[1].length() - 1) == '\'') {
                defaultValue = names[1].substring(1, names[1].length() - 1);
            }
            name = names[0];
        }
        String[] fields = name.split("\\.");
        for (int i=0; i < fields.length; i++) {
            Object value = source.get(fields[i]);
            if (value instanceof String) {
                return (String) value;
            }
            if (value instanceof Map) {
                source = (Map<String, Object>) value;
            }
        }
        return defaultValue;
    }

    protected long parseTimestamp(String timestamp) {
        if (definedPattern != null) {
            DateTimeFormatter formatter = null;
            try {
                formatter = DateTimeFormatter.ofPattern(definedPattern);
                return ZonedDateTime.parse(timestamp, formatter).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.debugf("Not able to parse [%s] with format [%s]", timestamp, formatter);
            }
        }
        for (DateTimeFormatter formatter : DEFAULT_DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(timestamp, formatter).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.debugf("Not able to parse [%s] with format [%s]", timestamp, formatter);
            }
        }
        try {
            return new Long(timestamp).longValue();
        } catch (Exception e) {
            log.debugf("Not able to parse [%s] as plain timestamp", timestamp);
        }
        return System.currentTimeMillis();
    }

}
