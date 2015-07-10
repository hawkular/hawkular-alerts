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
package org.hawkular.actions.email.listener;

import static org.hawkular.alerts.api.model.condition.Alert.Status;
import static org.hawkular.alerts.api.model.data.Availability.AvailabilityType;
import static org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.mail.Message;
import org.hawkular.actions.api.model.ActionMessage;
import org.hawkular.alerts.api.json.GsonUtil;
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
 * @author Thomas Segismont
 */
public class EmailListenerTest {

    private EmailListener emailListener = new EmailListener();

    private static ActionMessage openThresholdMsg;
    private static ActionMessage ackThresholdMsg;
    private static ActionMessage resolvedThresholdMsg;

    private static ActionMessage openAvailMsg;
    private static ActionMessage ackAvailMsg;
    private static ActionMessage resolvedAvailMsg;

    private static ActionMessage openTwoCondMsg;
    private static ActionMessage ackTwoCondMsg;
    private static ActionMessage resolvedTwoCondMsg;

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
        ThresholdCondition rtFiringCondition = new ThresholdCondition(rtTriggerId, Mode.FIRING,
                rtDataId, ThresholdCondition.Operator.GT, 1000d);
        ThresholdCondition rtResolveCondition = new ThresholdCondition(rtTriggerId, Mode.AUTORESOLVE,
                rtDataId, ThresholdCondition.Operator.LTE, 1000d);

        Dampening rtFiringDampening = Dampening.forStrictTime(rtTriggerId, Mode.FIRING, 10000);

        /*
            Demo bad data for threshold
         */
        NumericData rtBadData = new NumericData(rtDataId, System.currentTimeMillis(), 1001d);

        /*
            Manual alert creation for threshold
         */
        Alert rtAlert = new Alert(rtTrigger.getTenantId(), rtTrigger.getId(), rtTrigger.getSeverity(),
                getEvalList(rtFiringCondition, rtBadData));
        rtAlert.setTrigger(rtTrigger);
        rtAlert.setDampening(rtFiringDampening);
        rtAlert.setStatus(Status.OPEN);

        /*
            Manual Action creation for threshold
         */
        Map<String, String> props = new HashMap<>();
        props.put("to", "admin@hawkular.org");
        props.put("cc", "bigboss@hawkular.org");
        props.put("cc.acknowledged", "acknowledged@hawkular.org");
        props.put("cc.resolved", "resolved@hawkular.org");
        props.put("description", "This is an example of Email Action Plugin");
        props.put("template.hawkular.url", "http://www.hawkular.org");

        openThresholdMsg = new ActionMessage(tenantId, "email", "email-to-test", "Threshold Alert",
                GsonUtil.toJson(rtAlert));
        openThresholdMsg.setProperties(props);

        rtAlert.setStatus(Status.ACKNOWLEDGED);
        rtAlert.setAckBy("Test ACK user");
        rtAlert.setAckTime(System.currentTimeMillis() + 10000);
        rtAlert.setAckNotes("Test ACK notes");
        ackThresholdMsg = new ActionMessage(tenantId, "email", "email-to-test", "Threshold Alert",
                GsonUtil.toJson(rtAlert));
        ackThresholdMsg.setProperties(props);

        /*
            Demo good data to resolve a threshold alert
         */
        NumericData rtGoodData = new NumericData(rtDataId, System.currentTimeMillis() + 20000, 998d);

        rtAlert.setStatus(Status.RESOLVED);
        rtAlert.setResolvedBy("Test RESOLVED user");
        rtAlert.setResolvedTime(System.currentTimeMillis() + 20000);
        rtAlert.setResolvedNotes("Test RESOLVED notes");
        rtAlert.setResolvedEvalSets(getEvalList(rtResolveCondition, rtGoodData));

        resolvedThresholdMsg = new ActionMessage(tenantId, "email", "email-to-test", "Threshold Alert",
                GsonUtil.toJson(rtAlert));
        resolvedThresholdMsg.setProperties(props);


        /*
            Alert definition for availability
         */
        Trigger avTrigger = new Trigger(avTriggerId, "http://www.jboss.org");
        avTrigger.setTenantId(tenantId);
        AvailabilityCondition avFiringCondition = new AvailabilityCondition(avTriggerId, Mode.FIRING,
                avDataId, AvailabilityCondition.Operator.NOT_UP);
        AvailabilityCondition avResolveCondition = new AvailabilityCondition(avTriggerId, Mode.AUTORESOLVE,
                avDataId, AvailabilityCondition.Operator.UP);

