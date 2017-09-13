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

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * Helper class to find the classes annotated with ActionPlugin and instantiate them.
 *
 * @author Lucas Ponce
 */
public class ActionPlugins {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ActionPlugins.class);
    private ActionsService actions;
    private static ActionPlugins instance;
    private Map<String, ActionPluginListener> plugins;
    private Map<String, ActionPluginSender> senders;
    private ClassLoader cl = Thread.currentThread().getContextClassLoader();

    public static synchronized Map<String, ActionPluginListener> getPlugins() {
        if (instance == null) {
            instance = new ActionPlugins();
        }
        return Collections.unmodifiableMap(instance.plugins);
    }

    public static synchronized Map<String, ActionPluginSender> getSenders() {
        if (instance == null) {
            instance = new ActionPlugins();
        }
        return Collections.unmodifiableMap(instance.senders);
    }

    private ActionPlugins() {
        try {
            plugins = new HashMap<>();
            senders = new HashMap<>();
            actions = StandaloneAlerts.getActionsService();
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
                            log.debugf("Loading class %s", className);
                            Class clazz = cl.loadClass(className);
                            if (clazz.isAnnotationPresent(Plugin.class)) {
                                Plugin plugin = (Plugin)clazz.getAnnotation(Plugin.class);
                                String name = plugin.name();
                                Object newInstance = clazz.newInstance();
                                log.infof("Scanning %s", clazz.getName());
                                if (newInstance instanceof ActionPluginListener) {
                                    ActionPluginListener pluginInstance = (ActionPluginListener)newInstance;
                                    injectActionPluginSender(name, pluginInstance);
                                    plugins.put(name, pluginInstance);
                                } else {
                                    throw new IllegalStateException("Plugin [" + name + "] is not instance of " +
                                            "ActionPluginListener");
                                }
                            }
                        } catch (Exception e) {
                            log.errorf("Error loading ActionPlugin %s. Reason: %s", className, e.toString());
                            System.exit(1);
                        }
                    }
                }
            }
        }
    }

    /*
        Search and inject ActionPluginSender inside ActionPluginListener
     */
    private void injectActionPluginSender(String actionPlugin, ActionPluginListener pluginInstance) throws Exception {
        if (pluginInstance == null) {
            throw new IllegalArgumentException("pluginInstance must be not null");
        }
        Field[] fields = pluginInstance.getClass().getDeclaredFields();
        Field sender = null;
        for (Field field : fields) {
            if (field.isAnnotationPresent(Sender.class) &&
                    field.getType().isAssignableFrom(ActionPluginSender.class)) {
                sender = field;
                break;
            }
        }
        if (sender != null) {
            ActionPluginSender standaloneSender = new StandaloneActionPluginSender(actions);
            sender.setAccessible(true);
            sender.set(pluginInstance, standaloneSender);
            senders.put(actionPlugin, standaloneSender);
        }
    }
}
