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
import org.hawkular.alerts.api.json.GroupMemberInfo
import org.hawkular.alerts.api.json.UnorphanMemberInfo
import org.hawkular.alerts.api.model.Severity
import org.hawkular.alerts.api.model.action.ActionDefinition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.dampening.Dampening
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Triggers REST tests.
 *
 * @author Lucas Ponce
 */
class TriggersITest extends AbstractITestBase {

    static Logger logger = LoggerFactory.getLogger(TriggersITest.class)

    @Test
    void createTrigger() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // remove if it exists
        def resp = client.delete(path: "triggers/test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // Should not be able to create the same triggerId again
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(400, resp.status)

        resp = client.get(path: "triggers/test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("No-Metric", resp.data.name)

        testTrigger.setName("No-Metric-Modified")
        testTrigger.setSeverity(Severity.CRITICAL)
        resp = client.put(path: "triggers/test-trigger-1", body: testTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
        assertEquals("No-Metric-Modified", resp.data.name)
        assertEquals("CRITICAL", resp.data.severity)

        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)

        // Test create w/o assigning a TriggerId, it should generate a UUID
        testTrigger.setId(null);
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)
        assertNotNull(resp.data.id)

        // Delete the trigger
        resp = client.delete(path: "triggers/" + resp.data.id)
        assertEquals(200, resp.status)
    }

