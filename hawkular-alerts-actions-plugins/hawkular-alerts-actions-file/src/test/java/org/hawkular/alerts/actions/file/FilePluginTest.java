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
package org.hawkular.alerts.actions.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Lucas Ponce
 */
public class FilePluginTest {

    private FilePlugin filePlugin = new FilePlugin();

    private static PluginMessage openThresholdMsg;
    private static PluginMessage ackThresholdMsg;
    private static PluginMessage resolvedThresholdMsg;

    private static PluginMessage openAvailMsg;
    private static PluginMessage ackAvailMsg;
    private static PluginMessage resolvedAvailMsg;

    private static PluginMessage openTwoCondMsg;
    private static PluginMessage ackTwoCondMsg;
    private static PluginMessage resolvedTwoCondMsg;

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
    public static void prepareMessages() {
        final String tenantId = "test-tenant";
        final String rtTriggerId = "rt-trigger-jboss";
        final String rtDataId = "rt-jboss-data";
        final String avTriggerId = "av-trigger-jboss";
        final String avDataId = "av-jboss-data";
        final String mixTriggerId = "mix-trigger-jboss";

        /*
            Alert definition for threshold
         */
        Trigger rtTrigger = new Trigger(rtTriggerId, "http://www.jboss.org");
        rtTrigger.setTenantId(tenantId);
        ThresholdCondition rtFiringCondition = new ThresholdCondition(rtTriggerId, Trigger.Mode.FIRING,
                rtDataId, ThresholdCondition.Operator.GT, 1000d);
        ThresholdCondition rtResolveCondition = new ThresholdCondition(rtTriggerId, Trigger.Mode.AUTORESOLVE,
                rtDataId, ThresholdCondition.Operator.LTE, 1000d);

        Dampening rtFiringDampening = Dampening.forStrictTime(rtTriggerId, Trigger.Mode.FIRING, 10000);

        /*
            Demo bad data for threshold
         */
        NumericData rtBadData = new NumericData(rtDataId, System.currentTimeMillis(), 1001d);

        /*
            Manual alert creation for threshold
         */
        Alert rtAlertOpen = new Alert(rtTrigger.getTenantId(), rtTrigger.getId(), rtTrigger.getSeverity(),
                getEvalList(rtFiringCondition, rtBadData));
        rtAlertOpen.setTrigger(rtTrigger);
        rtAlertOpen.setDampening(rtFiringDampening);
        rtAlertOpen.setStatus(Alert.Status.OPEN);

        /*
            Manual Action creation for threshold
         */
        Map<String, String> props = new HashMap<>();
        props.put("path", "target/file-tests");

        Action openThresholdAction = new Action(tenantId, "email", "email-to-test", rtAlertOpen);

        openThresholdMsg = new TestPluginMessage(openThresholdAction, props);

        Alert rtAlertAck = new Alert(rtTrigger.getTenantId(), rtTrigger.getId(), rtTrigger.getSeverity(),
                getEvalList(rtFiringCondition, rtBadData));
        rtAlertAck.setTrigger(rtTrigger);
        rtAlertAck.setDampening(rtFiringDampening);
        rtAlertAck.setStatus(Alert.Status.ACKNOWLEDGED);
        rtAlertAck.setAckBy("Test ACK user");
        rtAlertAck.setAckTime(System.currentTimeMillis() + 10000);
        rtAlertAck.setAckNotes("Test ACK notes");

        Action ackThresholdAction = new Action(tenantId, "email", "email-to-test", rtAlertAck);

        ackThresholdMsg = new TestPluginMessage(ackThresholdAction, props);

        /*
            Demo good data to resolve a threshold alert
         */
        NumericData rtGoodData = new NumericData(rtDataId, System.currentTimeMillis() + 20000, 998d);

        Alert rtAlertResolved = new Alert(rtTrigger.getTenantId(), rtTrigger.getId(), rtTrigger.getSeverity(),
                getEvalList(rtFiringCondition, rtBadData));
        rtAlertResolved.setTrigger(rtTrigger);
        rtAlertResolved.setDampening(rtFiringDampening);
        rtAlertResolved.setStatus(Alert.Status.RESOLVED);
        rtAlertResolved.setResolvedBy("Test RESOLVED user");
        rtAlertResolved.setResolvedTime(System.currentTimeMillis() + 20000);
        rtAlertResolved.setResolvedNotes("Test RESOLVED notes");
        rtAlertResolved.setResolvedEvalSets(getEvalList(rtResolveCondition, rtGoodData));

        Action resolvedThresholdAction = new Action(tenantId, "email", "email-to-test", rtAlertResolved);

        resolvedThresholdMsg = new TestPluginMessage(resolvedThresholdAction, props);

        /*
            Alert definition for availability
         */
        Trigger avTrigger = new Trigger(avTriggerId, "http://www.jboss.org");
        avTrigger.setTenantId(tenantId);
        AvailabilityCondition avFiringCondition = new AvailabilityCondition(avTriggerId, Trigger.Mode.FIRING,
                avDataId, AvailabilityCondition.Operator.NOT_UP);
        AvailabilityCondition avResolveCondition = new AvailabilityCondition(avTriggerId, Trigger.Mode.AUTORESOLVE,
                avDataId, AvailabilityCondition.Operator.UP);

        Dampening avFiringDampening = Dampening.forStrictTime(avTriggerId, Trigger.Mode.FIRING, 10000);

        /*
            Demo bad data for availability
         */
        Availability avBadData = new Availability(avDataId, System.currentTimeMillis(),
                Availability.AvailabilityType.DOWN);

        /*
            Manual alert creation for availability
         */
        Alert avAlertOpen = new Alert(avTrigger.getTenantId(), avTrigger.getId(), avTrigger.getSeverity(),
                getEvalList(avFiringCondition, avBadData));
        avAlertOpen.setTrigger(avTrigger);
        avAlertOpen.setDampening(avFiringDampening);
        avAlertOpen.setStatus(Alert.Status.OPEN);

        /*
            Manual Action creation for availability
         */
        props = new HashMap<>();
        props.put("path", "target/file-tests");

        Action openAvailabilityAction = new Action(tenantId, "email", "email-to-test", avAlertOpen);

        openAvailMsg = new TestPluginMessage(openAvailabilityAction, props);

        Alert avAlertAck = new Alert(avTrigger.getTenantId(), avTrigger.getId(), avTrigger.getSeverity(),
                getEvalList(avFiringCondition, avBadData));
        avAlertAck.setTrigger(avTrigger);
        avAlertAck.setDampening(avFiringDampening);
        avAlertAck.setStatus(Alert.Status.ACKNOWLEDGED);
        avAlertAck.setAckBy("Test ACK user");
        avAlertAck.setAckTime(System.currentTimeMillis() + 10000);
        avAlertAck.setAckNotes("Test ACK notes");

        Action ackAvailabilityAction = new Action(tenantId, "email", "email-to-test", avAlertAck);

        ackAvailMsg = new TestPluginMessage(ackAvailabilityAction, props);

        /*
            Demo good data to resolve a availability alert
         */
        Availability avGoodData = new Availability(avDataId, System.currentTimeMillis() + 20000,
                Availability.AvailabilityType.UP);

        Alert avAlertResolved = new Alert(avTrigger.getTenantId(), avTrigger.getId(), avTrigger.getSeverity(),
                getEvalList(avFiringCondition, avBadData));
        avAlertResolved.setTrigger(avTrigger);
        avAlertResolved.setDampening(avFiringDampening);
        avAlertResolved.setStatus(Alert.Status.RESOLVED);
        avAlertResolved.setResolvedBy("Test RESOLVED user");
        avAlertResolved.setResolvedTime(System.currentTimeMillis() + 20000);
        avAlertResolved.setResolvedNotes("Test RESOLVED notes");
        avAlertResolved.setResolvedEvalSets(getEvalList(avResolveCondition, avGoodData));

        Action resolvedAvailabilityAction = new Action(tenantId, "email", "email-to-test", avAlertResolved);

        resolvedAvailMsg = new TestPluginMessage(resolvedAvailabilityAction, props);

        /*
            Alert definition for two conditions
         */
        Trigger mixTrigger = new Trigger(mixTriggerId, "http://www.jboss.org");
        mixTrigger.setTenantId(tenantId);
        ThresholdCondition mixRtFiringCondition = new ThresholdCondition(mixTriggerId, Trigger.Mode.FIRING,
                rtDataId, ThresholdCondition.Operator.GT, 1000d);
        ThresholdCondition mixRtResolveCondition = new ThresholdCondition(mixTriggerId, Trigger.Mode.AUTORESOLVE,
                rtDataId, ThresholdCondition.Operator.LTE, 1000d);
        AvailabilityCondition mixAvFiringCondition = new AvailabilityCondition(mixTriggerId, Trigger.Mode.FIRING,
                avDataId, AvailabilityCondition.Operator.NOT_UP);
        AvailabilityCondition mixAvResolveCondition = new AvailabilityCondition(mixTriggerId, Trigger.Mode.AUTORESOLVE,
                avDataId, AvailabilityCondition.Operator.UP);

        Dampening mixFiringDampening = Dampening.forStrictTime(mixTriggerId, Trigger.Mode.FIRING, 10000);

        /*
            Demo bad data for two conditions
         */
        rtBadData = new NumericData(rtDataId, System.currentTimeMillis(), 1003d);
        avBadData = new Availability(avDataId, System.currentTimeMillis(), Availability.AvailabilityType.DOWN);

        /*
            Manual alert creation for two conditions
         */
        List<Condition> mixConditions = new ArrayList<>();
        mixConditions.add(mixRtFiringCondition);
        mixConditions.add(mixAvFiringCondition);
        List<Data> mixBadData = new ArrayList<>();
        mixBadData.add(rtBadData);
        mixBadData.add(avBadData);
        Alert mixAlertOpen = new Alert(mixTrigger.getTenantId(), mixTrigger.getId(), mixTrigger.getSeverity(),
                getEvalList(mixConditions, mixBadData));
        mixAlertOpen.setTrigger(mixTrigger);
        mixAlertOpen.setDampening(mixFiringDampening);
        mixAlertOpen.setStatus(Alert.Status.OPEN);

        /*
            Manual Action creation for two conditions
         */
        props = new HashMap<>();
        props.put("path", "target/file-tests");

        Action openTwoCondAction = new Action(tenantId, "email", "email-to-test", mixAlertOpen);

        openTwoCondMsg = new TestPluginMessage(openTwoCondAction, props);

        Alert mixAlertAck = new Alert(mixTrigger.getTenantId(), mixTrigger.getId(), mixTrigger.getSeverity(),
                getEvalList(mixConditions, mixBadData));
        mixAlertAck.setTrigger(mixTrigger);
        mixAlertAck.setDampening(mixFiringDampening);
        mixAlertAck.setStatus(Alert.Status.ACKNOWLEDGED);
        mixAlertAck.setAckBy("Test ACK user");
        mixAlertAck.setAckTime(System.currentTimeMillis() + 10000);
        mixAlertAck.setAckNotes("Test ACK notes");

        Action ackTwoCondAction = new Action(tenantId, "email", "email-to-test", mixAlertAck);

        ackTwoCondMsg = new TestPluginMessage(ackTwoCondAction, props);

        /*
            Demo good data for two conditions
         */
        rtGoodData = new NumericData(rtDataId, System.currentTimeMillis() + 20000, 997d);
        avGoodData = new Availability(avDataId, System.currentTimeMillis() + 20000, Availability.AvailabilityType.UP);

        List<Condition> mixResolveConditions = new ArrayList<>();
        mixResolveConditions.add(mixRtResolveCondition);
        mixResolveConditions.add(mixAvResolveCondition);
        List<Data> mixGoodData = new ArrayList<>();
        mixGoodData.add(rtGoodData);
        mixGoodData.add(avGoodData);

        Alert mixAlertResolved = new Alert(mixTrigger.getTenantId(), mixTrigger.getId(), mixTrigger.getSeverity(),
                getEvalList(mixConditions, mixBadData));
        mixAlertResolved.setTrigger(mixTrigger);
        mixAlertResolved.setDampening(mixFiringDampening);
        mixAlertResolved.setStatus(Alert.Status.ACKNOWLEDGED);
        mixAlertResolved.setStatus(Alert.Status.RESOLVED);
        mixAlertResolved.setResolvedBy("Test RESOLVED user");
        mixAlertResolved.setResolvedTime(System.currentTimeMillis() + 20000);
        mixAlertResolved.setResolvedNotes("Test RESOLVED notes");
        mixAlertResolved.setResolvedEvalSets(getEvalList(mixResolveConditions, mixGoodData));

        Action resolvedTwoCondAction = new Action(tenantId, "email", "email-to-test", mixAlertResolved);

        resolvedTwoCondMsg = new TestPluginMessage(resolvedTwoCondAction, props);
    }

