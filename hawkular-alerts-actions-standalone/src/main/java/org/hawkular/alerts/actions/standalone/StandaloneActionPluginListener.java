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

import java.util.HashMap;
import java.util.Map;

import javax.naming.InitialContext;

import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

/**
 * Main bus listener for plugins implementation.
 *
 * @author Lucas Ponce
 */
public class StandaloneActionPluginListener implements ActionListener {
    public static final String DEFINITIONS_SERVICE = "java:app/hawkular-alerts-rest/CassDefinitionsServiceImpl";
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(StandaloneActionPluginListener.class);

    private InitialContext ctx;
    private DefinitionsService definitions;

    private Map<String, ActionPluginListener> plugins;

    public StandaloneActionPluginListener(Map<String, ActionPluginListener> plugins) {
        this.plugins = plugins;
    }

    @Override
    public void process(Action action) {
        try {
            init();
            if (ActionPlugins.getPlugins().isEmpty()) {
                msgLog.warnNoPluginsFound();
                return;
            }
            if (action == null || action.getActionPlugin() == null) {
                msgLog.warnMessageReceivedWithoutPluginInfo();
                return;
            }
            String actionPlugin = action.getActionPlugin();
            ActionPluginListener plugin = ActionPlugins.getPlugins().get(actionPlugin);
            if (plugin == null) {
                log.debug("Received action [" + actionPlugin +
                        "] but no ActionPluginListener found on this deployment");
                return;
            }
            if (definitions != null) {
                Map<String, String> properties = definitions.getAction(action.getTenantId(), action.getActionPlugin(),
                        action.getActionId());
                Map<String, String> defaultProperties = definitions.getDefaultActionPlugin(action.getActionPlugin());
                Map<String, String> mixedProps = mixProperties(properties, defaultProperties);

                PluginMessage pluginMessage = new StandalonePluginMessage(action, mixedProps);
                plugin.process(pluginMessage);
            } else {
                msgLog.warnCannotAccessToDefinitionsService();
            }
        } catch (Exception e) {
            log.debug("Error processing action: " + action.getActionPlugin(), e);
            msgLog.errorProcessingAction(e.getMessage());
        }

    }

    private void init() throws Exception {
        if (ctx == null) {
            ctx = new InitialContext();
        }
        if (definitions == null) {
            definitions = (DefinitionsService) ctx.lookup(DEFINITIONS_SERVICE);
        }
    }

    private Map<String, String> mixProperties(Map<String, String> props, Map<String, String> defProps) {
        Map<String, String> mixed = new HashMap<>();
        if (props != null) {
            mixed.putAll(props);
        }
        if (defProps != null) {
            for (String defKey : defProps.keySet()) {
                mixed.putIfAbsent(defKey, defProps.get(defKey));
            }
        }
        return mixed;
    }
}