    @Test
    void testGroupTrigger() {
        Trigger groupTrigger = new Trigger("group-trigger", "group-trigger");
        groupTrigger.setEnabled(false);

        // remove if it exists
        def resp = client.delete(path: "triggers/groups/group-trigger", query: [keepNonOrphans:false,keepOrphans:false])
        assert(200 == resp.status || 404 == resp.status)

        // create the group
        resp = client.post(path: "triggers/groups", body: groupTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;
        assertEquals( true, groupTrigger.isGroup() );

        ThresholdCondition cond1 = new ThresholdCondition("group-trigger", Mode.FIRING, "DataId1-Token",
            ThresholdCondition.Operator.GT, 10.0);
        Map<String, Map<String, String>> dataIdMemberMap = new HashMap<>();
        GroupConditionsInfo groupConditionsInfo = new GroupConditionsInfo( cond1, dataIdMemberMap );

        resp = client.put(path: "triggers/groups/group-trigger/conditions/firing", body: groupConditionsInfo)
        assertEquals(resp.toString(), 200, resp.status)
        assertEquals(1, resp.data.size())

        // create member 1
        Map<String,String> dataId1Map = new HashMap<>(2);
        dataId1Map.put("DataId1-Token", "DataId1-Child1");
        GroupMemberInfo groupMemberInfo = new GroupMemberInfo("group-trigger", "member1", "member1", null, null, null,
            dataId1Map);
        resp = client.post(path: "triggers/groups/members", body: groupMemberInfo);
        assertEquals(200, resp.status)

        // create member 2
        dataId1Map.put("DataId1-Token", "DataId1-Child2");
        groupMemberInfo = new GroupMemberInfo("group-trigger", "member2", "member2", null, null, null, dataId1Map);
        resp = client.post(path: "triggers/groups/members", body: groupMemberInfo);
        assertEquals(200, resp.status)

        // orphan member2, it should no longer get updates
        resp = client.post(path: "triggers/groups/members/member2/orphan");
        assertEquals(200, resp.status)

        // add dampening to the group
        Dampening groupDampening = Dampening.forRelaxedCount("", "group-trigger", Mode.FIRING, 2, 4);
        resp = client.post(path: "triggers/groups/group-trigger/dampenings", body: groupDampening);
        assertEquals(200, resp.status)
        groupDampening = resp.data // get the updated dampeningId for use below

        // add tag to the group (via update)
        groupTrigger.addTag( "group-tname", "group-tvalue" );
        resp = client.put(path: "triggers/groups/group-trigger", body: groupTrigger)
        assertEquals(200, resp.status)

        // add another condition to the group (only member1 is relevant, member2 is now an orphan)
        ThresholdCondition cond2 = new ThresholdCondition("group-trigger", Mode.FIRING, "DataId2-Token",
            ThresholdCondition.Operator.LT, 20.0);
        dataId1Map.clear();
        dataId1Map.put("member1", "DataId1-Child1");
        Map<String,String> dataId2Map = new HashMap<>(2);
        dataId2Map.put("member1", "DataId2-Child1");
        dataIdMemberMap.put("DataId1-Token", dataId1Map);
        dataIdMemberMap.put("DataId2-Token", dataId2Map);
        Collection<Condition> groupConditions = new ArrayList<>(2);
        groupConditions.add(cond1);
        groupConditions.add(cond2);
        groupConditionsInfo = new GroupConditionsInfo(groupConditions, dataIdMemberMap);
        resp = client.put(path: "triggers/groups/group-trigger/conditions/firing", body: groupConditionsInfo)
        assertEquals(200, resp.status)

        // update the group trigger
        groupTrigger.setAutoDisable( true );
        resp = client.put(path: "triggers/groups/group-trigger", body: groupTrigger)
        assertEquals(200, resp.status)

        // update the group dampening to the group
        String did = groupDampening.getDampeningId();
        groupDampening = Dampening.forRelaxedCount("", "group-trigger", Mode.FIRING, 2, 6);
        resp = client.put(path: "triggers/groups/group-trigger/dampenings/" + did, body: groupDampening);
        assertEquals(200, resp.status)

        // query w/o orphans
        resp = client.get(path: "triggers/groups/group-trigger/members", query: [includeOrphans:"false"])
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        Trigger member = (Trigger)resp.data[0];
        assertEquals( false, member.isOrphan() );
        assertEquals( "member1", member.getName() );

        // query w orphans
        resp = client.get(path: "triggers/groups/group-trigger/members", query: [includeOrphans:"true"])
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());
        member = (Trigger)resp.data[0];
        Trigger orphanMember = (Trigger)resp.data[1];
        if ( member.isOrphan() ) {
            member = (Trigger)resp.data[1];
            orphanMember = (Trigger)resp.data[0];
        }
        assertEquals( "member1", member.getName() );
        assertEquals( false, member.isOrphan() );
        assertEquals( true, member.isAutoDisable());
        assertEquals( "member2", orphanMember.getName() );
        assertEquals( true, orphanMember.isOrphan() );
        assertEquals( false, orphanMember.isAutoDisable());

        // get the group trigger
        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;
        assertEquals( "group-trigger", groupTrigger.getName() );
        assertEquals( true, groupTrigger.isGroup() );
        assertEquals( true, groupTrigger.isAutoDisable());

        // get the group dampening
        resp = client.get(path: "triggers/group-trigger/dampenings")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // check the group tags
        Map<String, String> groupTags = groupTrigger.getTags();
        assertEquals(1, groupTags.size());
        assertEquals("group-tvalue", groupTags.get("group-tname"));

        // get the group conditions
        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());
        groupConditions = (Collection<Condition>)resp.data;
        cond1 = resp.data[0];
        cond2 = resp.data[1];
        def dataIds = [cond1.getDataId(), cond2.getDataId()].sort();
        assertEquals("DataId1-Token", dataIds[0]);
        assertEquals("DataId2-Token", dataIds[1]);

        // get the member1 trigger
        resp = client.get(path: "triggers/member1")
        assertEquals(200, resp.status)
        def member1 = (Trigger)resp.data;
        assertEquals( "member1", member1.getName() );
        assertEquals( true, member1.isMember() );
        assertEquals( true, member1.isAutoDisable());

        // check the member1 tag
        Map<String, String> memberTags = member1.getTags();
        assertEquals(1, memberTags.size());
        assertEquals("group-tvalue", memberTags.get("group-tname"));

