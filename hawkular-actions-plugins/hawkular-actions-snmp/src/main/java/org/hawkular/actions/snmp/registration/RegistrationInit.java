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
package org.hawkular.actions.snmp.registration;

import java.io.IOException;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionPluginMessage;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import java.util.HashSet;
import java.util.Set;

/**
 * A initialization class to init the snmp plugin
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Startup
@Singleton
public class RegistrationInit {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(RegistrationInit.class);
    private static final String ACTION_PLUGIN_REGISTER = "HawkularAlertsPluginsQueue";

    @Resource(mappedName = "java:/HawkularBusConnectionFactory")
    private QueueConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;

    @PostConstruct
    public void init() {
        try {
            ccf = new ConnectionContextFactory(conFactory);
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.QUEUE, ACTION_PLUGIN_REGISTER));

            ActionPluginMessage apMsg = new ActionPluginMessage();
            apMsg.setOp("init");
            apMsg.setActionPlugin("snmp");
            Set<String> properties = new HashSet<String>();
            properties.add("host");
            properties.add("port");
            properties.add("oid");
            properties.add("description");
            apMsg.setProperties(properties);

            MessageId mid = new MessageProcessor().send(pcc, apMsg);

            msgLog.infoPluginRegistration("snmp", mid.toString());
        } catch (JMSException e) {
            log.debug(e.getMessage(), e);
            msgLog.errorCannotSendMessage("snmp", e.getMessage());
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