        Dampening avFiringDampening = Dampening.forStrictTime(avTriggerId, Mode.FIRING, 10000);

        /*
            Demo bad data for availability
         */
        Availability avBadData = new Availability(avDataId, System.currentTimeMillis(), AvailabilityType.DOWN);

        /*
            Manual alert creation for availability
         */
        Alert avAlert = new Alert(avTrigger.getTenantId(), avTrigger.getId(), avTrigger.getSeverity(),
                getEvalList(avFiringCondition, avBadData));
        avAlert.setTrigger(avTrigger);
        avAlert.setDampening(avFiringDampening);
        avAlert.setStatus(Status.OPEN);

        /*
            Manual Action creation for availability
         */
        props = new HashMap<>();
        props.put("to", "admin@hawkular.org");
        props.put("cc", "bigboss@hawkular.org");
        props.put("cc.acknowledged", "acknowledged@hawkular.org");
        props.put("cc.resolved", "resolved@hawkular.org");
        props.put("description", "This is an example of Email Action Plugin");
        props.put("template.hawkular.url", "http://www.hawkular.org");

        openAvailMsg = new ActionMessage(tenantId, "email", "email-to-test", "Availability Alert",
                GsonUtil.toJson(avAlert));
        openAvailMsg.setProperties(props);

        avAlert.setStatus(Status.ACKNOWLEDGED);
        avAlert.setAckBy("Test ACK user");
        avAlert.setAckTime(System.currentTimeMillis() + 10000);
        avAlert.setAckNotes("Test ACK notes");
        ackAvailMsg = new ActionMessage(tenantId, "email", "email-to-test", "Availability Alert",
                GsonUtil.toJson(avAlert));
        ackAvailMsg.setProperties(props);

        /*
            Demo good data to resolve a availability alert
         */
        Availability avGoodData = new Availability(avDataId, System.currentTimeMillis() + 20000, AvailabilityType.UP);

        avAlert.setStatus(Status.RESOLVED);
        avAlert.setResolvedBy("Test RESOLVED user");
        avAlert.setResolvedTime(System.currentTimeMillis() + 20000);
        avAlert.setResolvedNotes("Test RESOLVED notes");
        avAlert.setResolvedEvalSets(getEvalList(avResolveCondition, avGoodData));

        resolvedAvailMsg = new ActionMessage(tenantId, "email", "email-to-test", "Availability Alert",
                GsonUtil.toJson(avAlert));
        resolvedAvailMsg.setProperties(props);

        /*
            Alert definition for two conditions
         */
        Trigger mixTrigger = new Trigger(mixTriggerId, "http://www.jboss.org");
        mixTrigger.setTenantId(tenantId);
        ThresholdCondition mixRtFiringCondition = new ThresholdCondition(mixTriggerId, Mode.FIRING,
                rtDataId, ThresholdCondition.Operator.GT, 1000d);
        ThresholdCondition mixRtResolveCondition = new ThresholdCondition(mixTriggerId, Mode.AUTORESOLVE,
                rtDataId, ThresholdCondition.Operator.LTE, 1000d);
        AvailabilityCondition mixAvFiringCondition = new AvailabilityCondition(mixTriggerId, Mode.FIRING,
                avDataId, AvailabilityCondition.Operator.NOT_UP);
        AvailabilityCondition mixAvResolveCondition = new AvailabilityCondition(mixTriggerId, Mode.AUTORESOLVE,
                avDataId, AvailabilityCondition.Operator.UP);

        Dampening mixFiringDampening = Dampening.forStrictTime(mixTriggerId, Mode.FIRING, 10000);

        /*
            Demo bad data for two conditions
         */
        rtBadData = new NumericData(rtDataId, System.currentTimeMillis(), 1003d);
        avBadData = new Availability(avDataId, System.currentTimeMillis(), AvailabilityType.DOWN);

        /*
            Manual alert creation for two conditions
         */
        List<Condition> mixConditions = new ArrayList<>();
        mixConditions.add(mixRtFiringCondition);
        mixConditions.add(mixAvFiringCondition);
        List<Data> mixBadData = new ArrayList<>();
        mixBadData.add(rtBadData);
        mixBadData.add(avBadData);
        Alert mixAlert = new Alert(mixTrigger.getTenantId(), mixTrigger.getId(), mixTrigger.getSeverity(),
                getEvalList(mixConditions, mixBadData));
        mixAlert.setTrigger(mixTrigger);
        mixAlert.setDampening(mixFiringDampening);
        mixAlert.setStatus(Status.OPEN);

