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
package org.hawkular.alerts.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.json.JacksonDeserializer;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.ExternalConditionEval;
import org.hawkular.alerts.api.model.condition.MissingCondition;
import org.hawkular.alerts.api.model.condition.MissingConditionEval;
import org.hawkular.alerts.api.model.condition.NelsonCondition;
import org.hawkular.alerts.api.model.condition.NelsonCondition.NelsonRule;
import org.hawkular.alerts.api.model.condition.NelsonConditionEval;
import org.hawkular.alerts.api.model.condition.RateCondition;
import org.hawkular.alerts.api.model.condition.RateCondition.Direction;
import org.hawkular.alerts.api.model.condition.RateCondition.Period;
import org.hawkular.alerts.api.model.condition.RateConditionEval;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.StringConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Match;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Validation of JSON serialization/deserialization
 *
 * @author Lucas Ponce
 */
public class JsonTest {

    ObjectMapper objectMapper;

    private static final String TEST_TENANT = "jdoe";

    @Before
    public void before() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void jsonActionTest() throws Exception {
        String str = "{\"tenantId\":\"tenantTest\",\"actionPlugin\":\"plugin\"," +
                "\"actionId\":\"test\",\"global\":false,\"eventId\":\"testAlert\",\"ctime\":123}";
        Action action = objectMapper.readValue(str, Action.class);

        assertEquals("tenantTest", action.getTenantId());
        assertEquals("plugin", action.getActionPlugin());
        assertEquals("test", action.getActionId());
        assertEquals("testAlert", action.getEventId());
        assertEquals(123, action.getCtime());

        String output = objectMapper.writeValueAsString(action);

        assertEquals(str, output);

        str = "{\"actionId\":\"test\"}";
        action = objectMapper.readValue(str, Action.class);
        output = objectMapper.writeValueAsString(action);

        assertTrue(!output.contains("message"));
    }

    @Test
    public void jsonAlertTest() throws Exception {
        Trigger trigger = new Trigger(TEST_TENANT, "trigger-test", "trigger-test");
        Alert alert = new Alert(TEST_TENANT, trigger, null);

        String output = objectMapper.writeValueAsString(alert);

        assertTrue(!output.contains("evalSets"));

        AvailabilityCondition aCond = new AvailabilityCondition(TEST_TENANT, "trigger-test", "Default",
                AvailabilityCondition.Operator.UP);
        Data aData = Data.forAvailability(TEST_TENANT,"Metric-test", 1, AvailabilityType.UP);
        AvailabilityConditionEval aEval = new AvailabilityConditionEval(aCond, aData);

        ThresholdCondition tCond = new ThresholdCondition(TEST_TENANT, "trigger-test", "Default",
                ThresholdCondition.Operator.LTE,
                50.0);
        Data tData = Data.forNumeric(TEST_TENANT, "Metric-test2", 2, 25.5);
        ThresholdConditionEval tEval = new ThresholdConditionEval(tCond, tData);

        Set<ConditionEval> evals = new HashSet<>();
        evals.add(aEval);
        evals.add(tEval);

        List<Set<ConditionEval>> list = new ArrayList<>();
        list.add(evals);

        alert.setEvalSets(list);

        output = objectMapper.writeValueAsString(alert);

        assertTrue(output.contains("evalSets"));
    }

