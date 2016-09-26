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
package org.hawkular.alerts.actions.aerogear;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.jboss.aerogear.unifiedpush.DefaultPushSender;
import org.jboss.aerogear.unifiedpush.PushSender;
import org.jboss.aerogear.unifiedpush.message.UnifiedMessage;

/**
 * Listener for Aerogear processing.
 *
 * @author Thomas Segismont
 */
@Plugin(name = "aerogear")
public class AerogearPlugin implements ActionPluginListener {
    static final String ROOT_SERVER_URL_PROPERTY = "org.hawkular.alerts.actions.aerogear.root.server.url";
    static final String ROOT_SERVER_URL = System.getProperty(ROOT_SERVER_URL_PROPERTY);
    static final String APPLICATION_ID_PROPERTY = "org.hawkular.alerts.actions.aerogear.application.id";
    static final String APPLICATION_ID = System.getProperty(APPLICATION_ID_PROPERTY);
    static final String MASTER_SECRET_PROPERTY = "org.hawkular.alerts.actions.aerogear.master.secret";
    static final String MASTER_SECRET = System.getProperty(MASTER_SECRET_PROPERTY);

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    Map<String, String> defaultProperties = new HashMap<>();

    PushSender pushSender;

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public AerogearPlugin() {
        defaultProperties.put("alias", "Default aerogear alias");
        defaultProperties.put("description", "Default aerogear description");
        setup();
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
        if (pushSender == null) {
            msgLog.errorCannotProcessMessage("aerogear", "Plugin is not started");
            return;
        }

        try {
            UnifiedMessage.MessageBuilder alert = UnifiedMessage.withMessage().alert(prepareMessage(msg));
            if (msg.getAction().getProperties() != null) {
                String alias = msg.getAction().getProperties().get("alias");
                if (!isBlank(alias)) {
                    alert.config().criteria().aliases(alias);
                }
            }

            UnifiedMessage unifiedMessage = alert.build();
            pushSender.send(unifiedMessage);
            msgLog.infoActionReceived("aerogear", msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            msgLog.errorCannotProcessMessage("aerogear", e.getMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }
    }

    void setup() {
        if (isBlank(ROOT_SERVER_URL) || isBlank(APPLICATION_ID) || isBlank(MASTER_SECRET)) {
            String msg = "Configure " + ROOT_SERVER_URL_PROPERTY + ", " + APPLICATION_ID_PROPERTY + " and "
                    + MASTER_SECRET_PROPERTY;
            msgLog.errorCannotBeStarted("aerogear", msg);
            return;
        }
        try {
            pushSender = DefaultPushSender.withRootServerURL(ROOT_SERVER_URL).pushApplicationId(APPLICATION_ID)
                    .masterSecret(MASTER_SECRET).build();
        } catch (Exception e) {
            msgLog.errorCannotBeStarted("aerogear", e.getLocalizedMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String prepareMessage(ActionMessage msg) {
        String preparedMsg = null;
        Event event = msg.getAction() != null ? msg.getAction().getEvent() : null;

        if (event != null) {
            if (event instanceof Alert) {
                Alert alert = (Alert) event;
                preparedMsg = "Alert : " + alert.getTriggerId() + " at " + alert.getCtime() + " -- Severity: " +
                        alert.getSeverity().toString();
            } else {
                preparedMsg = "Event [" + event.getCategory() + "] " + event.getText() + " at " + event.getCtime();
            }
        } else {
            preparedMsg = "Message received without data at " + System.currentTimeMillis();
            msgLog.warnMessageReceivedWithoutPayload("aerogear");
        }
        return preparedMsg;
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
            msgLog.error("Error sending ActionResponseMessage", e);
        }
    }
}
