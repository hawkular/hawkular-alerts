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

import java.util.Map;
import java.util.Set;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.hawkular.alerts.bus.api.BusPluginOperationMessage;
import org.jboss.logging.Logger;

/**
 * A component that listens from the bus new action plugins to be registered into the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsPluginsQueue")})
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class ActionPluginRegistrationListener extends BasicMessageListener<BusPluginOperationMessage>  {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(ActionPluginRegistrationListener.class);

    @EJB
    DefinitionsService definitions;

    @Override
    protected void onBasicMessage(BusPluginOperationMessage msg) {
        log.debugf("Message received: [%s]", msg);
        String actionPlugin = msg.getActionPlugin();
        if (msg.getOperation() == null) {
            msgLog.warnActionPluginRegistrationWithoutOp();
            return;
        }
        switch (msg.getOperation()) {
            case REGISTRATION:
                try {
                    if (definitions.getActionPlugin(actionPlugin) == null) {
                        Set<String> properties = msg.getPropertyNames();
                        Map<String, String> defaultProperties = msg.getDefaultProperties();
                        if (defaultProperties != null && !defaultProperties.isEmpty()) {
                            definitions.addActionPlugin(actionPlugin, defaultProperties);
                        } else {
                            definitions.addActionPlugin(actionPlugin, properties);
                        }
                        msgLog.infoActionPluginRegistration(actionPlugin);
                    } else {
                        msgLog.warnActionPluginAlreadyRegistered(actionPlugin);
                    }
                } catch (Exception e) {
                    log.debugf(e.getMessage(), e);
                    msgLog.errorDefinitionsService(e.getMessage());
                }
                break;
        }
    }
}
