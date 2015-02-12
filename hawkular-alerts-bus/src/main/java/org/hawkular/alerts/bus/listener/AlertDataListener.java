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
package org.hawkular.alerts.bus.listener;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.bus.messages.AlertDataMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;

import org.jboss.logging.Logger;

/**
 * A component that listens from the bus data to send into the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertData") })
public class AlertDataListener extends BasicMessageListener<AlertDataMessage> {
    private final Logger log = Logger.getLogger(AlertDataListener.class);

    @EJB
    AlertsService alerts;

    @Override
    protected void onBasicMessage(AlertDataMessage msg) {
        log.debugf("Message received: [%s]", msg);
        alerts.sendData(msg.getData());
    }
}
