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
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hawkular.alerts.api.exception.NotFoundException;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.AvailabilityType;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class IspnBaseServiceImplTest {
    static final MsgLogger log = MsgLogging.getMsgLogger(IspnBaseServiceImplTest.class);

    static IspnActionsServiceImpl actions;
    static IspnAlertsServiceImpl alerts;
    static IspnDefinitionsServiceImpl definitions;

    protected void createTestTriggers(int numTenants, int numTriggers) throws Exception {
        int count = 0;
        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int trigger = 0; trigger < numTriggers; trigger++) {
                String triggerId = "trigger" + trigger;
                Trigger triggerX = new Trigger(tenantId, triggerId, "Trigger " + triggerId);
                String tag = "tag" + (count % 2);
                String value = "value" + (count % 4);
                triggerX.addTag(tag, value);
                count++;
                log.debugf("trigger: %s/%s tag: %s/%s", tenantId, triggerId, tag, value);
                definitions.addTrigger(tenantId, triggerX);
            }
        }
    }

    protected void deleteTestTriggers(int numTenants, int numTriggers) throws Exception {
        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int trigger = 0; trigger < numTriggers; trigger++) {
                String triggerId = "trigger" + trigger;
                definitions.removeTrigger(tenantId, triggerId);
                try {
                    definitions.getTrigger(tenantId, triggerId);
                    fail("IT should throw a NotFoundException");
                } catch (NotFoundException e) {
                    // expected
                } catch (Exception e) {
                    fail("IT should throw a NotFoundException, not " + e);
                }
                assertEquals(0, definitions.getTriggerConditions(tenantId, triggerId, null).size());
                assertEquals(0, definitions.getTriggerDampenings(tenantId, triggerId, null).size());
            }
        }
    }

    protected void createTestPluginsAndActions(int numTenants, int numPlugins, int numActions) throws Exception {
        for (int plugin = 0; plugin < numPlugins; plugin++) {
            String actionPlugin = "plugin" + plugin;
            Set<String> pluginX = new HashSet<>();
            pluginX.add("prop1");
            pluginX.add("prop2");
            pluginX.add("prop3");
            definitions.addActionPlugin(actionPlugin, pluginX);
        }

        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int plugin = 0; plugin < numPlugins; plugin++) {
                String actionPlugin = "plugin" + plugin;
                for (int action = 0; action < numActions; action++) {
                    String actionId = "action" + action;
                    ActionDefinition actionX = new ActionDefinition();
                    actionX.setTenantId(tenantId);
                    actionX.setActionPlugin(actionPlugin);
                    actionX.setActionId(actionId);
                    actionX.setProperties(new HashMap<>());
                    actionX.getProperties().put("prop1", UUID.randomUUID().toString());
                    actionX.getProperties().put("prop2", UUID.randomUUID().toString());
                    actionX.getProperties().put("prop3", UUID.randomUUID().toString());
                    definitions.addActionDefinition(tenantId, actionX);
                }
            }
        }
    }

    protected void deleteTestPluginsAndActions(int numTenants, int numPlugins, int numActions) throws Exception {
        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int plugin = 0; plugin < numPlugins; plugin++) {
                String actionPlugin = "plugin" + plugin;
                for (int action = 0; action < numActions; action++) {
                    String actionId = "action" + action;
                    definitions.removeActionDefinition(tenantId, actionPlugin, actionId);
                }
            }
        }

        for (int plugin = 0; plugin < numPlugins; plugin++) {
            String actionPlugin = "plugin" + plugin;
            definitions.removeActionPlugin(actionPlugin);
        }
    }

    protected void createTestAlerts(int numTenants, int numTriggers, int numAlerts) throws Exception {
        List<Alert> newAlerts = new ArrayList<>();
        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int trigger = 0; trigger < numTriggers; trigger++) {
                String triggerId = "trigger" + trigger;
                Trigger triggerX = new Trigger(tenantId, triggerId, "Trigger " + triggerId);
                AvailabilityCondition availability = new AvailabilityCondition(tenantId, triggerId, "Availability-" + trigger, AvailabilityCondition.Operator.DOWN);
                for (int alert = 0; alert < numAlerts; alert++) {
                    long dataTime = alert;
                    Data data = Data.forAvailability(tenantId, "Availability-" + trigger, dataTime, AvailabilityType.DOWN);
                    AvailabilityConditionEval eval = new AvailabilityConditionEval(availability, data);
                    Set<ConditionEval> evalSet = new HashSet<>();
                    evalSet.add(eval);
                    List<Set<ConditionEval>> evals = new ArrayList<>();
                    evals.add(evalSet);
                    Alert alertX = new Alert(tenantId, triggerX, evals);
                    // Hack to set up the right ctime for tests
                    alertX.setCtime(alert + 1);
                    alertX.getCurrentLifecycle().setStime(alert + 1);
                    switch (alert % 3) {
                        case 2:
                            alertX.setSeverity(Severity.CRITICAL);
                            break;
                        case 1:
                            alertX.setSeverity(Severity.LOW);
                            alertX.addLifecycle(Alert.Status.ACKNOWLEDGED, "user1", alert + 1);
                            break;
                        case 0:
                            alertX.setSeverity(Severity.MEDIUM);
                            alertX.addLifecycle(Alert.Status.RESOLVED, "user2", alert + 1);
                    }
                    newAlerts.add(alertX);
                }
            }
        }
        alerts.addAlerts(newAlerts);
    }

    protected void deleteTestAlerts(int numTenants) throws Exception {
        AlertsCriteria criteria = new AlertsCriteria();
        for (int i = 0; i < numTenants; i++) {
            alerts.deleteAlerts("tenant" + i, criteria);
        }
    }

    protected void createTestEvents(int numTenants, int numTriggers, int numEvents) throws Exception {
        List<Event> newEvents = new ArrayList<>();
        int count = 0;
        for (int tenant = 0; tenant < numTenants; tenant++) {
            String tenantId = "tenant" + tenant;
            for (int trigger = 0; trigger < numTriggers; trigger++) {
                String triggerId = "trigger" + trigger;
                Trigger triggerX = new Trigger(tenantId, triggerId, "Trigger " + triggerId);
                for (int event = 0; event < numEvents; event++) {
                    String eventId = "event" + count;
                    long eventTime = event;
                    String category = "category" + (event % 2);
                    String text = "this is text key" + (event % 2) + " for event";
                    Map<String, String> context = new HashMap<>();
                    context.put("context1", "value1");
                    Map<String, String> tags = new HashMap<>();
                    Event eventX = new Event(tenantId, eventId, eventTime, "testDataSource", "testDataId", category, text, context, tags);
                    eventX.setTrigger(triggerX);
                    newEvents.add(eventX);
                    count++;
                }
            }
        }
        alerts.addEvents(newEvents);
    }

    protected void deleteTestEvents(int numTenants) throws Exception {
        EventsCriteria criteria = new EventsCriteria();
        for (int i = 0; i < numTenants; i++) {
            alerts.deleteEvents("tenant" + i, criteria);
        }
    }

}
