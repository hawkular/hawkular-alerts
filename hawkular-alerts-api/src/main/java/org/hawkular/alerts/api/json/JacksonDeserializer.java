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
package org.hawkular.alerts.api.json;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.EventCondition;
import org.hawkular.alerts.api.model.condition.EventConditionEval;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.ExternalConditionEval;
import org.hawkular.alerts.api.model.condition.MissingCondition;
import org.hawkular.alerts.api.model.condition.MissingConditionEval;
import org.hawkular.alerts.api.model.condition.NelsonCondition;
import org.hawkular.alerts.api.model.condition.NelsonCondition.NelsonRule;
import org.hawkular.alerts.api.model.condition.NelsonConditionEval;
import org.hawkular.alerts.api.model.condition.RateCondition;
import org.hawkular.alerts.api.model.condition.RateConditionEval;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.StringConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.event.Thin;
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

    public static class ConditionDeserializer extends JsonDeserializer<Condition> {

        @Override
        public Condition deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {

            ObjectCodec objectCodec = jp.getCodec();
            JsonNode node = objectCodec.readTree(jp);

            if (null == node) {
                throw new ConditionException("Unexpected null node.");
            }
            return deserializeCondition(node);
        }
    }

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
                case AVAILABILITY: {
                    try {
                        conditionEval = new AvailabilityConditionEval();
                        AvailabilityConditionEval aConditionEval = (AvailabilityConditionEval) conditionEval;
                        aConditionEval.setCondition((AvailabilityCondition) condition);
                        if (node.get("value") != null) {
                            aConditionEval.setValue(AvailabilityType.valueOf(node.get("value").textValue()));
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case COMPARE: {
                    try {
                        conditionEval = new CompareConditionEval();
                        CompareConditionEval cConditionEval = (CompareConditionEval) conditionEval;
                        cConditionEval.setCondition((CompareCondition) condition);
                        if (node.get("value1") != null) {
                            cConditionEval.setValue1(node.get("value1").doubleValue());
                        }
                        if (node.get("value2") != null) {
                            cConditionEval.setValue2(node.get("value2").doubleValue());
                        }
                        if (node.get("context2") != null) {
                            cConditionEval.setContext2(deserializeMap(node.get("context2")));
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case EVENT: {
                    try {
                        conditionEval = new EventConditionEval();
                        EventConditionEval evConditionEval = (EventConditionEval) conditionEval;
                        evConditionEval.setCondition((EventCondition) condition);
                        if (node.get("value") != null) {
                            evConditionEval.setValue(node.get("value").traverse(objectCodec).readValueAs(Event.class));
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case EXTERNAL: {
                    try {
                        conditionEval = new ExternalConditionEval();
                        ExternalConditionEval eConditionEval = (ExternalConditionEval) conditionEval;
                        eConditionEval.setCondition((ExternalCondition) condition);
                        if (node.get("value") != null) {
                            eConditionEval.setValue(node.get("value").textValue());
                        }
                        if (node.get("event") != null) {
                            eConditionEval.setEvent(node.get("event").traverse(objectCodec).readValueAs(Event.class));
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case MISSING: {
                    try {
                        conditionEval = new MissingConditionEval();
                        MissingConditionEval mConditionEval = (MissingConditionEval) conditionEval;
                        mConditionEval.setCondition((MissingCondition) condition);
                        if (node.get("previousTime") != null) {
                            mConditionEval.setPreviousTime(node.get("previousTime").longValue());
                        }
                        if (node.get("time") != null) {
                            mConditionEval.setTime(node.get("time").longValue());
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case NELSON: {
                    try {
                        conditionEval = new NelsonConditionEval();
                        NelsonConditionEval nConditionEval = (NelsonConditionEval) conditionEval;
                        nConditionEval.setCondition((NelsonCondition) condition);
                        if (node.get("mean") != null) {
                            nConditionEval.setMean(node.get("mean").asDouble());
                        }
                        if (node.get("standardDeviation") != null) {
                            nConditionEval.setStandardDeviation(node.get("standardDeviation").asDouble());
                        }
                        if (node.get("violations") != null) {
                            List<NelsonRule> violations = new ArrayList<>();
                            Iterator<JsonNode> nodes = node.get("violations").elements();
                            while (nodes.hasNext()) {
                                violations.add(NelsonRule.valueOf(nodes.next().textValue()));
                            }
                            nConditionEval.setViolations(violations);
                        }
                        if (node.get("violationsData") != null) {
                            List<Data> violationsData = new ArrayList<>();
                            Iterator<JsonNode> nodes = node.get("violationsData").elements();
                            while (nodes.hasNext()) {
                                violationsData.add(nodes.next().traverse(objectCodec).readValueAs(Data.class));
                                //violations.add(NelsonRule.valueOf(nodes.next().textValue()));
                            }
                            nConditionEval.setViolationsData(violationsData);
                        }

                        // TODO ViolationsData?
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case RANGE: {
                    try {
                        conditionEval = new ThresholdRangeConditionEval();
                        ThresholdRangeConditionEval rConditionEval = (ThresholdRangeConditionEval) conditionEval;
                        rConditionEval.setCondition((ThresholdRangeCondition) condition);
                        if (node.get("value") != null) {
                            rConditionEval.setValue(node.get("value").doubleValue());
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case RATE: {
                    try {
                        conditionEval = new RateConditionEval();
                        RateConditionEval rConditionEval = (RateConditionEval) conditionEval;
                        rConditionEval.setCondition((RateCondition) condition);
                        if (node.get("time") != null) {
                            rConditionEval.setTime(node.get("time").longValue());
                        }
                        if (node.get("value") != null) {
                            rConditionEval.setValue(node.get("value").doubleValue());
                        }
                        if (node.get("previousTime") != null) {
                            rConditionEval.setPreviousTime(node.get("previousTime").longValue());
                        }
                        if (node.get("previousValue") != null) {
                            rConditionEval.setPreviousValue(node.get("previousValue").doubleValue());
                        }
                        if (node.get("rate") != null) {
                            rConditionEval.setRate(node.get("rate").doubleValue());
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case STRING: {
                    try {
                        conditionEval = new StringConditionEval();
                        StringConditionEval sConditionEval = (StringConditionEval) conditionEval;
                        sConditionEval.setCondition((StringCondition) condition);
                        if (node.get("value") != null) {
                            sConditionEval.setValue(node.get("value").textValue());
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                case THRESHOLD: {
                    try {
                        conditionEval = new ThresholdConditionEval();
                        ThresholdConditionEval tConditionEval = (ThresholdConditionEval) conditionEval;
                        tConditionEval.setCondition((ThresholdCondition) condition);
                        if (node.get("value") != null) {
                            tConditionEval.setValue(node.get("value").doubleValue());
                        }
                    } catch (Exception e) {
                        throw new ConditionEvalException(e);
                    }
                    break;
                }
                default: {
                    throw new ConditionEvalException("Unexpected Condition type [" + condition.getType().name() + "]");
                }
            }
            if (conditionEval != null) {
                try {
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
                } catch (Exception e) {
                    throw new ConditionEvalException(e);
                }
            }
            conditionEval.updateDisplayString();
            return conditionEval;
        }
    }

    public static Condition deserializeCondition(JsonNode node) throws JsonProcessingException {
        if (node == null) {
            return null;
        }

        Condition condition = null;
        Condition.Type conditionType = null;
        if (node.get("type") == null) {
            throw new ConditionException("Condition must have a type");
        }
        try {
            conditionType = Condition.Type.valueOf(node.get("type").asText().toUpperCase());
        } catch (Exception e) {
            throw new ConditionException(e);
        }

        switch (conditionType) {
            case THRESHOLD: {
                try {
                    condition = new ThresholdCondition();
                    ThresholdCondition tCondition = (ThresholdCondition) condition;
                    if (node.get("dataId") != null) {
                        tCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("operator") != null) {
                        tCondition.setOperator(ThresholdCondition.Operator.valueOf(node.get("operator").textValue()));
                    }
                    if (node.get("threshold") != null) {
                        tCondition.setThreshold(node.get("threshold").doubleValue());
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case AVAILABILITY: {
                try {
                    condition = new AvailabilityCondition();
                    AvailabilityCondition aCondition = (AvailabilityCondition) condition;
                    if (node.get("dataId") != null) {
                        aCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("operator") != null) {
                        aCondition.setOperator(AvailabilityCondition.Operator.valueOf(
                                node.get("operator").textValue()));
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case COMPARE: {
                try {
                    condition = new CompareCondition();
                    CompareCondition cCondition = (CompareCondition) condition;
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
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case RANGE: {
                try {
                    condition = new ThresholdRangeCondition();
                    ThresholdRangeCondition rCondition = (ThresholdRangeCondition) condition;
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
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case STRING: {
                try {
                    condition = new StringCondition();
                    StringCondition sCondition = (StringCondition) condition;
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
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case EXTERNAL: {
                try {
                    condition = new ExternalCondition();
                    ExternalCondition eCondition = (ExternalCondition) condition;
                    if (node.get("alerterId") != null) {
                        eCondition.setAlerterId(node.get("alerterId").textValue());
                    }
                    if (node.get("dataId") != null) {
                        eCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("expression") != null) {
                        eCondition.setExpression(node.get("expression").textValue());
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case EVENT: {
                try {
                    condition = new EventCondition();
                    EventCondition evCondition = (EventCondition) condition;
                    if (node.get("dataId") != null) {
                        evCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("expression") != null) {
                        evCondition.setExpression(node.get("expression").textValue());
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case MISSING: {
                try {
                    condition = new MissingCondition();
                    MissingCondition mCondition = (MissingCondition) condition;
                    if (node.get("dataId") != null) {
                        mCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("interval") != null) {
                        mCondition.setInterval(node.get("interval").longValue());
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case RATE: {
                try {
                    condition = new RateCondition();
                    RateCondition rCondition = (RateCondition) condition;
                    if (node.get("dataId") != null) {
                        rCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("direction") != null) {
                        rCondition.setDirection(RateCondition.Direction.valueOf(node.get("direction").textValue()));
                    }
                    if (node.get("period") != null) {
                        rCondition.setPeriod(RateCondition.Period.valueOf(node.get("period").textValue()));
                    }
                    if (node.get("operator") != null) {
                        rCondition.setOperator(RateCondition.Operator.valueOf(node.get("operator").textValue()));
                    }
                    if (node.get("threshold") != null) {
                        rCondition.setThreshold(node.get("threshold").doubleValue());
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            case NELSON: {
                try {
                    condition = new NelsonCondition();
                    NelsonCondition nCondition = (NelsonCondition) condition;
                    if (node.get("dataId") != null) {
                        nCondition.setDataId(node.get("dataId").textValue());
                    }
                    if (node.get("activeRules") != null) {
                        Set<NelsonRule> activeRules = new HashSet<>();
                        Iterator<JsonNode> nodes = node.get("activeRules").elements();
                        while (nodes.hasNext()) {
                            activeRules.add(NelsonRule.valueOf(nodes.next().textValue()));
                        }
                        nCondition.setActiveRules(activeRules);
                    }
                    if (node.get("sampleSize") != null) {
                        nCondition.setSampleSize(node.get("sampleSize").intValue());
                    }
                } catch (Exception e) {
                    throw new ConditionException(e);
                }
                break;
            }
            default:
                throw new ConditionException("Unexpected Condition Type [" + conditionType.name() + "]");
        }
        if (condition != null) {
            try {
                if (node.get("tenantId") != null) {
                    condition.setTenantId(node.get("tenantId").textValue());
                }
                if (node.get("triggerId") != null) {
                    condition.setTriggerId(node.get("triggerId").textValue());
                }
                if (node.get("triggerMode") != null) {
                    condition.setTriggerMode(Mode.valueOf(node.get("triggerMode").textValue()));
                }
                if (node.get("conditionSetSize") != null) {
                    condition.setConditionSetSize(node.get("conditionSetSize").intValue());
                }
                if (node.get("conditionSetIndex") != null) {
                    condition.setConditionSetIndex(node.get("conditionSetIndex").intValue());
                }
                if (node.get("context") != null) {
                    condition.setContext(deserializeMap(node.get("context")));
                }
            } catch (Exception e) {
                throw new ConditionException(e);
            }
        }
        condition.updateDisplayString();
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

    public static class ConditionException extends JsonProcessingException {
        private static final long serialVersionUID = 1L;

        protected ConditionException(String msg, JsonLocation loc, Throwable rootCause) {
            super(msg, loc, rootCause);
        }

        protected ConditionException(String msg) {
            super(msg);
        }

        protected ConditionException(String msg, JsonLocation loc) {
            super(msg, loc);
        }

        protected ConditionException(String msg, Throwable rootCause) {
            super(msg, rootCause);
        }

        protected ConditionException(Throwable rootCause) {
            super(rootCause);
        }
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

        private static final List<Class<?>> thinnables = Arrays.asList(Event.class, Alert.class, Action.class);

        List<String> ignorables = new ArrayList<>();

        public AlertThinDeserializer() {
            for (Class<?> clazz = Alert.class; (null != clazz); clazz = clazz.getSuperclass()) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Thin.class)) {
                        ignorables.add(field.getName());
                    }
                }
            }
            for (Field field : Action.class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Thin.class)) {
                    ignorables.add(field.getName());
                }
            }
        }

        @Override
        public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription beanDesc,
                BeanDeserializerBuilder builder) {
            if (!thinnables.contains(beanDesc.getBeanClass())) {
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
            if (!thinnables.contains(beanDesc.getBeanClass())) {
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
