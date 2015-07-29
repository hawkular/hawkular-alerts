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
package org.hawkular.alerts.bus.sender;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.TopicConnectionFactory;
import javax.jms.JMSException;
import javax.naming.InitialContext;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.bus.api.BusPluginMessage;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.jboss.logging.Logger;



/**
 * An implementation of {@link org.hawkular.alerts.api.services.ActionListener} that will send listener
 * messages through the bus.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Startup
@Singleton
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class ActionPluginSender implements ActionListener {
    private final MsgLogger msgLogger = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(ActionPluginSender.class);
    private static final String CONNECTION_FACTORY = "java:/HawkularBusConnectionFactory";
    private static final String ACTIONS_TOPIC = "HawkularAlertsActionsTopic";
    private static final String DEFINITIONS_SERVICE =
            "java:app/hawkular-alerts-rest/CassDefinitionsServiceImpl";

    private TopicConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;
    InitialContext ctx;

    DefinitionsService definitions;

    public ActionPluginSender() {
    }

    @Override
    public void process(Action action) {
        try {
            init();
            if (pcc == null) {
                msgLogger.warnCannotConnectToBus();
                return;
            }
            if (definitions != null) {
                Map<String, String> properties = definitions.getAction(action.getTenantId(),
                        action.getActionPlugin(), action.getActionId());
                Map<String, String> defaultProperties = definitions.getDefaultActionPlugin(action.getActionPlugin());
                Map<String, String> mixedProps = mixProperties(properties, defaultProperties);

                BusPluginMessage pluginMessage = new BusPluginMessage(action, mixedProps);
                MessageId mid = new MessageProcessor().send(pcc, pluginMessage);
                log.debug("Sent action message [" + mid.getId() + "] to the bus");
            } else {
                msgLogger.warnCannotAccessToDefinitionsService();
            }
        } catch (Exception e) {
            log.debug(e.getMessage(), e);
            msgLogger.errorProcessingAction(e.getMessage());
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
                    ccf = null;
                } catch (JMSException ignored) { }
            }
        }
    }

    private void init() throws Exception {
        if (ctx == null) {
            ctx = new InitialContext();
        }
        if (conFactory == null) {
            conFactory = (TopicConnectionFactory) ctx.lookup(CONNECTION_FACTORY);
        }
        if (ccf == null) {
            ccf = new ConnectionContextFactory(conFactory);
        }
        if (pcc == null) {
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.TOPIC, ACTIONS_TOPIC));
        }
        if (definitions == null) {
            definitions = (DefinitionsService) ctx.lookup(DEFINITIONS_SERVICE);
        }
    }

    private Map<String, String> mixProperties(Map<String, String> props, Map<String, String> defProps) {
        Map<String, String> mixed = new HashMap<>();
        if (props != null) {
            mixed.putAll(props);
        }
        if (defProps != null) {
            for (String defKey : defProps.keySet()) {
                mixed.putIfAbsent(defKey, defProps.get(defKey));
            }
        }
        return mixed;
    }
}
