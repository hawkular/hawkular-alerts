/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.actions.webhook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Store webhooks registered for this plugin.
 * It supports load and sync from file.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class WebHooks {
    private static final Logger log = Logger.getLogger(WebHooks.class);

    private static WebHooks instance = new WebHooks();

    /*
        Flag that indicates that webhooks are loaded and saved to file.
     */
    private boolean supportsFile = false;

    /*
        File from to load and store webhooks.
     */
    private String webhooksFile;

    private ObjectMapper mapper;

    /*
        Webhooks are stored with the following structure:
            - A first level map, using tenantId as key to store a list of webhooks for that tenant.
            - A webhook is a map with two entries:
               - An optional filter to differentiate for triggerId,alertId or other fields.
               - An url where to push the alert for the webhook implementation.


            {
                "tenantId1": [
                                {"filter": "triggerId=trigger1", "url": "http://webhook-url-to-callback"},
                                {"url": "http://webhook-url-to-callback"}
                              ]
                ...
            }

     */
    private Map<String, List<Map<String, String>>> webhooks;

    private WebHooks() {
        this.webhooks = new HashMap<>();
        this.mapper = new ObjectMapper();
    }


    public static synchronized void setFile(String file) {
        instance.supportsFile = true;
        instance.webhooksFile = file;
    }

    public static synchronized void releaseFile() {
        instance.supportsFile = false;
        instance.webhooksFile = null;
    }

    public static String getFile() {
        return instance.webhooksFile;
    }

    public static boolean isSupportsFile() {
        return instance.supportsFile;
    }

    public static synchronized void loadFile() throws IOException {
        File f = new File(instance.webhooksFile);
        Path path = f.toPath();
        StringBuilder fullFile = new StringBuilder();
        Files.lines(path).forEach(s -> fullFile.append(s));
        log.debug("Reading webhooks... " + fullFile.toString());
        instance.webhooks = instance.mapper.readValue(fullFile.toString(), Map.class);
    }

    public static synchronized void addWebHook(String tenantId, String filter, String url) throws IOException {
        if (!instance.webhooks.containsKey(tenantId)) {
            instance.webhooks.put(tenantId, new ArrayList<>());
        }
        List<Map<String, String>> tenantWebHooks = instance.webhooks.get(tenantId);
        Map<String, String> webhook = null;
        for (int i = 0; i < tenantWebHooks.size(); i++) {
            Map<String, String> item = tenantWebHooks.get(i);
            if (item.containsKey("url") && item.get("url").equals(url)) {
                webhook = item;
                break;
            }
        }
        if (webhook == null) {
            webhook = new HashMap<>();
            tenantWebHooks.add(webhook);
        }
        webhook.put("url", url);
        webhook.put("filter", filter);
        if (instance.supportsFile) {
            File f = new File(instance.webhooksFile);
            instance.mapper.writeValue(f, instance.webhooks);
        }
    }

    public static synchronized void removeWebHook(String tenantId, String url) throws IOException {
        if (!instance.webhooks.containsKey(tenantId)) {
            return;
        }
        List<Map<String, String>> tenantWebHooks = instance.webhooks.get(tenantId);
        for (int i = 0; i < tenantWebHooks.size(); i++) {
            Map<String, String> item = tenantWebHooks.get(i);
            if (item.containsKey("url") && item.get("url").equals(url)) {
                tenantWebHooks.remove(i);
                break;
            }
        }
        if (instance.supportsFile) {
            File f = new File(instance.webhooksFile);
            instance.mapper.writeValue(f, instance.webhooks);
        }
    }

    public static List<Map<String, String>> getWebHooks(String tenantId) {
        return instance.webhooks.get(tenantId);
    }

    public static synchronized void removeWebHooks(String tenantId) throws IOException {
        instance.webhooks.remove(tenantId);
        if (instance.supportsFile) {
            File f = new File(instance.webhooksFile);
            instance.mapper.writeValue(f, instance.webhooks);
        }
    }

    public static Map<String, List<Map<String, String>>> getAllWebHooks() {
        return Collections.unmodifiableMap(instance.webhooks);
    }

    public static void removeWebHooks() throws IOException {
        instance.webhooks.clear();
        if (instance.supportsFile) {
            File f = new File(instance.webhooksFile);
            instance.mapper.writeValue(f, instance.webhooks);
        }
    }

}
