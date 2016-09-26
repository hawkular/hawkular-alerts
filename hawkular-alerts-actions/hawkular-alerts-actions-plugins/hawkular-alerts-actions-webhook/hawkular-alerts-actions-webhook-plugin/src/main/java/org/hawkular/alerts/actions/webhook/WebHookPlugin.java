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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
import org.jboss.logging.Logger;

/**
 * An example of listener for basic webhook processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Plugin(name = "webhook")
public class WebHookPlugin implements ActionPluginListener {

    private static final String DEFAULT_URL = "http://localhost:8080/hawkular/actions/webhook/ping";
    private static final String DEFAULT_METHOD = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private static final Logger log = Logger.getLogger(WebHookPlugin.class);
    Map<String, String> defaultProperties = new HashMap<>();

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public WebHookPlugin() {
        defaultProperties.put("url", DEFAULT_URL);
        defaultProperties.put("method", DEFAULT_METHOD);
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
        msgLog.infoActionReceived("webhook", msg.toString());
        Action receivedAction = msg.getAction();
        try {
            invokeWebhook(receivedAction);
            receivedAction.setResult(MESSAGE_PROCESSED);
        } catch (Exception e) {
            msgLog.errorCannotProcessMessage("webhook", e.getMessage());
            receivedAction.setResult(MESSAGE_FAILED);
        }
        sendResult(receivedAction);
    }

    public void invokeWebhook(Action action) throws Exception {

        if (action.getProperties() == null) {
            throw new IllegalArgumentException("Received action without properties");
        }
        String url = isEmpty(action.getProperties().get("url")) ? DEFAULT_URL : action.getProperties().get("url");
        String method = isEmpty(action.getProperties().get("method")) ? DEFAULT_METHOD :
                action.getProperties().get("method");

        String jsonAction = JsonUtil.toJson(action);
        URL webHookUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection)webHookUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(method);
        conn.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);

        OutputStream os = conn.getOutputStream();
        os.write(jsonAction.getBytes());
        os.flush();

        if (log.isDebugEnabled()) {
            log.debug("Webhook for " + url + " . Request code: " + conn.getResponseCode());
        }

        conn.disconnect();
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

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

}
