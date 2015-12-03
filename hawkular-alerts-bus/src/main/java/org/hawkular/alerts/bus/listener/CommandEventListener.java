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
package org.hawkular.alerts.bus.listener;

import java.util.Collections;
import java.util.UUID;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.bus.common.BasicMessage;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.hawkular.cmdgw.api.DeployApplicationResponse;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.jboss.logging.Logger;

/**
 * <p><b>
 * NOTE: This listener should likely move to a dedicated deployment, in Hawkular, not Hawkular Alerts.  Hawkular
 * Alerts should not really have knowledge about Hawkular-level decisions, like which possible Events to filter out or
 * various special handling that needs to be performed.
 * </b></p>
 * <p>
 * Consume Command Gateway Events, convert to Hawkular Events and forward to the engine for persistence/evaluation.
 * </p>
 * This is useful only when deploying into the Hawkular Bus with Hawkular Command Gateway. The expected message
 * payload should a command pojo.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularCommandEvent") })
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class CommandEventListener extends BasicMessageListener<BasicMessage> {
    private final Logger log = Logger.getLogger(CommandEventListener.class);

    @EJB
    AlertsService alerts;

    @Override
    protected void onBasicMessage(BasicMessage msg) {

        if (msg instanceof DeployApplicationResponse) {
            DeployApplicationResponse dar = (DeployApplicationResponse) msg;

            String canonicalPathString = dar.getResourcePath();
            CanonicalPath canonicalPath = CanonicalPath.fromString(canonicalPathString);
            String tenantId = canonicalPath.ids().getTenantId();
            String resourceId = canonicalPath.ids().getResourcePath().getSegment().getElementId();
            resourceId = resourceId.substring(0, resourceId.length() - 2); // trim trailing '~~'
            String eventId = UUID.randomUUID().toString();
            String dataId = resourceId + "_DeployApplicationResponse";
            String category = "Hawkular Deployment";
            String text = dar.getStatus().name();
            Event event = new Event(tenantId, eventId, dataId, category, text);
            event.addContext("CanonicalPath", canonicalPathString);
            event.addContext("Message", dar.getMessage());

            if (log.isDebugEnabled()) {
                log.debug("EVENT! " + event.toString());
            }

            try {
                alerts.addEvents(Collections.singleton(event));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            log.error("Unexpected Event Message! " + msg.toJSON());
        }
    }
}
