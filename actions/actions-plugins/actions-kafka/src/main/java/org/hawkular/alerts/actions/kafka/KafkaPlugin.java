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
package org.hawkular.alerts.actions.kafka;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;

import com.bazaarvoice.jolt.Shiftr;

/**
 * Action Kafka Plugin.
 *
 * It processes Actions writing Event/Alerts into an ElasticSearch system.
 *
 * It supports optional JOLT Shiftr transformations to process Events/Alerts into custom JSON formats.
 *
 * i.e.  {"tenantId":"tenant", "ctime":"timestamp", "dataId":"dataId","context":"context"}
 *
 * https://github.com/bazaarvoice/jolt/blob/master/jolt-core/src/main/java/com/bazaarvoice/jolt/Shiftr.java
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Plugin(name = "kafka")
public class KafkaPlugin implements ActionPluginListener {
    public static final String PLUGIN_NAME = "kafka";

    /**
     * "kafka" property is used as main prefix for Apache Kafka properties.
     *
     * All "kafka.<property>" properties are passed to KafkaConsumer removing the "kafka" prefix.
     *
     * Properties can be defined per action based.
     * If not properties defined at action level, it takes default plugin properties.
     *
     * For these special "kafka" properties, if not properties defined at action plugin, it will search at
     * System.getProperties() level.
     */
    public static final String PROP_KAFKA = "kafka";

    /**
     * "topic" property is used to indicate the topic where the Events/Alerts will be written.
     */
    public static final String PROP_TOPIC = "topic";

    /**
     * "transform" defines an optional transformation expression based on JOLT Shiftr format to convert an
     * Event/Alert into a custom JSON format.
     */
    public static final String PROP_TRANSFORM = "transform";

    /**
     * "timestamp_pattern" used on ctime transformations
     */
    private static final String PROP_TIMESTAMP_PATTERN = "timestamp_pattern";

    private static final String KAFKA_TOPIC = "hawkular-alerts.kafka-topic";
    private static final String KAFKA_TOPIC_ENV = "KAFKA_TOPIC";
    private static final String KAFKA_TOPIC_DEFAULT = "alerts";
    private static final String KEY_SERIALIZER = "key.serializer";
    private static final String VALUE_SERIALIZER = "value.serializer";

    /*
        Timestamp fields
     */
    private static final Set<String> TIMESTAMP_FIELDS = new HashSet<>();
    static {
        TIMESTAMP_FIELDS.add("ctime");
        TIMESTAMP_FIELDS.add("stime");
        TIMESTAMP_FIELDS.add("evalTimestamp");
        TIMESTAMP_FIELDS.add("dataTimestamp");
    }
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, KafkaPlugin.class);

    Map<String, String> defaultProperties = new HashMap<>();

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public KafkaPlugin() {
        defaultProperties.put(PROP_KAFKA, "");
        defaultProperties.put(PROP_TOPIC, KAFKA_TOPIC_DEFAULT);
        defaultProperties.put(PROP_TRANSFORM, "");
        defaultProperties.put(PROP_TIMESTAMP_PATTERN, "");
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public void process(ActionMessage msg) throws Exception {
        if (msg == null || msg.getAction() == null) {
            log.warnMessageReceivedWithoutPayload(PLUGIN_NAME);
        }
        try {
            writeAlert(msg.getAction());
            log.infoActionReceived(PLUGIN_NAME, msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            log.errorCannotProcessMessage(PLUGIN_NAME, e.getMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }
    }

    protected void writeAlert(Action a) throws Exception {
        Properties props = initKafkaProperties(a.getProperties());

        Producer<String, String> producer = new KafkaProducer<>(props);
        String topic = a.getProperties().getOrDefault(PROP_TOPIC,
                HawkularProperties.getProperty(KAFKA_TOPIC, KAFKA_TOPIC_ENV, KAFKA_TOPIC_DEFAULT));

        producer.send(new ProducerRecord<>(topic, a.getActionId(), transform(a)));
        producer.close();
    }

    private Properties initKafkaProperties(Map<String, String> actionProperties) {
        Properties kafkaProperties = new Properties();
        actionProperties.entrySet().stream()
                .filter(e -> e.getKey().startsWith("kafka."))
                .forEach(e -> {
                    kafkaProperties.put(e.getKey().substring(6), e.getValue());
                });
        // Default serializers are handy to reduce verbosity on trigger definitions
        if (!kafkaProperties.containsKey(KEY_SERIALIZER)) {
            kafkaProperties.put(KEY_SERIALIZER, StringSerializer.class.getName());
        }
        if (!kafkaProperties.containsKey(VALUE_SERIALIZER)) {
            kafkaProperties.put(VALUE_SERIALIZER, StringSerializer.class.getName());
        }
        // TODO [lponce] implement a HawkularProperties.getAllProperties() and search all "kafka." properties there too
        return kafkaProperties;
    }

    protected String transform(Action a) {
        String spec = a.getProperties().get(PROP_TRANSFORM);
        if (spec == null || spec.isEmpty()) {
            return JsonUtil.toJson(a.getEvent());
        }
        try {
            Shiftr transformer = new Shiftr(JsonUtil.fromJson(spec, Map.class));
            Map<String, Object> eventMap = JsonUtil.getMap(a.getEvent());
            String timestampPattern = a.getProperties().get(PROP_TIMESTAMP_PATTERN);
            if (!isEmpty(timestampPattern)) {
                transformTimestamp(timestampPattern, eventMap);
            }
            return JsonUtil.toJson(transformer.transform(eventMap));
        } catch (Exception e) {
            log.warnf("Plugin kafka can not apply spec [%s]", spec);
            return JsonUtil.toJson(a.getEvent());
        }
    }

    private void transformTimestamp(String pattern, Object input) {
        if (input == null) {
            return;
        }
        if (input instanceof Map.Entry) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) input;
            if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                transformTimestamp(pattern, entry.getValue());
            } else {
                if (TIMESTAMP_FIELDS.contains(entry.getKey())) {
                    try {
                        Long timestamp = (Long) entry.getValue();
                        entry.setValue(
                                DateTimeFormatter.ofPattern(pattern)
                                        .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), UTC)));
                    } catch (Exception e) {
                        log.warnf("Cannot parse %s timestamp", entry.getKey());
                    }
                }
            }
        } else if (input instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) input;
            map.entrySet().stream().forEach(e -> transformTimestamp(pattern, e));
        } else if (input instanceof List) {
            List list = (List) input;
            list.stream().forEach(e -> transformTimestamp(pattern, e));
        }
    }

    private void sendResult(Action action) {
        if (sender == null) {
            throw new IllegalStateException("ActionPluginSender is not present in the plugin");
        }
        if (action == null) {
            throw new IllegalStateException("Action to update result must be not null");
        }
        ActionResponseMessage newMessage = sender.createMessage(ActionResponseMessage.Operation.RESULT);
        newMessage.getPayload().put("action", JsonUtil.toJson(action));
        try {
            sender.send(newMessage);
        } catch (Exception e) {
            log.error("Error sending ActionResponseMessage", e);
        }
    }

}
