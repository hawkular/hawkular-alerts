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
package org.hawkular.alerts.actions.sms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.MessageFactory;
import com.twilio.sdk.resource.instance.Account;

/**
 * An example of listener for sms processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 * @author Thomas Segismont
 */
@Plugin(name = "sms")
public class SmsPlugin implements ActionPluginListener {
    static final String ACCOUNT_SID_PROPERTY = "org.hawkular.actions.sms.sid";
    static final String ACCOUNT_SID = System.getProperty(ACCOUNT_SID_PROPERTY);
    static final String AUTH_TOKEN_PROPERTY = "org.hawkular.actions.sms.token";
    static final String AUTH_TOKEN = System.getProperty(AUTH_TOKEN_PROPERTY);
    static final String FROM_PROPERTY = "org.hawkular.actions.sms.from";
    static final String FROM = System.getProperty(FROM_PROPERTY);

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    Map<String, String> defaultProperties = new HashMap<>();

    MessageFactory messageFactory;

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public SmsPlugin() {
        defaultProperties.put("phone", "+1555123456");
        defaultProperties.put("description", "Default non-valid phone");
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
        if (messageFactory == null) {
            msgLog.errorCannotProcessMessage("sms", "Plugin is not started");
            return;
        }
        Map<String, String> properties = msg.getAction().getProperties();
        if (properties == null || properties.isEmpty()) {
            msgLog.errorCannotProcessMessage("sms", "Missing message properties");
            return;
        }
        String to = properties.get("phone");
        if (StringUtils.isBlank(to)) {
            msgLog.errorCannotProcessMessage("sms", "Missing recipient");
            return;
        }
        List<NameValuePair> params = new ArrayList<>(3);
        params.add(new BasicNameValuePair("To", to));
        params.add(new BasicNameValuePair("From", FROM));
        params.add(new BasicNameValuePair("Body", prepareMessage(msg)));

        try {
            messageFactory.create(params);
        } catch (TwilioRestException e) {
            msgLog.errorCannotProcessMessage("sms", e.getLocalizedMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }

        msgLog.infoActionReceived("sms", msg.toString());
        Action successAction = msg.getAction();
        successAction.setResult(MESSAGE_PROCESSED);
        sendResult(successAction);
    }

    void setup() {
        if (StringUtils.isBlank(ACCOUNT_SID) || StringUtils.isBlank(AUTH_TOKEN)) {
            String msg = "Configure " + ACCOUNT_SID_PROPERTY + " and " + AUTH_TOKEN_PROPERTY;
            msgLog.errorCannotBeStarted("sms", msg);
            return;
        }
        try {
            TwilioRestClient client = new TwilioRestClient(ACCOUNT_SID, AUTH_TOKEN);
            Account account = client.getAccount();
            messageFactory = account.getMessageFactory();
        } catch (Exception e) {
            msgLog.errorCannotBeStarted("sms", e.getLocalizedMessage());
        }
    }

    private String prepareMessage(ActionMessage msg) {
        String preparedMsg = null;
        if (msg.getAction() != null && msg.getAction().getAlert() != null) {
            Alert alert = msg.getAction().getAlert();
            if (alert != null) {
                preparedMsg = "Alert : " + alert.getTriggerId() + " at " + alert.getCtime() + " -- Severity: " +
                        alert.getSeverity().toString();
            } else {
                preparedMsg = "Message received without data at " + System.currentTimeMillis();
                msgLog.warnMessageReceivedWithoutPayload("pagerduty");
            }
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
