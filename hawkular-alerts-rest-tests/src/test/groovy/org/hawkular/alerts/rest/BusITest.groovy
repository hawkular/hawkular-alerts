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
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.junit.Test

import javax.jms.ConnectionFactory
import javax.jms.JMSContext
import javax.jms.JMSProducer
import javax.jms.Topic
import javax.naming.Context
import javax.naming.InitialContext

import static org.junit.Assert.assertEquals

/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class BusITest extends AbstractITestBase {

    @Test
    void availabilityThroughBusTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        Trigger testTrigger = new Trigger("test-bus-email-availability", "http://www.mydemourl.com");

        // remove if it exists
        resp = client.delete(path: "triggers/test-bus-email-availability")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setAutoResolveAlerts(false);
        /*
            email-to-admin action is pre-created from demo data
         */
        testTrigger.addAction("email", "email-to-admin");
        testTrigger.addAction("file", "file-to-admin");

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-bus-email-availability",
                Mode.FIRING, "test-bus-email-availability", AvailabilityCondition.Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-bus-email-availability/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-bus-email-availability", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-bus-email-availability");
        assertEquals(200, resp.status)
        assertEquals("http://www.mydemourl.com", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-bus-email-availability"] )
        assertEquals(200, resp.status)

        // Send in DOWN avail data to fire the trigger

        Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory")
        env.put(Context.PROVIDER_URL, "http-remoting://127.0.0.1:8080")
        env.put(Context.SECURITY_PRINCIPAL, 'hawkular')
        env.put(Context.SECURITY_CREDENTIALS, 'hawkular')

        InitialContext namingContext = new InitialContext(env)
        ConnectionFactory connectionFactory = (ConnectionFactory) namingContext.lookup('jms/RemoteConnectionFactory')
        JMSContext context = connectionFactory.createContext('hawkular', 'hawkular')
        Topic topic = (Topic) namingContext.lookup('java:/topic/HawkularAvailData')
        JMSProducer producer = context.createProducer()

        for (int i=0; i<5; i++) {
            String strAvailData = "{\"availData\":{\"data\":[{\"tenantId\":\"$testTenant\"," +
                    "\"id\":\"test-bus-email-availability\"," +
                    "\"timestamp\":" + (System.currentTimeMillis() + i ) + "," +
                    "\"avail\":\"DOWN\"}]}}"
            producer.send(topic, strAvailData)
        }

        context.close()

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            // println "SLEEP!" ;
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-bus-email-availability"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
    }

    @Test
    void thresholdThroughBusTest() {
        String start = String.valueOf(System.currentTimeMillis());

        // CREATE the trigger
        def resp = client.get(path: "")
        assert resp.status == 200 : resp.status

        Trigger testTrigger = new Trigger("test-bus-email-threshold", "http://www.mydemourl.com");

        // remove if it exists
        resp = client.delete(path: "triggers/test-bus-email-threshold")
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
        ThresholdCondition firingCond = new ThresholdCondition("test-bus-email-threshold",
                Mode.FIRING, "test-bus-email-threshold", ThresholdCondition.Operator.GT, 300);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-bus-email-threshold/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/test-bus-email-threshold", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-bus-email-threshold");
        assertEquals(200, resp.status)
        assertEquals("http://www.mydemourl.com", resp.data.name)
        assertEquals(true, resp.data.enabled)
        assertEquals(false, resp.data.autoDisable);
        assertEquals(false, resp.data.autoResolve);
        assertEquals(false, resp.data.autoResolveAlerts);

        // FETCH recent alerts for trigger, should not be any
        resp = client.get(path: "", query: [startTime:start,triggerIds:"test-bus-email-threshold"] )
        assertEquals(200, resp.status)

        // Send in data to fire the trigger

        Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory")
        env.put(Context.PROVIDER_URL, "http-remoting://127.0.0.1:8080")
        env.put(Context.SECURITY_PRINCIPAL, 'hawkular')
        env.put(Context.SECURITY_CREDENTIALS, 'hawkular')

        InitialContext namingContext = new InitialContext(env)
        ConnectionFactory connectionFactory = (ConnectionFactory) namingContext.lookup('jms/RemoteConnectionFactory')
        JMSContext context = connectionFactory.createContext('hawkular', 'hawkular')
        Topic topic = (Topic) namingContext.lookup('java:/topic/HawkularMetricData')
        JMSProducer producer = context.createProducer()

        for (int i=0; i<5; i++) {
            String strMetricData = "{\"metricData\":{\"tenantId\":\"$testTenant\"," +
                    "\"data\":[{\"source\":\"test-bus-email-threshold\"," +
                    "\"timestamp\":" + (System.currentTimeMillis() + i) + "," +
                    "\"value\":" + String.valueOf(305.5 + i) + "}]}}"
            producer.send(topic, strMetricData)
        }

        context.close()

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            // println "SLEEP!" ;
            Thread.sleep(500);

            // FETCH recent alerts for trigger, there should be 5
            resp = client.get(path: "", query: [startTime:start,triggerIds:"test-bus-email-threshold"] )
            if ( resp.status == 200 && resp.data.size() == 5 ) {
                break;
            }
        }
        assertEquals(200, resp.status)
        assertEquals(5, resp.data.size())
        assertEquals("OPEN", resp.data[0].status)
    }


}
