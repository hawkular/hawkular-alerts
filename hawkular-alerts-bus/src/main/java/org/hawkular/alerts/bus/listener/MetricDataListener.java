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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.data.NumericData;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.bus.messages.MetricDataMessage;
import org.hawkular.alerts.bus.messages.MetricDataMessage.MetricData;
import org.hawkular.alerts.bus.messages.MetricDataMessage.SingleMetric;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.infinispan.Cache;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.manager.CacheContainer;
import org.jboss.logging.Logger;

/**
 * An adapter that processes Hawkular Metrics data, extracts relevant metric datums, translates them to Alerting
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
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularMetricData") })
public class MetricDataListener extends BasicMessageListener<MetricDataMessage> {
    private final Logger log = Logger.getLogger(MetricDataListener.class);

    Set<String> activeDataIds;
    long activeDataIdsTime = 0L;

    @Resource(lookup = "java:jboss/infinispan/container/hawkular")
    private CacheContainer container;
    protected Cache<CacheEntry, Object> cache;

    @EJB
    AlertsService alerts;

    @EJB
    DefinitionsService definitions;

    @PostConstruct
    public void postContruct() {
        cache = this.container.getCache();
        updateActiveMetricIds();
    }

    private void updateActiveMetricIds() {

        if (null != activeDataIds) {
            if (null == cache) {
                log.error("ISPN Cache is null. All data being forwarded to alerting!");
                activeDataIds = null;
                return;
            }

            Long updateTime = (Long) cache.get("HawkularAlerts:ConditionUpdateTime");
            if (null == updateTime || updateTime <= activeDataIdsTime) {
                return;
            }

            activeDataIdsTime = updateTime;
        }

        Set<String> dataIds = null;
        try {
            Collection<Condition> conditions = definitions.getAllConditions();
            dataIds = new HashSet<>(conditions.size());
            for (Condition c : conditions) {
                if (c instanceof AvailabilityCondition) {
                    continue;
                }
                dataIds.add(c.getDataId());
                if (c instanceof CompareCondition) {
                    dataIds.add(((CompareCondition) c).getData2Id());
                }
            }
        } catch (Exception e) {
            log.error("FAILED to load conditions to create metricId filter. All data being forwarded to alerting!", e);
            activeDataIds = null;
            activeDataIdsTime = 0L;
        }

        activeDataIds = dataIds;
        log.debugf("Updated activeDataIds! %s", activeDataIds);
    }

    private boolean isNeeded(String metricId) {
        if (null == activeDataIds) {
            return true;
        }

        return activeDataIds.contains(metricId);
    }

    @Override
    protected void onBasicMessage(MetricDataMessage msg) {
        log.debugf("Message received: [%s]", msg);

        updateActiveMetricIds();

        // TODO: tenants?
        MetricData metricData = msg.getMetricData();

        List<SingleMetric> data = metricData.getData();
        List<Data> alertData = new ArrayList<>(data.size());
        for (SingleMetric m : data) {
            if (isNeeded(m.getSource())) {
                alertData.add(new NumericData(m.getSource(), m.getTimestamp(), m.getValue()));
            } else {
                log.debugf("Filtering data not used in Triggers. MetricId=%s", m.getSource());
            }
        }

        log.debugf("Sending: [%s]", alertData);
        alerts.sendData(alertData);
    }
}
