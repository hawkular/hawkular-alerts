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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.bus.init.CacheManager;
import org.hawkular.alerts.bus.messages.MetricDataMessage;
import org.hawkular.alerts.bus.messages.MetricDataMessage.MetricData;
import org.hawkular.alerts.bus.messages.MetricDataMessage.SingleMetric;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.jboss.logging.Logger;

/**
 * <p>
 * An adapter that processes Hawkular Metrics data, extracts relevant metric datums, translates them to Alerting
 * Data format, and forwards them for Alert processing.
 * </p>
 * This is useful only when deploying into the Hawkular Bus with Hawkular Metrics. The expected message payload should
 * be JSON representation of {@link MetricDataMessage}.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularMetricData") })
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class MetricDataListener extends BasicMessageListener<MetricDataMessage> {
    private final Logger log = Logger.getLogger(MetricDataListener.class);

    @EJB
    AlertsService alerts;

    @EJB
    CacheManager cacheManager;

    private boolean isNeeded(Set<String> activeMetricIds, String metricId) {
        if (null == activeMetricIds) {
            return true;
        }

        return activeMetricIds.contains(metricId);
    }

    @Override
    protected void onBasicMessage(MetricDataMessage msg) {

        // TODO: tenants?
        MetricData metricData = msg.getMetricData();
        if (log.isTraceEnabled()) {
            log.trace("Message received with [" + metricData.getData().size() + "] metrics.");
        }

        List<SingleMetric> data = metricData.getData();
        List<Data> alertData = null;
        Set<String> activeMetricIds = cacheManager.getActiveDataIds();
        for (SingleMetric m : data) {
            if (isNeeded(activeMetricIds, m.getSource())) {
                if (null == alertData) {
                    alertData = new ArrayList<>(data.size());
                }
                alertData.add(new Data(m.getSource(), m.getTimestamp(), String.valueOf(m.getValue())));
            }
        }
        if (null == alertData) {
            if (log.isTraceEnabled()) {
                log.trace("Forwarding 0 of [" + data.size() + "] metrics to Alerts Engine...");
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Forwarding [" + alertData.size() + "] of [" + data.size() + "] metrics to Alerts Engine " +
                    "(filtered [" + (data.size() - alertData.size()) + "])...");
            }
            try {
                alerts.sendData(alertData);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Metrics is not currently exposing the class it uses for the message.  So we needed to
    // implement a compatible class that we can use to deserialize the JSON.  If the class becomes
    // something we can get as a dependency, then import that and this can be removed.
    @Override
    protected String convertReceivedMessageClassNameToDesiredMessageClassName(String className) {

        if (className.equals("org.hawkular.metrics.component.publish.MetricDataMessage")) {
            return "org.hawkular.alerts.bus.messages.MetricDataMessage";
        }

        return null;
    }

}
