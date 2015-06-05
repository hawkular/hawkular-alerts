/**
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

import java.util.List

import org.hawkular.alerts.api.model.Severity
import org.hawkular.alerts.api.model.data.Availability
import org.hawkular.alerts.api.model.data.MixedData
import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.bus.messages.AlertData
import org.hawkular.bus.restclient.RestClient

import static org.hawkular.alerts.api.model.condition.AvailabilityCondition.Operator
import static org.hawkular.alerts.api.model.trigger.Trigger.Mode
import static org.junit.Assert.assertEquals

import org.junit.FixMethodOrder
import org.junit.Test

import static org.junit.runners.MethodSorters.NAME_ASCENDING

/**
 * Alerts REST tests.
 *
 * @author Jay Shaughnessy
 */
@FixMethodOrder(NAME_ASCENDING)
class LifecycleITest extends AbstractITestBase {

    static host = System.getProperty('hawkular.host') ?: '127.0.0.1'
    static port = Integer.valueOf(System.getProperty('hawkular.port') ?: "8080")
    static t01Start = String.valueOf(System.currentTimeMillis())
    static t02Start;

    @Test
    void t01_disableTest() {
        String start = t01Start;

        // CREATE the trigger
        def resp = client.get(path: "")
        assert resp.status == 200 || resp.status == 204 : resp.status

        Trigger testTrigger = new Trigger("test-autodisable-trigger", "test-autodisable-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-autodisable-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(true);
        testTrigger.setAutoResolve(false);
        testTrigger.setSeverity(Severity.LOW);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-autodisable-trigger",
                Mode.FIRING, "test-autodisable-avail", Operator.NOT_UP);

        resp = client.post(path: "triggers/test-autodisable-trigger/conditions", body: firingCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-autodisable-trigger/", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-autodisable-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autodisable-trigger", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(true, resp.data.autoDisable);
        assertEquals("LOW", resp.data.severity);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger"] )
        assertEquals(204, resp.status)

        // Send in avail data to fire the trigger
        // Note, the groovyx rest c;lient seems incapable of allowing a JSON payload and a TEXT response (which is what
        // we get back from the activemq rest client used by the bus), so use the bus' java rest client to do this.
        RestClient busClient = new RestClient(host, port);
        String json = "{\"data\":[{\"id\":\"test-autodisable-avail\",\"timestamp\":" + System.currentTimeMillis() +
                      ",\"value\"=\"DOWN\",\"type\"=\"availability\"}]}";
        busClient.postTopicMessage("HawkularAlertData", json, null);
        //assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            // println "SLEEP!" ;
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger"] )
            if ( resp.status == 200 ) {
                break;
            }
            assert resp.status == 204 : resp.status
        }
        assertEquals(200, resp.status)

        String alertId = resp.data[0].alertId;

        // FETCH trigger and make sure it's disabled
        resp = client.get(path: "triggers/test-autodisable-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autodisable-trigger", resp.data.name)
        assertEquals(false, resp.data.enabled)

        // RESOLVE manually the alert
        resp = client.put(path: "resolve", query: [alertIds:alertId,resolvedBy:"testUser",resolvedNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger"] )
        assertEquals(200, resp.status)
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals("testUser", resp.data[0].resolvedBy)
        assertEquals("testNotes", resp.data[0].resolvedNotes)
        assert null == resp.data[0].resolvedEvalSets
    }

    @Test
    void t02_autoResolveTest() {
        String start = t02Start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assert resp.status == 200 || resp.status == 204 : resp.status

        Trigger testTrigger = new Trigger("test-autoresolve-trigger", "test-autoresolve-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-autoresolve-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(true);
        testTrigger.setAutoResolveAlerts(true);
        testTrigger.setSeverity(Severity.HIGH);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-autoresolve-trigger",
                Mode.FIRING, "test-autoresolve-avail", Operator.NOT_UP);

        resp = client.post(path: "triggers/test-autoresolve-trigger/conditions", body: firingCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ADD AutoResolve condition
        AvailabilityCondition autoResolveCond = new AvailabilityCondition("test-autoresolve-trigger",
                Mode.AUTORESOLVE, "test-autoresolve-avail", Operator.UP);

        resp = client.post(path: "triggers/test-autoresolve-trigger/conditions", body: autoResolveCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-autoresolve-trigger", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-autoresolve-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autoresolve-trigger", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(true, resp.data.autoResolve);
        assertEquals(true, resp.data.autoResolveAlerts);
        assertEquals("HIGH", resp.data.severity);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger"] )
        assertEquals(204, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        Availability avail = new Availability("test-autoresolve-avail", System.currentTimeMillis(), "DOWN");
        MixedData mixedData = new MixedData();
        mixedData.getAvailability().add(avail);
        resp = client.post(path: "data", body: mixedData);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            // println "SLEEP!" ;
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                break;
            }
            assert resp.status == 204 : resp.status
        }
        assertEquals(200, resp.status)
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)

        // ACK the alert
        resp = client.put(path: "ack", query: [alertIds:resp.data[0].alertId,ackBy:"testUser",ackNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"ACKNOWLEDGED"] )
        assertEquals(200, resp.status)
        assertEquals("ACKNOWLEDGED", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)
        assertEquals("testUser", resp.data[0].ackBy)
        assertEquals("testNotes", resp.data[0].ackNotes)

        // FETCH trigger and make sure it's still enabled (note - we can't check the mode as that is runtime
        // info and not supplied in the returned json)
        resp = client.get(path: "triggers/test-autoresolve-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autoresolve-trigger", resp.data.name)
        assertEquals(true, resp.data.enabled)

        // Send in UP avail data to autoresolve the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        avail = new Availability("test-autoresolve-avail", System.currentTimeMillis(), "UP");
        mixedData.clear();
        mixedData.getAvailability().add(avail);
        resp = client.post(path: "data", body: mixedData);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            // println "SLEEP!" ;
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"RESOLVED"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                break;
            }
            assert resp.status == 204 : resp.status
        }
        assertEquals(200, resp.status)
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals("AUTO", resp.data[0].resolvedBy)
    }