    @Test
    public void jsonToAlertTest() throws Exception {
        String jsonAlert = "{\"tenantId\":\"jdoe\"," +
                "\"id\":\"trigger-test|1436964192878\"," +
                "\"eventType\":\"ALERT\"," +
                "\"trigger\":{\"tenantId\":\"jdoe\"," +
                    "\"id\":\"trigger-test\"," +
                    "\"name\":\"trigger-test\"," +
                    "\"description\":\"trigger-test\"," +
                    "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}" +
                    "}," +
                "\"ctime\":1436964192878," +
                "\"text\":\"trigger-test\"," +
                "\"evalSets\":[" +
                    "[{\"evalTimestamp\":1436964294055," +
                       "\"dataTimestamp\":2," +
                       "\"type\":\"THRESHOLD\"," +
                "\"condition\":{\"tenantId\":\"jdoe\"," +
                       "\"triggerId\":\"trigger-test\"," +
                       "\"triggerMode\":\"FIRING\"," +
                       "\"type\":\"THRESHOLD\"," +
                "\"conditionId\":\"my-organization-trigger-test-FIRING-1-1\"," +
                       "\"dataId\":\"Default\"," +
                       "\"operator\":\"LTE\"," +
                       "\"threshold\":50.0" +
                     "}," +
                     "\"value\":25.5}," +
                     "{\"evalTimestamp\":1436964284965," +
                       "\"dataTimestamp\":1," +
                       "\"type\":\"AVAILABILITY\"," +
                "\"condition\":{\"tenantId\":\"jdoe\"," +
                       "\"triggerId\":\"trigger-test\"," +
                       "\"triggerMode\":\"FIRING\"," +
                       "\"type\":\"AVAILABILITY\"," +
                "\"conditionId\":\"my-organization-trigger-test-FIRING-1-1\"," +
                       "\"dataId\":\"Default\"," +
                       "\"operator\":\"UP\"" +
                     "}," +
                     "\"value\":\"UP\"}]" +
                    "]," +
                "\"severity\":\"MEDIUM\"," +
                "\"status\":\"OPEN\"," +
                "\"lifecycle\":[{\"status\":\"OPEN\",\"user\":\"system\",\"stime\":1}]," +
                "\"notes\":[{\"user\":\"user1\",\"ctime\":1,\"text\":\"The comment 1\"}," +
                           "{\"user\":\"user2\",\"ctime\":2,\"text\":\"The comment 2\"}" +
                          "]," +
                "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";

        ObjectMapper mapper = new ObjectMapper();
        Alert alert = mapper.readValue(jsonAlert, Alert.class);
        assertNotNull(alert);
        assertNotNull(alert.getEvalSets());
        assertEquals(1, alert.getEvalSets().size());
        assertEquals(2, alert.getEvalSets().get(0).size());
        assertNotNull(alert.getContext());
        assertEquals(2, alert.getContext().size());
        assertEquals("v1", alert.getContext().get("n1"));
        assertEquals("v2", alert.getContext().get("n2"));
        assertEquals(Alert.Status.OPEN, alert.getCurrentLifecycle().getStatus());
        assertEquals("system", alert.getCurrentLifecycle().getUser());
        assertEquals(1, alert.getCurrentLifecycle().getStime());
        assertNotNull(alert.getLastOpenTime());
        assertEquals(1, alert.getLastOpenTime().longValue());
        assertEquals("trigger-test", alert.getText());

        /*
            Testing thin deserializer
         */
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.setDeserializerModifier(new JacksonDeserializer.AlertThinDeserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(simpleModule);
        alert = mapper.readValue(jsonAlert, Alert.class);
        assertNull(alert.getEvalSets());
    }

    @Test
    public void jsonToEventTest() throws Exception {
        String jsonEvent = "{\"tenantId\":\"jdoe\"," +
                "\"id\":\"trigger-test|1436964192878\"," +
                "\"eventType\":\"EVENT\"," +
                "\"trigger\":{\"tenantId\":\"jdoe\"," +
                "\"id\":\"trigger-test\"," +
                "\"name\":\"trigger-test\"," +
                "\"description\":\"trigger-test\"," +
                "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}" +
                "}," +
                "\"ctime\":1436964192878," +
                "\"eventType\":\"EVENT\"," +
                "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}," +
                "\"text\":\"trigger-test\"," +
                "\"category\":\"TRIGGER\"," +
                "\"evalSets\":[" +
                "[{\"evalTimestamp\":1436964294055," +
                "\"dataTimestamp\":2," +
                "\"type\":\"THRESHOLD\"," +
                "\"condition\":{\"tenantId\":\"jdoe\"," +
                "\"triggerId\":\"trigger-test\"," +
                "\"triggerMode\":\"FIRING\"," +
                "\"type\":\"THRESHOLD\"," +
                "\"conditionId\":\"my-organization-trigger-test-FIRING-1-1\"," +
                "\"dataId\":\"Default\"," +
                "\"operator\":\"LTE\"," +
                "\"threshold\":50.0" +
                "}," +
                "\"value\":25.5}," +
                "{\"evalTimestamp\":1436964284965," +
                "\"dataTimestamp\":1," +
                "\"type\":\"AVAILABILITY\"," +
                "\"condition\":{\"tenantId\":\"jdoe\"," +
                "\"triggerId\":\"trigger-test\"," +
                "\"triggerMode\":\"FIRING\"," +
                "\"type\":\"AVAILABILITY\"," +
                "\"conditionId\":\"my-organization-trigger-test-FIRING-1-1\"," +
                "\"dataId\":\"Default\"," +
                "\"operator\":\"UP\"" +
                "}," +
                "\"value\":\"UP\"}]" +
                "]," +
                "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";

        ObjectMapper mapper = new ObjectMapper();
        Event event = mapper.readValue(jsonEvent, Event.class);
        assertNotNull(event);
        assertNotNull(event.getEvalSets());
        assertEquals(1, event.getEvalSets().size());
        assertEquals(2, event.getEvalSets().get(0).size());
        assertNotNull(event.getContext());
        assertEquals(2, event.getContext().size());
        assertEquals("v1", event.getContext().get("n1"));
        assertEquals("v2", event.getContext().get("n2"));
        assertEquals("trigger-test", event.getText());
        assertEquals("EVENT", event.getEventType());
        assertEquals("TRIGGER", event.getCategory());

        /*
            Testing thin deserializer
         */
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.setDeserializerModifier(new JacksonDeserializer.AlertThinDeserializer());
        mapper = new ObjectMapper();
        mapper.registerModule(simpleModule);
        event = mapper.readValue(jsonEvent, Event.class);
        assertNull(event.getEvalSets());
    }

    @Test
    public void jsonAvailabilityConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"type\":\"AVAILABILITY\",\"conditionSetSize\":1,\"conditionSetIndex\":1," +
                "\"conditionId\":\"test-test-FIRING-1-1\"," +
                "\"displayString\":\"Default is UP\",\"dataId\":\"Default\",\"operator\":\"UP\"}";
        AvailabilityCondition condition = objectMapper.readValue(str, AvailabilityCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getOperator().equals(AvailabilityCondition.Operator.UP));

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\",\"dataId\":\"Default\",\"operator\":\"UP\"}";
        try {
            objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"dataId\":\"Default\",\"operator\":\"UPX\"}";
        try {
            objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        // Check uncompleted json

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"dataId\":\"Default\"}";
            objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"operator\":\"UP\"}";
            objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonAvailabilityConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"AVAILABILITY\"," +
                "\"condition\":{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"AVAILABILITY\"," +
                "\"dataId\":\"Default\",\"operator\":\"UP\"}," +
                "\"value\":\"UP\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        AvailabilityConditionEval eval = objectMapper.readValue(str, AvailabilityConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.AVAILABILITY));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getOperator().equals(AvailabilityCondition.Operator.UP));
        assertTrue(eval.getValue().equals(AvailabilityType.UP));
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));
    }