        // get the member1 dampening
        resp = client.get(path: "triggers/member1/dampenings")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // get the member1 conditions
        resp = client.get(path: "triggers/member1/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());

        // get the member2 trigger
        resp = client.get(path: "triggers/member2")
        assertEquals(200, resp.status)
        def member2 = (Trigger)resp.data;
        assertEquals( "member2", member2.getName() );
        assertEquals( true, member2.isMember() );
        assertEquals( false, member2.isAutoDisable());

        // check the member2 tag
        memberTags = member2.getTags();
        assertEquals(0, memberTags.size());

        // check the member2 dampening
        resp = client.get(path: "triggers/member2/dampenings")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size());

        // get the member2 condition
        resp = client.get(path: "triggers/member2/conditions")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // unorphan member2
        Map<String,String> dataIdMap = new HashMap<>(2);
        dataIdMap.put("DataId1-Token", "DataId1-Child2");
        dataIdMap.put("DataId2-Token", "DataId2-Child2");
        UnorphanMemberInfo unorphanMemberInfo = new UnorphanMemberInfo(null, null, dataIdMap);
        resp = client.post(path: "triggers/groups/members/member2/unorphan", body: unorphanMemberInfo);
        assertEquals(200, resp.status)

        // get the member2 trigger
        resp = client.get(path: "triggers/member2")
        assertEquals(200, resp.status)
        member2 = (Trigger)resp.data;
        assertEquals( "member2", member2.getName() );
        assertEquals( false, member2.isOrphan() );
        assertEquals( true, member2.isAutoDisable());

        // check the member2 tag
        memberTags = member2.getTags();
        assertEquals(1, memberTags.size());
        assertEquals("group-tvalue", memberTags.get("group-tname"));

