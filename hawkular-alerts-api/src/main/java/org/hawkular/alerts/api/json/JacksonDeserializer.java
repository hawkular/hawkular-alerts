/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.api.json;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.ExternalConditionEval;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.StringConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

/**
 * @author Lucas Ponce
 */
public class JacksonDeserializer {

    public static class ConditionEvalDeserializer extends JsonDeserializer<ConditionEval> {

        @Override
        public ConditionEval deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {

            ObjectCodec objectCodec = jp.getCodec();
            JsonNode node = objectCodec.readTree(jp);

            if (null == node) {
                throw new ConditionEvalException("Unexpected null node.");
            }

            ConditionEval conditionEval = null;
            JsonNode conditionNode = node.get("condition");
            Condition condition = deserializeCondition(conditionNode);
            switch (condition.getType()) {
                case THRESHOLD: {
                    conditionEval = new ThresholdConditionEval();
                    ThresholdConditionEval tConditionEval = (ThresholdConditionEval)conditionEval;
                    if (condition instanceof ThresholdCondition) {
                        tConditionEval.setCondition((ThresholdCondition)condition);
                    }
                    if (node.get("value") != null) {
                        tConditionEval.setValue(node.get("value").doubleValue());
                    }
                    break;
                }
                case AVAILABILITY: {
                    conditionEval = new AvailabilityConditionEval();
                    AvailabilityConditionEval aConditionEval = (AvailabilityConditionEval)conditionEval;
                    if (condition instanceof AvailabilityCondition) {
                        aConditionEval.setCondition((AvailabilityCondition)condition);
                    }
                    if (node.get("value") != null) {
                        aConditionEval.setValue(Availability.AvailabilityType.valueOf(node.get("value").textValue()));
                    }
                    break;
                }
                case COMPARE: {
                    conditionEval = new CompareConditionEval();
                    CompareConditionEval cConditionEval = (CompareConditionEval)conditionEval;
                    if (condition instanceof CompareCondition) {
                        cConditionEval.setCondition((CompareCondition)condition);
                    }
                    if (node.get("value1") != null) {
                        cConditionEval.setValue1(node.get("value1").doubleValue());
                    }
                    if (node.get("value2") != null) {
                        cConditionEval.setValue2(node.get("value2").doubleValue());
                    }
                    if (node.get("context2") != null) {
                        cConditionEval.setContext2(deserializeMap(node.get("context2")));
                    }
                    break;
                }
                case RANGE: {
                    conditionEval = new ThresholdRangeConditionEval();
                    ThresholdRangeConditionEval rConditionEval = (ThresholdRangeConditionEval)conditionEval;
                    if (condition instanceof ThresholdRangeCondition) {
                        rConditionEval.setCondition((ThresholdRangeCondition)condition);
                    }
                    if (node.get("value") != null) {
                        rConditionEval.setValue(node.get("value").doubleValue());
                    }
                    break;
                }
                case STRING: {
                    conditionEval = new StringConditionEval();
                    StringConditionEval sConditionEval = (StringConditionEval)conditionEval;
                    if (condition instanceof StringCondition) {
                        sConditionEval.setCondition((StringCondition)condition);
                    }
                    if (node.get("value") != null) {
                        sConditionEval.setValue(node.get("value").textValue());
                    }
                    break;
                }
                case EXTERNAL: {
                    conditionEval = new ExternalConditionEval();
                    ExternalConditionEval eConditionEval = (ExternalConditionEval)conditionEval;
                    if (condition instanceof ExternalCondition) {
                        eConditionEval.setCondition((ExternalCondition)condition);
                    }
                    if (node.get("value") != null) {
                        eConditionEval.setValue(node.get("value").textValue());
                    }
                    break;
                }
                default: {
                    throw new ConditionEvalException("Unexpected Condition type [" + condition.getType().name() + "]");
                }
            }

            if (conditionEval != null) {
                if (node.get("match") != null) {
                    conditionEval.setMatch(node.get("match").booleanValue());
                }
                if (node.get("evalTimestamp") != null) {
                    conditionEval.setEvalTimestamp(node.get("evalTimestamp").longValue());
                }
                if (node.get("dataTimestamp") != null) {
                    conditionEval.setDataTimestamp(node.get("dataTimestamp").longValue());
                }
                if (node.get("context") != null) {
                    conditionEval.setContext(deserializeMap(node.get("context")));
                }
            }

            return conditionEval;
        }
    }

