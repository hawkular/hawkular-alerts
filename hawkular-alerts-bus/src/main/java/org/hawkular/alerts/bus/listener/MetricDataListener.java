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

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.bus.messages.MetricDataMessage;
import org.hawkular.alerts.bus.messages.MetricDataMessage.MetricData;
import org.hawkular.alerts.bus.messages.MetricDataMessage.NumericDataPoint;
import org.hawkular.bus.common.consumer.BasicMessageListener;

import org.jboss.logging.Logger;

/**
 * An adapter that processes Hawkular Metrics reports, extracts relevant metric datums, translates then to Alerting
 * Data format, and forwards them for Alert processing.</br>
 * </br>
 * This is useful only when deploying into the Hawkular Bus with Hawkular Metrics.</br>
 * </br>
 * TODO: Add filtering of relevant Metric Ids.  This means fetching the active triggers, running through the conditions,
 * and collecting the dataIds.  Then using thise to filter the metricIds converted and forwarded to the engine. Note
 * that we will need a way to update that Id set as changes occur to the Trigger population. Changes are
 * rare so we don't want it to be too cumbersome.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularMetricData") })
public class MetricDataListener extends BasicMessageListener<MetricDataMessage> {
    private final Logger log = Logger.getLogger(MetricDataListener.class);

    @EJB
    AlertsService alerts;

    @EJB
    DefinitionsService definitions;

    @PostConstruct
    public void postContruct() {

    }

    private boolean isNeeded(String metricId) {
        // TODO: probably a Map lookup
        return true;
    }

    @Override
    protected void onBasicMessage(MetricDataMessage msg) {
        log.debugf("Message received: [%s]", msg);

        MetricData metricData = msg.getMetricData();
        String metricId = metricData.getMetricId();
        if (!isNeeded(metricId)) {
            return;
        }

        List<NumericDataPoint> metricDataPoints = metricData.getDataPoints();
        List<Data> alertData = new ArrayList<>(metricDataPoints.size());
        for (NumericDataPoint dp : metricDataPoints) {
            alertData.add(new NumericData(metricId, dp.getTimestamp(), dp.getValue()));
        }

        log.debugf("Sending: [%s]", alertData);
        alerts.sendData(alertData);
    }
}
