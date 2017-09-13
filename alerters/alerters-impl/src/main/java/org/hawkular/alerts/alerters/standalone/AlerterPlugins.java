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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkular.alerts.alerters.api.Alerter;
import org.hawkular.alerts.alerters.api.AlerterPlugin;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * Helper class to find the classes annotated with ActionPlugin and instantiate them.
 *
 * @author Lucas Ponce
 */
public class AlerterPlugins {
    private static final MsgLogger log = MsgLogging.getMsgLogger(AlerterPlugins.class);
    private static AlerterPlugins instance;
    private Map<String, AlerterPlugin> plugins;
    private ClassLoader cl = Thread.currentThread().getContextClassLoader();

    DefinitionsService definitions;
    AlertsService alerts;
    ExecutorService executor;

    public static synchronized Map<String, AlerterPlugin> getPlugins() {
        if (instance == null) {
            instance = new AlerterPlugins();
        }
        return Collections.unmodifiableMap(instance.plugins);
    }

    private AlerterPlugins() {
        try {
            plugins = new HashMap<>();
            definitions = StandaloneAlerts.getDefinitionsService();
            alerts = StandaloneAlerts.getAlertsService();
            executor = StandaloneAlerts.getExecutor();
            scan();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void scan() throws IOException {
        String[] classpath = System.getProperty("java.class.path").split(":");
        for (int i=0; i<classpath.length; i++) {
            if (classpath[i].contains("hawkular") && classpath[i].endsWith("jar")) {
                ZipInputStream zip = new ZipInputStream(new FileInputStream(classpath[i]));
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!entry.isDirectory() && entry.getName().contains("hawkular") && entry.getName().endsWith(".class")) {
                        String className = entry.getName().replace('/', '.'); // including ".class"
                        className = className.substring(0, className.length() - 6);
                        try {
                            Class clazz = cl.loadClass(className);
                            if (clazz.isAnnotationPresent(Alerter.class)) {
                                Alerter alerter = (Alerter)clazz.getAnnotation(Alerter.class);
                                String name = alerter.name();
                                Object newInstance = clazz.newInstance();
                                log.infof("Scanning %s", clazz.getName());
                                if (newInstance instanceof AlerterPlugin) {
                                    AlerterPlugin pluginInstance = (AlerterPlugin)newInstance;
                                    pluginInstance.init(definitions, alerts, executor);
                                    plugins.put(name, pluginInstance);
                                } else {
                                    throw new IllegalStateException("Alerter [" + name + "] is not instance of " +
                                            "AlerterPlugin");
                                }
                            }
                        } catch (Exception e) {
                            log.errorf("Error loading AlerterPlugin %s. Reason: %s", className, e.toString());
                            System.exit(1);
                        }
                    }
                }
            }
        }
    }
}
