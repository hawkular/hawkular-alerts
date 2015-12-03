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
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Cluster REST tests.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class ClusterITest extends AbstractITestBase {

    @Test
    void clusterAvailabilityTest() {
        String start = String.valueOf(System.currentTimeMillis());

        def resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        int numTriggers = 20;

        /*
            Create several triggers that should be partitioned in a transparent way, so this test should work on
            any cluster size or even in standalone scenarios.
         */
        for (int i = 0; i < numTriggers; i++) {

            Trigger testTrigger = new Trigger("test-cluster-" + i , "http://www.mydemourl.com");


            resp = client.put(path: "delete", query: [triggerIds:"test-cluster-" + i])
            assert resp.status == 200 : resp.status

            // remove if it exists
            resp = client.delete(path: "triggers/test-cluster-" + i)
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
            AvailabilityCondition firingCond = new AvailabilityCondition("test-cluster-" + i,
                    Mode.FIRING, "test-cluster-" + i, AvailabilityCondition.Operator.NOT_UP);

            Collection<Condition> conditions = new ArrayList<>(1);
            conditions.add( firingCond );
            resp = client.put(path: "triggers/test-cluster-" + i + "/conditions/firing", body: conditions)
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size())

            // ENABLE Trigger
            testTrigger.setEnabled(true);

            resp = client.put(path: "triggers/test-cluster-" + i, body: testTrigger)
            assertEquals(200, resp.status)

            // FETCH trigger and make sure it's as expected
            resp = client.get(path: "triggers/test-cluster-" + i);
            assertEquals(200, resp.status)
            assertEquals("http://www.mydemourl.com", resp.data.name)
            assertEquals(true, resp.data.enabled)
            assertEquals(false, resp.data.autoDisable);
            assertEquals(false, resp.data.autoResolve);
            assertEquals(false, resp.data.autoResolveAlerts);

            // FETCH recent alerts for trigger, should not be any
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-cluster-" + i] )
            assertEquals(200, resp.status)
        }


        for (int i = 0; i < numTriggers; i++) {

            // Send in DOWN avail data to fire the trigger
            // Instead of going through the bus, in this test we'll use the alerts rest API directly to send data
            for (int j = 0; j < 5; j++) {
                Data avail = new Data("test-cluster-" + i, j, "DOWN");
                Collection<Data> datums = new ArrayList<>();
                datums.add(avail);
                resp = client.post(path: "data", body: datums);
                assertEquals(200, resp.status)
            }
        }

        for (int i = 0; i < numTriggers; i++) {

            // The alert processing happens async, so give it a little time before failing...
            for (int j = 0; j < 100; ++j ) {
                // println "SLEEP!" ;
                Thread.sleep(100);

                // FETCH recent alerts for trigger, there should be 5
                resp = client.get(path: "", query: [startTime:start,triggerIds:"test-cluster-" + i] )
                if ( resp.status == 200 && resp.data.size() == 5 ) {
                    break;
                }
            }
            assertEquals(200, resp.status)
            assertEquals("test-cluster-" + i, 5, resp.data.size())
            assertEquals("OPEN", resp.data[0].status)
        }

    }
}
