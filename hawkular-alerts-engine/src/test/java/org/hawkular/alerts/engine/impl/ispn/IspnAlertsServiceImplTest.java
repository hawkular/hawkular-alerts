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
package org.hawkular.alerts.engine.impl.ispn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnAlertsServiceImplTest extends IspnBaseServiceImplTest {
    static final MsgLogger log = MsgLogging.getMsgLogger(IspnAlertsServiceImplTest.class);

    @BeforeClass
    public static void init() {
        alerts = new IspnAlertsServiceImpl();
        alerts.init();
    }

    @Test
    public void addAlerts() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numAlerts = 100;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        assertEquals(2 * 5 * 100, alerts.getAlerts(tenantIds, null, null).size());

        tenantIds.remove("tenant0");
        assertEquals(1 * 5 * 100, alerts.getAlerts(tenantIds, null, null).size());

        List<Alert> testAlerts = alerts.getAlerts(tenantIds, null, null);
        tenantIds.clear();
        Set<String> alertIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Alert alertX = testAlerts.get(i);
            tenantIds.add(alertX.getTenantId());
            alertIds.add(alertX.getAlertId());
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);

        assertEquals(3, alerts.getAlerts(tenantIds, criteria, null).size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void evaluateTagQuery() throws Exception {
        StringBuilder query = new StringBuilder();

        String e1 = "tagA";
        alerts.parseTagQuery(e1, query);
        assertEquals("('tagA')", query.toString());

        String e2 = "not tagA";
        query = new StringBuilder();
        alerts.parseTagQuery(e2, query);
        assertEquals("(not 'tagA')", query.toString());

        String e3 = "tagA  =      'abc'";
        query = new StringBuilder();
        alerts.parseTagQuery(e3, query);
        assertEquals("(/tagA_abc/)", query.toString().trim());

        String e4 = " tagA !=   'abc'";
        query = new StringBuilder();
        alerts.parseTagQuery(e4, query);
        assertEquals("('tagA' and not /tagA_abc/)", query.toString().trim());

        String e5 = "tagA IN ['abc', 'def', 'ghi']";
        query = new StringBuilder();
        alerts.parseTagQuery(e5, query);
        assertEquals("(/tagA_abc/ or /tagA_def/ or /tagA_ghi/)", query.toString().trim());

        String e5s1 = "tagA IN ['abc','def','ghi']";
        query = new StringBuilder();
        alerts.parseTagQuery(e5s1, query);
        assertEquals("(/tagA_abc/ or /tagA_def/ or /tagA_ghi/)", query.toString().trim());

        String e6 = "tagA NOT IN ['abc', 'def', 'ghi']";
        query = new StringBuilder();
        alerts.parseTagQuery(e6, query);
        assertEquals("('tagA' and (not /tagA_abc/) and (not /tagA_def/) and (not /tagA_ghi/))", query.toString().trim());

        String e7 = "tagA  =      '*'";
        query = new StringBuilder();
        alerts.parseTagQuery(e7, query);
        assertEquals("(/tagA_.*/)", query.toString().trim());

        String e8 = "tagA  =      abc";
        query = new StringBuilder();
        alerts.parseTagQuery(e8, query);
        assertEquals("('tagA_abc')", query.toString().trim());

        String e9 = " tagA !=   abc";
        query = new StringBuilder();
        alerts.parseTagQuery(e9, query);
        assertEquals("('tagA' and not 'tagA_abc')", query.toString().trim());

        String e10 = "tagA IN [abc, def, ghi]";
        query = new StringBuilder();
        alerts.parseTagQuery(e10, query);
        assertEquals("('tagA_abc' or 'tagA_def' or 'tagA_ghi')", query.toString().trim());

        String e11 = "tagA NOT IN [abc, def, ghi]";
        query = new StringBuilder();
        alerts.parseTagQuery(e11, query);
        assertEquals("('tagA' and (not 'tagA_abc') and (not 'tagA_def') and (not 'tagA_ghi'))", query.toString().trim());

        String e12 = "tagA  =      *";
        query = new StringBuilder();
        try {
            alerts.parseTagQuery(e12, query);
            fail("* should be used with single quotes");
        } catch (Exception e) {
            // Expected
        }

        String e13 = "tagA-01";
        query = new StringBuilder();
        alerts.parseTagQuery(e13, query);
        assertEquals("('tagA-01')", query.toString().trim());

        String e14 = "tagA and not tagB";
        query = new StringBuilder();
        alerts.parseTagQuery(e14, query);
        assertEquals("(('tagA') and (not 'tagB'))", query.toString().trim());

        String e15 = "not tagB and tagA";
        query = new StringBuilder();
        alerts.parseTagQuery(e15, query);
        assertEquals("(('tagA') and (not 'tagB'))", query.toString().trim());

        String e16 = "not tagB or tagA";
        query = new StringBuilder();
        alerts.parseTagQuery(e16, query);
        assertEquals("(('tagA') or (not 'tagB'))", query.toString().trim());

        String e17 = "tagA = 'abc' and tagB = 'def'";
        query = new StringBuilder();
        alerts.parseTagQuery(e17, query);
        assertEquals("((/tagA_abc/) and (/tagB_def/))", query.toString().trim());

        String e18 = "tagA and not tagB or tagC";
        query = new StringBuilder();
        alerts.parseTagQuery(e18, query);
        assertEquals("(('tagC') or (('tagA') and (not 'tagB')))", query.toString().trim());

        String e19 = "not tagA and tagB or tagC";
        query = new StringBuilder();
        alerts.parseTagQuery(e19, query);
        assertEquals("(('tagC') or (('tagB') and (not 'tagA')))", query.toString().trim());

        String e20 = "not tagA or tagB and tagC";
        query = new StringBuilder();
        alerts.parseTagQuery(e20, query);
        assertEquals("(('tagC') and (('tagB') or (not 'tagA')))", query.toString().trim());

        String e21 = "tagA and (not tagB or tagC)";
        query = new StringBuilder();
        alerts.parseTagQuery(e21, query);
        assertEquals("(('tagA') and (('tagC') or (not 'tagB')))", query.toString().trim());

        String e22 = "tagA and (not tagB and tagC)";
        query = new StringBuilder();
        alerts.parseTagQuery(e22, query);
        assertEquals("(('tagA') and (('tagC') and (not 'tagB')))", query.toString().trim());

        String e23 = "(not tagB or tagC) and tagA";
        query = new StringBuilder();
        alerts.parseTagQuery(e23, query);
        assertEquals("(('tagA') and (('tagC') or (not 'tagB')))", query.toString().trim());

        String e24 = "(tagA and not tagB) and (not tagC or tagD)";
        query = new StringBuilder();
        alerts.parseTagQuery(e24, query);
        assertEquals("((('tagA') and (not 'tagB')) and (('tagD') or (not 'tagC')))", query.toString().trim());
    }

    @Test
    public void addAlertTagsTest() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numAlerts = 2;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        List<Alert> nonTaggedAlerts = alerts.getAlerts(tenantIds, null, null);
        assertEquals(2 * 5 * 2, nonTaggedAlerts.size());

        int count = 0;
        for (Alert alert : nonTaggedAlerts) {
            Map<String, String> tags = new HashMap<>();
            if (count < 5) {
                tags.put("xyztag1", "value" + (count % 5));
            } else if (count >= 5 && count < 10) {
                tags.put("xyztag2", "value" + (count % 5));
            } else {
                // Yes, tag3/valueX can be repeated twice
                tags.put("xyztag3", "value" + (count % 5));
            }
            alerts.addAlertTags(alert.getTenantId(), Arrays.asList(alert.getAlertId()), tags);
            count++;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTagQuery("xyztag1");

        List<Alert> tag1Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, tag1Alerts.size());

        criteria.setTagQuery("xyztag2");
        List<Alert> tag2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, tag2Alerts.size());

        criteria.setTagQuery("xyztag3");
        List<Alert> tag3Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(10, tag3Alerts.size());

        criteria.setTagQuery("xyztag1 = 'value1'");
        List<Alert> tag1Value1Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(1, tag1Value1Alerts.size());

        criteria.setTagQuery("xyztag2 = 'value1'");
        List<Alert> tag2Value1Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(1, tag2Value1Alerts.size());

        criteria.setTagQuery("xyztag3 = 'value2'");
        List<Alert> tag3Value2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(2, tag3Value2Alerts.size());

        criteria.setTagQuery("xyztag1 = 'value10'");
        List<Alert> tag1Value10Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(0, tag1Value10Alerts.size());

        criteria.setTagQuery("xyztag1 or xyztag2");
        List<Alert> tag1OrTag2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(10, tag1OrTag2Alerts.size());

        criteria.setTagQuery("xyztag1 = 'value.*'");
        List<Alert> tag1ValueAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, tag1ValueAlerts.size());

        criteria.setTagQuery("xyztag1 != 'value0'");
        List<Alert> tag1NotValue0Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(4, tag1NotValue0Alerts.size());

        criteria.setTagQuery("xyztag1 != 'value0' or xyztag2 != 'value0'");
        List<Alert> tag1NotValue0Tag2NotValue0Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(8, tag1NotValue0Tag2NotValue0Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsByTriggerId() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId("trigger0");

        List<Alert> trigger0Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(10, trigger0Alerts.size());

        criteria.setTriggerIds(Arrays.asList("trigger0", "trigger1", "trigger2"));
        List<Alert> trigger012Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(30, trigger012Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsByCTime() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStartTime(2L);
        criteria.setEndTime(2L);

        List<Alert> ctime2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, ctime2Alerts.size());

        criteria.setEndTime(null);
        List<Alert> ctimeGTE2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 4, ctimeGTE2Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsByResolvedTime() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStartResolvedTime(1L);

        // Alerts on stime 1 and 4 are RESOLVED
        List<Alert> stimeGTE2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 2, stimeGTE2Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsByAcknowledgedTime() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStartAckTime(1L);

        // Alerts on stime 2 and 5 are ACKNOWLEDGED
        List<Alert> stimeGTE2Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 2, stimeGTE2Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsByStatusTime() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStartStatusTime(5L);
        criteria.setEndStatusTime(5L);

        List<Alert> stimeGTE5Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, stimeGTE5Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsBySeverity() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setSeverities(Arrays.asList(Severity.LOW));

        // Alerts on stime 2 and 5 are severity LOW
        List<Alert> lowAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 2, lowAlerts.size());

        // Alerts on stime 3 are severity CRITICAL
        criteria.setSeverities(Arrays.asList(Severity.CRITICAL));
        List<Alert> criticalAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, criticalAlerts.size());

        // Alerts on stime 1 and 4 are severity MEDIUM
        criteria.setSeverities(Arrays.asList(Severity.MEDIUM));
        List<Alert> mediumAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 2, mediumAlerts.size());

        criteria.setSeverities(Arrays.asList(Severity.MEDIUM, Severity.CRITICAL));
        List<Alert> mediumCriticalAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 3, mediumCriticalAlerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsByStatus() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.OPEN);

        List<Alert> openAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5, openAlerts.size());

        criteria.setStatus(Alert.Status.ACKNOWLEDGED);
        List<Alert> acknowledgedAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 2, acknowledgedAlerts.size());

        criteria.setStatus(Alert.Status.RESOLVED);
        List<Alert> resolvedAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 2, resolvedAlerts.size());

        criteria.setStatusSet(Arrays.asList(Alert.Status.ACKNOWLEDGED, Alert.Status.RESOLVED));
        List<Alert> ackResolvedAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(5 * 4, ackResolvedAlerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void queryAlertsCombined() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.RESOLVED);
        criteria.setTriggerId("trigger0");
        criteria.setStartTime(3L);

        List<Alert> resolvedAlerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(1, resolvedAlerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void ackAlert() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.OPEN);

        List<Alert> openAlerts = alerts.getAlerts("tenant0", criteria, null);
        assertEquals(1, openAlerts.size());

        String alertId = openAlerts.iterator().next().getAlertId();
        alerts.ackAlerts("tenant0", Arrays.asList(alertId), "test", "ACK from ackAlert() test");

        openAlerts = alerts.getAlerts("tenant0", criteria, null);
        assertEquals(0, openAlerts.size());

        Alert ackAlert = alerts.getAlert("tenant0", alertId, false);
        assertEquals(Alert.Status.ACKNOWLEDGED, ackAlert.getStatus());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void addNote() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.OPEN);

        List<Alert> openAlerts = alerts.getAlerts("tenant0", criteria, null);
        assertEquals(1, openAlerts.size());

        String alertId = openAlerts.iterator().next().getAlertId();
        alerts.addNote("tenant0", alertId, "xyz1", "Note1");

        Alert alert = alerts.getAlert("tenant0", alertId, false);
        assertEquals(1, alert.getNotes().size());

        alerts.addNote("tenant0", alertId, "xyz2", "Note2");
        alerts.addNote("tenant0", alertId, "xyz3", "Note3");

        alert = alerts.getAlert("tenant0", alertId, false);
        assertEquals(3, alert.getNotes().size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void resolveAlert() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.OPEN);

        List<Alert> openAlerts = alerts.getAlerts("tenant0", criteria, null);
        assertEquals(1, openAlerts.size());

        String alertId = openAlerts.iterator().next().getAlertId();
        alerts.resolveAlerts("tenant0", Arrays.asList(alertId), "test", "RESOLVED from resolveAlert() test", null);

        openAlerts = alerts.getAlerts("tenant0", criteria, null);
        assertEquals(0, openAlerts.size());

        Alert resolvedAlert = alerts.getAlert("tenant0", alertId, false);
        assertEquals(Alert.Status.RESOLVED, resolvedAlert.getStatus());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void resolveAlertForTrigger() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numAlerts = 5;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        alerts.resolveAlertsForTrigger("tenant0", "trigger0", "test", "RESOLVED from resolveAlert() test", null);

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setStatus(Alert.Status.RESOLVED);

        List<Alert> resolvedAlerts = alerts.getAlerts("tenant0", criteria, null);
        assertEquals(5, resolvedAlerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void addRemoveAlertTag() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numAlerts = 1;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        List<Alert> nonTaggedAlerts = alerts.getAlerts("tenant0", null, null);
        assertEquals(1, nonTaggedAlerts.size());

        String alertId = nonTaggedAlerts.iterator().next().getAlertId();
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        tags.put("tag2", "value2");
        tags.put("tag3", "value3");
        alerts.addAlertTags("tenant0", Arrays.asList(alertId), tags);

        Alert alert = alerts.getAlert("tenant0", alertId, false);

        assertEquals(3, alert.getTags().size());

        alerts.removeAlertTags("tenant0", Arrays.asList(alertId), Arrays.asList("tag1", "tag2"));

        alert = alerts.getAlert("tenant0", alertId, false);
        assertEquals(1, alert.getTags().size());
        assertEquals("value3", alert.getTags().get("tag3"));

        deleteTestAlerts(numTenants);
    }

    @Test
    public void addEvents() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numEvents = 100;
        createTestEvents(numTenants, numTriggers, numEvents);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        assertEquals(2 * 5 * 100, alerts.getEvents(tenantIds, null, null).size());

        tenantIds.remove("tenant0");
        assertEquals(1 * 5 * 100, alerts.getEvents(tenantIds, null, null).size());

        List<Event> testEvents = alerts.getEvents(tenantIds, null, null);
        tenantIds.clear();
        Set<String> eventIds = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            Event eventX = testEvents.get(i);
            tenantIds.add(eventX.getTenantId());
            eventIds.add(eventX.getId());
        }

        EventsCriteria criteria = new EventsCriteria();
        criteria.setEventIds(eventIds);

        assertEquals(3, alerts.getEvents(tenantIds, criteria, null).size());

        deleteTestEvents(numTenants);
    }

    @Test
    public void queryEventsByTriggerId() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numEvents = 5;
        createTestEvents(numTenants, numTriggers, numEvents);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        EventsCriteria criteria = new EventsCriteria();
        criteria.setTriggerId("trigger0");

        List<Event> trigger0Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(10, trigger0Events.size());

        criteria.setTriggerIds(Arrays.asList("trigger0", "trigger1", "trigger2"));
        List<Event> trigger012Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(30, trigger012Events.size());

        deleteTestEvents(numTenants);
    }

    @Test
    public void addEventsTagsTest() throws Exception {
        int numTenants = 2;
        int numTriggers = 5;
        int numEvents = 2;
        createTestEvents(numTenants, numTriggers, numEvents);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");
        tenantIds.add("tenant1");

        List<Event> nonTaggedEvents = alerts.getEvents(tenantIds, null, null);
        assertEquals(2 * 5 * 2, nonTaggedEvents.size());

        int count = 0;
        for (Event event : nonTaggedEvents) {
            Map<String, String> tags = new HashMap<>();
            if (count < 5) {
                tags.put("tag1", "value" + (count % 5));
            } else if (count >= 5 && count < 10) {
                tags.put("tag2", "value" + (count % 5));
            } else {
                // Yes, tag3/valueX can be repeated twice
                tags.put("tag3", "value" + (count % 5));
            }
            alerts.addEventTags(event.getTenantId(), Arrays.asList(event.getId()), tags);
            count++;
        }

        EventsCriteria criteria = new EventsCriteria();
        criteria.setTagQuery("tag1");

        List<Event> tag1Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(5, tag1Events.size());

        criteria.setTagQuery("tag2");
        List<Event> tag2Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(5, tag2Events.size());

        criteria.setTagQuery("tag3");
        List<Event> tag3Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(10, tag3Events.size());

        criteria.setTagQuery("tag1 = 'value1'");
        List<Event> tag1Value1Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(1, tag1Value1Events.size());

        criteria.setTagQuery("tag2 = 'value1'");
        List<Event> tag2Value1Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(1, tag2Value1Events.size());

        criteria.setTagQuery("tag3 = 'value2'");
        List<Event> tag3Value2Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(2, tag3Value2Events.size());

        criteria.setTagQuery("tag1 = 'value10'");
        List<Event> tag1Value10Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(0, tag1Value10Events.size());

        criteria.setTagQuery("tag1 or tag2");
        List<Event> tag1OrTag2Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(10, tag1OrTag2Events.size());

        criteria.setTagQuery("tag1 = 'value.*'");
        List<Event> tag1ValueEvents = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(5, tag1ValueEvents.size());

        criteria.setTagQuery("tag1 != 'value0'");
        List<Event> tag1NotValue0Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(4, tag1NotValue0Events.size());

        criteria.setTagQuery("tag1 != 'value0' or tag2 != 'value0'");
        List<Event> tag1NotValue0Tag2NotValue0Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(8, tag1NotValue0Tag2NotValue0Events.size());

        deleteTestEvents(numTenants);
    }

    @Test
    public void queryEventsByCTime() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numEvents = 5;
        createTestEvents(numTenants, numTriggers, numEvents);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        EventsCriteria criteria = new EventsCriteria();
        criteria.setStartTime(2L);
        criteria.setEndTime(2L);

        List<Event> ctime2Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(5, ctime2Events.size());

        criteria.setEndTime(null);
        List<Event> ctimeGTE2Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(5 * 4, ctimeGTE2Events.size());

        deleteTestEvents(numTenants);
    }

    @Test
    public void queryEventsByCategory() throws Exception {
        int numTenants = 1;
        int numTriggers = 5;
        int numEvents = 5;
        createTestEvents(numTenants, numTriggers, numEvents);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        EventsCriteria criteria = new EventsCriteria();
        criteria.setCategory("category0");

        List<Event> category0Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(15, category0Events.size());

        criteria.setCategories(Arrays.asList("category0", "category1"));
        List<Event> category01Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(5 * 5, category01Events.size());

        deleteTestEvents(numTenants);
    }

    @Test
    public void addRemoveEventTag() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numEvents = 1;
        createTestEvents(numTenants, numTriggers, numEvents);

        List<Event> nonTaggedEvents = alerts.getEvents("tenant0", null, null);
        assertEquals(1, nonTaggedEvents.size());

        String eventId = nonTaggedEvents.iterator().next().getId();
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1", "value1");
        tags.put("tag2", "value2");
        tags.put("tag3", "value3");
        alerts.addEventTags("tenant0", Arrays.asList(eventId), tags);

        Event event = alerts.getEvent("tenant0", eventId, false);

        assertEquals(3, event.getTags().size());

        alerts.removeEventTags("tenant0", Arrays.asList(eventId), Arrays.asList("tag1", "tag2"));

        event = alerts.getEvent("tenant0", eventId, false);
        assertEquals(1, event.getTags().size());
        assertEquals("value3", event.getTags().get("tag3"));

        deleteTestEvents(numTenants);
    }

    @Test
    public void combinedEventsAlerts() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numEvents = 1;
        int numAlerts = 1;
        createTestEvents(numTenants, numTriggers, numEvents);
        createTestAlerts(numTenants, numTriggers, numAlerts);

        List<Event> allEvents = alerts.getEvents("tenant0", null, null);
        assertEquals(2, allEvents.size());

        List<Alert> allAlerts = alerts.getAlerts("tenant0", null, null);
        assertEquals(1, allAlerts.size());

        String alertId = allAlerts.get(0).getAlertId();
        assertEquals(alertId, alerts.getAlert("tenant0", alertId, false).getAlertId());

        assertEquals("event0", alerts.getEvent("tenant0", "event0", false).getId());
        assertNull(alerts.getAlert("tenant0", "event0", false));

        deleteTestEvents(numTenants);
        deleteTestAlerts(numTenants);
    }

    @Test
    public void alertTagWithDots() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numAlerts = 1;
        createTestAlerts(numTenants, numTriggers, numAlerts);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        List<Alert> nonTaggedAlerts = alerts.getAlerts(tenantIds, null, null);
        assertEquals(1, nonTaggedAlerts.size());

        for (Alert alert : nonTaggedAlerts) {
            Map<String, String> tags = new HashMap<>();
            tags.put("123456789.alert.tag1", "value0");
            alerts.addAlertTags(alert.getTenantId(), Arrays.asList(alert.getId()), tags);
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTagQuery("123456789.alert.tag1");

        List<Alert> tag1Alerts = alerts.getAlerts(tenantIds, criteria, null);
        assertEquals(1, tag1Alerts.size());

        deleteTestAlerts(numTenants);
    }

    @Test
    public void eventTagWithDots() throws Exception {
        int numTenants = 1;
        int numTriggers = 1;
        int numEvents = 1;
        createTestEvents(numTenants, numTriggers, numEvents);

        Set<String> tenantIds = new HashSet<>();
        tenantIds.add("tenant0");

        List<Event> nonTaggedEvents = alerts.getEvents(tenantIds, null, null);
        assertEquals(1, nonTaggedEvents.size());

        for (Event event : nonTaggedEvents) {
            Map<String, String> tags = new HashMap<>();
            tags.put("123456789.event.tag1", "value0");
            alerts.addEventTags(event.getTenantId(), Arrays.asList(event.getId()), tags);
        }

        EventsCriteria criteria = new EventsCriteria();
        criteria.setTagQuery("123456789.event.tag1");

        List<Event> tag1Events = alerts.getEvents(tenantIds, criteria, null);
        assertEquals(1, tag1Events.size());

        deleteTestEvents(numTenants);
    }
}
