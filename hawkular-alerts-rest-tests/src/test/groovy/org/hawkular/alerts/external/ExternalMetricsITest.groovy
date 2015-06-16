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
package org.hawkular.alerts.external

import java.util.List

import org.hawkular.alerts.api.model.data.StringData
import org.hawkular.alerts.api.model.condition.ExternalCondition
import org.hawkular.alerts.api.model.trigger.Tag
import org.hawkular.alerts.api.model.trigger.Trigger

import static org.hawkular.alerts.api.model.trigger.Trigger.Mode
import static org.junit.Assert.assertEquals

import org.junit.FixMethodOrder
import org.junit.Test

import static org.junit.runners.MethodSorters.NAME_ASCENDING

/**
 * Tests External Metrics Alerting.  Requires a running server running Alerts+Metrics (like Hawkular).
 *
 * @author Jay Shaughnessy
 */
@FixMethodOrder(NAME_ASCENDING)
class ExternalMetricsITest extends AbstractExternalITestBase {

    static host = System.getProperty('hawkular.host') ?: '127.0.0.1'
    static port = Integer.valueOf(System.getProperty('hawkular.port') ?: "8080")
    static t01Start = String.valueOf(System.currentTimeMillis())
    static t02Start;

    @Test
    void t01_AverageTest() {
        String start = t01Start;

        // CREATE trigger using external metrics expression
        def resp = client.get(path: "")
        assert resp.status == 200 || resp.status == 204 : resp.status

        Trigger triggerTestAvg = new Trigger("trigger-test-avg", "trigger-test-avg");

        // remove if it exists
        resp = client.delete(path: "triggers/trigger-test-avg")
        assert(200 == resp.status || 404 == resp.status)

        triggerTestAvg.setAutoDisable(true);

        resp = client.post(path: "triggers", body: triggerTestAvg)
        assertEquals(200, resp.status)

        // ADD external metrics avg condition. Note: systemId must be "HawkularMetrics"
        // Average over the last 1 minute > 50, check every minute
        ExternalCondition firingCond = new ExternalCondition("trigger-test-avg", Mode.FIRING, "external-data-test-avg",
            "HawkularMetrics", "metric:1:avg(data-test-avg > 50),1");

        resp = client.post(path: "triggers/trigger-test-avg/conditions", body: firingCond)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // Tag the trigger as a HawkularMetrics:MetricsCondition so it gets picked up for processing
        Tag tag = new Tag( "trigger-test-avg", "HawkularMetrics", "MetricsCondition" );
        resp = client.post(path: "triggers/tags/", body: tag)
        assertEquals(200, resp.status)

        // FETCH trigger to validate and get the tenantId
        resp = client.get(path: "triggers/trigger-test-avg");
        assertEquals(200, resp.status)
        assertEquals("trigger-test-avg", resp.data.name)
        assertEquals(false, resp.data.enabled)
        assertEquals(true, resp.data.autoDisable);
        def tenantId = resp.data.tenantId;
        assert( null != tenantId )

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"trigger-test-avg"] )
        assertEquals(204, resp.status)

        // Send in METRICS data before enabling the trigger because the external evaluations start as soon
        // as the enabled Trigger is processed.
        long now = System.currentTimeMillis();
        DataPoint dp1 = new DataPoint( "data-test-avg", 50.0, now - 30000 );  // 30 seconds ago
        DataPoint dp2 = new DataPoint( "data-test-avg", 60.0, now - 25000 );  // 25 seconds ago
        DataPoint dp3 = new DataPoint( "data-test-avg", 73.0, now - 20000 );  // 20 seconds ago
        sendMetricDataViaRest( tenantId, dp1, dp2, dp3 );

        // ENABLE Trigger, this should get picked up by the listener and the expression should be submitted
        // to a runner for processing...
        triggerTestAvg.setEnabled(true);

        resp = client.put(path: "triggers/trigger-test-avg/", body: triggerTestAvg)
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            println "SLEEP!" ;
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 1
            resp = client.get(path: "", query: [startTime:start,triggerIds:"trigger-test-avg"] )
            if ( resp.status == 200 ) {
                break;
            }
            assert resp.status == 204 : resp.status
        }
        assertEquals(200, resp.status)

        String alertId = resp.data[0].alertId;
        println resp.data[0].toString();
    }

    private void sendMetricDataViaRest(String tenantId, DataPoint... dataPoints) {

        List<Map<String, Object>> mMetrics = new ArrayList<>();

        for( DataPoint dp : dataPoints ) {
            addDataItem(mMetrics, dp.metricId, dp.time, dp.value);
        }

        // Send it to metrics via rest
        println mMetrics
        def resp = metricsClient.post(path:"gauges/data", body:mMetrics, headers:['Hawkular-Tenant':tenantId]);
        assertEquals(200, resp.status)
    }

    private static void addDataItem(List<Map<String, Object>> mMetrics, String metricId, Long timestamp, Number value) {
        Map<String, Number> dataMap = new HashMap<>(2);
        dataMap.put("timestamp", timestamp);
        dataMap.put("value", value);
        List<Map<String, Number>> data = new ArrayList<>(1);
        data.add(dataMap);
        Map<String, Object> outer = new HashMap<>(2);
        outer.put("id", metricId);
        outer.put("data", data);
        mMetrics.add(outer);
    }

    private static class DataPoint {
        String metricId;
        Double value;
        long time;

        public DataPoint( String metricId, Double value, long time ) {
            this.metricId = metricId;
            this.value = value;
            this.time = time;
        }
    }
}
