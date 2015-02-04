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
package org.hawkular.notifiers.snmp.registration;

import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.hawkular.notifiers.api.log.MsgLogger;
import org.hawkular.notifiers.api.model.NotifierRegistrationMessage;
import org.jboss.logging.Logger;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

/**
 * An example of listener for snmp notifiers.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "NotifierRegisterTopic"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "NotifierType like 'snmp'")})
public class RegisterListener extends BasicMessageListener<NotifierRegistrationMessage>  {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(RegisterListener.class);

    protected void onBasicMessage(NotifierRegistrationMessage msg) {
        msgLog.infoNotifierRegistrationReceived("snmp", msg.getNotifierId(), msg.toString());
    }
}
