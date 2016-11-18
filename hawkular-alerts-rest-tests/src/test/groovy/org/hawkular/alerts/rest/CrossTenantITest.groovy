/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.junit.Assert.assertEquals

/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class CrossTenantITest extends AbstractITestBase {

    static Logger logger = LoggerFactory.getLogger(CrossTenantITest.class)


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
            testTrigger.setEnabled(true);

            resp = client.put(path: "triggers/test-multiple-tenants", body: testTrigger)
            assertEquals(200, resp.status)

            // SEND Data to generate Alerts
            for (int i=0; i<numAlerts; i++) {
                Data avail = new Data("test-multiple-tenants", System.currentTimeMillis() + (i*1000), "DOWN");
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
        generateAlertsForMultipleTenants(tenantIds, 5);

        client.headers.put("Hawkular-Tenant", "tenant1,tenant2,tenant3,tenant4")
        def resp
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "admin/events", query: [startTime:start,triggerIds:"test-multiple-tenants"] )
            if ( resp.status == 200 && resp.data.size() == 20 ) {
                break;
            }
        }

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "admin/alerts", query: [startTime:start,triggerIds:"test-multiple-tenants"] )
            if ( resp.status == 200 && resp.data.size() == 20 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(20, resp.data.size())
    }

}
