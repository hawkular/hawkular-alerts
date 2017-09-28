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
package org.hawkular.alerts.rest

import org.hawkular.alerts.api.model.action.ActionDefinition
import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.trigger.TriggerAction
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test

import static org.hawkular.alerts.api.model.event.Alert.Status
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class ActionsITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(ActionsITest.class)

    @Test
    void findPlugins() {
        def resp = client.get(path: "plugins")
        def data = resp.data
        assertEquals(200, resp.status)
        assertTrue(data.size() > 0)
        logger.info("Plugins: " + data)
    }

    @Test
    void findEmailPlugin() {
        /*
            Email plugin should be pre-installed on hawkular
         */
        def resp =client.get(path: "plugins/email")
        def data = resp.data
        assertEquals(200, resp.status)
        logger.info("Email plugin: " + data)
    }

    @Test
    void createAction() {
        String actionPlugin = "email"
        String actionId = "test-action";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-email@company.org");
        actionProperties.put("to", "to-email@company.org");
        actionProperties.put("cc", "cc-email@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId);
        assertEquals(200, resp.status)
        assertEquals("from-email@company.org", resp.data.properties.from)

        actionDefinition.getProperties().put("cc", "cc-modified@company.org")
        resp = client.put(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
        assertEquals("cc-modified@company.org", resp.data.properties.cc)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void createComplexAction() {
        String actionPlugin = "email"
        String actionId = "test-action";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-default@company.org");
        actionProperties.put("from.resolved", "from-resolved@company.org");
        actionProperties.put("from.acknowledged", "from-acknowledged@company.org");
        actionProperties.put("to", "to-default@company.org");
        actionProperties.put("to.acknowledged", "to-acknowledged@company.org");
        actionProperties.put("cc", "cc-email@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId);
        assertEquals(200, resp.status)
        assertEquals("from-default@company.org", resp.data.properties["from"])
        assertEquals("from-resolved@company.org", resp.data.properties["from.resolved"])
        assertEquals("from-acknowledged@company.org", resp.data.properties["from.acknowledged"])

        actionDefinition.getProperties().put("cc", "cc-modified@company.org")
        resp = client.put(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
        assertEquals("cc-modified@company.org", resp.data.properties.cc)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void failWithUnknownPropertyOnPlugin() {
        // CREATE the action definition
        String actionPlugin = "email"
        String actionId = "email-to-admin";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-admin@company.org");
        actionProperties.put("cc", "cc-developers@company.org");
        actionProperties.put("bad-property", "cc-developers@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assert(400 == resp.status)
    }

    @Test
    void createExplicitEmailPropertiesAction() {
        String actionPlugin = "email"
        String actionId = "test-action";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-default@company.org");
        actionProperties.put("from.resolved", "from-resolved@company.org");
        actionProperties.put("from.acknowledged", "from-acknowledged@company.org");
        actionProperties.put("to", "to-default@company.org");
        actionProperties.put("to.acknowledged", "to-acknowledged@company.org");
        actionProperties.put("cc", "cc-email@company.org");
        actionProperties.put("mail.smtp.host", "localhost");
        actionProperties.put("mail.smtp.port", "25");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId);
        assertEquals(200, resp.status)
        assertEquals("from-default@company.org", resp.data.properties["from"])
        assertEquals("from-resolved@company.org", resp.data.properties["from.resolved"])
        assertEquals("from-acknowledged@company.org", resp.data.properties["from.acknowledged"])

        actionDefinition.getProperties().put("cc", "cc-modified@company.org")
        resp = client.put(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
        assertEquals("cc-modified@company.org", resp.data.properties.cc)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void availabilityTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the action definition
        String actionPlugin = "email"
        String actionId = "email-to-admin";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-admin@company.org");
        actionProperties.put("cc", "cc-developers@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assert(200 == resp.status || 400 == resp.status)

        // CREATE the trigger
        resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        Trigger testTrigger = new Trigger("test-email-availability", "http://www.mydemourl.com");

        // remove if it exists
        resp = client.delete(path: "triggers/test-email-availability")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);
        /*
            email-to-admin action is pre-created from demo data
         */
        testTrigger.addAction(new TriggerAction("email", "email-to-admin"));

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-email-availability",
                Mode.FIRING, "test-email-availability", AvailabilityCondition.Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-email-availability/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-email-availability", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-email-availability");
        assertEquals(200, resp.status)
        assertEquals("http://www.mydemourl.com", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-email-availability"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=0; i<5; i++) {
            Data avail = new Data("test-email-availability", System.currentTimeMillis() + (i*1000), "DOWN");
            Collection<Data> datums = new ArrayList<>();
            datums.add(avail);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-email-availability"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        resp = client.delete(path: "triggers/test-email-availability");
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void thresholdTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the action definition
        String actionPlugin = "email"
        String actionId = "email-to-admin";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-admin@company.org");
        actionProperties.put("cc", "cc-developers@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assert(200 == resp.status || 400 == resp.status)

        // CREATE the trigger
        resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        Trigger testTrigger = new Trigger("test-email-threshold", "http://www.mydemourl.com");

        // remove if it exists
        resp = client.delete(path: "triggers/test-email-threshold")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);
        /*
            email-to-admin action is pre-created from demo data
         */
        testTrigger.addAction(new TriggerAction("email", "email-to-admin"));

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        ThresholdCondition firingCond = new ThresholdCondition("test-email-threshold",
                Mode.FIRING, "test-email-threshold", ThresholdCondition.Operator.GT, 300);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-email-threshold/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query:[triggerIds:"test-email-threshold",enabled:true] )
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-email-threshold");
        assertEquals(200, resp.status)
        assertEquals("http://www.mydemourl.com", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-email-threshold"] )
        assertEquals(200, resp.status)

        // Send in data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=0; i<5; i++) {
            Data threshold = new Data("test-email-threshold", System.currentTimeMillis() + (i*1000), String.valueOf(305.5 + i));
            Collection<Data> datums = new ArrayList<>();
            datums.add(threshold);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-email-threshold"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        resp = client.delete(path: "triggers/test-email-threshold");
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void actionByStatusTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // Check endpoint
        def resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        // Create an action definition for admins
        String actionPlugin = "email"
        String actionId = "notify-to-admins";

        // Remove previous history
        client.put(path: "actions/history/delete", query: [actionPlugins:"email"])

        // Remove a previous action
        client.delete(path: "actions/" + actionPlugin + "/" + actionId)

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-admin@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        // Create an action definition for developers
        actionPlugin = "email"
        actionId = "notify-to-developers";

        // Remove a previous action
        client.delete(path: "actions/" + actionPlugin + "/" + actionId)

        actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-developers@company.org");

        actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        // Create a trigger

        Trigger testTrigger = new Trigger("test-status-threshold", "http://www.mydemourl.com");

        // remove if it exists
        resp = client.delete(path: "triggers/test-status-threshold")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);

        TriggerAction notifyAdmins = new TriggerAction("email", "notify-to-admins");
        notifyAdmins.addState(Status.OPEN.name());
        TriggerAction notifyDevelopers = new TriggerAction("email", "notify-to-developers");
        notifyDevelopers.addState(Status.ACKNOWLEDGED.name());

        testTrigger.addAction(notifyAdmins);
        testTrigger.addAction(notifyDevelopers);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        ThresholdCondition firingCond = new ThresholdCondition("test-status-threshold",
                Mode.FIRING, "test-status-threshold", ThresholdCondition.Operator.GT, 300);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-status-threshold/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-status-threshold",enabled:true] )
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-status-threshold");
        assertEquals(200, resp.status)
        assertEquals("http://www.mydemourl.com", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // Send in data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=0; i<5; i++) {
            Data threshold = new Data("test-status-threshold", System.currentTimeMillis() + (i*1000), String.valueOf(305.5 + i));
            Collection<Data> datums = new ArrayList<>();
            datums.add(threshold);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 100; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-status-threshold"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        def alertsToAck = resp.data;

        // Check actions generated
        // This used to fail randomly, therefore try several times before failing
        for ( int i=0; i < 20; ++i ) {
            resp = client.get(path: "actions/history", query: [startTime:start,actionPlugins:"email"])
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
            Thread.sleep(500);
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())

        // Ack alerts generated
        def alertsToAckIds = "";
        for ( int i=0; i < alertsToAck.size(); i++ ) {
            alertsToAckIds += alertsToAck[i].id;
            if (i != 4) {
                alertsToAckIds += ",";
            }
        }

        // ACK Alerts generated
        client.put(path: "ack", query: [alertIds:alertsToAckIds,ackBy:"testUser",ackNotes:"testNotes"] )

        // Check if we have the actions for ACKNOWLEDGE
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "actions/history", query: [startTime:start,actionPlugins:"email"])
            if ( resp.status == 200 && resp.data.size() == 10 ) {
                break;
            }
        }

        assertEquals(200, resp.status)
        assertEquals(10, resp.data.size())

        resp = client.delete(path: "triggers/test-status-threshold");
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/email/notify-to-admins")
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/email/notify-to-developers")
        assertEquals(200, resp.status)
    }

    @Test
    void globalActionsTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // Check endpoint
        def resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        // Create an action definition for admins
        String actionPlugin = "email"
        String actionId = "global-action-notify-to-admins";

        // Remove previous history
        client.put(path: "actions/history/delete", query: [actionPlugins:"email"])

        // Remove a previous action
        client.delete(path: "actions/" + actionPlugin + "/" + actionId)

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-admin@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);
        actionDefinition.setGlobal(true);

        resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        // Create an action definition for developers
        actionPlugin = "email"
        actionId = "global-action-notify-to-developers";

        // Remove a previous action
        client.delete(path: "actions/" + actionPlugin + "/" + actionId)

        actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-developers@company.org");

        actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);
        actionDefinition.setGlobal(true);

        resp = client.post(path: "actions", body: actionDefinition)
        assertEquals(200, resp.status)

        // Create a trigger

        Trigger testTrigger = new Trigger("test-global-status-threshold", "http://www.mydemourl.com");

        // remove if it exists
        resp = client.delete(path: "triggers/test-global-status-threshold")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        ThresholdCondition firingCond = new ThresholdCondition("test-global-status-threshold",
                Mode.FIRING, "test-global-status-threshold", ThresholdCondition.Operator.GT, 300);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-global-status-threshold/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query:[triggerIds:"test-global-status-threshold",enabled:true] )
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-global-status-threshold");
        assertEquals(200, resp.status)
        assertEquals("http://www.mydemourl.com", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // Send in data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=0; i<5; i++) {
            Data threshold = new Data("test-global-status-threshold", System.currentTimeMillis() + (i*1000),
                    String.valueOf(305.5 + i));
            Collection<Data> datums = new ArrayList<>();
            datums.add(threshold);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 100; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-global-status-threshold"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        // Check actions generated
        // This used to fail randomly, therefore try several times before failing
        for ( int i=0; i < 30; ++i ) {
            resp = client.get(path: "actions/history",
                    query: [startTime:start,actionPlugins:"email",
                            actionIds:"global-action-notify-to-admins,global-action-notify-to-developers"])
            if ( resp.status == 200 && resp.data.size() == 10 ) {
                break;
            }
            Thread.sleep(500);
        }

        assertEquals(200, resp.status)
        assertEquals(10, resp.data.size())

        resp = client.delete(path: "triggers/test-global-status-threshold");
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/email/global-action-notify-to-admins")
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/email/global-action-notify-to-developers")
        assertEquals(200, resp.status)
    }
}
