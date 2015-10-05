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
package org.hawkular.alerts.actions.bus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.jboss.vfs.VirtualFile;

/**
 * Helper class to find the classes annotated with ActionPlugin and instantiate them.
 *
 * @author Lucas Ponce
 */
public class ActionPlugins {
    private static ActionPlugins instance;
    private Map<String, ActionPluginListener> plugins;
    private Map<String, ActionPluginSender> senders;

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
            URL webInfUrl = getWebInfUrl();
            List<Class> pluginClasses = findAnnotationInClasses(webInfUrl, Plugin.class);
            for (Class pluginClass : pluginClasses) {
                Annotation actionPlugin = pluginClass.getDeclaredAnnotation(Plugin.class);
                if (actionPlugin instanceof Plugin) {
                    String name = ((Plugin) actionPlugin).name();
                    Object newInstance = pluginClass.newInstance();
                    if (newInstance instanceof ActionPluginListener) {
                        ActionPluginListener pluginInstance = (ActionPluginListener)newInstance;
                        injectActionPluginSender(name, pluginInstance);
                        plugins.put(name, pluginInstance);
                    } else {
                        throw new IllegalStateException("Plugin [" + name + "] is not instance of " +
                                "ActionPluginListener");
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private URL getWebInfUrl() throws Exception {
        Enumeration<URL> allUrls = Thread.currentThread().getContextClassLoader().getResources("");
        while (allUrls != null && allUrls.hasMoreElements()) {
            URL url = allUrls.nextElement();
            if (url.toExternalForm().contains("WEB-INF/classes")) {
                return url;
            }
        }
        return null;
    }

    private List<Class> findAnnotationInClasses(URL url, Class annotation) throws Exception {
        if (url == null || annotation == null) {
            throw new IllegalArgumentException("url or annotation must be not null");
        }
        List<Class> plugins = new ArrayList<>();
        URLConnection conn = url.openConnection();
        VirtualFile root = (VirtualFile)conn.getContent();
        List<VirtualFile> children = root.getChildrenRecursively();
        for (VirtualFile vf : children) {
            String vfName = vf.toURI().toString();
            if (vfName.endsWith(".class")) {
                int startName = vfName.indexOf("classes/") + 8;
                int stopName = vfName.indexOf(".class");
                String className = vfName.substring(startName, stopName).replace("/", ".");
                Class clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
                if (clazz.isAnnotationPresent(annotation)) {
                    plugins.add(clazz);
                }
            }
        }
        return plugins;
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
            ActionPluginSender busSender = new BusActionPluginSender(actionPlugin);
            sender.setAccessible(true);
            sender.set(pluginInstance, busSender);
            senders.put(actionPlugin, busSender);
        }
    }

}
