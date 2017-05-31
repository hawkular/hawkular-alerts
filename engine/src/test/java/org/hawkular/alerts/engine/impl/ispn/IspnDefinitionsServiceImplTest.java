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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hawkular.alerts.api.exception.FoundException;
import org.hawkular.alerts.api.exception.NotFoundException;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnDefinitionsServiceImplTest {
    static final String TENANT = "testTenant";

    static IspnDefinitionsServiceImpl definitions;

    @BeforeClass
    public static void init() {
        System.setProperty("hawkular.data", "./target/ispn");
        definitions = new IspnDefinitionsServiceImpl();
        definitions.init();
    }

    @Test
    public void addGetUpdateRemoveActionPluginTest() throws Exception {
        Set<String> props = new HashSet<>();
        props.add("prop1");
        props.add("prop2");
        props.add("prop3");
        definitions.addActionPlugin("plugin1", props);
        assertNotNull(definitions.getActionPlugin("plugin1"));

        try {
            definitions.addActionPlugin("plugin1", props);
            fail("It should throw a FoundException");
        } catch (FoundException e) {
            // Expected
        }

        Set<String> updated = new HashSet<>();
        updated.add("prop4");
        updated.add("prop5");
        updated.add("prop6");
        definitions.updateActionPlugin("plugin1", updated);
        assertEquals(updated, definitions.getActionPlugin("plugin1"));

        try {
            definitions.updateActionPlugin("pluginX", updated);
        } catch (NotFoundException e) {
            // Expected
        }

        definitions.removeActionPlugin("plugin1");
        assertNull(definitions.getActionPlugin("plugin1"));
    }

    @Test
    public void addGetUpdateRemoveActionDefinitionTest() throws Exception {
        Set<String> props = new HashSet<>();
        props.add("prop1");
        props.add("prop2");
        props.add("prop3");
        definitions.addActionPlugin("plugin2", props);

        ActionDefinition actionDefinition = new ActionDefinition();
        actionDefinition.setTenantId(TENANT);
        actionDefinition.setActionPlugin("plugin2");
        actionDefinition.setActionId("action1");
        actionDefinition.setProperties(new HashMap<>());
        actionDefinition.getProperties().put("prop1", "value1");
        actionDefinition.getProperties().put("prop2", "value2");
        actionDefinition.getProperties().put("prop3", "value3");

        definitions.addActionDefinition(TENANT, actionDefinition);
        assertEquals(actionDefinition, definitions.getActionDefinition(TENANT, "plugin2", "action1"));

        try {
            definitions.addActionDefinition(TENANT, actionDefinition);
        } catch (FoundException e) {
            // Expected
        }

        Map<String, String> updated = new HashMap<>();
        updated.put("prop1", "value1-updated");
        updated.put("prop2", "value2-updated");
        updated.put("prop3", "value-updated");
        actionDefinition.setProperties(updated);
        definitions.updateActionDefinition(TENANT, actionDefinition);

        assertEquals(updated, definitions.getActionDefinition(TENANT, "plugin2", "action1").getProperties());

        Map<String, String> wrong = new HashMap<>();
        wrong.put("prop4", "prop4 doesnt exist in plugin1");
        actionDefinition.setProperties(wrong);

        try {
            definitions.updateActionDefinition(TENANT, actionDefinition);
        } catch (IllegalArgumentException e) {
            // Expected
        }

        try {
            actionDefinition.setActionId("action2");
            actionDefinition.setProperties(updated);
            definitions.updateActionDefinition(TENANT, actionDefinition);
        } catch (NotFoundException e) {
            // Expected
        }

        definitions.removeActionDefinition(TENANT, "plugin2", "action1");
        assertNull(definitions.getActionDefinition(TENANT, "plugin2", "action1"));

        definitions.removeActionPlugin("plugin2");
        assertNull(definitions.getActionPlugin("plugin2"));
    }

    void createTestPluginsAndActions(int numTenants, int numPlugins, int numActions) throws Exception {
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

    void deleteTestPluginsAndActions(int numTenants, int numPlugins, int numActions) throws Exception {
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

    @Test
    public void getActionDefinitions() throws Exception {
        int numTenants = 2;
        int numPlugins = 2;
        int numActions = 4;
        createTestPluginsAndActions(numTenants, numPlugins, numActions);

        assertEquals(2 * 2 * 4, definitions.getAllActionDefinitions().size());

        Map<String, Map<String, Set<String>>> actionIds = definitions.getAllActionDefinitionIds();
        assertEquals(numTenants, actionIds.keySet().size());
        assertEquals(numPlugins, actionIds.get("tenant0").keySet().size());
        assertEquals(numActions, actionIds.get("tenant0").get("plugin0").size());

        Map<String, Set<String>> actionIdsByTenant = definitions.getActionDefinitionIds("tenant1");
        assertEquals(numPlugins, actionIdsByTenant.keySet().size());

        Collection<String> actionIdsByTenantAndPlugin = definitions.getActionDefinitionIds("tenant1", "plugin1");
        assertEquals(numActions, actionIdsByTenantAndPlugin.size());

        deleteTestPluginsAndActions(numTenants, numPlugins, numActions);
    }
}
