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
package org.hawkular.notifiers.sms.registration;

import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.notifiers.api.model.NotifierTypeRegistrationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.HashSet;
import java.util.Set;

/**
 * A initialization class to init the sms plugin
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@WebListener
public class RegistrationInit implements ServletContextListener {
    private final Logger log = LoggerFactory.getLogger(RegistrationInit.class);
    private static final String NOTIFIER_TYPE_REGISTER = "NotifierTypeRegisterQueue";

    @Resource(mappedName = "java:/HawkularBusConnectionFactory")
    private QueueConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Unregistering plugin sms");
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ccf = new ConnectionContextFactory(conFactory);
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.QUEUE, NOTIFIER_TYPE_REGISTER));

            NotifierTypeRegistrationMessage ntrMsg = new NotifierTypeRegistrationMessage();
            ntrMsg.setOp("init");
            ntrMsg.setNotifierType("sms");
            Set<String> properties = new HashSet<String>();
            properties.add("prop1");
            properties.add("prop2");
            properties.add("prop3");
            ntrMsg.setProperties(properties);

            MessageId mid = new MessageProcessor().send(pcc, ntrMsg);

            log.info("Sent registration request for sms plugin. ");

        } catch (JMSException e) {
            log.error(e.getMessage(), e);
        }
    }
}
