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

import org.hawkular.alerts.api.model.notification.Notification;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.NotifierListener;
import org.hawkular.bus.common.ConnectionContextFactory;
import org.hawkular.bus.common.Endpoint;
import org.hawkular.bus.common.MessageId;
import org.hawkular.bus.common.MessageProcessor;
import org.hawkular.bus.common.producer.ProducerConnectionContext;
import org.hawkular.notifiers.api.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.TopicConnectionFactory;
import javax.naming.InitialContext;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link org.hawkular.alerts.api.services.NotifierListener} that will send notifications
 * messages through the bus.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class NotificationSender implements NotifierListener {
    private final Logger log = LoggerFactory.getLogger(NotificationSender.class);
    private final String CONNECTION_FACTORY = "java:/HawkularBusConnectionFactory";
    private final String NOTIFICATIONS_TOPIC = "NotificationsTopic";
    private final String DEFINITIONS_SERVICE =
            "java:global/hawkular-alerts/hawkular-alerts-engine/MemDefinitionsServiceImpl";

    private TopicConnectionFactory conFactory;
    private ConnectionContextFactory ccf;
    private ProducerConnectionContext pcc;
    InitialContext ctx;

    DefinitionsService definitions;

    public NotificationSender() {
    }

    @Override
    public void process(Notification notification) {
        try {
            init();
            if (pcc == null) {
                log.warn("Send " + notification);
            }
            NotificationMessage nMsg = new NotificationMessage();
            nMsg.setNotifierId(notification.getNotifierId());
            nMsg.setMessage(notification.getMessage());
            if (definitions != null) {
                Map<String, String> properties = definitions.getNotifier(notification.getNotifierId());
                MessageId mid;
                if (properties != null && properties.containsKey("NotifierType")) {
                    String notifierType = properties.get("NotifierType");
                    mid = new MessageProcessor().send(pcc, nMsg, notifierTypeFilter(notifierType));
                } else {
                    mid = new MessageProcessor().send(pcc, nMsg);
                }
                log.info("Sent {}", mid);
            } else {
                log.warn("Could not accesss to DefinitionsService");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void init() throws Exception {
        if (ctx == null) {
            ctx = new InitialContext();
        }
        if (conFactory == null) {
            conFactory = (TopicConnectionFactory) ctx.lookup(CONNECTION_FACTORY);
            ccf = new ConnectionContextFactory(conFactory);
            pcc = ccf.createProducerConnectionContext(new Endpoint(Endpoint.Type.TOPIC, NOTIFICATIONS_TOPIC));
        }
        if (definitions == null) {
            definitions = (DefinitionsService) ctx.lookup(DEFINITIONS_SERVICE);
        }
    }

    private static Map<String, String> notifierTypeFilter(String notifierType) {
        Map<String, String> map = new HashMap<String, String>(1);
        map.put("NotifierType", notifierType);
        return map;
    }
}
