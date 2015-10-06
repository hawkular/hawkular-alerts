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

import static org.hawkular.alerts.actions.api.ActionResponseMessage.Operation;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.bus.api.BusActionResponseMessage;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.jboss.logging.Logger;

/**
 * Main bus sender for plugins implementations
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class BusActionPluginSender implements ActionPluginSender {
    public static final int NUM_ATTEMPTS = 10;
    public static final int TIMEOUT = 2000;

    private static final String CONNECTION_FACTORY = "java:/HawkularBusConnectionFactory";
    private static final String ACTION_PLUGIN_REGISTER = "HawkularAlertsOperationsQueue";
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(BusActionPluginRegister.class);

    private String actionPlugin;

    private QueueConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;

    public BusActionPluginSender(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    private void init() throws Exception {
        /*
            Init and cache factories and contexts used.
            ActiveMQ recommends to catch MessageProducer and MessageConsumer objects that are wrapped in context:
            http://activemq.apache.org/how-do-i-use-jms-efficiently.html
         */
        if (conFactory == null) {
            try {
                InitialContext ctx = new InitialContext();
                conFactory = (QueueConnectionFactory)ctx.lookup(CONNECTION_FACTORY);
            } catch (NamingException e) {
                throw new IllegalStateException("Cannot get context");
            }
        }

        if (ccf == null) {
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
        }

        if (pcc == null) {
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.QUEUE, ACTION_PLUGIN_REGISTER));
        }
    }

    public void close() throws Exception {
        if (pcc != null) {
            try {
                pcc.close();
                pcc = null;
            } catch (IOException ignored) { }
        }
        if (ccf != null) {
            try {
                ccf.close();
            } catch (Exception ignored) { }
        }
    }

    @Override
    public ActionResponseMessage createMessage(Operation operation) {
        if (operation == null) {
            return new BusActionResponseMessage();
        }
        return new BusActionResponseMessage(operation);
    }

    @Override
    public void send(ActionResponseMessage msg) throws Exception {
        if (!(msg instanceof BusActionResponseMessage)) {
            throw new IllegalArgumentException("OperationMessage is not a BusOperationMessage instance");
        }
        init();
        try {
            MessageId mid = new MessageProcessor().send(pcc, (BusActionResponseMessage)msg);
            log.debugf("Plugin [%s] has sent an operation message: [%s]", actionPlugin, mid.toString());
        } catch (JMSException e) {
            log.debug(e.getMessage(), e);
            msgLog.errorCannotSendMessage(e.getMessage());
        }
    }
}
