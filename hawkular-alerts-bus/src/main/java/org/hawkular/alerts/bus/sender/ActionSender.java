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

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.actions.api.model.ActionMessage;
import org.jboss.logging.Logger;

import javax.jms.TopicConnectionFactory;
import javax.naming.InitialContext;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link org.hawkular.alerts.api.services.ActionListener} that will send listener
 * messages through the bus.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionSender implements ActionListener {
    private final MsgLogger msgLogger = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(ActionSender.class);
    private final String CONNECTION_FACTORY = "java:/HawkularBusConnectionFactory";
    private final String ACTIONS_TOPIC = "HawkularAlertsActionsTopic";
    private final String DEFINITIONS_SERVICE =
            "java:app/hawkular-alerts-engine/DbDefinitionsServiceImpl";

    private TopicConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;
    InitialContext ctx;

    DefinitionsService definitions;

    public ActionSender() {
    }

    @Override
    public void process(Action action) {
        try {
            init();
            if (pcc == null) {
                msgLogger.warnCannotConnectToBus();
                return;
            }
            ActionMessage nMsg = new ActionMessage();
            nMsg.setActionId(action.getActionId());
            nMsg.setMessage(action.getMessage());
            if (definitions != null) {
                Map<String, String> properties = definitions.getAction(action.getActionId());
                MessageId mid;
                if (properties != null && properties.containsKey("actionPlugin")) {
                    String notifierType = properties.get("actionPlugin");
                    nMsg.setProperties(properties);
                    mid = new MessageProcessor().send(pcc, nMsg, actionPluginFilter(notifierType));
                } else {
                    mid = new MessageProcessor().send(pcc, nMsg);
                }
                msgLogger.infoSentActionMessage(mid.getId());
            } else {
                msgLogger.warnCannotAccessToDefinitionsService();
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
            ccf = new ConnectionContextFactory(conFactory);
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.TOPIC, ACTIONS_TOPIC));
        }
        if (definitions == null) {
            definitions = (DefinitionsService) ctx.lookup(DEFINITIONS_SERVICE);
        }
    }

    private static Map<String, String> actionPluginFilter(String actionPlugin) {
        Map<String, String> map = new HashMap<String, String>(1);
        map.put("actionPlugin", actionPlugin);
        return map;
    }
}
