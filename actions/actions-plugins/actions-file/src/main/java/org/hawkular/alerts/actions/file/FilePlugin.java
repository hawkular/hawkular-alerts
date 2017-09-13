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
package org.hawkular.alerts.actions.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A simple file action plugin
 *
 * @author Lucas Ponce
 */
@Plugin(name = "file")
public class FilePlugin implements ActionPluginListener {
    private final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, FilePlugin.class);

    private Map<String, String> defaultProperties = new HashMap<>();
    private ObjectMapper objectMapper;

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

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
    public void process(ActionMessage msg) throws Exception {
        if (msg == null || msg.getAction() == null || msg.getAction().getEvent() == null) {
            log.warnMessageReceivedWithoutPayload("file");
        }

        String path = msg.getAction().getProperties() != null ? msg.getAction().getProperties().get("path") : null;
        path = path == null ? defaultProperties.get("path") : path;
        path = path == null ? System.getProperty("user.home") : path;

        Event event = msg.getAction() != null ? msg.getAction().getEvent() : null;
        String fileName = event.getId() + "-timestamp-" + System.currentTimeMillis() + ".txt";

        BufferedWriter writer = null;
        try {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                pathFile.mkdirs();
            }
            File alertFile = new File(pathFile, fileName);
            if (!alertFile.exists()) {
                alertFile.createNewFile();
            }

            writer = new BufferedWriter(new FileWriter(alertFile));
            String jsonEvent = objectMapper.writeValueAsString(event);
            writer.write(jsonEvent);
            log.infoActionReceived("file", msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            log.errorCannotProcessMessage("file", e.getMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private void sendResult(Action action) {
        if (sender == null) {
            throw new IllegalStateException("ActionPluginSender is not present in the plugin");
        }
        if (action == null) {
            throw new IllegalStateException("Action to update result must be not null");
        }
        ActionResponseMessage newMessage = sender.createMessage(ActionResponseMessage.Operation.RESULT);
        newMessage.getPayload().put("action", JsonUtil.toJson(action));
        try {
            sender.send(newMessage);
        } catch (Exception e) {
            log.error("Error sending ActionResponseMessage", e);
        }
    }

}
