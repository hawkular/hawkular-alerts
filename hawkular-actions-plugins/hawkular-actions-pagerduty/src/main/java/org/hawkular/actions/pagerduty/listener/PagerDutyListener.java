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
package org.hawkular.actions.pagerduty.listener;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import com.squareup.pagerduty.incidents.NotifyResult;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.squareup.pagerduty.incidents.Trigger;

import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;

/**
 * Listens to pagerduty bus notifications and interacts with PagerDuty REST API.
 *
 * @author Thomas Segismont
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsTopic"),
    @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "actionPlugin like 'pagerduty'") })
public class PagerDutyListener extends BasicMessageListener<ActionMessage> {
    static final String API_KEY_PROPERTY = "org.hawkular.actions.pagerduty.api.key";
    static final String API_KEY = System.getProperty(API_KEY_PROPERTY);

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    PagerDuty pagerDuty;

    @PostConstruct
    void setup() {
        if (isBlank(API_KEY)) {
            String msg = "Configure " + API_KEY;
            msgLog.errorCannotBeStarted("pagerduty", msg);
            return;
        }
        try {
            pagerDuty = PagerDuty.create(API_KEY);
        } catch (Exception e) {
            msgLog.errorCannotBeStarted("pagerduty", e.getLocalizedMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    protected void onBasicMessage(ActionMessage msg) {
        msgLog.infoActionReceived("pagerduty", msg.toString());

        if (pagerDuty == null) {
            msgLog.errorCannotSendMessage("pagerduty", "Plugin is not started");
            return;
        }

        Trigger trigger = new Trigger.Builder(msg.getMessage()).build();
        NotifyResult result = pagerDuty.notify(trigger);
        if (!"success".equals(result.status())) {
            msgLog.errorCannotSendMessage("pagerduty", result.message());
        }
    }
}
