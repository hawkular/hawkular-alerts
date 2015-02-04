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

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.hawkular.notifiers.api.model.NotifierTypeRegistrationMessage;
import org.jboss.logging.Logger;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;
import java.util.Set;

/**
 * A component that listens from the bus new notifier plugins to be registered into the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "NotifierTypeRegisterQueue")})
public class NotifierTypeListener extends BasicMessageListener<NotifierTypeRegistrationMessage>  {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(NotifierTypeListener.class);

    @EJB
    DefinitionsService definitions;

    @Override
    protected void onBasicMessage(NotifierTypeRegistrationMessage msg) {
        log.debugf("Message received: [%s]", msg);
        String op = msg.getOp();
        String notifierType = msg.getNotifierType();
        if (op == null || op.isEmpty()) {
            msgLog.warnNotifierTypeRegistrationWithoutOp();
        } else if (op.equals("init")) {
            if (definitions.getNotifierType(notifierType) == null) {
                Set<String> properties = msg.getProperties();
                definitions.addNotifierType(notifierType, properties);
                msgLog.infoNotifierTypeRegistration(notifierType);
            } else {
                msgLog.warnNotifierTypeAlreadyRegistered(notifierType);
            }
        } else {
            msgLog.warnNotifierTypeRegistrationWithUnknownOp(notifierType, op);
        }
    }
}
