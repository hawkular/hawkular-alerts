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
package org.hawkular.alerts.engine.cache;

import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.CONDITION_CHANGE;
import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.TRIGGER_REMOVE;

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

import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.data.CacheKey;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.jboss.logging.Logger;

/**
 * A helper class to initialize bus callbacks into the alerts engine.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@Startup
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class CacheManager {
    private final Logger log = Logger.getLogger(CacheManager.class);
    private final MsgLogger msgLog = MsgLogger.LOGGER;

    private static final String PUBLISH_REQUIRE_PREFIX_PROP = "hawkular-alerts.publish-require-prefix";
    private static final String PUBLISH_REQUIRE_PREFIX_ENV = "PUBLISH_REQUIRE_PREFIX";
    private static final String DISABLE_PUBLISH_FILTERING_PROP = "hawkular-alerts.disable-publish-filtering";
    private static final String DISABLE_PUBLISH_FILTERING_ENV = "DISABLE_PUBLISH_FILTERING";
    private static final String RESET_PUBLISH_CACHE_PROP = "hawkular-alerts.reset-publish-cache";
    private static final String RESET_PUBLISH_CACHE_ENV = "RESET_PUBLISH_CACHE";

    private static final Set<String> DATA_ID_PREFIXES = new HashSet<>();

    static {
        // TODO: For now just use the built-in H Metrics prefixes as a OOB convenience, but in the
        //       future this should maybe be configurable where a list of prefixes could be supplied
        //       via system prop.
        DATA_ID_PREFIXES.add("hm_a_");  // Metrics AVAILABILITY
        DATA_ID_PREFIXES.add("hm_c_");  // Metrics COUNTER
        DATA_ID_PREFIXES.add("hm_cr_"); // Metrics COUNTER_RATE
        DATA_ID_PREFIXES.add("hm_g_");  // Metrics GAUGE
        DATA_ID_PREFIXES.add("hm_gr_"); // Metrics GAUGE_RATE
        DATA_ID_PREFIXES.add("hm_s_");  // Metrics STRING
        DATA_ID_PREFIXES.add("hm_u_");  // Metrics UNDEFINED
    }

    private static boolean PUBLISH_REQUIRE_PREFIX;

    @EJB
    PropertiesService properties;

    @EJB
    DefinitionsService definitions;

    @Resource(lookup = "java:jboss/infinispan/cache/hawkular-alerts/publish")
    private Cache<CacheKey, String> publishCache;

    @PostConstruct
    public void init() {
        PUBLISH_REQUIRE_PREFIX = Boolean.parseBoolean(properties.getProperty(PUBLISH_REQUIRE_PREFIX_PROP,
                PUBLISH_REQUIRE_PREFIX_ENV, "true"));
        boolean disablePublish = Boolean.parseBoolean(properties.getProperty(DISABLE_PUBLISH_FILTERING_PROP,
                DISABLE_PUBLISH_FILTERING_ENV, "false"));
        boolean resetCache = Boolean.parseBoolean(properties.getProperty(RESET_PUBLISH_CACHE_PROP,
                RESET_PUBLISH_CACHE_ENV, "true"));
        if (!disablePublish) {
            if (resetCache) {
                msgLog.warnClearPublishCache();
                publishCache.clear();
            }
            msgLog.infoInitPublishCache();
            updateActiveIds();
            definitions.registerListener(e -> {
                updateActiveIds();
            }, CONDITION_CHANGE, TRIGGER_REMOVE);
        } else {
            msgLog.warnDisabledPublishCache();
        }
    }

    // TODO REMOVE THESE WHEN THE JMS LISTENERS GO AWAY !!!
    public Set<DataIdKey> getActiveDataIds() {
        return Collections.emptySet();
    }

    public Set<DataIdKey> getActiveAvailabilityIds() {
        return Collections.emptySet();
    }

    private synchronized void updateActiveIds() {
        try {
            CacheSet<CacheKey> currentlyPublished = publishCache.keySet();

            log.debugf("Published before update=%s", currentlyPublished.size());
            if (log.isTraceEnabled()) {
                publishCache.entrySet().stream().forEach(e -> log.tracef("Published: %s", e.getValue()));
            }

            // This will include group trigger conditions, which is OK because for data-driven group triggers the
            // dataIds will likely be the dataIds from the group level, made distinct by the source.
            Collection<Condition> conditions = definitions.getAllConditions();
            final Set<CacheKey> activeKeys = new HashSet<>();
            for (Condition c : conditions) {
                // external conditions are evaluated by the relevant external alerter. Data is not metrics-based
                // so don't waste energy adding it to the publishing cache.
                if (c instanceof ExternalCondition) {
                    continue;
                }

                DataIdKey dataIdKey = new DataIdKey(c.getTenantId(), c.getDataId());
                CacheKey cacheKey = dataIdKey.getCacheKey();
                if (dataIdKey.isValid() && !activeKeys.contains(cacheKey)) {
                    activeKeys.add(cacheKey);
                    if (!currentlyPublished.contains(cacheKey)) {
                        publish(dataIdKey);
                    }
                }
                if (c instanceof CompareCondition) {
                    String data2Id = ((CompareCondition) c).getData2Id();
                    DataIdKey dataIdKey2 = new DataIdKey(c.getTenantId(), data2Id);
                    CacheKey cacheKey2 = dataIdKey2.getCacheKey();
                    if (dataIdKey2.isValid() && !activeKeys.contains(cacheKey2)) {
                        activeKeys.add(cacheKey2);
                        if (!currentlyPublished.contains(cacheKey2)) {
                            publish(dataIdKey2);
                        }
                    }
                }
            }

            final Set<CacheKey> doomedKeys = new HashSet<>();
            if (!currentlyPublished.isEmpty()) {
                currentlyPublished.stream()
                .filter(k -> !activeKeys.contains(k))
                .forEach(k -> doomedKeys.add(k));
            }
            unpublish(doomedKeys);

            log.debugf("Published after update=%s", publishCache.size());
            if (log.isTraceEnabled()) {
                publishCache.entrySet().stream().forEach(e -> log.tracef("Published: %s", e.getValue()));
            }
        } catch (Exception e) {
            log.error("FAILED to load conditions to create Id filters. All data being forwarded to alerting!", e);
            return;
        }
    }

    private void publish(DataIdKey dataIdKey) {
        CacheKey cacheKey = dataIdKey.getCacheKey();
        if (cacheKey != null && publishCache != null) {
            // This logic assumes that alerting is the only writer for the shared publishCache
            log.debugf("Publishing:%s ", cacheKey);
            publishCache.put(cacheKey, dataIdKey.getDataId());

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

    private void unpublish(Set<CacheKey> cacheKeys) {
        for (CacheKey cacheKey : cacheKeys) {
            if (cacheKey != null && publishCache != null) {
                //This logic assumes that alerting is the only writer for the shared publishCache
                log.debugf("UN-Publishing:%s ", cacheKey);
                publishCache.remove(cacheKey);

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
    }

    public static class DataIdKey {
        private final Logger log = Logger.getLogger(DataIdKey.class);

        private String tenantId;
        private String dataId;
        private CacheKey cacheKey;

        public DataIdKey(String tenantId, String dataId) {
            this.tenantId = tenantId;
            this.dataId = dataId;

            String prefix = null;
            String suffix = null;
            for (String p : DATA_ID_PREFIXES) {
                if (dataId.startsWith(p)) {
                    prefix = p;
                    suffix = dataId.substring(p.length());
                    break;
                }
            }
            if (null != prefix) {
                this.cacheKey = new CacheKey(tenantId, prefix, suffix);
            } else if (!PUBLISH_REQUIRE_PREFIX) {
                log.debugf("Allowing Non-Prefixed Metric DataId: [%s]", dataId);
                this.cacheKey = new CacheKey(tenantId, "", dataId);
            } else {
                log.debugf("Ignoring Non-Prefixed Metric DataId: [%s]", dataId);
            }
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getDataId() {
            return dataId;
        }

        public CacheKey getCacheKey() {
            return cacheKey;
        }

        public boolean isValid() {
            return cacheKey != null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            DataIdKey dataIdKey = (DataIdKey) o;

            if (tenantId != null ? !tenantId.equals(dataIdKey.tenantId) : dataIdKey.tenantId != null)
                return false;
            return dataId != null ? dataId.equals(dataIdKey.dataId) : dataIdKey.dataId == null;

        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "DataIdKey [tenantId=" + tenantId + ", dataId=" + dataId + "]";
        }
    }

}
