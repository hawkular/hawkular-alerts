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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

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

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    @PostConstruct
    void setup() {
        // TODO initialize REST client
    }

    protected void onBasicMessage(ActionMessage msg) {
        msgLog.infoActionReceived("pagerduty", msg.toString());

        // TODO implement
    }
}
