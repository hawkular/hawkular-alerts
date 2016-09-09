/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.bus.init;

import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.CONDITION_CHANGE;
import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.TRIGGER_REMOVE;
import static org.hawkular.alerts.bus.api.DataIdPrefix.ALERT_AVAILABILITY;
import static org.hawkular.alerts.bus.api.DataIdPrefix.ALERT_COUNTER;
import static org.hawkular.alerts.bus.api.DataIdPrefix.ALERT_COUNTER_RATE;
import static org.hawkular.alerts.bus.api.DataIdPrefix.ALERT_GAUGE;
import static org.hawkular.alerts.bus.api.DataIdPrefix.ALERT_GAUGE_RATE;
import static org.hawkular.alerts.bus.api.DataIdPrefix.ALERT_STRING;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.bus.log.MsgLogger;
import org.hawkular.metrics.model.MetricId;
import org.hawkular.metrics.model.MetricType;
import org.infinispan.Cache;
import org.jboss.logging.Logger;

/**
 * A helper class to initialize bus callbacks into the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@Startup
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
public class CacheManager {
    private final Logger log = Logger.getLogger(CacheManager.class);
    private final MsgLogger msgLog = MsgLogger.LOGGER;

    private final String DISABLE_PUBLISH_FILTERING = "hawkular-alerts.disable-publish-filtering";
    private final String DISABLE_PUBLISH_FILTERING_ENV = "DISABLE_PUBLISH_FILTERING";
    private final String RESET_PUBLISH_CACHE = "hawkular-alerts.reset-publish-cache";
    private final String RESET_PUBLISH_CACHE_ENV = "RESET_PUBLISH_CACHE";

    Set<DataIdKey> activeDataIds;
    Set<DataIdKey> activeAvailabityIds;

    @EJB
    PropertiesService properties;

    @EJB
    DefinitionsService definitions;

    @Resource(lookup = "java:jboss/infinispan/cache/hawkular-metrics/publish")
    private Cache publishCache;

    @PostConstruct
    public void init() {
        if (!Boolean.parseBoolean(properties.getProperty(DISABLE_PUBLISH_FILTERING,
                DISABLE_PUBLISH_FILTERING_ENV,
                "false"))) {
            if (Boolean.parseBoolean(properties.getProperty(RESET_PUBLISH_CACHE,
                    RESET_PUBLISH_CACHE_ENV,
                    "true"))) {
                msgLog.warnClearPublishCache();
                publishCache.clear();
            }
            msgLog.infoInitPublishCache();
            updateActiveIds();
            definitions.registerListener(e -> { updateActiveIds(); }, CONDITION_CHANGE, TRIGGER_REMOVE);
        } else {
            msgLog.warnDisabledPublishCache();
        }
    }

    public Set<DataIdKey> getActiveDataIds() {
        return activeDataIds;
    }

    public Set<DataIdKey> getActiveAvailabilityIds() {
        return activeAvailabityIds;
    }

    private synchronized void updateActiveIds() {
        try {
            Collection<Condition> conditions = definitions.getAllConditions();
            final Set<DataIdKey> dataIds = new HashSet<>(conditions.size());
            final Set<DataIdKey> availIds = new HashSet<>(conditions.size());
            for (Condition c : conditions) {
                String tenantId = c.getTenantId();
                String dataId = c.getDataId();
                DataIdKey key = new DataIdKey(tenantId, dataId);
                if (c instanceof AvailabilityCondition) {
                    availIds.add(key);
                    if (isNewAvailId(key)) {
                        publish(key);
                    }
                    continue;
                }
                dataIds.add(key);
                if (isNewDataId(key)) {
                    publish(key);
                }
                if (c instanceof CompareCondition) {
                    String data2Id = ((CompareCondition) c).getData2Id();
                    DataIdKey key2 = new DataIdKey(tenantId, data2Id);
                    dataIds.add(key2);
                    if (isNewDataId(key2)) {
                        publish(key2);
                    }
                }
            }
            if (activeDataIds != null && !activeDataIds.isEmpty()) {
                activeDataIds.stream().filter(old -> !dataIds.contains(old)).forEach(old -> unpublish(old));
            }
            if (activeAvailabityIds != null && activeAvailabityIds.isEmpty()) {
                activeAvailabityIds.stream().filter(old -> !availIds.contains(old)).forEach(old -> unpublish(old));
            }

            activeDataIds = Collections.unmodifiableSet(dataIds);
            activeAvailabityIds = Collections.unmodifiableSet(availIds);

            log.debugf("Publish Cache size: %s", publishCache.size());
        } catch (Exception e) {
            log.error("FAILED to load conditions to create Id filters. All data being forwarded to alerting!", e);
            activeDataIds = null;
            activeAvailabityIds = null;
            return;
        }
    }

    private boolean isNewAvailId(DataIdKey availIdKey) {
        if (activeAvailabityIds == null || activeAvailabityIds.isEmpty()) {
            return true;
        }
        return !activeAvailabityIds.contains(availIdKey);
    }

    private boolean isNewDataId(DataIdKey dataIdKey) {
        if (activeDataIds == null || activeDataIds.isEmpty()) {
            return true;
        }
        return !activeDataIds.contains(dataIdKey);
    }


    private void publish(DataIdKey dataIdKey) {
        MetricId metricId = getMetricId(dataIdKey);
        if (metricId != null && publishCache != null) {
            /*
                This logic assumes that alerting is the only writer for the shared publishCache
             */
            log.debugf("Publishing metricId %s ", metricId);
            publishCache.put(convert(metricId), dataIdKey.getDataId());
            /*
                This alternative logic assumes that more writers can add/remove entries into the shared publishCache.

                Set<String> updatedSubscribers;
                if (publishCache.containsKey(metricId)) {
                    Set<String> subscribers = (Set<String>) publishCache.get(metricId);
                    if (!subscribers.contains(ALERTING)) {
                        updatedSubscribers = new HashSet<>(subscribers);
                        updatedSubscribers.add(ALERTING);
                        publishCache.put(metricId, updatedSubscribers);
                    }
                } else {
                    updatedSubscribers = new HashSet<>();
                    updatedSubscribers.add(ALERTING);
                    publishCache.put(metricId, updatedSubscribers);
                }
            */
        }
    }

    private void unpublish(DataIdKey dataIdKey) {
        MetricId metricId = getMetricId(dataIdKey);
        if (metricId != null && publishCache != null) {
            /*
                This logic assumes that alerting is the only writer for the shared publishCache
             */
            log.debugf("Unpublishing metricId %s ", metricId);
            publishCache.remove(convert(metricId));
            /*
                This alternative logic assumes that more writers can add/remove entries into the shared publishCache.

                if (publishCache.containsKey(metricId)) {
                    Set<String> updatedSubscribers;
                    Set<String> subscribers = (Set<String>) publishCache.get(metricId);
                    if (subscribers.contains(ALERTING)) {
                        updatedSubscribers = new HashSet<>(subscribers);
                        updatedSubscribers.remove(ALERTING);
                        if (updatedSubscribers.isEmpty()) {
                            publishCache.remove(metricId);
                        } else {
                            publishCache.put(metricId, updatedSubscribers);
                        }
                    }
                }
            */
        }
    }

    private MetricId getMetricId(DataIdKey dataIdKey) {
        String tenantId = dataIdKey.getTenantId();
        String dataId = dataIdKey.getDataId();
        String metricId;
        MetricType type;
        if (dataId.startsWith(ALERT_AVAILABILITY)) {
            type = MetricType.AVAILABILITY;
            metricId = dataId.substring(ALERT_AVAILABILITY.length());
        } else if (dataId.startsWith(ALERT_GAUGE)) {
            type = MetricType.GAUGE;
            metricId = dataId.substring(ALERT_GAUGE.length());
        } else if (dataId.startsWith(ALERT_GAUGE_RATE)) {
            type = MetricType.GAUGE_RATE;
            metricId = dataId.substring(ALERT_GAUGE_RATE.length());
        } else if (dataId.startsWith(ALERT_COUNTER)) {
            type = MetricType.COUNTER;
            metricId = dataId.substring(ALERT_COUNTER.length());
        } else if (dataId.startsWith(ALERT_COUNTER_RATE)) {
            type = MetricType.COUNTER_RATE;
            metricId = dataId.substring(ALERT_COUNTER_RATE.length());
        } else if (dataId.startsWith(ALERT_STRING)) {
            type = MetricType.STRING;
            metricId = dataId.substring(ALERT_STRING.length());
        } else {
            if (log.isDebugEnabled()) {
                log.debug("DataId " + dataId + " doesn't have a valid metrics type.");
            }
            return null;
        }
        return new MetricId(tenantId, type, metricId);
    }

    private String convert(MetricId id) {
        return new StringBuilder(id.getTenantId() == null ? "" : id.getTenantId()).append("-")
                .append((id.getType() == null ? "" : id.getType().getText())).append("-")
                .append((id.getName() == null ? "" : id.getName())).toString();
    }

    public static class DataIdKey {
        private String tenantId;
        private String dataId;

        public DataIdKey(String tenantId, String dataId) {
            this.tenantId = tenantId;
            this.dataId = dataId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getDataId() {
            return dataId;
        }

        public void setDataId(String dataId) {
            this.dataId = dataId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DataIdKey dataIdKey = (DataIdKey) o;

            if (tenantId != null ? !tenantId.equals(dataIdKey.tenantId) : dataIdKey.tenantId != null) return false;
            return dataId != null ? dataId.equals(dataIdKey.dataId) : dataIdKey.dataId == null;

        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
            return result;
        }
    }


}
