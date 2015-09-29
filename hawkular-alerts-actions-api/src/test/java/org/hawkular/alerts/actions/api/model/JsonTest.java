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
package org.hawkular.alerts.actions.api.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.NumericData;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Lucas Ponce
 */
public class JsonTest {

    private static final String TEST_TENANT = "jdoe";
    public static ObjectMapper objectMapper = new ObjectMapper();
    public static Action incomingAction;

    @BeforeClass
    public static void initTest() {
        Alert alert = new Alert(TEST_TENANT, "trigger-test", Severity.MEDIUM, null);

        AvailabilityCondition aCond = new AvailabilityCondition("trigger-test", "Default",
                AvailabilityCondition.Operator.UP);
        Availability aData = new Availability("Metric-test", 1, Availability.AvailabilityType.UP);
        AvailabilityConditionEval aEval = new AvailabilityConditionEval(aCond, aData);

        ThresholdCondition tCond = new ThresholdCondition("trigger-test", "Default",
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

        incomingAction = new Action(TEST_TENANT, "testPlugin", "testActionId", alert);
    }


    @Test
    public void jsonPluginMessage() throws Exception {

        Map<String, String> props = new HashMap<>();
        props.put("k1", "v1");
        props.put("k2", "v2");
        ActionMessage msg = new TestActionMessage(incomingAction, props);

        String json = objectMapper.writeValueAsString(msg);

        assertTrue(json.contains("v2"));

        ActionMessage newMsg = objectMapper.readValue(json, TestActionMessage.class);

        assertEquals("v2", newMsg.getProperties().get("k2"));
        assertEquals("trigger-test", newMsg.getAction().getAlert().getTriggerId());
    }

    public static class TestActionMessage implements ActionMessage {

        Action action;
        Map<String, String> properties;

        public TestActionMessage() {
        }

        public TestActionMessage(Action action, Map<String, String> properties) {
            this.action = action;
            this.properties = properties;
        }

        @Override
        public Action getAction() {
            return action;
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }
    }

}
