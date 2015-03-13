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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
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

/**
 * Validation of JSON serialization/deserialization
 *
 * @author Lucas Ponce
 */
public class JsonTest {

    ObjectMapper objectMapper;

    @Before
    public void before() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void jsonActionTest() throws Exception {
        String str = "{\"actionId\":\"test\",\"message\":\"test-msg\"}";
        Action action = objectMapper.readValue(str, Action.class);

        assert action.getActionId().equals("test");
        assert action.getMessage().equals("test-msg");

        String output = objectMapper.writeValueAsString(action);

        assert str.equals(output);

        str = "{\"actionId\":\"test\"}";
        action = objectMapper.readValue(str, Action.class);
        output = objectMapper.writeValueAsString(action);

        assert !output.contains("message");
    }

    @Test
    public void jsonAlertTest() throws Exception {
        Alert alert = new Alert("trigger-test", null);

        String output = objectMapper.writeValueAsString(alert);

        assert !output.contains("evalSets");

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

        assert output.contains("evalSets");
    }

    @Test
    public void jsonAvailabilityConditionTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"AVAILABILITY\"," +
                "\"dataId\":\"Default\",\"operator\":\"UP\"}";
        AvailabilityCondition condition = objectMapper.readValue(str, AvailabilityCondition.class);

        assert condition.getTriggerId().equals("test");
        assert condition.getTriggerMode().equals(Mode.FIRE);
        assert condition.getDataId().equals("Default");
        assert condition.getOperator().equals(AvailabilityCondition.Operator.UP);

        String output = objectMapper.writeValueAsString(condition);

