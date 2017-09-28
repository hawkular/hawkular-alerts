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

import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.EventCondition
import org.hawkular.alerts.api.model.condition.MissingCondition
import org.hawkular.alerts.api.model.event.EventCategory
import org.hawkular.alerts.api.model.event.EventType
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.trigger.TriggerType
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.FixMethodOrder
import org.junit.Test


import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.runners.MethodSorters.NAME_ASCENDING

/**
 * Events lifecycle end-to-end tests.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@FixMethodOrder(NAME_ASCENDING)
class EventsLifecycleITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(EventsLifecycleITest.class)

    static host = System.getProperty('hawkular.host') ?: '127.0.0.1'
    static port = Integer.valueOf(System.getProperty('hawkular.port') ?: "8080")

    @Test
    void t01_eventsBasicTest() {
        logger.info( "Running t01_eventsBasicTest" )

        String start = String.valueOf(System.currentTimeMillis())

        // Clean previous tests
        def resp = client.delete(path: "triggers/test-events-t01")
        assert(200 == resp.status || 404 == resp.status)

        // Trigger to fire alerts
        Trigger t01 = new Trigger("test-events-t01", "Basic trigger for Events tests");

        resp = client.post(path: "triggers", body: t01)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond = new EventCondition("test-events-t01", Mode.FIRING, "test-app.war", null);
        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );

        resp = client.put(path: "triggers/test-events-t01/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query:[triggerIds:"test-events-t01",enabled:true] )
        assertEquals(200, resp.status)

        String jsonEvent = "{" +
                "\"id\":\"" + UUID.randomUUID().toString() + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"" + EventCategory.DEPLOYMENT.toString() + "\"," +
                "\"dataId\":\"test-app.war\"" +
                "}";

        resp = client.post(path: "events", body: jsonEvent);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 100; ++i ) {
            Thread.sleep(100);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-events-t01"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size > 0) {
                if ( i > 50 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

    }

    @Test
    void t02_eventsChainedTest() {
        logger.info( "Running t02_eventsChainedTest" )

        String start = String.valueOf(System.currentTimeMillis())

        // Clean previous tests
        def resp = client.delete(path: "triggers/test-events-t02-app1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t02-app2")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t02-combined")
        assert(200 == resp.status || 404 == resp.status)

        // Triggers to fire events
        Trigger t02app1 = new Trigger("test-events-t02-app1", "Check if app1 is down");
        t02app1.setEventType(EventType.EVENT);

        resp = client.post(path: "triggers", body: t02app1)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond1 = new EventCondition("test-events-t02-app1", Mode.FIRING, "app1.war",
            "text == 'DOWN'");
        Collection<Condition> conditions1 = new ArrayList<>(1);
        conditions1.add( firingCond1 );

        resp = client.put(path: "triggers/test-events-t02-app1/conditions/firing", body: conditions1)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t02-app1",enabled:true])
        assertEquals(200, resp.status)

        // Triggers to fire events
        Trigger t02app2 = new Trigger("test-events-t02-app2", "Check if app2 is down");
        t02app2.setEventType(EventType.EVENT);

        resp = client.post(path: "triggers", body: t02app2)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond2 = new EventCondition("test-events-t02-app2", Mode.FIRING, "app2.war",
            "text == 'DOWN'");
        Collection<Condition> conditions2 = new ArrayList<>(1);
        conditions2.add( firingCond2 );

        resp = client.put(path: "triggers/test-events-t02-app2/conditions/firing", body: conditions2)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t02-app2",enabled:true])
        assertEquals(200, resp.status)

        // Trigger to fire alerts
        Trigger t02combined = new Trigger("test-events-t02-combined", "App1 and App2 are down");

        resp = client.post(path: "triggers", body: t02combined)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond3 = new EventCondition("test-events-t02-combined", Mode.FIRING,
            "test-events-t02-app1", null);
        EventCondition firingCond4 = new EventCondition("test-events-t02-combined", Mode.FIRING,
            "test-events-t02-app2", null);

        Collection<Condition> conditions3 = new ArrayList<>(2);
        conditions3.add( firingCond3 );
        conditions3.add( firingCond4 );

        resp = client.put(path: "triggers/test-events-t02-combined/conditions/firing", body: conditions3)
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t02-combined",enabled:true])
        assertEquals(200, resp.status)

        String jsonEventApp1Down = "{" +
                "\"id\":\"" + UUID.randomUUID().toString() + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"" + EventCategory.DEPLOYMENT.toString() + "\"," +
                "\"dataId\":\"app1.war\"," +
                "\"text\":\"DOWN\"" +
                "}";

        resp = client.post(path: "events", body: jsonEventApp1Down);
        assertEquals(200, resp.status)

        String jsonEventApp2Down = "{" +
                "\"id\":\"" + UUID.randomUUID().toString() + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"" + EventCategory.DEPLOYMENT.toString() + "\"," +
                "\"dataId\":\"app2.war\"," +
                "\"text\":\"DOWN\"" +
                "}";

        resp = client.post(path: "events", body: jsonEventApp2Down);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 100; ++i ) {
            Thread.sleep(100);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-events-t02-combined"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size > 0) {
                if ( i > 50 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
    }

    @Test
    void t03_eventsChainedWithoutPersistingInputEventsTest() {
        logger.info( "Running t03_eventsChainedWithoutPersistingInputEventsTest" )

        String start = String.valueOf(System.currentTimeMillis())

        // Clean previous tests
        def resp = client.delete(path: "triggers/test-events-t03-app1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t03-app2")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t03-combined")
        assert(200 == resp.status || 404 == resp.status)

        // Triggers to fire events
        Trigger t03app1 = new Trigger("test-events-t03-app1", "Check if app1 is down");
        t03app1.setEventType(EventType.EVENT);

        resp = client.post(path: "triggers", body: t03app1)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond1 = new EventCondition("test-events-t03-app1", Mode.FIRING, "app1-03.war",
                "text == 'DOWN'");
        Collection<Condition> conditions1 = new ArrayList<>(1);
        conditions1.add( firingCond1 );

        resp = client.put(path: "triggers/test-events-t03-app1/conditions/firing", body: conditions1)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t03-app1",enabled:true])
        assertEquals(200, resp.status)

        // Triggers to fire events
        Trigger t03app2 = new Trigger("test-events-t03-app2", "Check if app2 is down");
        t03app2.setEventType(EventType.EVENT);

        resp = client.post(path: "triggers", body: t03app2)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond2 = new EventCondition("test-events-t03-app2", Mode.FIRING, "app2-03.war",
                "text == 'DOWN'");
        Collection<Condition> conditions2 = new ArrayList<>(1);
        conditions2.add( firingCond2 );

        resp = client.put(path: "triggers/test-events-t03-app2/conditions/firing", body: conditions2)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t03-app2",enabled:true])
        assertEquals(200, resp.status)

        // Trigger to fire alerts
        Trigger t03combined = new Trigger("test-events-t03-combined", "App1 and App2 are down");

        resp = client.post(path: "triggers", body: t03combined)
        assertEquals(200, resp.status)

        // Add a condition over events
        EventCondition firingCond3 = new EventCondition("test-events-t03-combined", Mode.FIRING,
                "test-events-t03-app1", null);
        EventCondition firingCond4 = new EventCondition("test-events-t03-combined", Mode.FIRING,
                "test-events-t03-app2", null);

        Collection<Condition> conditions3 = new ArrayList<>(2);
        conditions3.add( firingCond3 );
        conditions3.add( firingCond4 );

        resp = client.put(path: "triggers/test-events-t03-combined/conditions/firing", body: conditions3)
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t03-combined",enabled:true])
        assertEquals(200, resp.status)

        String jsonEventApp1DownId = UUID.randomUUID().toString();
        String jsonEventApp1Down = "[{" +
                "\"id\":\"" + jsonEventApp1DownId + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"" + EventCategory.DEPLOYMENT.toString() + "\"," +
                "\"dataId\":\"app1-03.war\"," +
                "\"text\":\"DOWN\"" +
                "}]";

        resp = client.post(path: "events/data", body: jsonEventApp1Down);
        assertEquals(200, resp.status)

        resp = client.get(path: "events/event/" + jsonEventApp1DownId);
        assertEquals(404, resp.status)

        String jsonEventApp2DownId = UUID.randomUUID().toString();
        String jsonEventApp2Down = "[{" +
                "\"id\":\"" + jsonEventApp2DownId + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"" + EventCategory.DEPLOYMENT.toString() + "\"," +
                "\"dataId\":\"app2-03.war\"," +
                "\"text\":\"DOWN\"" +
                "}]";

        resp = client.post(path: "events/data", body: jsonEventApp2Down);
        assertEquals(200, resp.status)

        resp = client.get(path: "events/event/" + jsonEventApp2DownId);
        assertEquals(404, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 100; ++i ) {
            Thread.sleep(100);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-events-t03-combined"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size > 0) {
                if ( i > 50 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
    }

    @Test
    void t04_missingEventsTest() {
        logger.info( "Running t04_missingEventsTest" )

        String start = String.valueOf(System.currentTimeMillis())

        // Clean previous tests
        def resp = client.delete(path: "triggers/test-events-t04")
        assert(200 == resp.status || 404 == resp.status)

        // Triggers to fire events
        Trigger t04 = new Trigger("test-events-t04", "Check if we receive events on 5 secs");
        t04.setEventType(EventType.EVENT);

        resp = client.post(path: "triggers", body: t04)
        assertEquals(200, resp.status)

        MissingCondition firingCond = new MissingCondition("test-events-t04", Mode.FIRING,
            "event-data-id-t04", 5000L);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );

        resp = client.put(path: "triggers/test-events-t04/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t04",enabled:true])
        assertEquals(200, resp.status)

        // Wait for 7 seconds to fire the missing events
        Thread.sleep(7000);

        resp = client.get(path: "events", query: [startTime:start,triggerIds:"test-events-t04"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Clean previous tests
        resp = client.delete(path: "triggers/test-events-t04")
        assert(200 == resp.status || 404 == resp.status)
    }

    @Test
    void t05_missingEventsWithDataTest() {
        logger.info( "Running t05_missingEventsWithDataTest" )

        String start = String.valueOf(System.currentTimeMillis())

        // Clean previous tests
        def resp = client.delete(path: "triggers/test-events-t05")
        assert(200 == resp.status || 404 == resp.status)

        // Triggers to fire events
        Trigger t05 = new Trigger("test-events-t05", "Check if we receive events on 5 secs");
        t05.setEventType(EventType.EVENT);

        resp = client.post(path: "triggers", body: t05)
        assertEquals(200, resp.status)

        MissingCondition firingCond = new MissingCondition("test-events-t05", Mode.FIRING, "event-data-id-t05", 5000L);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );

        resp = client.put(path: "triggers/test-events-t05/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Enable trigger
        resp = client.put(path: "triggers/enabled", query: [triggerIds:"test-events-t05",enabled:true])
        assertEquals(200, resp.status)

        Thread.sleep(2000);

        String jsonEventTest05Id = UUID.randomUUID().toString();
        String jsonEventTest05 = "[{" +
                "\"id\":\"" + jsonEventTest05Id + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"Test05\"," +
                "\"dataId\":\"event-data-id-t05\"," +
                "\"text\":\"Test05 text\"" +
                "}]";

        resp = client.post(path: "events/data", body: jsonEventTest05);
        assertEquals(200, resp.status)

        Thread.sleep(2000);

        jsonEventTest05Id = UUID.randomUUID().toString();
        jsonEventTest05 = "[{" +
                "\"id\":\"" + jsonEventTest05Id + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"Test05\"," +
                "\"dataId\":\"event-data-id-t05\"," +
                "\"text\":\"Test05 text\"" +
                "}]";

        resp = client.post(path: "events/data", body: jsonEventTest05);
        assertEquals(200, resp.status)

        Thread.sleep(2000);

        jsonEventTest05Id = UUID.randomUUID().toString();
        jsonEventTest05 = "[{" +
                "\"id\":\"" + jsonEventTest05Id + "\"," +
                "\"ctime\":" + System.currentTimeMillis() + "," +
                "\"category\":\"Test05\"," +
                "\"dataId\":\"event-data-id-t05\"," +
                "\"text\":\"Test05 text\"" +
                "}]";

        resp = client.post(path: "events/data", body: jsonEventTest05);
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [startTime:start,triggerIds:"test-events-t05"] )
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        // Clean previous tests
        resp = client.delete(path: "triggers/test-events-t05")
        assert(200 == resp.status || 404 == resp.status)
    }

    @Test
    void t100_eventsCleanup() {
        logger.info("Running t100_eventsCleanup")

        // clean up triggers
        def resp = client.delete(path: "triggers/test-events-t01")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t02-app1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t02-app2")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t02-combined")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t03-app1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t03-app2")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t03-combined")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t04")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.delete(path: "triggers/test-events-t05")
        assert(200 == resp.status || 404 == resp.status)

        // clean up alerts
        resp = client.put(path: "delete", query: [triggerIds:"test-events-t01"])
        assertEquals(200, resp.status)

        resp = client.put(path: "delete", query: [triggerIds:"test-events-t02-combined"])
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete", query: [triggerIds:"test-events-t04"])
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete", query: [triggerIds:"test-events-t05"])
        assertEquals(200, resp.status)

        // clean up events
        resp = client.put(path: "events/delete", query: [categories:"DEPLOYMENT"] )
        assertEquals(200, resp.status)
    }

}
