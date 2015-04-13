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
package org.hawkular.actions.aerogear.listener;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;

import org.jboss.aerogear.unifiedpush.DefaultPushSender;
import org.jboss.aerogear.unifiedpush.PushSender;
import org.jboss.aerogear.unifiedpush.message.UnifiedMessage;
import org.jboss.aerogear.unifiedpush.message.UnifiedMessage.MessageBuilder;

/**
 * Listener for Aerogear processing.
 *
 * @author Thomas Segismont
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsTopic"),
    @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "actionPlugin like 'aerogear'") })
public class AerogearListener extends BasicMessageListener<ActionMessage> {
    static final String ROOT_SERVER_URL_PROPERTY = "org.hawkular.actions.aerogear.root.server.url";
    static final String ROOT_SERVER_URL = System.getProperty(ROOT_SERVER_URL_PROPERTY);
    static final String APPLICATION_ID_PROPERTY = "org.hawkular.actions.aerogear.application.id";
    static final String APPLICATION_ID = System.getProperty(APPLICATION_ID_PROPERTY);
    static final String MASTER_SECRET_PROPERTY = "org.hawkular.actions.aerogear.master.secret";
    static final String MASTER_SECRET = System.getProperty(MASTER_SECRET_PROPERTY);

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    PushSender pushSender;

    @PostConstruct
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

    protected void onBasicMessage(ActionMessage msg) {
        msgLog.infoActionReceived("aerogear", msg.toString());

        if (pushSender == null) {
            msgLog.errorCannotSendMessage("aerogear", "Plugin is not started");
            return;
        }

        MessageBuilder alert = UnifiedMessage.withMessage().alert(msg.getMessage());
        if (msg.getProperties() != null) {
            String alias = msg.getProperties().get("alias");
            if (!isBlank(alias)) {
                alert.config().criteria().aliases(alias);
            }
        }

        UnifiedMessage unifiedMessage = alert.build();
        pushSender.send(unifiedMessage);
    }
}
