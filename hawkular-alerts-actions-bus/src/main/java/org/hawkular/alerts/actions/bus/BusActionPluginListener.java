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
package org.hawkular.alerts.actions.bus;

import java.util.Collection;

import javax.annotation.PreDestroy;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.MessageListener;

import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.bus.api.BusActionMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.jboss.logging.Logger;

/**
 * Main bus listener for plugins implementation.
 *
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsTopic")})
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class BusActionPluginListener extends BasicMessageListener<BusActionMessage> {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(BusActionPluginListener.class);

    @Override
    protected void onBasicMessage(BusActionMessage basicMessage) {
        if (ActionPlugins.getPlugins().isEmpty()) {
            msgLog.warnNoPluginsFound();
            return;
        }
        if (basicMessage == null
                || basicMessage.getAction() == null
                || basicMessage.getAction().getActionPlugin() == null) {
            msgLog.warnMessageReceivedWithoutPluginInfo();
            return;
        }
        String actionPlugin = basicMessage.getAction().getActionPlugin();
        ActionPluginListener plugin = ActionPlugins.getPlugins().get(actionPlugin);
        if (plugin == null) {
            log.debug("Received action [" + actionPlugin + "] but no ActionPluginListener found on this deployment");
            return;
        }
        try {
            plugin.process(basicMessage);
            msgLog.infoActionReceived(actionPlugin, basicMessage.getMessageId().getId());
            log.debug("Received payload: " + basicMessage.toJSON());
        } catch (Exception e) {
            msgLog.error("Plugin [" + actionPlugin + "] processing error", e);
        }
    }

    @PreDestroy
    public void close() throws Exception {
        Collection<ActionPluginSender> senders = ActionPlugins.getSenders().values();
        for (ActionPluginSender sender: senders) {
            if (sender instanceof BusActionPluginSender) {
                ((BusActionPluginSender)sender).close();
            }
        }
    }

}
