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

import org.hawkular.alerts.api.json.GroupConditionsInfo
import org.hawkular.alerts.api.model.Severity
import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.trigger.TriggerType
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.FixMethodOrder
import org.junit.Test


import static org.hawkular.alerts.api.model.condition.AvailabilityCondition.Operator
import static org.junit.Assert.*
import static org.junit.runners.MethodSorters.NAME_ASCENDING

/**
 * Alerts REST tests.
 *
 * @author Jay Shaughnessy
 */
@FixMethodOrder(NAME_ASCENDING)
class GroupITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(GroupITest.class)

    static host = System.getProperty('hawkular.host') ?: '127.0.0.1'
    static port = Integer.valueOf(System.getProperty('hawkular.port') ?: "8080")
    static t01Start = String.valueOf(System.currentTimeMillis())
    static t02Start;

    @Test
    void t01_dataDrivenGroupTest() {
        logger.info( "Running t01_dataDrivenGroupTest")
        String start = t01Start;

        // CREATE the data-driven group trigger
        def resp = client.get(path: "")
        assertEquals(200, resp.status)

        // sub-test: add context and ensure it carries through to the members
        Map<String,String> context = new HashMap<>(1);
        context.put("contextName","contextValue");
        context.put("contextName2","contextValue2");
        Trigger testTrigger = new Trigger("test-ddgroup-trigger", "test-ddgroup-trigger", context);

        // sub-test: add tag and ensure it carries through to the members
        testTrigger.addTag("test-ddgroup-tname","test-ddgroup-tvalue");

        // remove if it exists
        resp = client.delete(path: "triggers/groups/test-ddgroup-trigger")
        assert(200 == resp.status || 404 == resp.status)

        testTrigger.setType(TriggerType.DATA_DRIVEN_GROUP);
        testTrigger.setAutoDisable(false);
        testTrigger.setAutoResolve(false);
        testTrigger.setSeverity(Severity.LOW);

        resp = client.post(path: "triggers/groups", body: testTrigger)
        assertEquals(200, resp.status)

        // ADD Firing condition
        AvailabilityCondition firingCond = new AvailabilityCondition("test-ddgroup-trigger",
                Mode.FIRING, "test-ddgroup-avail", Operator.NOT_UP);

        Collection<Condition> conditions = new ArrayList<>(1);
        conditions.add( firingCond );
        resp = client.put(path: "triggers/test-ddgroup-trigger/conditions/firing", body: conditions)
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        // ENABLE Trigger
        testTrigger.setEnabled(true);

        resp = client.put(path: "triggers/groups/test-ddgroup-trigger/", body: testTrigger)
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-ddgroup-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-ddgroup-trigger", resp.data.name)
        assertTrue(resp.data.enabled)
        assertFalse(resp.data.autoDisable);
        assertFalse(resp.data.autoEnable);
        assertEquals("LOW", resp.data.severity);
        assertNotNull(resp.data.context);
        assertEquals("contextValue", resp.data.context.get("contextName"));
        assertEquals("contextValue2", resp.data.context.get("contextName2"));
        assertEquals("DATA_DRIVEN_GROUP", resp.data.type);

        // FETCH members, should not be any
        resp = client.get(path: "triggers/groups/test-ddgroup-trigger/members")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size)

        // Send in avail data to create Source-1 member
        String jsonData = "[{\"source\":\"Source-1\",\"id\":\"test-ddgroup-avail\",\"timestamp\":" + System.currentTimeMillis() + ",\"value\":\"UP\"}]";
        resp = client.post(path: "data", body: jsonData);
        assertEquals(200, resp.status)

        // Send in avail data to create Source-2 member
        jsonData = "[{\"source\":\"Source-2\",\"id\":\"test-ddgroup-avail\",\"timestamp\":" + System.currentTimeMillis() + ",\"value\":\"UP\"}]";
        resp = client.post(path: "data", body: jsonData);
        assertEquals(200, resp.status)

        // The member creation/alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH members for group trigger, there should be 2
            resp = client.get(path: "triggers/groups/test-ddgroup-trigger/members")
            if ( resp.status == 200 && resp.data != null && resp.data.size > 1) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        // VALIDATE member triggers
        Trigger mt1 = resp.data[0]
        Trigger mt2 = resp.data[1]
        if ( mt1.id != "test-ddgroup-trigger_Source-1" ) {
          mt1 = resp.data[1];
          mt2 = resp.data[0];
        }
        assertEquals("test-ddgroup-trigger_Source-1", mt1.id)
        assertEquals("test-ddgroup-trigger_Source-2", mt2.id)
        assertEquals("test-ddgroup-trigger", mt1.name)
        assertEquals("test-ddgroup-trigger", mt2.name)
        assertEquals(TriggerType.MEMBER, mt1.type)
        assertEquals(TriggerType.MEMBER, mt2.type)
        assertEquals("test-ddgroup-trigger", mt1.memberOf)
        assertEquals("test-ddgroup-trigger", mt2.memberOf)
        assertEquals("contextValue", mt1.context.get("contextName"));
        assertEquals("contextValue2", mt1.context.get("contextName2"));
        assertEquals("contextValue", mt2.context.get("contextName"));
        assertEquals("contextValue2", mt2.context.get("contextName2"));

        // Send in avail data to create Source-1 alert
        jsonData = "[{\"source\":\"Source-1\",\"id\":\"test-ddgroup-avail\",\"timestamp\":" + (System.currentTimeMillis() + 1000) + ",\"value\":\"DOWN\"}]";
        resp = client.post(path: "data", body: jsonData);
        assertEquals(200, resp.status)

        // Send in avail data to generate Source-2 alert
        jsonData = "[{\"source\":\"Source-2\",\"id\":\"test-ddgroup-avail\",\"timestamp\":" + (System.currentTimeMillis() + 1000) + ",\"value\":\"DOWN\"}]";
        resp = client.post(path: "data", body: jsonData);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 20; ++i ) {
            Thread.sleep(500);

            // FETCH alerts for member triggers, there should be 2
            resp = client.get(path: "",
                              query: [startTime:start,
                                      triggerIds:"test-ddgroup-trigger_Source-1,test-ddgroup-trigger_Source-2"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size > 1) {
                if ( i > 10 ) {
                    logger.info( "Perf: passing but sleep iterations high [" + i + "]" );
                }
                break;
            }
            assertEquals(200, resp.status)
        }
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size())

        // Test to make sure disabling the group trigger disables the member triggers (and updates the rule base)

        // DISABLE Trigger
        resp = client.put(path: "triggers/groups/enabled", query: [triggerIds:"test-ddgroup-trigger",enabled:false])
        assertEquals(200, resp.status)

        // FETCH trigger and make sure it's as expected
        resp = client.get(path: "triggers/test-ddgroup-trigger");
        assertEquals(200, resp.status)
        assertEquals("test-ddgroup-trigger", resp.data.name)
        assertFalse(resp.data.enabled)

        // FETCH members, should not be any
        resp = client.get(path: "triggers/groups/test-ddgroup-trigger/members")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size)
        assertFalse(resp.data[0].enabled)
        assertFalse(resp.data[1].enabled)

        // Send in avail data to create Source-1 alert if it were enabled (it should not generate an alert)
        jsonData = "[{\"source\":\"Source-1\",\"id\":\"test-ddgroup-avail\",\"timestamp\":" + (System.currentTimeMillis() + 1000) + ",\"value\":\"DOWN\"}]";
        resp = client.post(path: "data", body: jsonData);
        assertEquals(200, resp.status)

        // The alert processing happens async, so give it a little time before failing...
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(500);

            // FETCH alerts for member triggers, there should still be only 2
            resp = client.get(path: "",
                              query: [startTime:start,
                                      triggerIds:"test-ddgroup-trigger_Source-1,test-ddgroup-trigger_Source-2"] )
            if ( resp.status == 200 && resp.data != null && resp.data.size > 2) {
                fail("The disabled trigger should not generate an alert!")
                break;
            }
            assertEquals(200, resp.status)
        }

        // an update to the group conditions should invalidate the data-driven members, and they will need to be
        // regenerated.
        AvailabilityCondition updatedFiringCond = new AvailabilityCondition("test-ddgroup-trigger",
                Mode.FIRING, "test-ddgroup-avail", Operator.DOWN);
        GroupConditionsInfo groupConditionsInfo = new GroupConditionsInfo( updatedFiringCond, null );

        resp = client.put(path: "triggers/groups/test-ddgroup-trigger/conditions/firing", body: groupConditionsInfo)
        assertEquals(resp.toString(), 200, resp.status)
        assertEquals(1, resp.data.size())

        // FETCH members, should not be any
        resp = client.get(path: "triggers/groups/test-ddgroup-trigger/members")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size)

        resp = client.delete(path: "triggers/groups/test-ddgroup-trigger")
        assertEquals(200, resp.status)
    }
}
