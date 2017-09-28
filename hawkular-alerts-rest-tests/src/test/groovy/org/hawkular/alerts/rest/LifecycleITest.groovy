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

import static org.hawkular.alerts.api.model.condition.AvailabilityCondition.Operator
import static org.hawkular.alerts.api.model.data.AvailabilityType.DOWN
import static org.hawkular.alerts.api.model.data.AvailabilityType.UP
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.runners.MethodSorters.NAME_ASCENDING

import org.hawkular.alerts.api.model.Severity
import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.MissingCondition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.event.Alert
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.FixMethodOrder
import org.junit.Test


/**
 * Alerts REST tests.
 *
 * @author Jay Shaughnessy
 */
@FixMethodOrder(NAME_ASCENDING)
class LifecycleITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(LifecycleITest.class)

    static host = System.getProperty('hawkular.host') ?: '127.0.0.1'
    static port = Integer.valueOf(System.getProperty('hawkular.port') ?: "8080")
    static t01Start = String.valueOf(System.currentTimeMillis())
    static t02Start;

    @Test
    void t01_disableTest() {
        logger.info( "Running t01_disableTest")
        String start = t01Start;

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        // sub-test: add context and ensure it carries through to the alert
        Map<String,String> context = new HashMap<>(1);
        context.put("contextName","contextValue");
        context.put("contextName2","contextValue2");
        Trigger testTrigger = new Trigger("test-autodisable-trigger", "test-autodisable-trigger", context);

        // sub-test: add tag and ensure it carries through to the alert
        testTrigger.addTag("test-autodisable-tname","test-autodisable-tvalue");

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


        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-autodisable-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-autodisable-trigger",enabled:true])
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-autodisable-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autodisable-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertTrue(resp.data.autoDisable);
        assertFalse(resp.data.autoEnable);
        assertEquals("LOW", resp.data.severity);
        assertNotNull(resp.data.context);
        assertEquals("contextValue", resp.data.context.get("contextName"));
        assertEquals("contextValue2", resp.data.context.get("contextName2"));

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger"] )
        assertEquals(200, resp.status)

        // Send in avail data to fire the trigger
        String jsonData =
            "[{\"id\":\"test-autodisable-avail\",\"timestamp\":" + 1000 + ",\"value\":\"DOWN\"}]";
        resp = client.post(path: "data", body: jsonData);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size > 0) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        String alertId = resp.data[0].id;

        // FETCH trigger and make sure it's disabled
        resp = client.get(path: "triggers/test-autodisable-trigger");
        assertEquals(200, resp.status)
        Trigger t = (Trigger)resp.data;
        logger.info(t.toString());
        assertEquals("test-autodisable-trigger", resp.data.name)
        assertFalse(t.toString(), resp.data.enabled)

        // RESOLVE manually the alert
        resp = client.put(path: "resolve", query: [alertIds:alertId,resolvedBy:"testUser",resolvedNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger"] )
        assertEquals(200, resp.status)
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals(2, resp.data[0].lifecycle.size())
        assertEquals("testUser", resp.data[0].lifecycle[1].user)
        assertEquals("testNotes", resp.data[0].notes[0].text)
        assertNull(resp.data[0].resolvedEvalSets)
        assertNotNull(resp.data[0].trigger.context);
        Map<String,String> alertContext = (Map<String,String>)resp.data[0].trigger.context;
        assertEquals("contextValue", alertContext.get("contextName"));
        assertEquals("contextValue2", alertContext.get("contextName2"));

        // FETCH trigger and make sure it's still disabled, because autoEnable was set to false
        resp = client.get(path: "triggers/test-autodisable-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autodisable-trigger", resp.data.name)
        assertFalse(resp.data.enabled)
    }

    @Test
    void t02_autoResolveTest() {
        logger.info( "Running t02_autoResolveTest")
        String start = t02Start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

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

        // ADD Firing conditions
        AvailabilityCondition firingCond = new AvailabilityCondition("test-autoresolve-trigger",
                Mode.FIRING, "test-autoresolve-avail", Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-autoresolve-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ADD AutoResolve condition
        AvailabilityCondition autoResolveCond = new AvailabilityCondition("test-autoresolve-trigger",
                Mode.AUTORESOLVE, "test-autoresolve-avail", Operator.UP);

        conditions.clear();
        conditions.add( autoResolveCond );
        resp = client.put(path: "triggers/test-autoresolve-trigger/conditions/autoresolve", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-autoresolve-trigger",enabled:true])
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-autoresolve-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autoresolve-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertTrue(resp.data.autoResolve);
        assertTrue(resp.data.autoResolveAlerts);
        assertEquals("HIGH", resp.data.severity);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        Map<String,String> context = new HashMap<>(1);
        context.put("contextName","contextValue");
        Data avail = Data.forAvailability("", "test-autoresolve-avail", 1000 , DOWN, context);
        Collection<Data> datums = new ArrayList<Data>();
        datums.add(avail);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)

        // ACK the alert
        resp = client.put(path: "ack", query: [alertIds:resp.data[0].id,ackBy:"testUser",ackNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"ACKNOWLEDGED"] )
        assertEquals(200, resp.status)
        assertEquals("ACKNOWLEDGED", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)
        assertEquals(2, resp.data[0].lifecycle.size())
        assertEquals("testUser", resp.data[0].lifecycle[1].user)
        assertEquals("testNotes", resp.data[0].notes[0].text)

        // FETCH trigger and make sure it's still enabled (note - we can't check the mode as that is runtime
        // info and not supplied in the returned json)
        resp = client.get(path: "triggers/test-autoresolve-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autoresolve-trigger", resp.data.name)
        assertTrue(resp.data.enabled)

        // Send in UP avail data to autoresolve the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        avail = Data.forAvailability("", "test-autoresolve-avail", 1000, UP);
        datums.clear();
        datums.add(avail);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"RESOLVED"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals(3, resp.data[0].lifecycle.size())
        assertEquals("AutoResolve", resp.data[0].lifecycle[2].user)
    }

    @Test
    void t03_manualResolutionTest() {
        logger.info( "Running t03_manualResolutionTest")
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

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

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-manual-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-manual-trigger",enabled:true])
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-manual-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-manual-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertFalse(resp.data.autoResolve);
        assertFalse(resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=1000; i<=5000; i+=1000) {
            Data avail = Data.forAvailability("", "test-manual-avail", i, DOWN);
            Collection<Data> datums = new ArrayList<>();
            datums.add(avail);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 30; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                if ( i > 15 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
        }
        assertEquals(200, resp.status)
        assertFalse(resp.data.isEmpty())
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        // RESOLVE manually the alert
        resp = client.put(path: "resolve", query: [alertIds:resp.data[0].id,resolvedBy:"testUser",
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
        logger.info( "Running t04_fetchTest")
        // queries will look for alerts generated in this test tun
        String start = t01Start;

        // FETCH alerts for bogus trigger, should not be any
        def resp = client.get(path: "", query: [startTime:start,triggerIds:"XXX"] )
        assertEquals(200, resp.status)
        assertTrue(resp.data.isEmpty())

        // FETCH alerts for bogus alert id, should not be any
        resp = client.get(path: "", query: [startTime:start,alertIds:"XXX,YYY"] )
        assertEquals(200, resp.status)
        assertTrue(resp.data.isEmpty())

        // FETCH alerts for bogus name tag, should not be any
        resp = client.get(path: "", query: [startTime:start,tags:"XXX|*"] )
        assertEquals(200, resp.status)
        assertTrue(resp.data.isEmpty())

        // FETCH alerts for bogus name|value tag, should not be any
        resp = client.get(path: "", query: [startTime:start,tags:"XXX|YYY"] )
        assertEquals(200, resp.status)
        assertTrue(resp.data.isEmpty())

        // FETCH alerts for bogus value name/value tag syntax, should fail
        resp = client.get(path: "", query: [startTime:start,tags:"test-autodisable-tname/test-autodisable-tvalue"] )
        assertEquals(400, resp.status)

        // FETCH alerts for just triggers generated in test t01, by time, should be 1
        resp = client.get(path: "", query: [startTime:t01Start,endTime:t02Start] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("test-autodisable-trigger", resp.data[0].trigger.id)

        // FETCH the alert above again, this time by alert id
        def alertId = resp.data[0].id
        resp = client.get(path: "", query: [startTime:start,alertIds:"XXX,"+alertId] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals(alertId, resp.data[0].id)

        // FETCH the alert above again, this time by tag
        resp = client.get(path: "", query: [startTime:start,tags:"test-autodisable-tname|test-autodisable-tvalue"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals(alertId, resp.data[0].id)

        // FETCH the alert above again (fail), with a good triggerId but a bad tag
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autodisable-trigger",tags:"XXX|*"] )
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        // FETCH alerts for test-autoresolve-trigger, there should be 1 from the earlier test, with context data
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertNotNull(resp.data[0].evalSets)
        assertTrue(!resp.data[0].evalSets.isEmpty())
        AvailabilityConditionEval eval =
            (AvailabilityConditionEval)resp.data[0].evalSets.iterator().next().iterator().next();
        assertNotNull(eval.getContext())
        assertTrue("contextValue".equals(eval.getContext().get("contextName")))

        // FETCH alerts for test-manual-trigger, there should be 5 from the earlier test
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())

        // 4 OPEN and 1 RESOLVED
        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN"] )
        assertEquals(200, resp.status)
        assertEquals(4, resp.data.size())

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"RESOLVED"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        resp = client.get(path: "",
                query: [startResolvedTime:start,triggerIds:"test-manual-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

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
        assertEquals(200, resp.status)

        // FETCH by severity (1 HIGH and 1 LOW, five MEDIUM)
        resp = client.get(path: "", query: [startTime:start,severities:"CRITICAL"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,severities:"LOW,HIGH,MEDIUM"] )
        assertEquals(200, resp.status)
        assertEquals(7, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,severities:"LOW"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("LOW", resp.data[0].severity)
        assertEquals("test-autodisable-trigger", resp.data[0].trigger.id)

        resp = client.get(path: "", query: [startTime:start,severities:"HIGH"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("HIGH", resp.data[0].severity)
        assertEquals("test-autoresolve-trigger", resp.data[0].trigger.id)

        // test thinning as well as verifying the RESOLVED status fetch (using the autoresolve alert)
        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"RESOLVED",thin:false] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals(3, resp.data[0].lifecycle.size())
        assertEquals("AutoResolve", resp.data[0].lifecycle[2].user)
        assertNotNull(resp.data[0].evalSets)
        assertNotNull(resp.data[0].resolvedEvalSets)
        assertFalse(resp.data[0].evalSets.isEmpty())
        assertFalse(resp.data[0].resolvedEvalSets.isEmpty())

        resp = client.get(path: "",
            query: [startTime:start,triggerIds:"test-autoresolve-trigger",statuses:"RESOLVED",thin:true] )
        assertEquals(200, resp.status)
        assertEquals("RESOLVED", resp.data[0].status)
        assertEquals(3, resp.data[0].lifecycle.size())
        assertEquals("AutoResolve", resp.data[0].lifecycle[2].user)
        assertNull(resp.data[0].evalSets)
        assertNull(resp.data[0].resolvedEvalSets)
    }

    @Test
    void t05_paging() {
        logger.info( "Running t05_paging")
        // queries will look for alerts generated in this test tun
        String start = t01Start;

        // 4 OPEN and 1 RESOLVED
        def resp = client.get(path: "",
                query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN,RESOLVED", page: "0", per_page: "3"] )
        assertEquals(200, resp.status)
        assertEquals(3, resp.data.size())

        // logger.info(resp.headers)

        resp = client.get(path: "",
                query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"OPEN,RESOLVED", page: "1", per_page: "3"] )
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        // logger.info(resp.headers)
    }

    @Test
    void t055_tagging() {
        logger.info( "Running t055_tagging")
        // queries will look for alerts generated in this test tun
        String start = t01Start;

        // Fetch the 1 RESOLVED alert and play with its tags
        def resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-trigger",statuses:"RESOLVED"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        Alert alert = resp.data[0]

        resp = client.put(path: "tags", query: [alertIds:alert.id,tags:"tag1name|tag1value"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,tags:"tag1name|tag1value"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        alert = resp.data[0]
        assertEquals(1, alert.tags.size())
        assertEquals("tag1value", alert.tags.get("tag1name"))

        resp = client.delete(path: "tags", query: [alertIds:alert.id,tagNames:"tag1name"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,tags:"tag1name|tag1value"] )
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())
    }

    @Test
    void t06_manualAckAndResolutionTest() {
        logger.info( "Running t06_manualAckAndResolutionTest")
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        Trigger testTrigger = new Trigger("test-manual2-trigger", "test-manual2-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-manual2-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-manual2-trigger",
                Mode.FIRING, "test-manual2-avail", Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-manual2-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-manual2-trigger",enabled:true])
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-manual2-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-manual2-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertFalse(resp.data.autoResolve);
        assertFalse(resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual2-trigger"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=1000; i<=5000; i+=1000) {
            Data avail = Data.forAvailability("", "test-manual2-avail", i, DOWN);
            Collection<Data> datums = new ArrayList<>();
            datums.add(avail);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 30; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual2-trigger"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size() == 5 ) {
                if ( i > 15 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        def alertId1 = resp.data[0].id;
        def alertId2 = resp.data[1].id;
        // RESOLVE manually 1 alert
        resp = client.put(path: "resolve/" + alertId1,
                query: [resolvedBy:"testUser", resolvedNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.put(path: "ack/" + alertId2,
                query: [ackBy:"testUser", ackNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual2-trigger",statuses:"OPEN"] )
        assertEquals(200, resp.status)
        assertEquals(3, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual2-trigger",statuses:"ACKNOWLEDGED"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        resp = client.get(path: "", query: [startAckTime:start,triggerIds:"test-manual2-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual2-trigger",statuses:"RESOLVED"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        resp = client.get(path: "", query: [startResolvedTime:start,triggerIds:"test-manual2-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
    }

    @Test
    void t07_autoResolveWithThresholdTest() {
        logger.info( "Running t07_autoResolveWithThresholdTest")
        String start = String.valueOf(System.currentTimeMillis());

        /*
            Step 0: Check REST API is up and running
         */
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        /*
            Step 1: Remove previous existing definition for this test
         */
        resp = client.delete(path: "triggers/test-autoresolve-threshold-trigger")
        assert(200 == resp.status || 404 == resp.status)

        /*
            Step 2: Create a new trigger
         */
        Trigger testTrigger = new Trigger("test-autoresolve-threshold-trigger", "http://www.myresource.com");
        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(true);
        testTrigger.setAutoResolveAlerts(false);
        testTrigger.setSeverity(Severity.HIGH);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        /*
            Step 3: Create a threshold FIRING condition
                    Fires when ResponseTime > 100 ms
                    "Normal" scenario, the condition represents a "bad" situation we want to monitor and alert
         */
        ThresholdCondition firingCond = new ThresholdCondition("test-autoresolve-threshold-trigger",
                Mode.FIRING, "test-autoresolve-threshold", ThresholdCondition.Operator.GT, 100);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-autoresolve-threshold-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        /*
            Step 4: Create a threshold AUTORESOLVE condition
                    Fires when ResponseTime <= 100 ms
                    "Resolution" scenario, the condition represent a "good" situation to enable again "Normal" scenario
                    Typically it should be the "opposite" than
         */
        ThresholdCondition autoResolveCond = new ThresholdCondition("test-autoresolve-threshold-trigger",
                Mode.AUTORESOLVE, "test-autoresolve-threshold", ThresholdCondition.Operator.LTE, 100);

        conditions.clear();
        conditions.add( autoResolveCond );
        resp = client.put(path: "triggers/test-autoresolve-threshold-trigger/conditions/autoresolve", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        /*
            Step 5: Enable the trigger to accept data
         */
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-autoresolve-threshold-trigger",enabled:true])
        assertEquals(200, resp.status)

        /*
            Step 6: Check trigger is created correctly
         */
        resp = client.get(path: "triggers/test-autoresolve-threshold-trigger");
        assertEquals(200, resp.status)
        assertEquals("http://www.myresource.com", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertTrue(resp.data.autoResolve);
        assertFalse(resp.data.autoResolveAlerts);
        assertEquals("HIGH", resp.data.severity);

        /*
            Step 7: Check there is not alerts for this trigger at this point
         */
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-threshold-trigger"] )
        assertEquals(200, resp.status)
        assertTrue(resp.data.isEmpty())

        /*
            Step 8: Sending "bad" data to fire the trigger
                    Using direct API instead bus messages
         */
        Data responseTime = Data.forNumeric("", "test-autoresolve-threshold", 1000, 101);
        Collection<Data> datums = new ArrayList<>();
        datums.add(responseTime);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        /*
             Step 9: Wait until the engine detects the data, matches the conditions and sends an alert
         */
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-threshold-trigger"] )
            /*
                We should have only 1 alert
             */
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)

        /*
            Step 10: Sending "bad" data to fire the trigger, should not fire, trigger now in AutoResolve mode
         */
        responseTime = Data.forNumeric("", "test-autoresolve-threshold", 2000, 102);
        datums = new ArrayList<>();
        datums.add(responseTime);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        /*
             Step 11: Wait for engine to process data
                      It should retrieve only 1 data not 2 as previous data shouldn't generate a new alert
         */
        Thread.sleep(2500);
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-threshold-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)

        /*
            Step 12: Sending "good" data to change trigger from FIRING to AUTORESOLVE
         */
        responseTime = Data.forNumeric("", "test-autoresolve-threshold", 3000, 95);
        datums = new ArrayList<>();
        datums.add(responseTime);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        /*
             Step 13: Wait until the engine detects the data
                      It should retrieve only 1 data not 2 as previous data shouldn't generate a new alert
         */
        Thread.sleep(2500);
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-threshold-trigger"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)

        /*
            Step 14: Sending "bad" data to fire the trigger
         */
        responseTime = Data.forNumeric("", "test-autoresolve-threshold", 4000, 103);
        datums = new ArrayList<>();
        datums.add(responseTime);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        /*
             Step 15: Wait until the engine detects the data
                      It should retrieve 2 data
         */
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoresolve-threshold-trigger"] )
            /*
                We should have only 2 alert
             */
            if ( resp.status == 200 && resp.data.size() == 2 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)
    }

    @Test
    void t08_autoEnableTest() {
        logger.info("Running t08_autoEnableTest")
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        Trigger testTrigger = new Trigger("test-autoenable-trigger", "test-autoenable-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-autoenable-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(true);
        testTrigger.setAutoEnable(true);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-autoenable-trigger",
                Mode.FIRING, "test-autoenable-avail", Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-autoenable-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-autoenable-trigger", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-autoenable-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-autoenable-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertTrue(resp.data.autoDisable);
        assertTrue(resp.data.autoEnable);
        assertFalse(resp.data.autoResolve);
        assertFalse(resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoenable-trigger"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        for (int i=1000; i<=2000; i+=1000) {
            Data avail = Data.forAvailability("", "test-autoenable-avail", i, DOWN);
            Collection<Data> datums = new ArrayList<>();
            datums.add(avail);
            resp = client.post(path: "data", body: datums);
            assertEquals(200, resp.status)
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1 because the trigger should have disabled after firing
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoenable-trigger"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        // FETCH trigger and make sure it's disabled
        def resp2 = client.get(path: "triggers/test-autoenable-trigger");
        assertEquals(200, resp2.status)
        assertEquals("test-autoenable-trigger", resp2.data.name)
        assertFalse(resp2.data.enabled)

        // RESOLVE manually the alert
        resp = client.put(path: "resolve", query: [alertIds:resp.data[0].id,resolvedBy:"testUser",
                resolvedNotes:"testNotes"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoenable-trigger",statuses:"OPEN"] )
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-autoenable-trigger",statuses:"RESOLVED"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // delete any other alerts for the trigger because all alerts for the trigger
        // must be resolved for autoEnable to kick in...
        resp = client.put(path: "delete", query: [triggerIds:"test-autoenable-trigger",statuses:"OPEN"] )
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's now enabled
        resp2 = client.get(path: "triggers/test-autoenable-trigger");
        assertEquals(200, resp2.status)
        assertEquals("test-autoenable-trigger", resp2.data.name)
        assertTrue(resp2.data.enabled)
    }

    @Test
    void t09_manualAutoResolveTest() {
        logger.info("Running t09_manualAutoResolveTest")
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        Trigger testTrigger = new Trigger("test-manual-autoresolve-trigger", "test-manual-autoresolve-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-manual-autoresolve-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(true);
        testTrigger.setAutoResolveAlerts(true);
        testTrigger.setSeverity(Severity.HIGH);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-manual-autoresolve-trigger",
                Mode.FIRING, "test-manual-autoresolve-avail", Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-manual-autoresolve-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ADD AutoResolve condition
        AvailabilityCondition autoResolveCond = new AvailabilityCondition("test-manual-autoresolve-trigger",
                Mode.AUTORESOLVE, "test-manual-autoresolve-avail", Operator.UP);

        conditions.clear();
        conditions.add( autoResolveCond );
        resp = client.put(path: "triggers/test-manual-autoresolve-trigger/conditions/autoresolve", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-manual-autoresolve-trigger",enabled:true])
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-manual-autoresolve-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-manual-autoresolve-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertFalse(resp.data.autoEnable);
        assertTrue(resp.data.autoResolve);
        assertTrue(resp.data.autoResolveAlerts);
        assertEquals("HIGH", resp.data.severity);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-autoresolve-trigger"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger
        // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
        Data avail = Data.forAvailability("", "test-manual-autoresolve-avail", 1000, DOWN);
        Collection<Data> datums = new ArrayList<>();
        datums.add(avail);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-manual-autoresolve-trigger"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
        assertEquals("HIGH", resp.data[0].severity)
        String alertId = resp.data[0].id;

        // FETCH trigger and make sure it's still enabled (note - we can't check the mode as that is runtime
        // info and not supplied in the returned json)
        resp = client.get(path: "triggers/test-manual-autoresolve-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-manual-autoresolve-trigger", resp.data.name)
        assertTrue(resp.data.enabled)

        // Manually RESOLVE the alert prior to an autoResolve
        // At the time of this writing we don't have a service to purge/delete alerts, so for this to work we
        // have to make sure any old alerts (from prior runs) are also resolved. Because all alerts for the trigger
        // must be resolved for autoEnable to kick in...
        resp = client.get(path: "", query: [triggerIds:"test-manual-autoresolve-trigger",statuses:"OPEN"] )
        assertEquals(200, resp.status)
        for(int i=0; i < resp.data.size(); ++i) {
            def resp2 = client.put(path: "resolve", query: [alertIds:resp.data[i].id,resolvedBy:"testUser",
                resolvedNotes:"testNotes"] )
            assertEquals(200, resp2.status)
        }

        // manually resolve the alert again, from here it just ensures that this doesn't create a problem
        // but examination of the debug log allows us to ensure that this does not cause a reload of the
        // the trigger into the engine.
        resp = client.put(path: "resolve", query: [alertIds:alertId,resolvedBy:"testUser", resolvedNotes:"testNotes"] )
        assertEquals(200, resp.status)

        // Send in another DOWN data and we should get another alert assuming the trigger was reset to Firing mode
        avail = Data.forAvailability("", "test-manual-autoresolve-avail", 2000, DOWN);
        datums = new ArrayList<>();
        datums.add(avail);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent OPEN alerts for trigger, there should be 1
            resp = client.get(path: "",
                query: [startTime:start,triggerIds:"test-manual-autoresolve-trigger",statuses:"OPEN"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
    }

    @Test
    void t10_multiDisable() {
        def testTriggerIds = "test-autodisable-trigger";
        testTriggerIds += ",test-autoresolve-trigger";
        testTriggerIds += ",test-manual-trigger";
        testTriggerIds += ",test-manual2-trigger";
        testTriggerIds += ",test-autoresolve-threshold-trigger";
        testTriggerIds += ",test-autoenable-trigger";
        testTriggerIds += ",test-manual-autoresolve-trigger";
        def resp = client.get(path: "triggers", query: [triggerIds:testTriggerIds,thin:true])
        assert(200 == resp.status)

        def triggerIds = "";
        def numTriggers = 0;
        for (int i=0; i < resp.data.size(); ++i) {
            triggerIds += (( i > 0 ) ? "," : "");
            triggerIds += resp.data[i].id;
            ++numTriggers;
        }

        resp = client.put(path: "triggers/enabled", query: [triggerIds:triggerIds,enabled:false])
        assert(200 == resp.status)

        resp = client.get(path: "triggers", query: [triggerIds:triggerIds,thin:true])
        assert(200 == resp.status)
        assert(numTriggers == resp.data.size())

        for (int i=0; i < resp.data.size(); ++i) {
            assert(false == resp.data[i].enabled)
        }

        // test NotFound (nothing should get set true)
        def badIds = triggerIds + "BOGUS";
        resp = client.put(path: "triggers/enabled", query: [triggerIds:badIds,enabled:true])
        assert(404 == resp.status)

        resp = client.get(path: "triggers", query: [triggerIds:triggerIds,thin:true])
        assert(200 == resp.status)
        assert(numTriggers == resp.data.size())

        for (int i=0; i < resp.data.size(); ++i) {
            assert(false == resp.data[i].enabled)
        }
    }

    @Test
    void t11_hwkalerts234Test() {
        logger.info("Running t10_hwkalerts234Test")
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        Trigger testTrigger = new Trigger("test-hwkalerts234-trigger", "test-hwkalerts234-trigger");

        // remove if it exists
        resp = client.delete(path: "triggers/test-hwkalerts234-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoResolve(true);
        testTrigger.setAutoResolveAlerts(true);

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        MissingCondition firingCond = new MissingCondition("test-hwkalerts234-trigger",
                Mode.FIRING, "test-hwkalerts234-avail", 3000);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-hwkalerts234-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ADD AutoResolve condition
        AvailabilityCondition autoResolveCond = new AvailabilityCondition("test-hwkalerts234-trigger",
                Mode.AUTORESOLVE, "test-hwkalerts234-avail", Operator.UP);

        conditions.clear();
        conditions.add( autoResolveCond );
        resp = client.put(path: "triggers/test-hwkalerts234-trigger/conditions/autoresolve", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-hwkalerts234-trigger", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-hwkalerts234-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-hwkalerts234-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertFalse(resp.data.autoEnable);
        assertTrue(resp.data.autoResolve);
        assertTrue(resp.data.autoResolveAlerts);

        // This tests that one and only one alert is generated, so let it run for 15s.  This is plenty of time
        // to ensure we are properly skipping the MissingState check based on the trigger now being in
        // autoresolve mode (3s missingcondition interval and 2s engine runs)
        for ( int i=0; i < 30; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-hwkalerts234-trigger"] )
            if ( resp.status == 200 && resp.data.size() >= 2 ) {
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)

        // FETCH trigger and make sure it's still enabled (note - we can't check the mode as that is runtime
        // info and not supplied in the returned json)
        resp = client.get(path: "triggers/test-hwkalerts234-trigger");
        assertEquals(200, resp.status)
        assertTrue(resp.data.enabled)

        // Send in UP avail and complete the auto-resolve
        def avail = Data.forAvailability("", "test-hwkalerts234-avail", System.currentTimeMillis(), UP);
        def datums = new ArrayList<>();
        datums.add(avail);
        resp = client.post(path: "data", body: datums);
        assertEquals(200, resp.status)

        // We should see our alert quickly move to RESOLVED
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "",
                query: [startTime:start,triggerIds:"test-hwkalerts234-trigger",statuses:"RESOLVED"] )
            if ( resp.status == 200 && resp.data.size() == 1 ) {
                System.out.println(resp.data);
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
        assertEquals("RESOLVED", resp.data[0].status)

        // DISABLE Trigger to stop it from firing again
        testTrigger.setEnabled(false);

        resp = client.put(path: "triggers/test-hwkalerts234-trigger", body: testTrigger)
        assertEquals(200, resp.status)
    }

    @Test
    void t99_cleanup() {
        logger.info("Running t99_cleanup")
        // clean up triggers
        def resp = client.delete(path: "triggers/test-autodisable-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-autoresolve-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-manual-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-manual2-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-autoresolve-threshold-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-autoenable-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-manual-autoresolve-trigger")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-hwkalerts234-trigger")
        assert(200 == resp.status || 404 == resp.status)

        // clean up alerts

        resp = client.get(path: "", query: [triggerIds:"test-autodisable-trigger"])
        assertEquals(200, resp.status)
        assertFalse( resp.data.isEmpty() )

        def resp2;
        for(int i=0; i < resp.data.size(); ++i) {
            // test single alert get
            resp2 = client.get(path: "alert/" + resp.data[i].id )
            assertEquals(200, resp2.status)
            // test single alert delete
            def resp3 = client.delete(path: resp.data[i].id )
            assertEquals(200, resp3.status)
        }
        // test failed single alert get
        resp = client.get(path: "alert/" + resp2.data.id )
        assertEquals(404, resp.status)
        // test failed single alert delete
        resp = client.delete(path: resp2.data.id )
        assertEquals(404, resp.status)

        // test empty multi-alert delete
        resp = client.put(path: "delete", query: [triggerIds:"test-autodisable-trigger"])
        assertEquals(200, resp.status)
        assertEquals( 0, resp.data.deleted )

        // test success multi-alert delete
        resp = client.put(path: "delete", query: [triggerIds:"test-autoresolve-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )

        resp = client.put(path: "delete", query: [triggerIds:"test-manual-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )

        resp = client.put(path: "delete", query: [triggerIds:"test-manual2-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )

        resp = client.put(path: "delete", query: [triggerIds:"test-autoresolve-threshold-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )

        resp = client.put(path: "delete", query: [triggerIds:"test-autoenable-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )

        resp = client.put(path: "delete", query: [triggerIds:"test-manual-autoresolve-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )

        resp = client.put(path: "delete", query: [triggerIds:"test-hwkalerts234-trigger"])
        assertEquals(200, resp.status)
        assertTrue( resp.data.deleted > 0 )
    }
}
