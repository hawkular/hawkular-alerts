/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.TopicConnectionFactory;
import javax.naming.InitialContext;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.bus.api.BusActionMessage;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.jboss.logging.Logger;



/**
 * An implementation of {@link org.hawkular.alerts.api.services.ActionListener} that will process action messages
 * through the bus architecture.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class BusActionListener implements ActionListener {
    private final MsgLogger msgLogger = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(BusActionListener.class);
    private static final String CONNECTION_FACTORY = "java:/HawkularBusConnectionFactory";
    private static final String ACTIONS_TOPIC = "HawkularAlertsActionsTopic";

    private TopicConnectionFactory conFactory;
    private ThreadLocal<ConnectionContextFactory> ccf = new ThreadLocal<>();
    private ThreadLocal<ProducerConnectionContext> pcc = new ThreadLocal<>();
    InitialContext ctx;

    public BusActionListener() {
    }

    @Override
    public void process(Action action) {
        try {
            init();
            if (pcc == null) {
                msgLogger.warnCannotConnectToBus();
                return;
            }
            BusActionMessage pluginMessage = new BusActionMessage(action);
            MessageId mid = new MessageProcessor().send(pcc.get(), pluginMessage);
            if (log.isDebugEnabled()) {
                log.debug("Sent action message [" + mid.getId() + "] to the bus");
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            msgLogger.errorProcessingAction(e.getMessage());
        }
    }

    private void init() throws Exception {
        if (ctx == null) {
            ctx = new InitialContext();
        }
        if (conFactory == null) {
            conFactory = (TopicConnectionFactory) ctx.lookup(CONNECTION_FACTORY);
        }
        if (ccf.get() == null) {
            ccf.set(new ConnectionContextFactory(conFactory));
        }
        if (pcc.get() == null) {
            pcc.set(ccf.get().createProducerConnectionContext(new Endpoint(Endpoint.Type.TOPIC, ACTIONS_TOPIC)));
        }
    }

    public void close() throws Exception {
        if (pcc.get() != null) {
            try {
                pcc.get().close();
                pcc.remove();
            } catch (IOException ignored) { }
        }
        if (ccf.get() != null) {
            try {
                ccf.get().close();
                ccf.remove();
            } catch (JMSException ignored) { }
        }
    }

}
