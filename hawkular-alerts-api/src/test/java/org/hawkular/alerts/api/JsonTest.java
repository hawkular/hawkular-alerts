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
package org.hawkular.alerts.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.json.JacksonDeserializer;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
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
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Availability.AvailabilityType;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.data.StringData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate.Match;
import org.junit.Before;
import org.junit.Test;

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
                "\"actionId\":\"test\",\"message\":\"test-msg\"}";
        Action action = objectMapper.readValue(str, Action.class);

        assertEquals("tenantTest", action.getTenantId());
        assertEquals("plugin", action.getActionPlugin());
        assertEquals("test", action.getActionId());
        assertEquals("test-msg", action.getMessage());

        String output = objectMapper.writeValueAsString(action);

        assertEquals(str, output);

        str = "{\"actionId\":\"test\"}";
        action = objectMapper.readValue(str, Action.class);
        output = objectMapper.writeValueAsString(action);

        assertTrue(!output.contains("message"));
    }

    @Test
    public void jsonAlertTest() throws Exception {
        Alert alert = new Alert(TEST_TENANT, "trigger-test", Severity.MEDIUM, null);

        String output = objectMapper.writeValueAsString(alert);

        assertTrue(!output.contains("evalSets"));

        AvailabilityCondition aCond = new AvailabilityCondition("trigger-test",
                "Default",
                AvailabilityCondition.Operator.UP);
        Availability aData = new Availability("Metric-test", 1, AvailabilityType.UP);
        AvailabilityConditionEval aEval = new AvailabilityConditionEval(aCond, aData);

        ThresholdCondition tCond = new ThresholdCondition("trigger-test",
                "Default",
                ThresholdCondition.Operator.LTE,
                50.0);
        NumericData tData = new NumericData("Metric-test2", 2, 25.5);
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
                "\"alertId\":\"trigger-test|1436964192878\"," +
                "\"triggerId\":\"trigger-test\"," +
                "\"ctime\":1436964192878," +
                "\"evalSets\":[" +
                    "[{\"evalTimestamp\":1436964294055," +
                        "\"dataTimestamp\":2," +
                        "\"type\":\"THRESHOLD\"," +
                        "\"condition\":{\"tenantId\":null," +
                                        "\"triggerId\":\"trigger-test\"," +
                                        "\"triggerMode\":\"FIRING\"," +
                                        "\"type\":\"THRESHOLD\"," +
                                        "\"conditionId\":\"trigger-test-FIRING-1-1\"," +
                                        "\"dataId\":\"Default\"," +
                                        "\"operator\":\"LTE\"," +
                                        "\"threshold\":50.0" +
                                        "}," +
                        "\"value\":25.5}," +
                    "{\"evalTimestamp\":1436964284965," +
                        "\"dataTimestamp\":1," +
                        "\"type\":\"AVAILABILITY\"," +
                        "\"condition\":{\"tenantId\":null," +
                                        "\"triggerId\":\"trigger-test\"," +
                                        "\"triggerMode\":\"FIRING\"," +
                                        "\"type\":\"AVAILABILITY\"," +
                                        "\"conditionId\":\"trigger-test-FIRING-1-1\"," +
                                        "\"dataId\":\"Default\"," +
                                        "\"operator\":\"UP\"" +
                                        "}," +
                        "\"value\":\"UP\"}]" +
                    "]," +
                "\"severity\":\"MEDIUM\"," +
                "\"status\":\"OPEN\"," +
                "\"ackTime\":0," +
                "\"ackBy\":null," +
                "\"ackNotes\":null," +
                "\"resolvedTime\":0," +
                "\"resolvedBy\":null," +
                "\"resolvedNotes\":null," +
                "\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";

        ObjectMapper mapper = new ObjectMapper();
        Alert alert = mapper.readValue(jsonAlert, Alert.class);
        assertNotNull(alert);
        assertNotNull(alert.getEvalSets());
        assertEquals(1, alert.getEvalSets().size());
        assertEquals(2, alert.getEvalSets().get(0).size());
        assertTrue(alert.getContext() != null);
        assertTrue(alert.getContext().size() == 2);
        assertTrue(alert.getContext().get("n1").equals("v1"));
        assertTrue(alert.getContext().get("n2").equals("v2"));

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
    public void jsonAvailabilityConditionTest() throws Exception {
        String str = "{\"tenantId\":\"test\",\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"type\":\"AVAILABILITY\",\"conditionSetSize\":1,\"conditionSetIndex\":1," +
                "\"conditionId\":\"test-FIRING-1-1\",\"dataId\":\"Default\",\"operator\":\"UP\"}";
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
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"dataId\":\"Default\",\"operator\":\"UPX\"}";
        try {
            objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"dataId\":\"Default\"}";
        condition = objectMapper.readValue(str, AvailabilityCondition.class);

        assertTrue(condition.getOperator() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("operator"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"operator\":\"UP\"}";
        condition = objectMapper.readValue(str, AvailabilityCondition.class);

        assertTrue(condition.getDataId() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("dataId"));
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
                "\"conditionSetSize\":1,\"conditionSetIndex\":1,\"conditionId\":\"test-FIRING-1-1\"," +
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
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LTX\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        try {
            objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        condition = objectMapper.readValue(str, CompareCondition.class);

        assertTrue(condition.getDataId() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("dataId"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default1\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        condition = objectMapper.readValue(str, CompareCondition.class);

        assertTrue(condition.getOperator() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("operator"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Multiplier\":1.2}";

        condition = objectMapper.readValue(str, CompareCondition.class);

        assertTrue(condition.getData2Id() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("data2Id"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\"}";

        condition = objectMapper.readValue(str, CompareCondition.class);

        assertTrue(condition.getData2Multiplier() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("data2Multiplier"));
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
                "\"conditionSetSize\":1,\"conditionSetIndex\":1,\"conditionId\":\"test-FIRING-1-1\"," +
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
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCHX\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        try {
            objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assertTrue(condition.getDataId() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("dataId"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assertTrue(condition.getOperator() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("operator"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"ignoreCase\":false}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assertTrue(condition.getPattern() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("pattern"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assertFalse(condition.isIgnoreCase());

        output = objectMapper.writeValueAsString(condition);

        // ignoreCase will be present as it is a boolean with default value
        assertTrue(output.contains("ignoreCase"));
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
                "\"conditionId\":\"test-FIRING-1-1\"," +
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
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operator\":\"LTX\",\"threshold\":10.5}";
        try {
            objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"operator\":\"LT\",\"threshold\":10.5}";
        condition = objectMapper.readValue(str, ThresholdCondition.class);

        assertTrue(condition.getDataId() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("dataId"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"threshold\":10.5}";
        condition = objectMapper.readValue(str, ThresholdCondition.class);

        assertTrue(condition.getOperator() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("operator"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"}";
        condition = objectMapper.readValue(str, ThresholdCondition.class);

        assertTrue(condition.getThreshold() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("threshold"));
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
                "\"conditionSetSize\":1,\"conditionSetIndex\":1,\"conditionId\":\"test-FIRING-1-1\"," +
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
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVEX\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVEX\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertTrue(condition.getDataId() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("dataId"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertTrue(condition.getOperatorLow() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("operatorLow"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertTrue(condition.getOperatorHigh() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("operatorHigh"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertTrue(condition.getThresholdLow() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("thresholdLow"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"," +
                "\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertTrue(condition.getThresholdHigh() == null);

        output = objectMapper.writeValueAsString(condition);

        assertTrue(!output.contains("thresholdHigh"));

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\"}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assertFalse(condition.isInRange());

        output = objectMapper.writeValueAsString(condition);

        assertTrue(output.contains("inRange"));
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
                + "\"type\":\"EXTERNAL\", \"conditionId\":\"test-FIRING-1-1\",\"conditionSetSize\":1,"
                + "\"conditionSetIndex\":1,\"dataId\":\"Default\",\"systemId\":\"HawkularMetrics\","
                + "\"expression\":\"metric:5:avg(foo > 100.5)\"}";
        ExternalCondition condition = objectMapper.readValue(str, ExternalCondition.class);

        assertTrue(condition.getType().equals(Condition.Type.EXTERNAL));
        assertTrue(condition.getTenantId().equals("test"));
        assertTrue(condition.getTriggerId().equals("test"));
        assertTrue(condition.getTriggerMode().equals(Mode.FIRING));
        assertTrue(condition.getDataId().equals("Default"));
        assertTrue(condition.getSystemId().equals("HawkularMetrics"));
        assertTrue(condition.getExpression().equals("metric:5:avg(foo > 100.5)"));

        String output = objectMapper.writeValueAsString(condition);
    }

    @Test
    public void jsonExternalConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1,\"type\":\"EXTERNAL\"," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"EXTERNAL\"," +
                "\"dataId\":\"Default\",\"systemId\":\"HawkularMetrics\"," +
                "\"expression\":\"metric:5:avg(foo > 100.5)\"}," +
                "\"value\":\"foo\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        ExternalConditionEval eval = objectMapper.readValue(str, ExternalConditionEval.class);

        assertTrue(eval.getEvalTimestamp() == 1);
        assertTrue(eval.getDataTimestamp() == 1);
        assertTrue(eval.getCondition().getType().equals(Condition.Type.EXTERNAL));
        assertTrue(eval.getCondition().getTriggerId().equals("test"));
        assertTrue(eval.getCondition().getTriggerMode().equals(Mode.FIRING));
        assertTrue(eval.getCondition().getDataId().equals("Default"));
        assertTrue(eval.getCondition().getSystemId().equals("HawkularMetrics"));
        assertTrue(eval.getCondition().getExpression().equals("metric:5:avg(foo > 100.5)"));
        assertTrue(eval.getValue().equals("foo"));
        assertTrue(eval.getContext().size() == 2);
        assertTrue(eval.getContext().get("n1").equals("v1"));
        assertTrue(eval.getContext().get("n2").equals("v2"));
    }

    @Test
    public void jsonDampeningTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"STRICT\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        Dampening damp = objectMapper.readValue(str, Dampening.class);

        assertTrue(damp.getDampeningId().equals("test-FIRING"));
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

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRINGX\",\"type\":\"STRICT\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        try {
            objectMapper.readValue(str, Dampening.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (InvalidFormatException e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRING\",\"type\":\"STRICTX\"," +
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
        Availability aData = objectMapper.readValue(str, Availability.class);

        assertTrue(aData.getId().equals("test"));
        assertTrue(aData.getTimestamp() == 1);
        assertTrue(aData.getValue().equals(AvailabilityType.UP));
        assertTrue(aData.getContext() != null);
        assertTrue(aData.getContext().size() == 2);
        assertTrue(aData.getContext().get("n1").equals("v1"));
        assertTrue(aData.getContext().get("n2").equals("v2"));

        String output = objectMapper.writeValueAsString(aData);

        assertTrue(output.contains("type"));
        assertTrue(output.contains("AVAILABILITY"));
        assertTrue(output.contains("n1"));
        assertTrue(output.contains("v1"));

        str = "{\"id\":\"test\",\"timestamp\":1,\"value\":10.45,\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        NumericData nData = objectMapper.readValue(str, NumericData.class);

        assertTrue(nData.getId().equals("test"));
        assertTrue(nData.getTimestamp() == 1);
        assertTrue(nData.getValue() == 10.45);
        assertTrue(nData.getContext() != null);
        assertTrue(nData.getContext().size() == 2);
        assertTrue(nData.getContext().get("n1").equals("v1"));
        assertTrue(nData.getContext().get("n2").equals("v2"));

        output = objectMapper.writeValueAsString(nData);

        assertTrue(output.contains("type"));
        assertTrue(output.contains("NUMERIC"));
        assertTrue(output.contains("n1"));
        assertTrue(output.contains("v1"));

        str = "{\"id\":\"test\",\"timestamp\":1,\"value\":\"test-value\",\"context\":{\"n1\":\"v1\",\"n2\":\"v2\"}}";
        StringData sData = objectMapper.readValue(str, StringData.class);

        assertTrue(sData.getId().equals("test"));
        assertTrue(sData.getTimestamp() == 1);
        assertTrue(sData.getValue().equals("test-value"));
        assertTrue(sData.getContext() != null);
        assertTrue(sData.getContext().size() == 2);
        assertTrue(sData.getContext().get("n1").equals("v1"));
        assertTrue(sData.getContext().get("n2").equals("v2"));

        output = objectMapper.writeValueAsString(sData);

        assertTrue(output.contains("type"));
        assertTrue(output.contains("STRING"));
        assertTrue(output.contains("n1"));
        assertTrue(output.contains("v1"));
    }

    @Test
    public void jsonTriggerTest() throws Exception {
        String str = "{\"name\":\"test-name\",\"description\":\"test-description\"," +
                "\"actions\":{\"plugin1\":[\"uno\",\"dos\",\"tres\"]}," +
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
        assertEquals(1, trigger.getActions().size());
        assertEquals(3, trigger.getActions().get("plugin1").size());
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
    public void jsonTriggerMatchAnyTest() throws Exception {
        String str = "{\"name\":\"test-name\",\"description\":\"test-description\"," +
                "\"actions\":{\"plugin1\":[\"uno\",\"dos\",\"tres\"]}," +
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
        assertEquals(1, trigger.getActions().size());
        assertEquals(3, trigger.getActions().get("plugin1").size());
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

}