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
package org.hawkular.actions.sms.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.MessageFactory;
import com.twilio.sdk.resource.instance.Account;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;

/**
 * An example of listener for sms processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsTopic"),
    @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "actionPlugin like 'sms'") })
public class SmsListener extends BasicMessageListener<ActionMessage> {
    static final String ACCOUNT_SID_PROPERTY = "org.hawkular.actions.sms.sid";
    static final String ACCOUNT_SID = System.getProperty(ACCOUNT_SID_PROPERTY);
    static final String AUTH_TOKEN_PROPERTY = "org.hawkular.actions.sms.token";
    static final String AUTH_TOKEN = System.getProperty(AUTH_TOKEN_PROPERTY);
    static final String FROM_PROPERTY = "org.hawkular.actions.sms.from";
    static final String FROM = System.getProperty(FROM_PROPERTY);

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    MessageFactory messageFactory;

    @PostConstruct
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

    protected void onBasicMessage(ActionMessage msg) {
        msgLog.infoActionReceived("sms", msg.toString());

        if (messageFactory == null) {
            msgLog.errorCannotSendMessage("sms", "Plugin is not started");
        }

        Map<String, String> properties = msg.getProperties();
        if (properties == null || properties.isEmpty()) {
            msgLog.errorCannotSendMessage("sms", "Missing message properties");
            return;
        }
        String to = properties.get("phone");
        if (StringUtils.isBlank(to)) {
            msgLog.errorCannotSendMessage("sms", "Missing recipient");
            return;
        }

        List<NameValuePair> params = new ArrayList<>(3);
        params.add(new BasicNameValuePair("To", to));
        params.add(new BasicNameValuePair("From", FROM));
        params.add(new BasicNameValuePair("Body", msg.getMessage()));

        try {
            messageFactory.create(params);
        } catch (TwilioRestException e) {
            msgLog.errorCannotSendMessage("sms", e.getLocalizedMessage());
        }
    }
}
