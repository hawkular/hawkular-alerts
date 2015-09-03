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
package org.hawkular.alerts.rest

import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.data.Availability
import org.hawkular.alerts.api.model.data.MixedData
import org.hawkular.alerts.api.model.data.NumericData
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class ActionsITest extends AbstractITestBase {

    @Test
    void findPlugins() {
        def resp = client.get(path: "plugins")
        def data = resp.data
        assertEquals(200, resp.status)
        assertTrue(data.size() > 0)
        println "Plugins: " + data
    }

    @Test
    void findEmailPlugin() {
        /*
            Email plugin should be pre-installed on hawkular
         */
        def resp =client.get(path: "plugins/email")
        def data = resp.data
        assertEquals(200, resp.status)
        println "Email plugin: " + data
    }

    @Test
    void findInitialActions() {
        def resp = client.get(path: "actions")
        def data = resp.data
        assertEquals(200, resp.status)
        assertTrue(data.size() > 0)
        Map map = (Map)data;
        for (String actionPlugin : map.keySet()) {
            println "ActionPlugin: " + actionPlugin + " - Plugins: " + map.get(actionPlugin)
        }
    }

    @Test
    void createAction() {

        String actionPlugin = "email"
        String actionId = "test-action";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("actionPlugin", actionPlugin);
        actionProperties.put("actionId", actionId);
        actionProperties.put("prop1", "value1");
        actionProperties.put("prop2", "value2");
        actionProperties.put("prop3", "value3");

        def resp = client.post(path: "actions", body: actionProperties)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId);
        assertEquals(200, resp.status)
        assertEquals("value1", resp.data.prop1)

        actionProperties.put("prop3", "value3Modified")
        resp = client.put(path: "actions/" + actionPlugin + "/" + actionId, body: actionProperties)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
        assertEquals("value3Modified", resp.data.prop3)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void availabilityTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
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
        testTrigger.addAction("email", "email-to-admin");

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
            Availability avail = new Availability("test-email-availability", System.currentTimeMillis(), "DOWN");
            MixedData mixedData = new MixedData();
            mixedData.getAvailability().add(avail);
            resp = client.post(path: "data", body: mixedData);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            // println "SLEEP!" ;
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
    }

    @Test
    void thresholdTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
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
        testTrigger.addAction("email", "email-to-admin");

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
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-email-threshold", body: testTrigger)
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

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=0; i<5; i++) {
            NumericData threshold = new NumericData("test-email-threshold", System.currentTimeMillis(), 305.5 + i);
            MixedData mixedData = new MixedData();
            mixedData.getNumericData().add(threshold);
            resp = client.post(path: "data", body: mixedData);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            // println "SLEEP!" ;
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
    }


}