        // check the member2 dampening
        resp = client.get(path: "triggers/member2/dampenings")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // get the member2 condition
        resp = client.get(path: "triggers/member2/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());

        // delete group tag
        groupTrigger.getTags().clear();
        resp = client.put(path: "triggers/groups/group-trigger", body: groupTrigger)
        assertEquals(200, resp.status)

        // delete group dampening
        resp = client.delete(path: "triggers/groups/group-trigger/dampenings/" + did)
        assertEquals(200, resp.status)

        // delete group cond1 (done by re-setting conditions w/o the undesired condition)
        groupConditions.clear();
        groupConditions.add(cond2);
        logger.info(groupConditions.toString())
        assertEquals(1, groupConditions.size());
        dataId2Map.clear();
        dataId2Map.put("member1", "DataId2-Child1");
        dataId2Map.put("member2", "DataId2-Child2");
        dataIdMemberMap.clear();
        dataIdMemberMap.put("DataId2-Token", dataId2Map);
        groupConditionsInfo = new GroupConditionsInfo(groupConditions, dataIdMemberMap);
        resp = client.put(path: "triggers/groups/group-trigger/conditions/firing", body: groupConditionsInfo)
        assertEquals(200, resp.status)

        // get the group trigger
        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;
        assertEquals( "group-trigger", groupTrigger.getName() );
        assertEquals( true, groupTrigger.isGroup() );
        assertEquals( true, groupTrigger.isAutoDisable());

        // check the group dampening
        groupTags = groupTrigger.getTags();
        assertEquals(0, groupTags.size());

        // check the group condition
        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // query w/o orphans
        resp = client.get(path: "triggers/groups/group-trigger/members", query: [includeOrphans:"false"])
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());
        for(int i=0; i < resp.data.size(); ++i) {
            member = (Trigger)resp.data[i];
            String name = member.getName();
            assertEquals( false, member.isOrphan() );
            assertEquals( true, member.isAutoDisable());
            resp = client.get(path: "triggers/" + name + "/dampenings")
            assertEquals(200, resp.status)
            assertEquals(0, resp.data.size());
            resp = client.get(path: "triggers/" + name + "/conditions")
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size());
        }

        // ensure a member trigger can be removed with the standard remove trigger
        resp = client.delete(path: "triggers/member2")
        assertEquals(200, resp.status)
        resp = client.get(path: "triggers/member2")
        assertEquals(404, resp.status)

        // ensure a group trigger can not be removed with the standard remove trigger
        resp = client.delete(path: "triggers/group-trigger")
        assertEquals(400, resp.status)

        // remove group trigger
        resp = client.delete(path: "triggers/groups/group-trigger")
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/group-trigger")
        assertEquals(404, resp.status)
        resp = client.get(path: "triggers/member1")
        assertEquals(404, resp.status)
        resp = client.get(path: "triggers/member2")
        assertEquals(404, resp.status)
    }

    @Test
    void testTag() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");
        testTrigger.addTag("tname", "tvalue");

        // remove if it exists
        def resp = client.delete(path: "triggers/test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        // create the test trigger
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // make sure the test trigger exists
        resp = client.get(path: "triggers/test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("No-Metric", resp.data.name)

        Map<String, String> tags = resp.data.tags;
        assertEquals("tvalue", tags.get("tname"));

        resp = client.get(path: "triggers", query: [tags:"tname|tvalue"] );
        assertEquals(200, resp.status)
        assertEquals("test-trigger-1", resp.data.iterator().next().id)

        resp = client.get(path: "triggers", query: [tags:"tname|*"] );
        assertEquals(200, resp.status)
        assertEquals("test-trigger-1", resp.data.iterator().next().id)

        resp = client.get(path: "triggers", query: [tags:"funky|funky"] );
        assertEquals(200, resp.status)
        assertEquals(false, resp.data.iterator().hasNext())

        // delete the tag
        testTrigger.getTags().clear();
        resp = client.put(path: "triggers/test-trigger-1", body: testTrigger )
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers", query: [tags:"tname|tvalue"] );
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        // delete the trigger
        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
    }

    @Test
    void createFullTrigger() {
        // CREATE the action definition
        String actionPlugin = "email"
        String actionId = "email-to-admin";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("from", "from-alerts@company.org");
        actionProperties.put("to", "to-admin@company.org");
        actionProperties.put("cc", "cc-developers@company.org");

        ActionDefinition actionDefinition = new ActionDefinition(null, actionPlugin, actionId, actionProperties);

        def resp = client.post(path: "actions", body: actionDefinition)
        assert(200 == resp.status || 400 == resp.status)

        String jsonTrigger = "{\n" +
                "      \"trigger\":{\n" +
                "        \"id\": \"full-test-trigger-1\",\n" +
                "        \"enabled\": true,\n" +
                "        \"name\": \"NumericData-01-low\",\n" +
                "        \"description\": \"description 1\",\n" +
                "        \"severity\": \"HIGH\",\n" +
                "        \"actions\": [\n" +
                "          {\"actionPlugin\":\"email\", \"actionId\":\"email-to-admin\"}\n" +
                "        ],\n" +
                "        \"context\": {\n" +
                "          \"name1\":\"value1\"\n" +
                "        },\n" +
                "        \"tags\": {\n" +
                "          \"tname1\":\"tvalue1\",\n" +
                "          \"tname2\":\"tvalue2\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"dampenings\":[\n" +
                "        {\n" +
                "          \"triggerMode\": \"FIRING\",\n" +
                "          \"type\": \"STRICT\",\n" +
                "          \"evalTrueSetting\": 2,\n" +
                "          \"evalTotalSetting\": 2\n" +
                "        }\n" +
                "      ],\n" +
                "      \"conditions\":[\n" +
                "        {\n" +
                "          \"triggerMode\": \"FIRING\",\n" +
                "          \"type\": \"threshold\",\n" +
                "          \"dataId\": \"NumericData-01\",\n" +
                "          \"operator\": \"LT\",\n" +
                "          \"threshold\": 10.0,\n" +
                "          \"context\": {\n" +
                "            \"description\": \"Response Time\",\n" +
                "            \"unit\": \"ms\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }";

        // remove if it exists
        resp = client.delete(path: "triggers/full-test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        // create the test trigger
        resp = client.post(path: "triggers/trigger", body: jsonTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/trigger/full-test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("NumericData-01-low", resp.data.trigger.name)
        assertEquals(testTenant, resp.data.trigger.tenantId)
        assertEquals(1, resp.data.dampenings.size())
        assertEquals(1, resp.data.conditions.size())

        resp = client.delete(path: "triggers/full-test-trigger-1")
        assertEquals(200, resp.status)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

    @Test
    void createFullTriggerWithNullValues() {
        String jsonTrigger = "{\n" +
                "      \"trigger\":{\n" +
                "        \"id\": \"full-test-trigger-with-nulls\",\n" +
                "        \"enabled\": true,\n" +
                "        \"name\": \"NumericData-01-low\",\n" +
                "        \"description\": \"description 1\",\n" +
                "        \"severity\": null\n," +
                "        \"autoResolve\": null\n," +
                "        \"autoResolveAlerts\": null\n," +
                "        \"eventType\": null\n," +
                "        \"tenantId\": null\n," +
                "        \"description\": null\n," +
                "        \"autoEnable\": null\n," +
                "        \"autoDisable\": null\n" +
                "      }\n" +
                "    }";

        // remove if it exists
        def resp = client.delete(path: "triggers/full-test-trigger-with-nulls")
        assert(200 == resp.status || 404 == resp.status)

        // create the test trigger
        resp = client.post(path: "triggers/trigger", body: jsonTrigger)
        assertEquals(200, resp.status)

        resp = client.delete(path: "triggers/full-test-trigger-with-nulls")
        assertEquals(200, resp.status)
    }

    @Test
    void failWithUnknownActionOrPluginFullTrigger() {
        String jsonTrigger = "{\n" +
                "      \"trigger\":{\n" +
                "        \"id\": \"full-test-trigger-1\",\n" +
                "        \"enabled\": true,\n" +
                "        \"name\": \"NumericData-01-low\",\n" +
                "        \"description\": \"description 1\",\n" +
                "        \"severity\": \"HIGH\",\n" +
                "        \"actions\": [\n" +
                // Unknown email-to-nothing action
                "          {\"actionPlugin\":\"email\", \"actionId\":\"email-to-nothing\"}\n" +
                "        ],\n" +
                "        \"context\": {\n" +
                "          \"name1\":\"value1\"\n" +
                "        },\n" +
                "        \"tags\": {\n" +
                "          \"tname1\":\"tvalue1\",\n" +
                "          \"tname2\":\"tvalue2\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"dampenings\":[\n" +
                "        {\n" +
                "          \"triggerMode\": \"FIRING\",\n" +
                "          \"type\": \"STRICT\",\n" +
                "          \"evalTrueSetting\": 2,\n" +
                "          \"evalTotalSetting\": 2\n" +
                "        }\n" +
                "      ],\n" +
                "      \"conditions\":[\n" +
                "        {\n" +
                "          \"triggerMode\": \"FIRING\",\n" +
                "          \"type\": \"threshold\",\n" +
                "          \"dataId\": \"NumericData-01\",\n" +
                "          \"operator\": \"LT\",\n" +
                "          \"threshold\": 10.0,\n" +
                "          \"context\": {\n" +
                "            \"description\": \"Response Time\",\n" +
                "            \"unit\": \"ms\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }";

        // remove if it exists
        def resp = client.delete(path: "triggers/full-test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        // create the test trigger
        resp = client.post(path: "triggers/trigger", body: jsonTrigger)
        assertEquals(400, resp.status)

        jsonTrigger = "{\n" +
                "      \"trigger\":{\n" +
                "        \"id\": \"full-test-trigger-1\",\n" +
                "        \"enabled\": true,\n" +
                "        \"name\": \"NumericData-01-low\",\n" +
                "        \"description\": \"description 1\",\n" +
                "        \"severity\": \"HIGH\",\n" +
                "        \"actions\": [\n" +
                // Unknown plugin
                "          {\"actionPlugin\":\"unknown\", \"actionId\":\"email-to-nothing\"}\n" +
                "        ],\n" +
                "        \"context\": {\n" +
                "          \"name1\":\"value1\"\n" +
                "        },\n" +
                "        \"tags\": {\n" +
                "          \"tname1\":\"tvalue1\",\n" +
                "          \"tname2\":\"tvalue2\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"dampenings\":[\n" +
                "        {\n" +
                "          \"triggerMode\": \"FIRING\",\n" +
                "          \"type\": \"STRICT\",\n" +
                "          \"evalTrueSetting\": 2,\n" +
                "          \"evalTotalSetting\": 2\n" +
                "        }\n" +
                "      ],\n" +
                "      \"conditions\":[\n" +
                "        {\n" +
                "          \"triggerMode\": \"FIRING\",\n" +
                "          \"type\": \"threshold\",\n" +
                "          \"dataId\": \"NumericData-01\",\n" +
                "          \"operator\": \"LT\",\n" +
                "          \"threshold\": 10.0,\n" +
                "          \"context\": {\n" +
                "            \"description\": \"Response Time\",\n" +
                "            \"unit\": \"ms\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }";

        // create the test trigger
        resp = client.post(path: "triggers/trigger", body: jsonTrigger)
        assertEquals(400, resp.status)
    }

    @Test
    void validateMultipleTriggerModeConditions() {
        Trigger testTrigger = new Trigger("test-multiple-mode-conditions", "No-Metric")

        // make sure clean test trigger exists
        client.delete(path: "triggers/test-multiple-mode-conditions")
        def resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        ThresholdCondition testCond1 = new ThresholdCondition("test-multiple-mode-conditions", Mode.FIRING,
                "No-Metric", ThresholdCondition.Operator.GT, 10.12);

        ThresholdCondition testCond2 = new ThresholdCondition("test-multiple-mode-conditions", Mode.AUTORESOLVE,
                "No-Metric", ThresholdCondition.Operator.LT, 4.10);

        Collection<Condition> conditions = new ArrayList<>(2);
        conditions.add( testCond1 );
        conditions.add( testCond2 );
        resp = client.put(path: "triggers/test-multiple-mode-conditions/conditions/firing", body: conditions)
        assertEquals(400, resp.status)

        resp = client.delete(path: "triggers/test-multiple-mode-conditions")
        assertEquals(200, resp.status)
    }

    @Test
    void validateMultipleTriggerModeConditionsInGroups() {
        Trigger groupTrigger = new Trigger("group-trigger", "group-trigger");
        groupTrigger.setEnabled(false);

        // remove if it exists
        def resp = client.delete(path: "triggers/groups/group-trigger", query: [keepNonOrphans:false,keepOrphans:false])
        assert(200 == resp.status || 404 == resp.status)

        // create the group
        resp = client.post(path: "triggers/groups", body: groupTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;
        assertEquals( true, groupTrigger.isGroup() );

        ThresholdCondition cond1 = new ThresholdCondition("group-trigger", Mode.FIRING, "DataId1-Token",
                ThresholdCondition.Operator.GT, 10.0);
        ThresholdCondition cond2 = new ThresholdCondition("group-trigger", Mode.AUTORESOLVE, "DataId2-Token",
                ThresholdCondition.Operator.LT, 20.0);

        Map<String, Map<String, String>> dataIdMemberMap = new HashMap<>();
        GroupConditionsInfo groupConditionsInfo = new GroupConditionsInfo(Arrays.asList(cond1, cond2), dataIdMemberMap)

        resp = client.put(path: "triggers/groups/group-trigger/conditions/firing", body: groupConditionsInfo)
        assertEquals(resp.toString(), 400, resp.status)

        // remove group trigger
        resp = client.delete(path: "triggers/groups/group-trigger")
        assertEquals(200, resp.status)
    }
}