    @Test
    void t03_manualResolutionTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assert resp.status == 200 || resp.status == 204 : resp.status

        Trigger testTrigger = new Trigger("test-manual-trigger", "test-manual-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-manual-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-manual-trigger",
                Mode.FIRING, "test-manual-avail", Operator.NOT_UP);

        resp = client.post(path: "triggers/test-manual-trigger/conditions", body: firingCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-manual-trigger", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-manual-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-manual-trigger", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger"] )
        assertEquals(204, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=0; i<5; i++) {
            Availability avail = new Availability("test-manual-avail", System.currentTimeMillis(), "DOWN");
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
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        // RESOLVE manually the alert
        resp = client.put(path: "resolve", query: [alertIds:resp.data[0].alertId,resolvedBy:"testUser",
                resolvedNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN"] )
        assertEquals(200, resp.status)
        assertEquals(4, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"RESOLVED"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
    }

    // Given name ordering this test runs after tests 1..3, it uses the alerts generated in those tests
    // to test alert queries and updates.
    @Test
    void t04_fetchTest() {
        // queries will look for alerts generated in this test tun
        String start = t01Start;

        // FETCH alerts for bogus trigger, should not be any
        def resp = client.get(path: "", query: [startTime:start,triggerIds:"XXX"] )
        assertEquals(204, resp.status)

        // FETCH alerts for bogus alert id, should not be any
        resp = client.get(path: "", query: [startTime:start,alertIds:"XXX,YYY"] )
        assertEquals(204, resp.status)

        // FETCH alerts for bogus tag, should not be any
        resp = client.get(path: "", query: [startTime:start,tags:"XXX"] )
        assertEquals(204, resp.status)

        // FETCH alerts for bogus category|tag, should not be any
        resp = client.get(path: "", query: [startTime:start,tags:"XXX|YYY"] )
        assertEquals(204, resp.status)

        // FETCH alerts for just triggers generated in test t01, by time, should be 1
        resp = client.get(path: "", query: [startTime:t01Start,endTime:t02Start] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("test-autodisable-trigger", resp.data[0].triggerId)

        // FETCH the alert above again, this time by alert id
        def alertId = resp.data[0].alertId
        resp = client.get(path: "", query: [startTime:start,alertIds:"XXX,"+alertId] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals(alertId, resp.data[0].alertId)

        // FETCH the alert above again, this time by tag
        resp = client.get(path: "", query: [startTime:start,tags:"dataId|test-autodisable-avail"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals(alertId, resp.data[0].alertId)

        // FETCH the alert above again, this time by union of (good) triggerId and (bad) tag
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger",tags:"XXX"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals(alertId, resp.data[0].alertId)

        // FETCH alerts for test-manual-trigger, there should be 5 from the earlier test
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())

        // 4 OPEN and 1 RESOLVED
        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN,RESOLVED"] )
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN,RESOLVED,ACKNOWLEDGED"] )
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN,ACKNOWLEDGED"] )
        assertEquals(200, resp.status)
        assertEquals(4, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"ACKNOWLEDGED"] )
        assertEquals(204, resp.status)

        // FETCH by severity (1 HIGH and 1 LOW, five MEDIUM)
        resp = client.get(path: "", query: [startTime:start,severities:"CRITICAL"] )
        assertEquals(204, resp.status)

        resp = client.get(path: "", query: [startTime:start,severities:"LOW,HIGH,MEDIUM"] )
        assertEquals(200, resp.status)
        assertEquals(7, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,severities:"LOW"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("LOW", resp.data[0].severity)
        assertEquals("test-autodisable-trigger", resp.data[0].triggerId)

        resp = client.get(path: "", query: [startTime:start,severities:"HIGH"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("HIGH", resp.data[0].severity)
        assertEquals("test-autoresolve-trigger", resp.data[0].triggerId)

        // test thinning as well as verifying the RESOLVED status fetch (using the autoresolve alert)
        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"RESOLVED",thin:false] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals("AUTO", resp.data[0].resolvedBy)
        assert null != resp.data[0].evalSets
        assert null != resp.data[0].resolvedEvalSets
        assert !resp.data[0].evalSets.isEmpty()
        assert !resp.data[0].resolvedEvalSets.isEmpty()

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"RESOLVED",thin:true] )
        assertEquals(200, resp.status)
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals("AUTO", resp.data[0].resolvedBy)
        assert null == resp.data[0].evalSets
        assert null == resp.data[0].resolvedEvalSets
    }
}
