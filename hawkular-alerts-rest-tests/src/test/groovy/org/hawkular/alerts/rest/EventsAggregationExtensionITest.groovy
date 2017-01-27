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
import org.hawkular.alerts.api.model.condition.ExternalCondition
import org.hawkular.alerts.api.model.event.Event
import org.hawkular.alerts.api.model.export.Definitions
import org.hawkular.alerts.api.model.trigger.FullTrigger
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * Events REST tests.
 *
 * @author Lucas Ponce
 */
class EventsAggregationExtensionITest extends AbstractITestBase {

    public static final String TEST_TENANT = "28026b36-8fe4-4332-84c8-524e173a68bf";
    public static final String MARKETING = "marketing";
    public static final String FRAUD = "fraud"
    public static final String RETENTION = "retention"
    public static final String ALERTER_ID = "EventsAggregation";

    private static final String TAG_NAME = "HawkularExtension";
    private static final String TAG_VALUE = ALERTER_ID;


    String uuid() {
        return UUID.randomUUID().toString()
    }

    void sleep(long milliseconds) {
        System.out.println("Sleeping [" + milliseconds + "] ms");
        Thread.sleep(milliseconds);
    }

    long now() {
        return System.currentTimeMillis();
    }

    @Test
    void marketingScenarioRealTime() {

        Trigger trigger = new Trigger(TEST_TENANT, "marketing-scenario", "Marketing Scenario")
        trigger.addTag(TAG_NAME, TAG_VALUE)
        trigger.setEnabled(true)
        String expression = "event:groupBy(context.accountId):window(time,10s):having(count > 2)"
        ExternalCondition condition = new ExternalCondition(trigger.getId(), Mode.FIRING, MARKETING, ALERTER_ID, expression)

        Definitions definitions = new Definitions(
                Arrays.asList(new FullTrigger(trigger, null, Arrays.asList(condition))),
                new ArrayList<ActionDefinition>()
        )

        def resp = client.post(path: "import/delete", body: definitions)
        assertEquals(200, resp.status)

        // Let some time triggers to update the definitions

        sleep(3000)

        def start = now()

        // t0

        // User1 buys 5 times in < 10 seconds
        Event e1 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E1 - Buy Book")
        e1.addTag(TAG_NAME, TAG_VALUE)
        e1.addContext("duration", "1000")
        e1.addContext("accountId", "user1")

        // User2 buys 3 times > 10 seconds
        Event e6 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E6 - Buy Book")
        e6.addTag(TAG_NAME, TAG_VALUE)
        e6.addContext("duration", "1000")
        e6.addContext("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e1, e6))
        assertEquals(200, resp.status)

        // t0 + 1000
        sleep(1000)

        Event e2 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E2 - Buy Music")
        e2.addTag(TAG_NAME, TAG_VALUE)
        e2.addContext("duration", "2000")
        e2.addContext("accountId", "user1")

        client.post(path: "events/data", body: Arrays.asList(e2))
        assertEquals(200, resp.status)


        // t0 + 2000
        sleep(1000)

        Event e3 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E3 - Buy Groceries")
        e3.addTag(TAG_NAME, TAG_VALUE)
        e3.addContext("duration", "1500")
        e3.addContext("accountId", "user1")

        client.post(path: "events/data", body: Arrays.asList(e3))
        assertEquals(200, resp.status)

        // t0 + 3000
        sleep(1000)

        Event e4 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E4 - Buy VideoGames")
        e4.addTag(TAG_NAME, TAG_VALUE)
        e4.addContext("duration", "3000")
        e4.addContext("accountId", "user1")

        client.post(path: "events/data", body: Arrays.asList(e4))
        assertEquals(200, resp.status)

        // t0 + 4000
        sleep(1000)

        Event e5 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E5 - Buy VideoGames")
        e5.addTag(TAG_NAME, TAG_VALUE)
        e5.addContext("duration", "3000")
        e5.addContext("accountId", "user1")

        client.post(path: "events/data", body: Arrays.asList(e5))
        assertEquals(200, resp.status)

        // t0 + 5000
        sleep(1000)

        Event e7 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E7 - Buy Music")
        e7.addTag(TAG_NAME, TAG_VALUE)
        e7.addContext("duration", "2000")
        e7.addContext("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e7))
        assertEquals(200, resp.status)

        // t0 + 11000
        sleep(6000)

        Event e8 = new Event(TEST_TENANT, uuid(), now(), MARKETING, "TraceCompletion", "E8 - Buy Groceries")
        e8.addTag(TAG_NAME, TAG_VALUE)
        e8.addContext("duration", "1500")
        e8.addContext("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e8))
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);
            resp = client.get(path: "", query: [startTime:start,triggerIds:"marketing-scenario"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size >= 5) {
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
    }

    @Test
    void fraudScenarioDslRealTime() {
        Trigger trigger = new Trigger(TEST_TENANT, "fraud-scenario", "Fraud Scenario");
        trigger.addTag(TAG_NAME, TAG_VALUE)
        trigger.setEnabled(true)
        String expression = "event:groupBy(tags.accountId):window(time,10s):having(count > 1, count.tags.location > 1)"
        ExternalCondition condition = new ExternalCondition(trigger.getId(), Mode.FIRING, FRAUD, ALERTER_ID, expression)

        Definitions definitions = new Definitions(
                Arrays.asList(new FullTrigger(trigger, null, Arrays.asList(condition))),
                new ArrayList<ActionDefinition>()
        )

        def resp = client.post(path: "import/delete", body: definitions)
        assertEquals(200, resp.status)

        // Let some time triggers to update the definitions

        sleep(3000)

        def start = now()

        // Init t0

        // User1 buys 5 times in < 10 seconds from different locations
        Event e1 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Book")
        e1.addTag(TAG_NAME, TAG_VALUE)
        e1.addTag("duration", "1000")
        e1.addTag("accountId", "user1")
        e1.addTag("location", "ip1")

        // User2 buys 3 times > 10 seconds from single location
        Event e6 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Book")
        e6.addTag(TAG_NAME, TAG_VALUE)
        e6.addTag("duration", "1000")
        e6.addTag("accountId", "user2")
        e6.addTag("location", "ip3")

        // User3 buys 5 times in < 10 seconds from single location
        Event e11 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Book")
        e11.addTag(TAG_NAME, TAG_VALUE)
        e11.addTag("duration", "1000")
        e11.addTag("accountId", "user3")
        e11.addTag("location", "ip10")

        client.post(path: "events/data", body: Arrays.asList(e1, e6, e11))
        assertEquals(200, resp.status)

        // t0 + 1000
        sleep(1000)

        Event e2 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Music")
        e2.addTag(TAG_NAME, TAG_VALUE)
        e2.addTag("duration", "2000")
        e2.addTag("accountId", "user1")
        e2.addTag("location", "ip1")

        Event e12 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Music")
        e12.addTag(TAG_NAME, TAG_VALUE)
        e12.addTag("duration", "2000")
        e12.addTag("accountId", "user3")
        e12.addTag("location", "ip10")

        client.post(path: "events/data", body: Arrays.asList(e2, e12))
        assertEquals(200, resp.status)

        // t0 + 2000
        sleep(1000)

        Event e3 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Groceries")
        e3.addTag(TAG_NAME, TAG_VALUE)
        e3.addTag("duration", "1500")
        e3.addTag("accountId", "user1")
        e3.addTag("location", "ip1")

        Event e13 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Groceries")
        e13.addTag(TAG_NAME, TAG_VALUE)
        e13.addTag("duration", "1500")
        e13.addTag("accountId", "user3")
        e13.addTag("location", "ip10")

        client.post(path: "events/data", body: Arrays.asList(e3, e13))
        assertEquals(200, resp.status)

        // t0 + 3000
        sleep(1000)

        Event e4 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy VideoGames")
        e4.addTag(TAG_NAME, TAG_VALUE)
        e4.addTag("duration", "3000")
        e4.addTag("accountId", "user1")
        e4.addTag("location", "ip2")

        Event e14 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy VideoGames")
        e14.addTag(TAG_NAME, TAG_VALUE)
        e14.addTag("duration", "3000")
        e14.addTag("accountId", "user3")
        e14.addTag("location", "ip10")

        client.post(path: "events/data", body: Arrays.asList(e4, e14))
        assertEquals(200, resp.status)

        // t0 + 4000
        sleep(1000)

        Event e5 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy VideoGames")
        e5.addTag(TAG_NAME, TAG_VALUE)
        e5.addTag("duration", "3000")
        e5.addTag("accountId", "user1")
        e5.addTag("location", "ip1")

        Event e15 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy VideoGames")
        e15.addTag(TAG_NAME, TAG_VALUE)
        e15.addTag("duration", "3000")
        e15.addTag("accountId", "user3")
        e15.addTag("location", "ip10")

        client.post(path: "events/data", body: Arrays.asList(e5, e15))
        assertEquals(200, resp.status)

        // t0 + 15000
        sleep(11000)

        Event e7 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Music")
        e7.addTag(TAG_NAME, TAG_VALUE)
        e7.addTag("duration", "2000")
        e7.addTag("accountId", "user2")
        e7.addTag("location", "ip4")

        client.post(path: "events/data", body: Arrays.asList(e7))
        assertEquals(200, resp.status)

        // t0 + 20000
        sleep(5000)

        Event e8 = new Event(TEST_TENANT, uuid(), now(), FRAUD, "TraceCompletion", "Buy Groceries")
        e8.addTag(TAG_NAME, TAG_VALUE)
        e8.addTag("duration", "1500")
        e8.addTag("accountId", "user2")
        e8.addTag("location", "ip5")

        client.post(path: "events/data", body: Arrays.asList(e8))
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);
            resp = client.get(path: "", query: [startTime:start,triggerIds:"fraud-scenario"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size >= 6) {
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(6, resp.data.size())
    }

    @Test
    void customerRetentionScenarioDslRealTime() {
        Trigger trigger = new Trigger(TEST_TENANT, "customer-retention-scenario", "Customer Retention Scenario");
        trigger.addTag(TAG_NAME, TAG_VALUE)
        trigger.setEnabled(true)
        String expression = "event:groupBy(tags.traceId):" +
                "filter((category == \"Credit Check\" && text == \"Exceptionally Good\") || " +
                "(category == \"Stock Check\" && text == \"Out of Stock\")):" +
                "having(count > 1, count.tags.accountId == 1)"
        ExternalCondition condition = new ExternalCondition(trigger.getId(), Mode.FIRING, RETENTION, ALERTER_ID, expression)

        Definitions definitions = new Definitions(
                Arrays.asList(new FullTrigger(trigger, null, Arrays.asList(condition))),
                new ArrayList<ActionDefinition>()
        )

        def resp = client.post(path: "import/delete", body: definitions)
        assertEquals(200, resp.status)

        // Let some time triggers to update the definitions

        sleep(3000)

        def start = now()

        // Init t0
        Event e1 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Credit Check", "Exceptionally Good")
        e1.addTag(TAG_NAME, TAG_VALUE)
        e1.addTag("duration", "1000")
        e1.addTag("traceId", "trace1")
        e1.addTag("accountId", "user1")

        Event e11 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Credit Check", "Exceptionally Good")
        e11.addTag(TAG_NAME, TAG_VALUE)
        e11.addTag("duration", "1000")
        e11.addTag("traceId", "trace4")
        e11.addTag("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e1, e11))
        assertEquals(200, resp.status)

        // t0 + 1000
        sleep(1000)

        Event e2 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Stock Check", "Out of Stock")
        e2.addTag(TAG_NAME, TAG_VALUE)
        e2.addTag("duration", "2000")
        e2.addTag("traceId", "trace1")
        e2.addTag("accountId", "user1")

        Event e12 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Stock Check", "Out of Stock")
        e12.addTag(TAG_NAME, TAG_VALUE)
        e12.addTag("duration", "2000")
        e12.addTag("traceId", "trace4")
        e12.addTag("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e2, e12))
        assertEquals(200, resp.status)

        // t0 + 2000
        sleep(1000)

        Event e3 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Credit Check", "Good")
        e3.addTag(TAG_NAME, TAG_VALUE)
        e3.addTag("duration", "1500")
        e3.addTag("traceId", "trace2")
        e3.addTag("accountId", "user1")

        Event e13 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Credit Check", "Good")
        e13.addTag(TAG_NAME, TAG_VALUE)
        e13.addTag("duration", "1500")
        e13.addTag("traceId", "trace5")
        e13.addTag("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e3, e13))
        assertEquals(200, resp.status)

        // t0 + 3000
        sleep(1000)

        Event e4 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Stock Check", "Out of Stock")
        e4.addTag(TAG_NAME, TAG_VALUE)
        e4.addTag("duration", "2000")
        e4.addTag("traceId", "trace2")
        e4.addTag("accountId", "user1")

        Event e14 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Stock Check", "Out of Stock")
        e14.addTag(TAG_NAME, TAG_VALUE)
        e14.addTag("duration", "2000")
        e14.addTag("traceId", "trace5")
        e14.addTag("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e4, e14))
        assertEquals(200, resp.status)

        // t0 + 4000
        sleep(1000)

        Event e5 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Credit Check", "Exceptionally Good")
        e5.addTag(TAG_NAME, TAG_VALUE)
        e5.addTag("duration", "1500")
        e5.addTag("traceId", "trace3")
        e5.addTag("accountId", "user1")

        Event e15 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Credit Check", "Exceptionally Good")
        e15.addTag(TAG_NAME, TAG_VALUE)
        e15.addTag("duration", "1500")
        e15.addTag("traceId", "trace6")
        e15.addTag("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e5, e15))
        assertEquals(200, resp.status)

        // t0 + 5000
        sleep(1000)

        Event e6 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Stock Check", "Available")
        e6.addTag(TAG_NAME, TAG_VALUE)
        e6.addTag("duration", "2000")
        e6.addTag("traceId", "trace3")
        e6.addTag("accountId", "user1")

        Event e16 = new Event(TEST_TENANT, uuid(), now(), RETENTION, "Stock Check", "Available")
        e16.addTag(TAG_NAME, TAG_VALUE)
        e16.addTag("duration", "2000")
        e16.addTag("traceId", "trace6")
        e16.addTag("accountId", "user2")

        client.post(path: "events/data", body: Arrays.asList(e6, e16))
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);
            resp = client.get(path: "", query: [startTime:start,triggerIds:"customer-retention-scenario"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size >= 2) {
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())
    }

}
