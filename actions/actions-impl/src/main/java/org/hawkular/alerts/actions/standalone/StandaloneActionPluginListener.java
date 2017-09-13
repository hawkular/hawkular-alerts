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

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * Main standalone listener for plugins implementation.
 *
 * @author Lucas Ponce
 */
public class StandaloneActionPluginListener implements ActionListener {
    private static final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, StandaloneActionPluginRegister.class);

    private DefinitionsService definitions;

    ExecutorService executorService;

    private Map<String, ActionPluginListener> plugins;

    public StandaloneActionPluginListener(Map<String, ActionPluginListener> plugins, ExecutorService executorService) {
        this.plugins = plugins;
        if (executorService == null) {
            throw new IllegalArgumentException("ExecutorService must be non null");
        }
        this.executorService = executorService;
    }

    @Override
    public void process(Action action) {
        try {
            definitions = StandaloneAlerts.getDefinitionsService();
            if (plugins.isEmpty()) {
                log.warnNoPluginsFound();
                return;
            }
            if (action == null || action.getActionPlugin() == null) {
                log.warnMessageReceivedWithoutPluginInfo();
                return;
            }
            String actionPlugin = action.getActionPlugin();
            final ActionPluginListener plugin = plugins.get(actionPlugin);
            if (plugin == null) {
                if (log.isDebugEnabled()) {
                    log.debugf("Received action [%s] but no ActionPluginListener found on this deployment", actionPlugin);
                }
                return;
            }

            ActionMessage pluginMessage = new StandaloneActionMessage(action);
            if (plugin != null) {
                executorService.execute(() -> {
                    try {
                        plugin.process(pluginMessage);
                    } catch (Exception e) {
                        log.debugf("Error processing action: %s", action.getActionPlugin(), e);
                        log.errorProcessingAction(e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.debugf("Error processing action: %s", action.getActionPlugin(), e);
            log.errorProcessingAction(e.getMessage());
        }
    }

    public void close() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public String toString() {
        return new StringBuilder("StandaloneActionPluginListener - [")
                .append(String.join(",", plugins.keySet()))
                .append("] plugins")
                .toString();
    }
}