        assert str.equals(output);

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIREX\",\"dataId\":\"Default\",\"operator\":\"UP\"}";
        try {
            condition = objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"dataId\":\"Default\",\"operator\":\"UPX\"}";
        try {
            condition = objectMapper.readValue(str, AvailabilityCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"dataId\":\"Default\"}";
        condition = objectMapper.readValue(str, AvailabilityCondition.class);

        assert condition.getOperator() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("operator");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"operator\":\"UP\"}";
        condition = objectMapper.readValue(str, AvailabilityCondition.class);

        assert condition.getDataId() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("dataId");
    }

    @Test
    public void jsonAvailabilityConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"dataId\":\"Default\",\"operator\":\"UP\"}," +
                "\"value\":\"UP\"}";
        AvailabilityConditionEval eval = objectMapper.readValue(str, AvailabilityConditionEval.class);

        assert eval.getEvalTimestamp() == 1;
        assert eval.getDataTimestamp() == 1;
        assert eval.getCondition().getType().equals(Condition.Type.AVAILABILITY);
        assert eval.getCondition().getTriggerId().equals("test");
        assert eval.getCondition().getTriggerMode().equals(Mode.FIRE);
        assert eval.getCondition().getDataId().equals("Default");
        assert eval.getCondition().getOperator().equals(AvailabilityCondition.Operator.UP);
        assert eval.getValue().equals(AvailabilityType.UP);
    }

    @Test
    public void jsonCompareConditionTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"COMPARE\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        CompareCondition condition = objectMapper.readValue(str, CompareCondition.class);

        assert condition.getTriggerId().equals("test");
        assert condition.getTriggerMode().equals(Mode.FIRE);
        assert condition.getDataId().equals("Default1");
        assert condition.getOperator().equals(CompareCondition.Operator.LT);
        assert condition.getData2Id().equals("Default2");
        assert condition.getData2Multiplier() == 1.2d;

        String output = objectMapper.writeValueAsString(condition);

        assert str.equals(output);

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIREX\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        try {
            condition = objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LTX\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        try {
            condition = objectMapper.readValue(str, CompareCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        condition = objectMapper.readValue(str, CompareCondition.class);

        assert condition.getDataId() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("dataId");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default1\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}";
        condition = objectMapper.readValue(str, CompareCondition.class);

        assert condition.getOperator() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("operator");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Multiplier\":1.2}";

        condition = objectMapper.readValue(str, CompareCondition.class);

        assert condition.getData2Id() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("data2Id");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\"}";

        condition = objectMapper.readValue(str, CompareCondition.class);

        assert condition.getData2Multiplier() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("data2Multiplier");
    }

    @Test
    public void jsonCompareConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default1\",\"operator\":\"LT\",\"data2Id\":\"Default2\",\"data2Multiplier\":1.2}," +
                "\"value1\":10.0," +
                "\"value2\":15.0}";
        CompareConditionEval eval = objectMapper.readValue(str, CompareConditionEval.class);

        assert eval.getEvalTimestamp() == 1;
        assert eval.getDataTimestamp() == 1;
        assert eval.getCondition().getType().equals(Condition.Type.COMPARE);
        assert eval.getCondition().getTriggerId().equals("test");
        assert eval.getCondition().getTriggerMode().equals(Mode.FIRE);
        assert eval.getCondition().getDataId().equals("Default1");
        assert eval.getCondition().getOperator().equals(CompareCondition.Operator.LT);
        assert eval.getValue1().equals(10.0);
        assert eval.getValue2().equals(15.0);
    }

    @Test
    public void jsonStringConditionTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"STRING\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        StringCondition condition = objectMapper.readValue(str, StringCondition.class);

        assert condition.getTriggerId().equals("test");
        assert condition.getTriggerMode().equals(Mode.FIRE);
        assert condition.getDataId().equals("Default");
        assert condition.getOperator().equals(StringCondition.Operator.MATCH);
        assert condition.getPattern().equals("test-pattern");
        assert condition.isIgnoreCase() == false;

        String output = objectMapper.writeValueAsString(condition);

        assert str.equals(output);

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIREX\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        try {
            condition = objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCHX\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        try {
            condition = objectMapper.readValue(str, StringCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assert condition.getDataId() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("dataId");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"pattern\":\"test-pattern\",\"ignoreCase\":false}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assert condition.getOperator() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("operator");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"ignoreCase\":false}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assert condition.getPattern() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("pattern");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"}";
        condition = objectMapper.readValue(str, StringCondition.class);

        assert condition.isIgnoreCase() == false;

        output = objectMapper.writeValueAsString(condition);

        // ignoreCase will be present as it is a boolean with default value
        assert output.contains("ignoreCase");
    }

    @Test
    public void jsonStringConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operator\":\"MATCH\",\"pattern\":\"test-pattern\",\"ignoreCase\":false}," +
                "\"value\":\"test-value\"}";
        StringConditionEval eval = objectMapper.readValue(str, StringConditionEval.class);

        assert eval.getEvalTimestamp() == 1;
        assert eval.getDataTimestamp() == 1;
        assert eval.getCondition().getType().equals(Condition.Type.STRING);
        assert eval.getCondition().getTriggerId().equals("test");
        assert eval.getCondition().getTriggerMode().equals(Mode.FIRE);
        assert eval.getCondition().getDataId().equals("Default");
        assert eval.getCondition().getOperator().equals(StringCondition.Operator.MATCH);
        assert eval.getCondition().getPattern().equals("test-pattern");
        assert eval.getCondition().isIgnoreCase() == false;
        assert eval.getValue().equals("test-value");
    }

    @Test
    public void jsonThresholdConditionTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"THRESHOLD\"," +
                "\"dataId\":\"Default\",\"operator\":\"LT\",\"threshold\":10.5}";
        ThresholdCondition condition = objectMapper.readValue(str, ThresholdCondition.class);

        assert condition.getTriggerId().equals("test");
        assert condition.getTriggerMode().equals(Mode.FIRE);
        assert condition.getDataId().equals("Default");
        assert condition.getOperator().equals(ThresholdCondition.Operator.LT);
        assert condition.getThreshold() == 10.5d;

        String output = objectMapper.writeValueAsString(condition);

        assert str.equals(output);

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIREX\"," +
                "\"dataId\":\"Default\",\"operator\":\"LT\",\"threshold\":10.5}";
        try {
            condition = objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operator\":\"LTX\",\"threshold\":10.5}";
        try {
            condition = objectMapper.readValue(str, ThresholdCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"operator\":\"LT\",\"threshold\":10.5}";
        condition = objectMapper.readValue(str, ThresholdCondition.class);

        assert condition.getDataId() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("dataId");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"threshold\":10.5}";
        condition = objectMapper.readValue(str, ThresholdCondition.class);

        assert condition.getOperator() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("operator");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"}";
        condition = objectMapper.readValue(str, ThresholdCondition.class);

        assert condition.getThreshold() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("threshold");
    }

    @Test
    public void jsonThresholdConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operator\":\"LT\",\"threshold\":10.5}," +
                "\"value\":1.0}";
        ThresholdConditionEval eval = objectMapper.readValue(str, ThresholdConditionEval.class);

        assert eval.getEvalTimestamp() == 1;
        assert eval.getDataTimestamp() == 1;
        assert eval.getCondition().getType().equals(Condition.Type.THRESHOLD);
        assert eval.getCondition().getTriggerId().equals("test");
        assert eval.getCondition().getTriggerMode().equals(Mode.FIRE);
        assert eval.getCondition().getDataId().equals("Default");
        assert eval.getCondition().getOperator().equals(ThresholdCondition.Operator.LT);
        assert eval.getCondition().getThreshold() == 10.5;
        assert eval.getValue() == 1.0;
    }

    @Test
    public void jsonThresholdRangeConditionTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"RANGE\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        ThresholdRangeCondition condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.getTriggerId().equals("test");
        assert condition.getTriggerMode().equals(Mode.FIRE);
        assert condition.getDataId().equals("Default");
        assert condition.getOperatorLow().equals(ThresholdRangeCondition.Operator.INCLUSIVE);
        assert condition.getOperatorHigh().equals(ThresholdRangeCondition.Operator.INCLUSIVE);
        assert condition.getThresholdLow() == 10.5d;
        assert condition.getThresholdHigh() == 20.5d;

        String output = objectMapper.writeValueAsString(condition);

        assert str.equals(output);

        // Check bad mode and operator

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIREX\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            condition = objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVEX\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            condition = objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVEX\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        try {
            condition = objectMapper.readValue(str, ThresholdRangeCondition.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        // Check uncompleted json

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.getDataId() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("dataId");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.getOperatorLow() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("operatorLow");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.getOperatorHigh() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("operatorHigh");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"thresholdHigh\":20.5,\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.getThresholdLow() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("thresholdLow");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"inRange\":true}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.getThresholdHigh() == null;

        output = objectMapper.writeValueAsString(condition);

        assert !output.contains("thresholdHigh");

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"}";
        condition = objectMapper.readValue(str, ThresholdRangeCondition.class);

        assert condition.isInRange() == false;

        output = objectMapper.writeValueAsString(condition);

        assert output.contains("inRange");
    }

    @Test
    public void jsonThresholdRangeConditionEvalTest() throws Exception {
        String str = "{\"evalTimestamp\":1,\"dataTimestamp\":1," +
                "\"condition\":" +
                "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\"," +
                "\"dataId\":\"Default\",\"operatorLow\":\"INCLUSIVE\",\"operatorHigh\":\"INCLUSIVE\"," +
                "\"thresholdLow\":10.5,\"thresholdHigh\":20.5,\"inRange\":true}," +
                "\"value\":1.0}";
        ThresholdRangeConditionEval eval = objectMapper.readValue(str, ThresholdRangeConditionEval.class);

        assert eval.getEvalTimestamp() == 1;
        assert eval.getDataTimestamp() == 1;
        assert eval.getCondition().getType().equals(Condition.Type.RANGE);
        assert eval.getCondition().getTriggerId().equals("test");
        assert eval.getCondition().getTriggerMode().equals(Mode.FIRE);
        assert eval.getCondition().getDataId().equals("Default");
        assert eval.getCondition().getOperatorLow().equals(ThresholdRangeCondition.Operator.INCLUSIVE);
        assert eval.getCondition().getOperatorHigh().equals(ThresholdRangeCondition.Operator.INCLUSIVE);
        assert eval.getCondition().getThresholdLow() == 10.5;
        assert eval.getCondition().getThresholdHigh() == 20.5;
        assert eval.getValue() == 1.0;
    }

    @Test
    public void jsonDampeningTest() throws Exception {
        String str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"STRICT\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        Dampening damp = objectMapper.readValue(str, Dampening.class);

        assert damp.getDampeningId().equals("test-FIRE");
        assert damp.getTriggerId().equals("test");
        assert damp.getTriggerMode().equals(Mode.FIRE);
        assert damp.getType().equals(Dampening.Type.STRICT);
        assert damp.getEvalTrueSetting() == 1;
        assert damp.getEvalTotalSetting() == 1;
        assert damp.getEvalTimeSetting() == 1;

        String output = objectMapper.writeValueAsString(damp);

        // Checking ignored fields are not there

        assert output.contains("dampeningId");
        assert !output.contains("numTrueEvals");
        assert !output.contains("numEvals");
        assert !output.contains("trueEvalsStartTime");
        assert !output.contains("satisfied");
        assert !output.contains("satisfyingEvals");

        // Checking bad fields

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIREX\",\"type\":\"STRICT\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        try {
            damp = objectMapper.readValue(str, Dampening.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }

        str = "{\"triggerId\":\"test\",\"triggerMode\":\"FIRE\",\"type\":\"STRICTX\"," +
                "\"evalTrueSetting\":1,\"evalTotalSetting\":1,\"evalTimeSetting\":1}";
        try {
            damp = objectMapper.readValue(str, Dampening.class);
            throw new Exception("It should throw an InvalidFormatException");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void jsonDataTest() throws Exception {
        String str = "{\"id\":\"test\",\"timestamp\":1,\"value\":\"UNAVAILABLE\"}";
        Availability aData = objectMapper.readValue(str, Availability.class);

        assert aData.getId().equals("test");
        assert aData.getTimestamp() == 1;
        assert aData.getValue().equals(AvailabilityType.UNAVAILABLE);

        String output = objectMapper.writeValueAsString(aData);

        assert output.contains("type");
        assert output.contains("AVAILABILITY");

        str = "{\"id\":\"test\",\"timestamp\":1,\"value\":10.45}";
        NumericData nData = objectMapper.readValue(str, NumericData.class);

        assert nData.getId().equals("test");
        assert nData.getTimestamp() == 1;
        assert nData.getValue() == 10.45;

        output = objectMapper.writeValueAsString(nData);

        assert output.contains("type");
        assert output.contains("NUMERIC");

        str = "{\"id\":\"test\",\"timestamp\":1,\"value\":\"test-value\"}";
        StringData sData = objectMapper.readValue(str, StringData.class);

        assert sData.getId().equals("test");
        assert sData.getTimestamp() == 1;
        assert sData.getValue().equals("test-value");

        output = objectMapper.writeValueAsString(sData);

        assert output.contains("type");
        assert output.contains("STRING");
    }

    @Test
    public void jsonTriggerTest() throws Exception {
        String str = "{\"name\":\"test-name\",\"description\":\"test-description\"," +
                "\"actions\":[\"uno\",\"dos\",\"tres\"]," +
                "\"firingMatch\":\"ALL\"," +
                "\"safetyMatch\":\"ALL\"," +
                "\"id\":\"test\"," +
                "\"enabled\":true," +
                "\"safetyEnabled\":true}";
        Trigger trigger = objectMapper.readValue(str, Trigger.class);

        assert trigger.getName().equals("test-name");
        assert trigger.getDescription().equals("test-description");
        assert trigger.getActions() != null && trigger.getActions().size() == 3;
        assert trigger.getFiringMatch().equals(Match.ALL);
        assert trigger.getSafetyMatch().equals(Match.ALL);
        assert trigger.getId().equals("test");
        assert trigger.isEnabled() == true;
        assert trigger.isSafetyEnabled() == true;

        String output = objectMapper.writeValueAsString(trigger);

        assert !output.contains("mode");
        assert !output.contains("match");
    }

}
