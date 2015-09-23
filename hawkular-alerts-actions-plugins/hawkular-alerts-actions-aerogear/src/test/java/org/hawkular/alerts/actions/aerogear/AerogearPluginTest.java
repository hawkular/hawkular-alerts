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
package org.hawkular.alerts.actions.aerogear;

import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.jboss.aerogear.unifiedpush.PushSender;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * @author Thomas Segismont
 */
@RunWith(MockitoJUnitRunner.class)
public class AerogearPluginTest {
    private static final String TEST_TENANT = "jdoe";

    public static PluginMessage testMessage;
    public static PluginMessage testBroadcastMessage;
    public static String preparedMessage;
    public static String alias;

    public static class TestPluginMessage implements PluginMessage {
        Action action;
        Map<String, String> properties;

        public TestPluginMessage(Action action, Map<String, String> properties) {
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

    @BeforeClass
    public static void configureListener() {
        System.setProperty(AerogearPlugin.ROOT_SERVER_URL_PROPERTY, "http://localhost:9191/ag-push");
        System.setProperty(AerogearPlugin.APPLICATION_ID_PROPERTY, "4d564d56qs4056-d0sq564065");
        System.setProperty(AerogearPlugin.MASTER_SECRET_PROPERTY, "sddqs--sqd-qs--d-qs000dsq0d");

        Trigger trigger = new Trigger( TEST_TENANT, "trigger-test", "trigger-test");
        Alert alert = new Alert(TEST_TENANT, trigger, null);

        AvailabilityCondition aCond = new AvailabilityCondition("trigger-test",
                "Default",
                AvailabilityCondition.Operator.UP);
        Data aData = Data.forAvailability("Metric-test", 1, AvailabilityType.UP);
        AvailabilityConditionEval aEval = new AvailabilityConditionEval(aCond, aData);

        ThresholdCondition tCond = new ThresholdCondition("trigger-test",
                "Default",
                ThresholdCondition.Operator.LTE,
                50.0);
        Data tData = Data.forNumeric("Metric-test2", 2, 25.5);
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

        alias = "GeorgeAbitbol";

        Map<String, String> properties = new HashMap<>();
        properties.put("alias", alias);

        testMessage = new TestPluginMessage(incomingAction, properties);

        testBroadcastMessage = new TestPluginMessage(incomingAction, Collections.EMPTY_MAP);
    }

    private PushSender pushSender;
    private AerogearPlugin aerogearPlugin;

    @Before
    public void setup() {
        pushSender = mock(PushSender.class);
        aerogearPlugin = new AerogearPlugin();
        aerogearPlugin.pushSender = pushSender;
    }

    @Test
    public void testSend() throws Exception {
        aerogearPlugin.process(testMessage);

        verify(pushSender, times(1)).send(argThat(UnifiedMessageMatcher
                .matchesUnifiedMessage(alias, preparedMessage)));
    }

    @Test
    public void testBroadcast() throws Exception {
        aerogearPlugin.process(testBroadcastMessage);

        verify(pushSender, times(1)).send(argThat(UnifiedMessageMatcher.matchesUnifiedMessage(null, preparedMessage)));
    }
}