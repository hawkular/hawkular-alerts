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
package org.hawkular.alerts.actions.pagerduty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.gson.InstanceCreator;
import com.squareup.pagerduty.incidents.FakePagerDuty;
import com.squareup.pagerduty.incidents.NotifyResult;

/**
 * @author Thomas Segismont
 */
public class PagerDutyPluginTest {
    private static final String TEST_TENANT = "jdoe";

    public static ActionMessage testMessage;
    public static String preparedMessage;

    public static class TestActionMessage implements ActionMessage {
        Action action;

        public TestActionMessage(Action action, Map<String, String> properties) {
            this.action = action;
        }

        @Override
        public Action getAction() {
            return action;
        }
    }

    @BeforeClass
    public static void configureListener() {
        System.setProperty(PagerDutyPlugin.API_KEY_PROPERTY, "test");

        Alert alert = new Alert(TEST_TENANT, "trigger-test", Severity.MEDIUM, null);

        AvailabilityCondition aCond = new AvailabilityCondition("trigger-test",
                "Default",
                AvailabilityCondition.Operator.UP);
        Availability aData = new Availability("Metric-test", 1, Availability.AvailabilityType.UP);
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

        preparedMessage = "Alert : " + alert.getTriggerId() + " at " + alert.getCtime() + " -- Severity: " +
                alert.getSeverity().toString();

        Action incomingAction = new Action(TEST_TENANT, "testPlugin", "testActionId", alert);

        Map<String, String> properties = new HashMap<>();
        properties.put("description", "This is my personalized description");

        testMessage = new TestActionMessage(incomingAction, properties);
    }

    private FakePagerDuty fakePagerDuty;
    private PagerDutyPlugin pagerDutyPlugin;

    @Before
    public void setup() {
        pagerDutyPlugin = new PagerDutyPlugin();
        pagerDutyPlugin.sender = new TestActionSender();
        fakePagerDuty = new FakePagerDuty();
        pagerDutyPlugin.pagerDuty = fakePagerDuty;
    }

    @Test
    public void testSend() throws Exception {
        pagerDutyPlugin.process(testMessage);

        assertEquals("Expected PagerDuty incident to be created", 1, fakePagerDuty.openIncidents().size());
        assertEquals(preparedMessage, fakePagerDuty.openIncidents().values().iterator().next());
    }

    @Test
    public void testNotifyResultCreator() throws Exception {
        // Expecting no exception
        InstanceCreator<NotifyResult> instanceCreator = pagerDutyPlugin.buildNotifyResultCreator();
        assertNotNull("Should not return null", instanceCreator.createInstance(null));

        NotifyResult instance1 = instanceCreator.createInstance(null);
        NotifyResult instance2 = instanceCreator.createInstance(null);

        assertNotSame(instance1, instance2);
    }

    public class TestActionResponseMessage implements ActionResponseMessage {

        ActionResponseMessage.Operation operation;

        Map<String, String> payload;

        public TestActionResponseMessage() {
            this.operation = ActionResponseMessage.Operation.RESULT;
            this.payload = new HashMap<>();
        }

        public TestActionResponseMessage(ActionResponseMessage.Operation operation) {
            this.operation = operation;
            this.payload = new HashMap<>();
        }

        @Override
        public Operation getOperation() {
            return operation;
        }

        @Override
        public Map<String, String> getPayload() {
            return payload;
        }
    }

    public class TestActionSender implements ActionPluginSender {

        @Override
        public ActionResponseMessage createMessage(ActionResponseMessage.Operation operation) {
            return new TestActionResponseMessage(operation);
        }

        @Override
        public void send(ActionResponseMessage msg) throws Exception {
            // Nothing to do
        }
    }
}