    private static List<Set<ConditionEval>> getEvalList(Condition condition, Data data) {
        ConditionEval eval = null;
        if (condition instanceof ThresholdCondition &&
                data instanceof NumericData) {
            eval = new ThresholdConditionEval((ThresholdCondition)condition, (NumericData)data);
        }
        if (condition instanceof AvailabilityCondition &&
                data instanceof Availability) {
            eval = new AvailabilityConditionEval((AvailabilityCondition)condition, (Availability)data);
        }
        Set<ConditionEval> tEvalsSet = new HashSet<>();
        tEvalsSet.add(eval);
        List<Set<ConditionEval>> tEvalsList = new ArrayList<>();
        tEvalsList.add(tEvalsSet);
        return tEvalsList;
    }

    private static List<Set<ConditionEval>> getEvalList(List<Condition> condition, List<Data> data) {
        ConditionEval eval = null;
        Set<ConditionEval> tEvalsSet = new HashSet<>();
        for (int i = 0; i < condition.size(); i++) {
            if (condition.get(i) instanceof ThresholdCondition &&
                    data.get(i) instanceof NumericData) {
                eval = new ThresholdConditionEval((ThresholdCondition)condition.get(i), (NumericData)data.get(i));
            }
            if (condition.get(i) instanceof AvailabilityCondition &&
                    data.get(i) instanceof Availability) {
                eval = new AvailabilityConditionEval((AvailabilityCondition)condition.get(i),
                        (Availability)data.get(i));
            }
            tEvalsSet.add(eval);
        }
        List<Set<ConditionEval>> tEvalsList = new ArrayList<>();
        tEvalsList.add(tEvalsSet);
        return tEvalsList;
    }

    @Test
    public void thresholdTest() throws Exception {
        filePlugin.process(openThresholdMsg);
        filePlugin.process(ackThresholdMsg);
        filePlugin.process(resolvedThresholdMsg);
    }

    @Test
    public void availabilityTest() throws Exception {
        filePlugin.process(openAvailMsg);
        filePlugin.process(ackAvailMsg);
        filePlugin.process(resolvedAvailMsg);
    }

    @Test
    public void mixedTest() throws Exception {
        filePlugin.process(openTwoCondMsg);
        filePlugin.process(ackTwoCondMsg);
        filePlugin.process(resolvedTwoCondMsg);
    }
}
