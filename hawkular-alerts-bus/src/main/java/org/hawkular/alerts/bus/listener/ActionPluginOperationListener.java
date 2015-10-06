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
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.bus.api.BusActionResponseMessage;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.jboss.logging.Logger;

/**
 * A component that listens from the bus operation messages coming from plugins.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsResponseQueue")})
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class ActionPluginOperationListener extends BasicMessageListener<BusActionResponseMessage>  {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(ActionPluginOperationListener.class);

    @EJB
    ActionsService actions;

    @Override
    protected void onBasicMessage(BusActionResponseMessage msg) {
        log.debugf("Message received: [%s]", msg);
        if (msg != null && msg.getPayload().containsKey("action")) {
            String jsonAction = msg.getPayload().get("action");
            Action updatedAction = JsonUtil.fromJson(jsonAction, Action.class);
            actions.updateResult(updatedAction);
            log.debugf("Operation message received from plugin [%s] with payload [%s]",
                    updatedAction.getActionPlugin(), updatedAction.getResult());
        } else {
            msgLog.warnActionResponseMessageWithoutPayload();
        }
    }
}