    public static Condition deserializeCondition(JsonNode node) throws JsonProcessingException {
        if (node == null) {
            return null;
        }

        Condition condition = null;
        Condition.Type conditionType = null;
        try {
            conditionType = Condition.Type.valueOf(node.get("type").asText().toUpperCase());
        } catch (Exception e) {
            throw new ConditionEvalException(e);
        }

        switch (conditionType) {
            case THRESHOLD: {
                condition = new ThresholdCondition();
                ThresholdCondition tCondition = (ThresholdCondition)condition;
                if (node.get("dataId") != null) {
                    tCondition.setDataId(node.get("dataId").textValue());
                }
                if (node.get("operator") != null) {
                    tCondition.setOperator(ThresholdCondition.Operator.valueOf(node.get("operator").textValue()));
                }
                if (node.get("threshold") != null) {
                    tCondition.setThreshold(node.get("threshold").doubleValue());
                }
                break;
            }
            case AVAILABILITY: {
                condition = new AvailabilityCondition();
                AvailabilityCondition aCondition = (AvailabilityCondition)condition;
                if (node.get("dataId") != null) {
                    aCondition.setDataId(node.get("dataId").textValue());
                }
                if (node.get("operator") != null) {
                    aCondition.setOperator(AvailabilityCondition.Operator.valueOf(node.get("operator").textValue()));
                }
                break;
            }
            case COMPARE: {
                condition = new CompareCondition();
                CompareCondition cCondition = (CompareCondition)condition;
                if (node.get("dataId") != null) {
                    cCondition.setDataId(node.get("dataId").textValue());
                }
                if (node.get("operator") != null) {
                    cCondition.setOperator(CompareCondition.Operator.valueOf(node.get("operator").textValue()));
                }
                if (node.get("data2Id") != null) {
                    cCondition.setData2Id(node.get("data2Id").textValue());
                }
                if (node.get("data2Multiplier") != null) {
                    cCondition.setData2Multiplier(node.get("data2Multiplier").doubleValue());
                }
                break;
            }
            case RANGE: {
                condition = new ThresholdRangeCondition();
                ThresholdRangeCondition rCondition = (ThresholdRangeCondition)condition;
                if (node.get("dataId") != null) {
                    rCondition.setDataId(node.get("dataId").textValue());
                }
                if (node.get("operatorLow") != null) {
                    rCondition.setOperatorLow(ThresholdRangeCondition
                            .Operator.valueOf(node.get("operatorLow").textValue()));
                }
                if (node.get("operatorHigh") != null) {
                    rCondition.setOperatorHigh(ThresholdRangeCondition
                            .Operator.valueOf(node.get("operatorHigh").textValue()));
                }
                if (node.get("thresholdLow") != null) {
                    rCondition.setThresholdLow(node.get("thresholdLow").doubleValue());
                }
                if (node.get("thresholdHigh") != null) {
                    rCondition.setThresholdHigh(node.get("thresholdHigh").doubleValue());
                }
                if (node.get("inRange") != null) {
                    rCondition.setInRange(node.get("inRange").booleanValue());
                }
                break;
            }
            case STRING: {
                condition = new StringCondition();
                StringCondition sCondition = (StringCondition)condition;
                if (node.get("dataId") != null) {
                    sCondition.setDataId(node.get("dataId").textValue());
                }
                if (node.get("operator") != null) {
                    sCondition.setOperator(StringCondition.Operator.valueOf(node.get("operator").textValue()));
                }
                if (node.get("pattern") != null) {
                    sCondition.setPattern(node.get("pattern").textValue());
                }
                if (node.get("ignoreCase") != null) {
                    sCondition.setIgnoreCase(node.get("ignoreCase").booleanValue());
                }
                break;
            }
            case EXTERNAL: {
                condition = new ExternalCondition();
                ExternalCondition eCondition = (ExternalCondition)condition;
                if (node.get("systemId") != null) {
                    eCondition.setSystemId(node.get("systemId").textValue());
                }
                if (node.get("dataId") != null) {
                    eCondition.setDataId(node.get("dataId").textValue());
                }
                if (node.get("expression") != null) {
                    eCondition.setExpression(node.get("expression").textValue());
                }
                break;
            }
            default:
                throw new ConditionEvalException("Unexpected Condition Type [" + conditionType.name() + "]");
        }
        if (condition != null) {
            if (node.get("tenantId") != null) {
                condition.setTenantId(node.get("tenantId").textValue());
            }
            if (node.get("triggerId") != null) {
                condition.setTriggerId(node.get("triggerId").textValue());
            }
            if (node.get("triggerMode") != null) {
                condition.setTriggerMode(Mode.valueOf(node.get("triggerMode").textValue()));
            }
        }
        return condition;
    }

    public static Map<String, String> deserializeMap(JsonNode mapNode) {
        Map<String, String> map = new HashMap<>();
        Iterator<String> fieldNames = mapNode.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            map.put(field, mapNode.get(field).asText());
        }
        return map;
    }

    public static class ConditionEvalException extends JsonProcessingException {
        private static final long serialVersionUID = 1L;

        protected ConditionEvalException(String msg, JsonLocation loc, Throwable rootCause) {
            super(msg, loc, rootCause);
        }

        protected ConditionEvalException(String msg) {
            super(msg);
        }

        protected ConditionEvalException(String msg, JsonLocation loc) {
            super(msg, loc);
        }

        protected ConditionEvalException(String msg, Throwable rootCause) {
            super(msg, rootCause);
        }

        protected ConditionEvalException(Throwable rootCause) {
            super(rootCause);
        }
    }

    public static class AlertThinDeserializer extends BeanDeserializerModifier {

        List<String> ignorables = new ArrayList<>();

        public AlertThinDeserializer() {
            for (Field field : Alert.class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Alert.Thin.class)) {
                    ignorables.add(field.getName());
                }
            }
        }

        @Override
        public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription beanDesc,
                BeanDeserializerBuilder builder) {
            if (!beanDesc.getBeanClass().equals(Alert.class)) {
                return builder;
            }
            for (String ignore : ignorables) {
                builder.addIgnorable(ignore);
            }
            return builder;
        }

        @Override
        public List<BeanPropertyDefinition> updateProperties(DeserializationConfig config,
                BeanDescription beanDesc, List<BeanPropertyDefinition> propDefs) {
            if (!beanDesc.getBeanClass().equals(Alert.class)) {
                return propDefs;
            }
            List<BeanPropertyDefinition> newPropDefs = new ArrayList<>();
            for (BeanPropertyDefinition propDef : propDefs) {
                if (!ignorables.contains(propDef.getName())) {
                    newPropDefs.add(propDef);
                }
            }
            return newPropDefs;
        }
    }

}
