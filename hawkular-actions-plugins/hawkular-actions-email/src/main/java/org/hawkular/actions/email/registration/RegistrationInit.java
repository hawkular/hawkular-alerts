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
package org.hawkular.actions.email.registration;


import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;

import org.hawkular.actions.email.EmailPlugin;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionPluginMessage;
import org.jboss.logging.Logger;

/**
 * A initialization class to init the email plugin
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

            /*
                This is a registration message, it should contain
             */

            ActionPluginMessage apMsg = new ActionPluginMessage();
            apMsg.setOp(EmailPlugin.INIT_PLUGIN);
            apMsg.setActionPlugin(EmailPlugin.PLUGIN_NAME);
            Set<String> properties = new HashSet<String>();
            properties.add(EmailPlugin.PROP_FROM);
            properties.add(EmailPlugin.PROP_FROM_NAME);
            properties.add(EmailPlugin.PROP_TO);
            properties.add(EmailPlugin.PROP_CC);
            properties.add(EmailPlugin.PROP_MESSAGE_ID);
            properties.add(EmailPlugin.PROP_IN_REPLY_TO);
            properties.add(EmailPlugin.PROP_TEMPLATE_HAWKULAR_URL);
            properties.add(EmailPlugin.PROP_TEMPLATE_LOCALE);
            properties.add(EmailPlugin.PROP_TEMPLATE_PLAIN);
            properties.add(EmailPlugin.PROP_TEMPLATE_HTML);
            apMsg.setProperties(properties);

            MessageId mid = new MessageProcessor().send(pcc, apMsg);

            msgLog.infoPluginRegistration(EmailPlugin.INIT_PLUGIN, mid.toString());
        } catch (JMSException e) {
            log.debug(e.getMessage(), e);
            msgLog.errorCannotSendMessage(EmailPlugin.INIT_PLUGIN, e.getMessage());
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
