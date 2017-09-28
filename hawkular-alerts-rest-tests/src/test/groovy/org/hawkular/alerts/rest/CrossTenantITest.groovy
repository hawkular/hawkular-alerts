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

import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test


import static org.junit.Assert.assertEquals

/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class CrossTenantITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(CrossTenantITest.class)


    void generateAlertsForMultipleTenants(List<String> tenantIds, int numAlerts) {
        for (String tenantId : tenantIds) {
            client.headers.put("Hawkular-Tenant", tenantId)
            Trigger testTrigger = new Trigger("test-multiple-tenants", "CrossTenantITest");
            def resp = client.delete(path: "triggers/test-multiple-tenants");
            assert(200 == resp.status || 404 == resp.status)
            resp = client.post(path: "triggers", body: testTrigger)
            assertEquals(200, resp.status)

            // ADD Firing condition
            AvailabilityCondition firingCond = new AvailabilityCondition("test-multiple-tenants",
                    Mode.FIRING, "test-multiple-tenants", AvailabilityCondition.Operator.NOT_UP);

            Collection<Condition> conditions = new ArrayList<>(1);
            conditions.add( firingCond );
            resp = client.put(path: "triggers/test-multiple-tenants/conditions/firing", body: conditions)
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size())

            // ENABLE Trigger
            resp = client.put(path: "triggers/enabled", query:[triggerIds:"test-multiple-tenants",enabled:true] )
            assertEquals(200, resp.status)

            if (cluster) {
                // In cluster scenarios we should give some time to process the trigger before sending data
                Thread.sleep(500);
            }

            // SEND Data to generate Alerts
            for (int i=0; i<numAlerts; i++) {
                Data avail = new Data("test-multiple-tenants", System.currentTimeMillis() + (i*2000), "DOWN");
                Collection<Data> datums = new ArrayList<>();
                datums.add(avail);
                resp = client.post(path: "data", body: datums);
                assertEquals(200, resp.status)
            }
        }
    }

    @Test
    void fetchEventsAndAlertsFromMultipleTenants() {
        def start = System.currentTimeMillis();

        List<String> tenantIds = ["tenant1", "tenant2", "tenant3", "tenant4"];

        def watcherRun = true
        def alertsWatched = 0, eventsWatched = 0, tenant1AlertsWatched = 0, tenant1EventsWatched = 0

        Thread.start {
            URL watcherUrl = new URL(baseURI + "admin/watch/alerts")
            HttpURLConnection conn = watcherUrl.openConnection()
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Hawkular-Tenant", "tenant1,tenant2,tenant3,tenant4")
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
            def notification
            while (watcherRun && ( (notification = reader.readLine()) != null )) {
                logger.info(notification)
                alertsWatched++
            }
            reader.close()
        }

        Thread.start {
            URL watcherUrl = new URL(baseURI + "admin/watch/events")
            HttpURLConnection conn = watcherUrl.openConnection()
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Hawkular-Tenant", "tenant1,tenant2,tenant3,tenant4")
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
            def notification
            while (watcherRun && ( (notification = reader.readLine()) != null )) {
                logger.info(notification)
                eventsWatched++
            }
            reader.close()
        }

        Thread.start {
            URL watcherUrl = new URL(baseURI + "watch")
            HttpURLConnection conn = watcherUrl.openConnection()
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Hawkular-Tenant", "tenant1")
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
            def notification
            while (watcherRun && ( (notification = reader.readLine()) != null )) {
                logger.info(notification)
                tenant1AlertsWatched++
            }
            reader.close()
        }

        Thread.start {
            URL watcherUrl = new URL(baseURI + "events/watch")
            HttpURLConnection conn = watcherUrl.openConnection()
            conn.setRequestMethod("GET")
            conn.setRequestProperty("Hawkular-Tenant", "tenant1")
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
            def notification
            while (watcherRun && ( (notification = reader.readLine()) != null )) {
                logger.info(notification)
                tenant1EventsWatched++
            }
            reader.close()
        }

        generateAlertsForMultipleTenants(tenantIds, 5);

        client.headers.put("Hawkular-Tenant", "tenant1,tenant2,tenant3,tenant4")
        def resp
        for ( int i=0; i < 30; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "admin/events", query: [startTime:start,triggerIds:"test-multiple-tenants"] )
            if ( resp.status == 200 && resp.data.size() == 20 ) {
                break;
            }
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 30; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "admin/alerts", query: [startTime:start,triggerIds:"test-multiple-tenants"] )
            if ( resp.status == 200 && resp.data.size() == 20 ) {
                break;
            }
        }
        // Give watchers some time
        Thread.sleep(6000)
        watcherRun = false
        if (cluster) {
            logger.info("Alerts generated: ")
            for (int i = 0; i < resp.data.size(); i++) {
                logger.info(resp.data[i].toString())
            }
        }
        assertEquals(200, resp.status)
        assertEquals(20, resp.data.size())
        assertEquals(20, alertsWatched)
        assertEquals(20, eventsWatched)
        assertEquals(5, tenant1AlertsWatched)
        assertEquals(5, tenant1EventsWatched)
    }

}
