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
package org.hawkular.alerts.actions.webhook;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.Global;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.jboss.logging.Logger;

/**
 * An example of listener for webhook gateway processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Global
@Plugin(name = "webhook")
public class WebHookPlugin implements ActionPluginListener {
    public static final String FILE_PROPERTY = "org.hawkular.alerts.actions.webhooks.file";
    private static final String TRIGGER_ID = "triggerId";
    private static final String ALERT_ID = "alertId";
    private static final String STATUS = "status";
    private static final String SEVERITY = "severity";
    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private static final Logger log = Logger.getLogger(WebHookPlugin.class);
    Map<String, String> defaultProperties = new HashMap<>();

    public WebHookPlugin() {
        String webHooksFile = System.getProperty(FILE_PROPERTY);
        if (webHooksFile != null) {
            WebHooks.setFile(webHooksFile);
            File f = new File(webHooksFile);
            if (!f.exists()) {
                log.debugf("WebHooks file %s doesn't exist", webHooksFile);
            } else {
                try {
                    WebHooks.loadFile();
                    log.debugf("WebHooks file loaded.");
                } catch (IOException e) {
                    msgLog.warn(e.toString(), e);
                }
            }
        }
        if (WebHooks.isSupportsFile()) {
            defaultProperties.put("usingWebHooksFile", "true");
            defaultProperties.put("webHooksFile", WebHooks.getFile());
        } else {
            defaultProperties.put("usingWebHooksFile", "false");
        }
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
        String tenantId = receivedAction.getTenantId();
        List<Map<String,String>> webhooks = WebHooks.getWebHooks(tenantId);
        if (webhooks == null) {
            log.debugf("Webhook received a message but there are not webhooks configured");
            return;
        }
        for (Map<String, String> webhook : webhooks) {
            if (checkFilter(receivedAction, webhook.get("filter"))) {
                try {
                    invokeWebhook(receivedAction, webhook.get("url"));
                } catch (IOException e) {
                    msgLog.errorCannotProcessMessage("webhook",
                            "Webhook with url " + webhook.get("url") + " cannot be invoked");
                }
            }
        }
    }

    /**
     * Implement a filter for actions.
     * Filter is a comma separated list of field=value to filter with.
     * Filter is a basic startsWith() match.
     * More than one filter is considered as an AND filtered.
     * Filters on non supported fields are just ignored.
     *
     * Supported fields:
     * - Action.alert.triggerId
     * - Action.alert.alertId
     * - Action.alert.status
     * - Action.alert.severity
     *
     * @param action to be filtered
     * @param filter comma separated list
     * @return filter result
     */
    public boolean checkFilter(Action action, String filter) {
        if (action == null || action.getEvent() == null || filter == null || filter.isEmpty()) {
            return true;
        }
        if (!(action.getEvent() instanceof Alert)) {
            return true;
        }
        Alert alert = (Alert)action.getEvent();
        String[] filters = filter.split(",");
        for (String f : filters) {
            String[] filterDetails = f.split("=");
            if (filterDetails.length != 2) {
                continue;
            }
            String key = filterDetails[0];
            String value = filterDetails[1];
            if (!TRIGGER_ID.equals(key) && !ALERT_ID.equals(key) && !SEVERITY.equals(key) && !STATUS.equals(key)) {
                continue;
            }
            if (TRIGGER_ID.equals(key) && !alert.getTriggerId().equals(value)) {
                return false;
            } else if (ALERT_ID.equals(key) && !alert.getAlertId().equals(value)) {
                return false;
            } else if (STATUS.equals(key) && !alert.getStatus().name().equals(value)) {
                return false;
            } else if (SEVERITY.equals(key) && !alert.getSeverity().name().equals(value)) {
                return false;
            }
        }
        return true;
    }

    public void invokeWebhook(Action action, String url) throws IOException {
        String jsonAction = JsonUtil.toJson(action);
        URL webHookUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection)webHookUrl.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod(POST);
        conn.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);

        OutputStream os = conn.getOutputStream();
        os.write(jsonAction.getBytes());
        os.flush();

        log.debugf("Webhook for %s . Request code: %s ", url, conn.getResponseCode());

        conn.disconnect();
    }
}
