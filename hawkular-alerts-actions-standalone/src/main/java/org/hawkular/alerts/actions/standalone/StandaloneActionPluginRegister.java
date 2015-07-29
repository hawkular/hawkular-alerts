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
package org.hawkular.alerts.actions.standalone;

import java.util.Map;

import java.util.Set;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

/**
 * Main bus register for plugins implementations
 *
 * @author Lucas Ponce
 */
@Startup
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class StandaloneActionPluginRegister {
    public static final String DEFINITIONS_SERVICE = "java:app/hawkular-alerts-rest/CassDefinitionsServiceImpl";
    public static final String ACTIONS_SERVICE = "java:app/hawkular-alerts-rest/MemActionsServiceImpl";
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(StandaloneActionPluginRegister.class);

    DefinitionsService definitions;
    ActionsService actions;

    @PostConstruct
    public void init() {
        try {
            InitialContext ctx = new InitialContext();
            definitions = (DefinitionsService)ctx.lookup(DEFINITIONS_SERVICE);
            actions = (ActionsService)ctx.lookup(ACTIONS_SERVICE);
        } catch (NamingException e) {
            msgLog.error("Cannot access to JNDI context", e);
        }

        Map<String, ActionPluginListener> plugins = ActionPlugins.getPlugins();
        for (String actionPlugin : plugins.keySet()) {
            ActionPluginListener actionPluginListener = plugins.get(actionPlugin);
            Set<String> properties = actionPluginListener.getProperties();
            Map<String, String> defaultProperties = actionPluginListener.getDefaultProperties();
            try {
                if (defaultProperties != null && !defaultProperties.isEmpty() ) {
                    definitions.addActionPlugin(actionPlugin, defaultProperties);
                } else {
                    definitions.addActionPlugin(actionPlugin, properties);
                }
                ActionListener actionListener = new StandaloneActionPluginListener(ActionPlugins.getPlugins());
                actions.addListener(actionListener);
                msgLog.infoActionPluginRegistration(actionPlugin);
            } catch (Exception e) {
                msgLog.errorCannotRegisterPlugin(actionPlugin, e.getMessage());
            }
        }
    }
}
