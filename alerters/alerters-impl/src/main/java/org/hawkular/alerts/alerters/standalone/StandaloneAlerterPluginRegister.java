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
package org.hawkular.alerts.alerters.standalone;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.alerters.api.AlerterPlugin;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StandaloneAlerterPluginRegister {
    private static final MsgLogger log = MsgLogging.getMsgLogger(StandaloneAlerterPluginRegister.class);

    private static StandaloneAlerterPluginRegister instance;
    private static ExecutorService executor;

    Map<String, AlerterPlugin> plugins;

    private StandaloneAlerterPluginRegister() {
        init();
    }

    public static void setExecutor(ExecutorService executor) {
        StandaloneAlerterPluginRegister.executor = executor;
    }

    public void init() {
        plugins = AlerterPlugins.getPlugins();
        log.info("Alerter Plugins load finished");
    }

    public static synchronized void start() {
        if (instance == null) {
            instance = new StandaloneAlerterPluginRegister();
        }
    }

    public static synchronized void stop() {
        if (instance != null && instance.plugins != null) {
            instance.plugins.entrySet().stream().forEach(pluginEntry -> {
                log.infof("Stopping Alerter %s", pluginEntry.getKey());
                pluginEntry.getValue().stop();
            });
        }
        instance = null;
    }

}
