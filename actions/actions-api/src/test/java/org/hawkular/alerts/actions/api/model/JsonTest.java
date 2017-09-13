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
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.trigger.Trigger;
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
        Trigger trigger = new Trigger(TEST_TENANT, "trigger-test", "trigger-test");
        Alert alert = new Alert(TEST_TENANT, trigger, null);

        AvailabilityCondition aCond = new AvailabilityCondition(TEST_TENANT, "trigger-test", "Default",
                AvailabilityCondition.Operator.UP);
        Data aData = Data.forAvailability(TEST_TENANT, "Metric-test", 1, AvailabilityType.UP);
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

        incomingAction = new Action(TEST_TENANT, "testPlugin", "testActionId", alert);
        Map<String, String> props = new HashMap<>();
        props.put("k1", "v1");
        props.put("k2", "v2");
        incomingAction.setProperties(props);
    }

    @Test
    public void jsonPluginMessage() throws Exception {

        ActionMessage msg = new TestActionMessage(incomingAction);

        String json = objectMapper.writeValueAsString(msg);

        assertTrue(json.contains("v2"));

        ActionMessage newMsg = objectMapper.readValue(json, TestActionMessage.class);

        assertEquals("v2", newMsg.getAction().getProperties().get("k2"));
        assertEquals("trigger-test", newMsg.getAction().getEvent().getTrigger().getId());
    }

    public static class TestActionMessage implements ActionMessage {

        Action action;

        public TestActionMessage() {
        }

        public TestActionMessage(Action action) {
            this.action = action;
        }

        @Override
        public Action getAction() {
            return action;
        }
    }

}