        /*
            Manual Action creation for two conditions
         */
        props = new HashMap<>();
        props.put("to", "admin@hawkular.org");
        props.put("cc", "bigboss@hawkular.org");
        props.put("cc.acknowledged", "acknowledged@hawkular.org");
        props.put("cc.resolved", "resolved@hawkular.org");
        props.put("description", "This is an example of Email Action Plugin");
        props.put("template.hawkular.url", "http://www.hawkular.org");

        openTwoCondMsg = new ActionMessage(tenantId, "email", "email-to-test", "Response Time and Availability Alert",
                GsonUtil.toJson(mixAlert));
        openTwoCondMsg.setProperties(props);

        mixAlert.setStatus(Status.ACKNOWLEDGED);
        mixAlert.setAckBy("Test ACK user");
        mixAlert.setAckTime(System.currentTimeMillis() + 10000);
        mixAlert.setAckNotes("Test ACK notes");
        ackTwoCondMsg = new ActionMessage(tenantId, "email", "email-to-test", "Availability Alert",
                GsonUtil.toJson(mixAlert));
        ackTwoCondMsg.setProperties(props);

        /*
            Demo good data for two conditions
         */
        rtGoodData = new NumericData(rtDataId, System.currentTimeMillis() + 20000, 997d);
        avGoodData = new Availability(avDataId, System.currentTimeMillis() + 20000, AvailabilityType.UP);

        List<Condition> mixResolveConditions = new ArrayList<>();
        mixResolveConditions.add(mixRtResolveCondition);
        mixResolveConditions.add(mixAvResolveCondition);
        List<Data> mixGoodData = new ArrayList<>();
        mixGoodData.add(rtGoodData);
        mixGoodData.add(avGoodData);

        mixAlert.setStatus(Status.RESOLVED);
        mixAlert.setResolvedBy("Test RESOLVED user");
        mixAlert.setResolvedTime(System.currentTimeMillis() + 20000);
        mixAlert.setResolvedNotes("Test RESOLVED notes");
        mixAlert.setResolvedEvalSets(getEvalList(mixResolveConditions, mixGoodData));

        resolvedTwoCondMsg = new ActionMessage(tenantId, "email", "email-to-test", "Availability Alert",
                GsonUtil.toJson(mixAlert));
        resolvedTwoCondMsg.setProperties(props);
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


    private void writeEmailFile(Message msg, String fileName) throws Exception {
        File dir = new File("target/test-emails");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        msg.writeTo(fos);
        fos.close();
    }

    @Test
    public void thresholdTest() throws Exception {
        Message message = emailListener.createMimeMessage(openThresholdMsg);
        assertNotNull(message);
        writeEmailFile(message, "threshold-open-test-" + System.currentTimeMillis() + ".eml");

        message = emailListener.createMimeMessage(ackThresholdMsg);
        assertNotNull(message);
        writeEmailFile(message, "threshold-ack-test-" + System.currentTimeMillis() + ".eml");

        message = emailListener.createMimeMessage(resolvedThresholdMsg);
        assertNotNull(message);
        writeEmailFile(message, "threshold-resolved-test-" + System.currentTimeMillis() + ".eml");
    }

    @Test
    public void availabilityTest() throws Exception {
        Message message = emailListener.createMimeMessage(openAvailMsg);
        assertNotNull(message);
        writeEmailFile(message, "availability-open-test-" + System.currentTimeMillis() + ".eml");

        message = emailListener.createMimeMessage(ackAvailMsg);
        assertNotNull(message);
        writeEmailFile(message, "availability-ack-test-" + System.currentTimeMillis() + ".eml");

        message = emailListener.createMimeMessage(resolvedAvailMsg);
        assertNotNull(message);
        writeEmailFile(message, "availability-resolved-test-" + System.currentTimeMillis() + ".eml");
    }

    @Test
    public void mixedTest() throws Exception {
        Message message = emailListener.createMimeMessage(openTwoCondMsg);
        assertNotNull(message);
        writeEmailFile(message, "mixed-open-test-" + System.currentTimeMillis() + ".eml");

        message = emailListener.createMimeMessage(ackTwoCondMsg);
        assertNotNull(message);
        writeEmailFile(message, "mixed-ack-test-" + System.currentTimeMillis() + ".eml");

        message = emailListener.createMimeMessage(resolvedTwoCondMsg);
        assertNotNull(message);
        writeEmailFile(message, "mixed-resolved-test-" + System.currentTimeMillis() + ".eml");
    }
}