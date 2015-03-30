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
import javax.jms.MessageListener;

import org.hawkular.alerts.api.model.data.Availability;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.bus.init.CacheManager;
import org.hawkular.alerts.bus.messages.AvailDataMessage;
import org.hawkular.alerts.bus.messages.AvailDataMessage.AvailData;
import org.hawkular.alerts.bus.messages.AvailDataMessage.SingleAvail;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.jboss.logging.Logger;

import com.google.gson.GsonBuilder;

/**
 * An adapter that processes Hawkular Availability data, extracts relevant avail datums, translates them to Alerting
 * Data format, and forwards them for Alert processing.
 * </p>
 * This is useful only when deploying into the Hawkular Bus with Hawkular Metrics. The expected format of the
 * data is JSON like:
 * </p>
 * <code>
 *  { tenantId , List<org.rhq.metrics.client.common.SingleMetric> }
 * </code>
 * </p>
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
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAvailData") })
public class AvailDataListener extends BasicMessageListener<AvailDataMessage> {
    private final Logger log = Logger.getLogger(AvailDataListener.class);

    @EJB
    AlertsService alerts;

    @EJB
    DefinitionsService definitions;

    @EJB
    CacheManager cacheManager;

    private boolean isNeeded(Set<String> activeAvailabilityIds, String id) {
        if (null == activeAvailabilityIds) {
            return true;
        }

        return activeAvailabilityIds.contains(id);
    }


    @Override
    protected void onBasicMessage(AvailDataMessage msg) {
        log.debugf("Message received: [%s]", msg);

        AvailData availData = msg.getAvailData();

        List<SingleAvail> data = availData.getData();
        List<Data> alertData = new ArrayList<>(data.size());
        Set<String> activeAvailabilityIds = cacheManager.getActiveAvailabilityIds();
        for (SingleAvail a : data) {
            if (isNeeded(activeAvailabilityIds, a.getId())) {
                alertData.add(new Availability(a.getId(), a.getTimestamp(), a.getAvail()));
            }
        }

        log.debugf("Sending: [%s]", alertData);
        alerts.sendData(alertData);
    }

    // just dumps the expected json
    public static void main(String[] args) {
        AvailData d = new AvailData();
        List<SingleAvail> sa = new ArrayList<>(1);
        sa.add(new SingleAvail("tenant", "Avail-01", 123L, "DOWN"));
        d.setData(sa);
        System.out.println(new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(d).toString());
    }

}