    @Test
    public void jsonCompareConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"COMPARE\"," +
                "\"conditionSetSize\":1,\"conditionSetIndex\":1,\"conditionId\":\"test-test-FIRING-1-1\"," +
                "\"displayString\":\"Default1 LT 120.00% Default2\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        CompareCondition condition = objectMapper.readValue(str, CompareCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default1"));
        assertTrue(condition.getOperator().equals(CompareCondition.Operator.LT));
        assertTrue(condition.getData2Id().equals("Default2"));
        assertTrue(condition.getData2Multiplier() == 1.2d);

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        try {
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LTX\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        try {
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        // Check uncompleted json
        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"dataId\":\"Default1\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Multiplier\":1.2}";
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\"}";
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonCompareConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"COMPARE\","
                + "\"condition\":"
                + "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"COMPARE\","
                + "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2},"
                + "\"value1\":10.0,\"value2\":15.0,"
                + "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"},\"context2\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        CompareConditionEval eval = objectMapper.readValue(str, CompareConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.COMPARE));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default1"));
        assertTrue(eval.getCondition().getOperator().equals(CompareCondition.Operator.LT));
        assertTrue(eval.getValue1().equals(10.0));
        assertTrue(eval.getValue2().equals(15.0));
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));
        assertTrue(eval.getContext2().size() == 2);
        assertTrue(eval.getContext2().get("n1").equals("v1"));
        assertTrue(eval.getContext2().get("n2").equals("v2"));
    }

    @Test
    public void jsonStringConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"STRING\"," +
                "\"conditionSetSize\":1,\"conditionSetIndex\":1,\"conditionId\":\"test-test-FIRING-1-1\"," +
                "\"displayString\":\"Default MATCH [test-pattern]\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        StringCondition condition = objectMapper.readValue(str, StringCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getOperator().equals(StringCondition.Operator.MATCH));
        assertTrue(condition.getPattern().equals("test-pattern"));
        assertFalse(condition.isIgnoreCase());

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        try {
            objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCHX\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        try {
            objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        // Check uncompleted json

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
            condition = objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
            objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"ignoreCase\":false}";
            objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"}";
            objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonStringConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"STRING\"," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"STRING\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}," +
                "\"value\":\"test-value\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        StringConditionEval eval = objectMapper.readValue(str, StringConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.STRING));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getOperator().equals(StringCondition.Operator.MATCH));
        assertTrue(eval.getCondition().getPattern().equals("test-pattern"));
        assertFalse(eval.getCondition().isIgnoreCase());
        assertTrue(eval.getValue().equals("test-value"));
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));
    }

    @Test
    public void jsonThresholdConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"type\":\"THRESHOLD\",\"conditionSetSize\":1,\"conditionSetIndex\":1," +
                "\"conditionId\":\"test-test-FIRING-1-1\"," +
                "\"displayString\":\"Default LT 10.50\"," +
                "\"dataId\":\"Default\",\"operator\":\"LT\",\"threshold\":10.5}";
        ThresholdCondition condition = objectMapper.readValue(str, ThresholdCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getOperator().equals(ThresholdCondition.Operator.LT));
        assertTrue(condition.getThreshold() == 10.5d);

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\"," +
                "\"dataId\":\"Default\",\"operator\":\"LT\",\"threshold\":10.5}";
        try {
            objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operator\":\"LTX\",\"threshold\":10.5}";
        try {
            objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        // Check uncompleted json

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"operator\":\"LT\",\"threshold\":10.5}";
            objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"threshold\":10.5}";
            objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"}";
            objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonThresholdConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"THRESHOLD\"," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"THRESHOLD\"," +
                "\"dataId\":\"Default\",\"operator\":\"LT\",\"threshold\":10.5}," +
                "\"value\":1.0,\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        ThresholdConditionEval eval = objectMapper.readValue(str, ThresholdConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.THRESHOLD));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getOperator().equals(ThresholdCondition.Operator.LT));
        assertTrue(eval.getCondition().getThreshold() == 10.5);
        assertTrue(eval.getValue() == 1.0);
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));
    }

    @Test
    public void jsonThresholdRangeConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"RANGE\"," +
                "\"conditionSetSize\":1,\"conditionSetIndex\":1,\"conditionId\":\"test-test-FIRING-1-1\"," +
                "\"displayString\":\"Default in [10.50 , 20.50]\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        ThresholdRangeCondition condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getOperatorLow().equals(ThresholdRangeCondition.Operator.INCLUSIVE));
        assertTrue(condition.getOperatorHigh().equals(ThresholdRangeCondition.Operator.INCLUSIVE));
        assertTrue(condition.getThresholdLow() == 10.5d);
        assertTrue(condition.getThresholdHigh() == 20.5d);

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw an JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVEX\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVEX\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        // Check uncompleted json
        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                    "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
            condition = objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"operatorHigh\":\"INCLUSIVE\"," +
                    "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
            condition = objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"thresholdHigh\":20.5,\"inRange\":true}";
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                    "\"inRange\":true}";
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }

        try {
            str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"}";
            condition = objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw a JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonThresholdRangeConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"RANGE\"," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"RANGE\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}," +
                "\"value\":1.0,\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        ThresholdRangeConditionEval eval = objectMapper.readValue(str, ThresholdRangeConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.RANGE));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getOperatorLow().equals(ThresholdRangeCondition.Operator.INCLUSIVE));
        assertTrue(eval.getCondition().getOperatorHigh().equals(ThresholdRangeCondition.Operator.INCLUSIVE));
        assertTrue(eval.getCondition().getThresholdLow() == 10.5);
        assertTrue(eval.getCondition().getThresholdHigh() == 20.5);
        assertTrue(eval.getValue() == 1.0);
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));
    }

    @Test
    public void jsonExternalConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\","
                + "\"type\":\"EXTERNAL\", \"conditionId\":\"test-test-FIRING-1-1\",\"conditionSetSize\":1,"
                + "\"conditionSetIndex\":1,\"dataId\":\"Default\",\"alerterId\":\"HawkularMetrics\","
                + "\"expression\":\"metric:5:avg(foo > 100.5)\"}";
        ExternalCondition condition = objectMapper.readValue(str, ExternalCondition.class);

        assertTrue(condition.getType().equals(Condition.Type.EXTERNAL));
        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getAlerterId().equals("HawkularMetrics"));
        assertTrue(condition.getExpression().equals("metric:5:avg(foo > 100.5)"));

        String output = objectMapper.writeValueAsString(condition);
    }

    @Test
    public void jsonExternalConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"EXTERNAL\"," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"EXTERNAL\"," +
                "\"dataId\":\"Default\",\"alerterId\":\"HawkularMetrics\"," +
                "\"expression\":\"metric:5:avg(foo > 100.5)\"}," +
                "\"value\":\"foo\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        ExternalConditionEval eval = objectMapper.readValue(str, ExternalConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.EXTERNAL));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getAlerterId().equals("HawkularMetrics"));
        assertTrue(eval.getCondition().getExpression().equals("metric:5:avg(foo > 100.5)"));
        assertTrue(eval.getValue().equals("foo"));
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));

        // Test with event
        str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"EXTERNAL\"," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"EXTERNAL\"," +
                "\"dataId\":\"Default\",\"alerterId\":\"HawkularMetrics\"," +
                "\"expression\":\"event:test\"}," +
                "\"event\":{\"id\":\"test-event\",\"text\":\"text-value\"},\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        eval = objectMapper.readValue(str, ExternalConditionEval.class);
        assertNotNull(eval.getEvent());
        assertEquals("test-event", eval.getEvent().getId());
        assertEquals("text-value", eval.getEvent().getText());
    }

    @Test
    public void jsonMissingConditionTest() throws Exception {
        String str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"MISSING\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"displayString\":\"Default missing GTE 123ms\"," //
                + "\"dataId\":\"Default\"," //
                + "\"interval\":123}";
        MissingCondition condition = objectMapper.readValue(str, MissingCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getInterval() == 123L);

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));
    }

    @Test
    public void jsonMissingConditionEvalTest() throws Exception {
        String str = "{" //
                + "\"evalTimestamp\":1," //
                + "\"dataTimestamp\":1," //
                + "\"condition\":{" //
                + "  \"triggerId\":\"test\"," //
                + "  \"triggerMode\":\"FIRING\"," //
                + "  \"type\":\"MISSING\"," //
                + "  \"dataId\":\"Default\"," //
                + "  \"interval\":123}," //
                + "\"time\":2,"
                + "\"previousTime\":1"
                + "}";
        MissingConditionEval eval = objectMapper.readValue(str, MissingConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.MISSING));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getInterval() == 123L);
        assertTrue(eval.getTime() == 2);
        assertTrue(eval.getPreviousTime() == 1);
    }

    @Test
    public void jsonNelsonConditionTest() throws Exception {
        String str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"NELSON\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"displayString\":\"Default activeNelsonRules=[Rule6] sampleSize=100\"," //
                + "\"dataId\":\"Default\"," //
                + "\"activeRules\":[\"Rule6\"]," //
                + "\"sampleSize\":100}";
        NelsonCondition condition = objectMapper.readValue(str, NelsonCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getSampleSize() == 100);
        assertTrue(condition.getActiveRules().equals(EnumSet.of(NelsonRule.Rule6)));


        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"NELSON\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"dataId\":\"Default\"}";
        condition = objectMapper.readValue(str, NelsonCondition.class);

        assertTrue(condition.getActiveRules().equals(EnumSet.allOf(NelsonRule.class)));
        assertTrue(condition.getSampleSize() == 50);

        // check bogus value
        str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"NELSON\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"dataId\":\"Default\"," //
                + "\"activeRules\":[\"Rule10\"]";
        try {
            objectMapper.readValue(str, NelsonCondition.class);
            throw new Exception("It should throw an JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonNelsonConditionEvalTest() throws Exception {
        String str = "{" //
                + "\"evalTimestamp\":1," //
                + "\"dataTimestamp\":1," //
                + "\"condition\":{" //
                + "  \"triggerId\":\"test\"," //
                + "  \"triggerMode\":\"FIRING\"," //
                + "  \"type\":\"NELSON\"," //
                + "  \"dataId\":\"Default\"," //
                + "  \"activeRules\":[\"Rule1\",\"Rule6\"]},"
                + "\"mean\":10.0,"
                + "\"standardDeviation\":2.50,"
                + "\"violationsData\":[{\"tenantId\":\"test\",\"id\":\"data-id\",\"timestamp\":1234,\"value\":\"10.0\"}],"
                + "\"violations\":[\"Rule1\"]"
                + "}";
        NelsonConditionEval eval = objectMapper.readValue(str, NelsonConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.NELSON));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getActiveRules().equals(EnumSet.of(NelsonRule.Rule1, NelsonRule.Rule6)));
        assertTrue(eval.getMean() == 10.0);
        assertTrue(eval.getStandardDeviation() == 2.5);
        assertTrue(eval.getViolationsData().size() == 1);
        assertTrue(eval.getViolationsData().get(0).getTenantId().equals("test"));
        assertTrue(eval.getViolationsData().get(0).getId().equals("data-id"));
        assertTrue(eval.getViolationsData().get(0).getTimestamp() == 1234);
        assertTrue(eval.getViolationsData().get(0).getValue().equals("10.0"));
        assertTrue(eval.getViolations().size() == 1);
        assertTrue(eval.getViolations().get(0) == NelsonRule.Rule1);
    }

    @Test
    public void jsonRateConditionTest() throws Exception {
        String str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"RATE\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"displayString\":\"Default DECREASING GT 10.50 per HOUR\","
                + "\"dataId\":\"Default\"," //
                + "\"direction\":\"DECREASING\"," //
                + "\"period\":\"HOUR\"," //
                + "\"operator\":\"GT\"," //
                + "\"threshold\":10.5}";
        RateCondition condition = objectMapper.readValue(str, RateCondition.class);

        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getDirection().equals(RateCondition.Direction.DECREASING));
        assertTrue(condition.getPeriod().equals(Period.HOUR));
        assertTrue(condition.getOperator().equals(RateCondition.Operator.GT));
        assertTrue(condition.getThreshold() == 10.5d);

        String output = objectMapper.writeValueAsString(condition);

        assertTrue(output, str.equals(output));

        // Check defaults
        str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"RATE\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"dataId\":\"Default\"," //
                + "\"operator\":\"GT\"," //
                + "\"threshold\":10.5}";
        condition = objectMapper.readValue(str, RateCondition.class);

        assertTrue(condition.getDirection().equals(Direction.INCREASING));
        assertTrue(condition.getPeriod().equals(Period.MINUTE));

        // check bogus value
        str = "{" //
                + "\"tenantId\":\"test\"," //
                + "\"triggerId\":\"test\"," //
                + "\"triggerMode\":\"FIRING\"," //
                + "\"type\":\"RATE\"," //
                + "\"conditionSetSize\":1," //
                + "\"conditionSetIndex\":1," //
                + "\"conditionId\":\"test-test-FIRING-1-1\"," //
                + "\"dataId\":\"Default\"," //
                + "\"direction\":\"UP\"," //
                + "\"operator\":\"GT\"," //
                + "\"threshold\":10.5}";
        try {
            objectMapper.readValue(str, RateCondition.class);
            throw new Exception("It should throw an JsonProcessingException");
        } catch (JsonProcessingException e) {
            // Expected
        }
    }

    @Test
    public void jsonRateConditionEvalTest() throws Exception {
        String str = "{" //
                + "\"evalTimestamp\":1," //
                + "\"dataTimestamp\":1," //
                + "\"condition\":{" //
                + "  \"triggerId\":\"test\"," //
                + "  \"triggerMode\":\"FIRING\"," //
                + "  \"type\":\"RATE\"," //
                + "  \"dataId\":\"Default\"," //
                + "  \"direction\":\"NA\"," //
                + "  \"period\":\"DAY\"," //
                + "  \"operator\":\"GT\"," //
                + "  \"threshold\":10.5}," //
                + "\"time\":2,"
                + "\"value\":15,"
                + "\"previousTime\":1,"
                + "\"previousValue\":10"
                + "}";
        RateConditionEval eval = objectMapper.readValue(str, RateConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.RATE));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getDirection().equals(Direction.NA));
        assertTrue(eval.getCondition().getPeriod().equals(Period.DAY));
        assertTrue(eval.getCondition().getOperator().equals(RateCondition.Operator.GT));
        assertTrue(eval.getCondition().getThreshold() == 10.5);
        assertTrue(eval.getTime() == 2);
        assertTrue(eval.getValue() == 15.0);
        assertTrue(eval.getPreviousValue() == 10.0);
        assertTrue(eval.getPreviousTime() == 1);
    }

    @Test
    public void jsonDampeningTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"STRICT\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        Dampening damp = objectMapper.readValue(str, Dampening.class);

        assertTrue(damp.getDampeningId().equals("test-test-FIRING"));
        assertTrue(damp.getTriggerId().equals("test"));
        assertTrue(damp.getTriggerMode().equals(Mode.FIRING));
        assertTrue(damp.getType().equals(Dampening.Type.STRICT));
        assertTrue(damp.getEvalTrueSetting() == 1);
        assertTrue(damp.getEvalTotalSetting() == 1);
        assertTrue(damp.getEvalTimeSetting() == 1);

        String output = objectMapper.writeValueAsString(damp);

        // Checking ignored fields are not there

        assertTrue(output.contains("dampeningId"));
        assertTrue(!output.contains("numTrueEvals"));
        assertTrue(!output.contains("numEvals"));
        assertTrue(!output.contains("trueEvalsStartTime"));
        assertTrue(!output.contains("satisfied"));
        assertTrue(!output.contains("satisfyingEvals"));

        // Checking bad fields

        str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\",\"type\":\"STRICT\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        try {
            objectMapper.readValue(str, Dampening.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"STRICTX\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        try {
            objectMapper.readValue(str, Dampening.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }
    }

    @Test
    public void jsonDataTest() throws Exception {
        String str = "{\"id\":\"test\",\"timestamp\":1,\"value\":\"UP\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        Data aData = objectMapper.readValue(str, Data.class);

        assertTrue(aData.getId().equals("test"));
        assertTrue(aData.getTimestamp() == 1);
        assertTrue(AvailabilityType.valueOf(aData.getValue()).equals(AvailabilityType.UP));
        assertTrue(aData.getContext() != null);
        assertTrue(aData.getContext().size() == 2);
        assertTrue(aData.getContext().get("n1").equals("v1"));
        assertTrue(aData.getContext().get("n2").equals("v2"));

        String output = objectMapper.writeValueAsString(aData);
        assertTrue(output.contains("UP"));
        assertTrue(output.contains("n1"));
        assertTrue(output.contains("v1"));

        str = "{\"id\":\"test\",\"timestamp\":1,\"value\":10.45,\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        Data nData = objectMapper.readValue(str, Data.class);

        assertTrue(nData.getId().equals("test"));
        assertTrue(nData.getTimestamp() == 1);
        assertTrue(Double.valueOf(nData.getValue()) == 10.45);
        assertTrue(nData.getContext() != null);
        assertTrue(nData.getContext().size() == 2);
        assertTrue(nData.getContext().get("n1").equals("v1"));
        assertTrue(nData.getContext().get("n2").equals("v2"));

        output = objectMapper.writeValueAsString(nData);

        assertTrue(output.contains("10.45"));
        assertTrue(output.contains("n1"));
        assertTrue(output.contains("v1"));

        str = "{\"id\":\"test\",\"timestamp\":1,\"value\":\"test-value\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        Data sData = objectMapper.readValue(str, Data.class);

        assertTrue(sData.getId().equals("test"));
        assertTrue(sData.getTimestamp() == 1);
        assertTrue(sData.getValue().equals("test-value"));
        assertTrue(sData.getContext() != null);
        assertTrue(sData.getContext().size() == 2);
        assertTrue(sData.getContext().get("n1").equals("v1"));
        assertTrue(sData.getContext().get("n2").equals("v2"));

        output = objectMapper.writeValueAsString(sData);

        assertTrue(output.contains("test-value"));
        assertTrue(output.contains("n1"));
        assertTrue(output.contains("v1"));
    }

    @Test
    public void jsonTriggerTest() throws Exception {
        String str = "{\"name\":\"test-name\",\"description\":\"test-description\"," +
                "\"actions\":[" +
                    "{\"actionPlugin\":\"plugin1\",\"actionId\":\"uno\"}," +
                    "{\"actionPlugin\":\"plugin1\",\"actionId\":\"dos\"}," +
                    "{\"actionPlugin\":\"plugin1\",\"actionId\":\"tres\"}" +
                "]," +
                "\"firingMatch\":\"ALL\"," +
                "\"autoResolveMatch\":\"ALL\"," +
                "\"id\":\"test\"," +
                "\"enabled\":true," +
                "\"autoDisable\":true," +
                "\"autoEnable\":true," +
                "\"autoResolve\":true," +
                "\"autoResolveAlerts\":true," +
                "\"severity\":\"HIGH\"," +
                "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        Trigger trigger = objectMapper.readValue(str, Trigger.class);

        assertTrue(trigger.getName().equals("test-name"));
        assertTrue(trigger.getDescription().equals("test-description"));
        assertEquals(3, trigger.getActions().size());
        assertTrue(trigger.getFiringMatch().equals(Match.ALL));
        assertTrue(trigger.getAutoResolveMatch().equals(Match.ALL));
        assertTrue(trigger.getId().equals("test"));
        assertTrue(trigger.isEnabled());
        assertTrue(trigger.isAutoDisable());
        assertTrue(trigger.isAutoEnable());
        assertTrue(trigger.isAutoResolve());
        assertTrue(trigger.isAutoResolveAlerts());
        assertTrue(trigger.getSeverity() == Severity.HIGH);
        assertTrue(trigger.getContext() != null);
        assertTrue(trigger.getContext().size() == 2);
        assertTrue(trigger.getContext().get("n1").equals("v1"));
        assertTrue(trigger.getContext().get("n2").equals("v2"));

        String output = objectMapper.writeValueAsString(trigger);

        assertTrue(!output.contains("mode"));
        assertTrue(!output.contains("match"));
    }

    @Test
    public void jsonTriggerNullValuesTest() throws Exception {
        String str = "{\"name\":\"test-name\"," +
                "\"description\":null," +
                "\"actions\":null," +
                "\"firingMatch\":null," +
                "\"autoResolveMatch\":null," +
                "\"id\":null," +
                "\"enabled\":true," +
                "\"autoDisable\":null," +
                "\"autoEnable\":null," +
                "\"autoResolve\":null," +
                "\"autoResolveAlerts\":null," +
                "\"severity\":null," +
                "\"context\":null}";
        Trigger trigger = objectMapper.readValue(str, Trigger.class);

        assertTrue(trigger.getName().equals("test-name"));
        assertNull(trigger.getDescription());
        assertNotNull(trigger.getActions());
        assertTrue(trigger.getFiringMatch().equals(Match.ALL));
        assertTrue(trigger.getAutoResolveMatch().equals(Match.ALL));
        assertNull(trigger.getId());
        assertTrue(trigger.isEnabled());
        assertFalse(trigger.isAutoDisable());
        assertFalse(trigger.isAutoEnable());
        assertFalse(trigger.isAutoResolve());
        assertFalse(trigger.isAutoResolveAlerts());
        assertTrue(trigger.getSeverity() == Severity.MEDIUM);
        assertNotNull(trigger.getContext());
    }

    @Test
    public void jsonTriggerMatchAnyTest() throws Exception {
        String str = "{\"name\":\"test-name\",\"description\":\"test-description\"," +
                "\"actions\":[" +
                    "{\"actionPlugin\":\"plugin1\",\"actionId\":\"uno\"}," +
                    "{\"actionPlugin\":\"plugin1\",\"actionId\":\"dos\"}," +
                    "{\"actionPlugin\":\"plugin1\",\"actionId\":\"tres\"}" +
                "]," +
                "\"firingMatch\":\"ANY\"," +
                "\"autoResolveMatch\":\"ALL\"," +
                "\"id\":\"test\"," +
                "\"enabled\":true," +
                "\"autoDisable\":true," +
                "\"autoEnable\":true," +
                "\"autoResolve\":true," +
                "\"autoResolveAlerts\":true," +
                "\"severity\":\"HIGH\"}";
        Trigger trigger = objectMapper.readValue(str, Trigger.class);

        assertEquals(Match.ANY, trigger.getMatch());

        assertTrue(trigger.getName().equals("test-name"));
        assertTrue(trigger.getDescription().equals("test-description"));
        assertEquals(3, trigger.getActions().size());
        assertTrue(trigger.getFiringMatch().equals(Match.ANY));
        assertTrue(trigger.getAutoResolveMatch().equals(Match.ALL));
        assertTrue(trigger.getId().equals("test"));
        assertTrue(trigger.isEnabled());
        assertTrue(trigger.isAutoDisable());
        assertTrue(trigger.isAutoEnable());
        assertTrue(trigger.isAutoResolve());
        assertTrue(trigger.isAutoResolveAlerts());
        assertTrue(trigger.getSeverity() == Severity.HIGH);

        String output = objectMapper.writeValueAsString(trigger);

        assertTrue(!output.contains("mode"));
        assertTrue(!output.contains("match"));
    }

    @Test
    public void jsonTriggerMinimalParametersTest() throws Exception {
        String str = "{}";
        Trigger trigger = objectMapper.readValue(str, Trigger.class);

        assertNotNull(trigger.getId().equals("test"));
        assertTrue(trigger.getName().equals("defaultName"));
        assertNull(trigger.getDescription());
        assertEquals(0, trigger.getActions().size());
        assertTrue(trigger.getFiringMatch().equals(Match.ALL));
        assertTrue(trigger.getAutoResolveMatch().equals(Match.ALL));

        assertFalse(trigger.isEnabled());
        assertFalse(trigger.isAutoDisable());
        assertFalse(trigger.isAutoEnable());
        assertFalse(trigger.isAutoResolve());
        assertTrue(trigger.isAutoResolveAlerts());
        assertFalse(trigger.isGroup());
        assertFalse(trigger.isMember());
        assertFalse(trigger.isOrphan());
        assertEquals(Severity.MEDIUM, trigger.getSeverity());
        assertNotNull(trigger.getContext());
        assertTrue(trigger.getContext().isEmpty());

        String output = objectMapper.writeValueAsString(trigger);

        assertTrue(output.contains("defaultName"));
        assertTrue(!output.contains("match"));
    }

    @Test
    public void jsonComplexActionTest() throws Exception {
        String str = "{" +
                "\"tenantId\":\"my-organization\"," +
                "\"actionPlugin\":\"email\"," +
                "\"actionId\":\"email-to-admin-group\"," +
                "\"eventId\":\"chained-trigger-1447150834164\"," +
                "\"ctime\":1447150834164," +
                "\"event\":{" +
                    "\"eventType\":\"ALERT\"," +
                    "\"tenantId\":\"my-organization\"," +
                    "\"id\":\"chained-trigger-1447150834164\"," +
                    "\"ctime\":1447150834164," +
                    "\"dataId\":\"chained-trigger\"," +
                    "\"category\":\"ALERT\"," +
                    "\"text\":\"Show how to define a trigger using Events generated from other trigger\"," +
                    "\"trigger\":{" +
                        "\"tenantId\":\"my-organization\"," +
                        "\"id\":\"chained-trigger\"," +
                        "\"name\":\"Chained trigger\"," +
                        "\"description\":\"Show how to define a trigger using Events generated from other trigger\"," +
                        "\"eventType\":\"ALERT\"," +
                        "\"eventCategory\":null," +
                        "\"eventText\":null," +
                        "\"severity\":\"HIGH\"," +
                        "\"actions\":[{\"actionPlugin\":\"email\",\"actionId\":\"email-to-admin-group\"}]," +
                        "\"autoDisable\":false," +
                        "\"autoEnable\":false," +
                        "\"autoResolve\":false," +
                        "\"autoResolveAlerts\":false," +
                        "\"autoResolveMatch\":\"ALL\"," +
                        "\"enabled\":true," +
                        "\"firingMatch\":\"ALL\"," +
                        "\"type\":\"STANDARD\"" +
                    "}," +
                    "\"dampening\":{" +
                        "\"tenantId\":\"my-organization\"," +
                        "\"triggerId\":\"chained-trigger\"," +
                        "\"triggerMode\":\"FIRING\"," +
                        "\"type\":\"STRICT\"," +
                        "\"evalTrueSetting\":1," +
                        "\"evalTotalSetting\":1," +
                        "\"evalTimeSetting\":0," +
                        "\"dampeningId\":\"my-organization-chained-trigger-FIRING\"" +
                    "}," +
                    // List<Set<ConditionEval>>
                    "\"evalSets\":[" +  // Open List
                        "[" +   // Open Set
                            "{" +   // ConditionEval
                                "\"evalTimestamp\":1447150834163," +
                                "\"dataTimestamp\":1447150834163," +
                                "\"type\":\"EVENT\"," +
                                "\"condition\":{" +
                                    "\"tenantId\":\"my-organization\"," +
                                    "\"triggerId\":\"chained-trigger\"," +
                                    "\"triggerMode\":\"FIRING\"," +
                                    "\"type\":\"EVENT\"," +
                                    "\"conditionSetSize\":1," +
                                    "\"conditionSetIndex\":1," +
                                    "\"conditionId\":\"my-organization-chained-trigger-FIRING-1-1\"," +
                                    "\"dataId\":\"detect-undeployment-containerZ-with-errors\"" +
                                "}," +
                                "\"value\":{" +
                                    "\"eventType\":\"ALERT\"," +
                                    "\"tenantId\":\"my-organization\"," +
                                    "\"id\":\"detect-undeployment-containerZ-with-errors-1447150834163\"," +
                                    "\"ctime\":1447150834163," +
                                    "\"dataId\":\"detect-undeployment-containerZ-with-errors\"," +
                                    "\"category\":\"ALERT\"," +
                                    "\"text\":\"Detect undeployments on containerZ with log errors\"," +
                                    "\"trigger\":{" +
                                        "\"tenantId\":\"my-organization\"," +
                                        "\"id\":\"detect-undeployment-containerZ-with-errors\"," +
                                        "\"name\":\"Undeployments detection with Errors\"," +
                                        "\"description\":\"Detect undeployments on containerZ with log errors\"," +
                                        "\"eventType\":\"ALERT\"," +
                                        "\"eventCategory\":null," +
                                        "\"eventText\":null," +
                                        "\"severity\":\"HIGH\"," +
                                        "\"autoDisable\":false," +
                                        "\"autoEnable\":false," +
                                        "\"autoResolve\":false," +
                                        "\"autoResolveAlerts\":false," +
                                        "\"autoResolveMatch\":\"ALL\"," +
                                        "\"enabled\":true," +
                                        "\"firingMatch\":\"ALL\"," +
                                        "\"type\":\"STANDARD\"" +
                                    "}," +
                                    "\"dampening\":{" +
                                    "\"tenantId\":\"my-organization\"," +
                                        "\"triggerId\":\"detect-undeployment-containerZ-with-errors\"," +
                                        "\"triggerMode\":\"FIRING\"," +
                                        "\"type\":\"STRICT\"," +
                                        "\"evalTrueSetting\":1," +
                                        "\"evalTotalSetting\":1," +
                                        "\"evalTimeSetting\":0," +
                                        "\"dampeningId\":\"my-organization-" +
                                                          "detect-undeployment-containerZ-with-errors-FIRING\"" +
                                    "}," +
                                    "\"evalSets\":[" +  // Open List
                                        "[" +   // Open Set
                                            "{" +   // Open ConditionEval
                                                "\"evalTimestamp\":1447150834162," +
                                                "\"dataTimestamp\":1447150832," +
                                                "\"type\":\"EVENT\"," +
                                                "\"condition\":{" +
                                                    "\"tenantId\":\"my-organization\"," +
                                                    "\"triggerId\":\"detect-undeployment-containerZ-with-errors\"," +
                                                    "\"triggerMode\":\"FIRING\"," +
                                                    "\"type\":\"EVENT\"," +
                                                    "\"conditionSetSize\":2," +
                                                    "\"conditionSetIndex\":2," +
                                                    "\"conditionId\":\"my-organization-" +
                                                                      "detect-undeployment-containerZ-with-errors-" +
                                                                      "FIRING-2-2\"," +
                                                    "\"dataId\":\"events-logs-source\"," +
                                                    "\"expression\":\"text starts 'ERROR'\"}," +
                                                "\"value\":{" +
                                                    "\"eventType\":\"EVENT\"," +
                                                    "\"eventType\":null," +
                                                    "\"tenantId\":\"my-organization\"," +
                                                    "\"id\":\"67892015-7ef8-42a3-ae5c-efd9782ec040\"," +
                                                    "\"ctime\":1447150832," +
                                                    "\"dataId\":\"events-logs-source\"," +
                                                    "\"category\":\"LOG\"," +
                                                    "\"text\":\"ERROR [org.hawkular.alerts.actions.api] " +
                                                                "(ServerService Thread Pool -- 62) " +
                                                                "HAWKALERT240006: Plugin [aerogear] " +
                                                                "cannot be started. " +
                                                                "Error: " +
                                                                "[Configure org.hawkular.alerts." +
                                                                "actions.aerogear.root." +
                                                                "server.url, org.hawkular.alerts.actions.aerogear." +
                                                                "application.id and " +
                                                                "org.hawkular.alerts.actions." +
                                                                "aerogear.master.secret]\"," +
                                                    "\"tags\":{\"app\":\"appA\"}" +
                                                "}" +
                                            "}," + // Close Condition Eval
                                            "{" + // Open Condition Eval
                                                "\"evalTimestamp\":1447150834163," +
                                                "\"dataTimestamp\":1447150832," +
                                                "\"type\":\"EVENT\"," +
                                                "\"condition\":{" +
                                                    "\"tenantId\":\"my-organization\"," +
                                                    "\"triggerId\":\"detect-undeployment-containerZ-with-errors\"," +
                                                    "\"triggerMode\":\"FIRING\"," +
                                                    "\"type\":\"EVENT\"," +
                                                    "\"conditionSetSize\":2," +
                                                    "\"conditionSetIndex\":1," +
                                                    "\"conditionId\":\"my-organization-" +
                                                                      "detect-undeployment-containerZ-with-errors-" +
                                                                      "FIRING-2-1\"," +
                                                    "\"dataId\":\"events-deployments-source\"," +
                                                    "\"expression\":\"tags.operation == 'undeployment'," +
                                                                "tags.container == 'containerZ'\"" +
                                                "}," + // End condition
                                                "\"value\":{" +
                                                    "\"eventType\":\"EVENT\"," +
                                                    "\"tenantId\":\"my-organization\"," +
                                                    "\"id\":\"4831ae55-967a-4aac-a1dd-c9dc6f37e51f\"," +
                                                    "\"ctime\":1447150832," +
                                                    "\"dataId\":\"events-deployments-source\"," +
                                                    "\"category\":\"DEPLOYMENT\"," +
                                                    "\"text\":\"undeployment of appA on containerZ\"," +
                                                    "\"tags\":{" +
                                                        "\"operation\":\"undeployment\"," +
                                                        "\"app\":\"appA\"," +
                                                        "\"container\":\"containerZ\"}" +
                                                "}" +
                                            "}" + // Close Condition Eval
                                        "]" + // Close Set
                                    "]," + // Close List
                                    "\"severity\":\"HIGH\"," +
                                    "\"status\":\"OPEN\"" +
                                "}" + // End value
                            "}" + // End Condition Eval
                        "]" +   // End Set
                    "]," +  // End List
                    "\"severity\":\"HIGH\"," +
                    "\"status\":\"OPEN\"" +
                    "}," +
                "\"properties\":{" +
                    "\"cc\":\"cc-group@hawkular.org\"," +
                    "\"template.html\":\"\"," +
                    "\"template.plain\":\"\"," +
                    "\"from-name\":\"Hawkular\"," +
                    "\"template.hawkular.url\":\"http://www.hawkular.org\"," +
                    "\"tenantId\":\"my-organization\"," +
                    "\"actionId\":\"email-to-admin-group\"," +
                    "\"from\":\"noreply@hawkular.org\"," +
                    "\"to\":\"admin-group@hawkular.org\"," +
                    "\"actionPlugin\":\"email\"}," +
                "\"result\":\"PROCESSED\"" +
        "}";

        Action action = objectMapper.readValue(str, Action.class);
        assertTrue(action.getEvent().getEvalSets() != null);
    }

    @Test
    public void jsonTriggerAction() throws Exception {
        String str = "{" +
                "\"tenantId\":\"tenant\"," +
                "\"actionPlugin\":\"plugin\"," +
                "\"actionId\":\"action\"" +
                "}";
        TriggerAction triggerAction = objectMapper.readValue(str, TriggerAction.class);
        assertEquals("tenant", triggerAction.getTenantId());
        assertEquals("plugin", triggerAction.getActionPlugin());
        assertEquals("action", triggerAction.getActionId());
        assertNull(triggerAction.getCalendar());
    }

    @Test
    public void jsonTimeConstraints() throws Exception {

        String jsonTrigger1 = "{" +
                "\"name\":\"calendar-trigger1\"," +
                "\"description\":\"An email action that sends mail to 'admins' " +
                                  "but from 00:00-06:00 it sends to 'on-call'.\"," +
                "\"actions\":[" +
                        "{" +
                            "\"actionPlugin\":\"email\"," +
                            "\"actionId\":\"admins\"" +     // Admins is always generated
                        "}," +
                        "{" +
                            "\"actionPlugin\":\"sms\"," +
                            "\"actionId\":\"on-call\"," +
                            "\"calendar\":{" +
                                "\"startTime\":\"00:00\"," +
                                "\"endTime\":\"06:00\"" +
                            "}" +
                        "}" +
                    "]" +
                "}";

        Trigger trigger1 = objectMapper.readValue(jsonTrigger1, Trigger.class);
        assertEquals(2, trigger1.getActions().size());

        String jsonTrigger2 = "{" +
                "\"name\":\"calendar-trigger2\"," +
                "\"description\":\"An email action that sends mail to 'admins' on weekdays" +
                                  "but on weekends to 'on-call'.\"," +
                "\"actions\":[" +
                        "{" +
                            "\"actionPlugin\":\"email\"," +
                            "\"actionId\":\"admins\"," +
                            "\"calendar\":{" +              // From Monday 09:00 to Friday 18:00
                                "\"startTime\":\"Monday,09:00\"," +
                                "\"endTime\":\"Friday,18:00\"" +
                            "}" +
                        "}," +
                        "{" +
                            "\"actionPlugin\":\"sms\"," +
                            "\"actionId\":\"on-call\"," +
                            "\"calendar\":{" +              // From Friday 18:00 to Monday 09:00
                                "\"startTime\":\"Friday,18:00\"," +
                                "\"endTime\":\"Monday,09:00\"" +
                            "}" +
                        "}" +
                    "]" +
                "}";

        Trigger trigger2 = objectMapper.readValue(jsonTrigger2, Trigger.class);
        assertEquals(2, trigger2.getActions().size());

        String jsonTrigger3 = "{" +
                "\"name\":\"calendar-trigger3\"," +
                "\"description\":\"A sms action that is active only for alerts created from 22:00-06:00\"," +
                "\"actions\":[" +
                        "{" +
                            "\"actionPlugin\":\"sms\"," +
                            "\"actionId\":\"on-call\"," +
                            "\"calendar\":{" +
                                "\"startTime\":\"22:00\"," +
                                "\"endTime\":\"06:00\"" +
                            "}" +
                        "}" +
                    "]" +
                "}";

        Trigger trigger3 = objectMapper.readValue(jsonTrigger3, Trigger.class);
        assertEquals(2, trigger2.getActions().size());
    }

}
