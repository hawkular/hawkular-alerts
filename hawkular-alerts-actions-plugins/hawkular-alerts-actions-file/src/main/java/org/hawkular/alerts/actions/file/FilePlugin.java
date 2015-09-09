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
package org.hawkular.alerts.actions.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionPlugin;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.api.model.condition.Alert;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A simple file action plugin
 *
 * @author Lucas Ponce
 */
@ActionPlugin(name = "file")
public class FilePlugin implements ActionPluginListener {
    private final MsgLogger msgLog = MsgLogger.LOGGER;

    private Map<String, String> defaultProperties = new HashMap<>();
    private ObjectMapper objectMapper;

    public FilePlugin() {
        defaultProperties.put("path",
                new File(System.getProperty("java.io.tmpdir"), "hawkular/alerts/actions/file").getAbsolutePath());
        objectMapper = new ObjectMapper();
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public void process(PluginMessage msg) throws Exception {
        if (msg == null || msg.getAction() == null || msg.getAction().getAlert() == null) {
            msgLog.warnMessageReceivedWithoutPayload("file");
        }

        String path = msg.getProperties() != null ? msg.getProperties().get("path") : null;
        path = path == null ? defaultProperties.get("path") : path;
        path = path == null ? System.getProperty("user.home") : path;

        Alert alert = msg.getAction().getAlert();
        String fileName = alert.getAlertId() + "-timestamp-" + System.currentTimeMillis() + ".txt";

        File pathFile = new File(path);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }
        File alertFile = new File(pathFile, fileName);
        if (!alertFile.exists()) {
            alertFile.createNewFile();
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(alertFile));
            String jsonAlert = objectMapper.writeValueAsString(alert);
            writer.write(jsonAlert);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        msgLog.infoActionReceived("file", msg.toString());
    }
}
