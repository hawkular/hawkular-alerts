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

import static org.hawkular.alerts.actions.api.PluginOperationMessage.Operation.REGISTRATION;

import java.io.IOException;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;

import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.bus.api.BusPluginOperationMessage;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.jboss.logging.Logger;

/**
 * Main bus register for plugins implementations
 *
 * @author Lucas Ponce
 */
@Startup
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class BusActionPluginRegister {
    public static final int NUM_ATTEMPTS = 10;
    public static final int TIMEOUT = 2000;

    private static final String ACTION_PLUGIN_REGISTER = "HawkularAlertsPluginsQueue";
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(BusActionPluginRegister.class);


    @Resource(mappedName = "java:/HawkularBusConnectionFactory")
    private QueueConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;

    @PostConstruct
    public void init() {
        /*
            Init the bus
         */
        int i = NUM_ATTEMPTS;
        while (ccf == null && i >= 0) {
            try {
                ccf = new ConnectionContextFactory(conFactory);
            } catch (JMSException e) {
                msgLog.warnCannotConnectBroker(i, TIMEOUT, e.getMessage());
            }
            i--;
            try {
                Thread.sleep(TIMEOUT);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (ccf == null) {
            throw new IllegalStateException("Cannot connect to the broker.");
        }

        try {
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.QUEUE, ACTION_PLUGIN_REGISTER));

            Map<String, ActionPluginListener> plugins = ActionPlugins.getPlugins();
            for (String actionPlugin : plugins.keySet()) {
                ActionPluginListener actionPluginListener = plugins.get(actionPlugin);
                BusPluginOperationMessage msg = new BusPluginOperationMessage(REGISTRATION,
                        actionPlugin,
                        actionPluginListener.getProperties(),
                        actionPluginListener.getDefaultProperties());
                MessageId mid = new MessageProcessor().send(pcc, msg);
                msgLog.infoPluginRegistration(actionPlugin, mid.toString());
            }
        } catch (JMSException e) {
            log.debug(e.getMessage(), e);
            msgLog.errorCannotSendMessage(e.getMessage());
        } finally {
            if (pcc != null) {
                try {
                    pcc.close();
                    pcc = null;
                } catch (IOException ignored) { }
            }
            if (ccf != null) {
                try {
                    ccf.close();
                } catch (JMSException ignored) { }
            }
        }
    }
}
