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
package org.hawkular.alerts.actions.standalone;

import static org.hawkular.alerts.actions.standalone.ServiceNames.Service.ACTIONS_SERVICE;
import static org.hawkular.alerts.actions.standalone.ServiceNames.Service.DEFINITIONS_SERVICE;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.api.exception.FoundException;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

/**
 * Main standalone register for plugins implementations
 *
 * @author Lucas Ponce
 */
@Startup
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class StandaloneActionPluginRegister {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(StandaloneActionPluginRegister.class);

    DefinitionsService definitions;
    ActionsService actions;

    Set<ActionListener> actionListeners = new HashSet<>();

    @PostConstruct
    public void init() {
        try {
            InitialContext ctx = new InitialContext();
            definitions = (DefinitionsService)ctx.lookup(ServiceNames.getServiceName(DEFINITIONS_SERVICE));
            actions = (ActionsService)ctx.lookup(ServiceNames.getServiceName(ACTIONS_SERVICE));
        } catch (NamingException e) {
            msgLog.error("Cannot access to JNDI context", e);
        }
        Map<String, ActionPluginListener> plugins = ActionPlugins.getPlugins();
        for (String actionPlugin : plugins.keySet()) {
            ActionPluginListener actionPluginListener = plugins.get(actionPlugin);
            Set<String> properties = actionPluginListener.getProperties();
            Map<String, String> defaultProperties = actionPluginListener.getDefaultProperties();

            try {
                try {
                    if (defaultProperties != null && !defaultProperties.isEmpty() ) {
                        definitions.addActionPlugin(actionPlugin, defaultProperties);
                    } else {
                        definitions.addActionPlugin(actionPlugin, properties);
                    }
                } catch (FoundException e) {
                    if (defaultProperties != null && !defaultProperties.isEmpty() ) {
                        definitions.updateActionPlugin(actionPlugin, defaultProperties);
                    } else {
                        definitions.updateActionPlugin(actionPlugin, properties);
                    }
                }
                ActionListener actionListener = new StandaloneActionPluginListener(ActionPlugins.getPlugins());
                actions.addListener(actionListener);
                actionListeners.add(actionListener);
                msgLog.infoActionPluginRegistration(actionPlugin);
            } catch (Exception e) {
                msgLog.errorCannotRegisterPlugin(actionPlugin, e.getMessage());
            }
        }
    }

    @PreDestroy
    public void close() {
        actionListeners.stream().forEach(a -> {
            try {
                if (a instanceof StandaloneActionPluginListener) {
                    ((StandaloneActionPluginListener)a).close();
                }
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
            }
        });
    }
}
