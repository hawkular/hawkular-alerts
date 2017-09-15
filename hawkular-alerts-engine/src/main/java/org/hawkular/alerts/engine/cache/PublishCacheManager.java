/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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

import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.TRIGGER_CONDITION_CHANGE;
import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.TRIGGER_REMOVE;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.cache.IspnCacheManager;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.filter.CacheKey;
import org.infinispan.Cache;
import org.jboss.logging.Logger;

/**
 * Manages the cache of globally active dataIds. Incoming Data and Events with Ids not in the cache will be filtered
 * away.  Only Data and Events with active Ids will be forwarded (published) to the engine for evaluation.
 *
 * This implementation design initialize the cache on each new node.
 * Definitions events are not propagated into the cluster as there is not a real use case for them and
 * it can overload the clustering traffic.
 * A coordinator strategy to initialize will not help either as each new node can became coordinator.
 * So, it is tradeoff to maintain an extra state or let each node initialize the publish* caches.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
@Startup
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class PublishCacheManager {
    private final Logger log = Logger.getLogger(PublishCacheManager.class);
    private final MsgLogger msgLog = MsgLogger.LOGGER;

    private static final String DISABLE_PUBLISH_FILTERING_PROP = "hawkular-alerts.disable-publish-filtering";
    private static final String DISABLE_PUBLISH_FILTERING_ENV = "DISABLE_PUBLISH_FILTERING";
    private static final String RESET_PUBLISH_CACHE_PROP = "hawkular-alerts.reset-publish-cache";
    private static final String RESET_PUBLISH_CACHE_ENV = "RESET_PUBLISH_CACHE";

    @EJB
    PropertiesService properties;

    @EJB
    DefinitionsService definitions;

    // It stores a list of dataIds used per key (tenantId, triggerId).
    private Cache<TriggerKey, Set<String>> publishDataIdsCache;

    // It stores a list of triggerIds used per key (tenantId, dataId).
    // This cache is used by CacheClient to check which dataIds are active
    private Cache<CacheKey, Set<String>> publishCache;

    @PostConstruct
    public void init() {
        publishDataIdsCache = IspnCacheManager.getCacheManager().getCache("dataIds");
        publishCache = IspnCacheManager.getCacheManager().getCache("publish");

        boolean disablePublish = Boolean.parseBoolean(properties.getProperty(DISABLE_PUBLISH_FILTERING_PROP,
                DISABLE_PUBLISH_FILTERING_ENV, "false"));
        boolean resetCache = Boolean.parseBoolean(properties.getProperty(RESET_PUBLISH_CACHE_PROP,
                RESET_PUBLISH_CACHE_ENV, "true"));
        if (!disablePublish) {
            if (resetCache) {
                msgLog.warnClearPublishCache();
                publishCache.clear();
                publishDataIdsCache.clear();
            }
            msgLog.infoInitPublishCache();

            initialCacheUpdate();

            definitions.registerListener(events -> {
                log.debugf("Receiving %s", events);
                events.stream()
                        .forEach(e -> {
                            log.debugf("Received %s", e);
                            String tenantId = e.getTargetTenantId();
                            String triggerId = e.getTargetId();
                            TriggerKey triggerKey = new TriggerKey(tenantId, triggerId);
                            publishCache.startBatch();
                            publishDataIdsCache.startBatch();
                            switch (e.getType()) {
                                case TRIGGER_CONDITION_CHANGE: {
                                    Set<String> oldDataIds = publishDataIdsCache.getOrDefault(triggerKey,
                                            Collections.emptySet());
                                    Set<String> newDataIds = e.getDataIds();
                                    if (!oldDataIds.equals(newDataIds)) {
                                        removePublishCache(tenantId, triggerId, oldDataIds);
                                        addPublishCache(tenantId, triggerId, newDataIds);
                                        publishDataIdsCache.put(triggerKey, newDataIds);
                                    }
                                    break;
                                }
                                case TRIGGER_REMOVE: {
                                    Set<String> oldDataIds = publishDataIdsCache.get(triggerKey);
                                    removePublishCache(tenantId, triggerId, oldDataIds);
                                    publishDataIdsCache.remove(triggerKey);
                                    break;
                                }
                                default:
                                    throw new IllegalStateException("Unexpected notification: " + e.toString());
                            }
                            publishDataIdsCache.endBatch(true);
                            publishCache.endBatch(true);
                        });
            }, TRIGGER_CONDITION_CHANGE, TRIGGER_REMOVE);

        } else {
            msgLog.warnDisabledPublishCache();
        }
    }

    private void removePublishCache(String tenantId, String triggerId, Set<String> dataIds) {
        if (!isEmpty(dataIds)) {
            dataIds.stream().forEach(dataId -> {
                CacheKey cacheKey = new CacheKey(tenantId, dataId);
                Set<String> triggerIds = publishCache.get(cacheKey);
                if (!isEmpty(triggerIds)) {
                    triggerIds.remove(triggerId);
                    if (triggerIds.isEmpty()) {
                        publishCache.remove(cacheKey);
                    } else {
                        publishCache.put(cacheKey, triggerIds);
                    }
                }
            });
        }
    }

    private void addPublishCache(String tenantId, String triggerId, Set<String> dataIds) {
        if (!isEmpty(dataIds)) {
            dataIds.stream().forEach(dataId -> {
                CacheKey cacheKey = new CacheKey(tenantId, dataId);
                Set<String> triggerIds = publishCache.get(cacheKey);
                if (triggerIds == null) {
                    triggerIds = new HashSet<>();
                }
                triggerIds.add(triggerId);
                publishCache.put(cacheKey, triggerIds);
            });
        }
    }

    private void initialCacheUpdate() {
        try {
            log.debug("Initial PublishCacheManager update in progress..");

            publishCache.startBatch();
            publishDataIdsCache.startBatch();
            // This will include group trigger conditions, which is OK because for data-driven group triggers the
            // dataIds will likely be the dataIds from the group level, made distinct by the source.
            Collection<Condition> conditions = definitions.getAllConditions();
            for (Condition c : conditions) {
                String triggerId = c.getTriggerId();
                TriggerKey triggerKey = new TriggerKey(c.getTenantId(), triggerId);
                Set<String> dataIds = new HashSet<>();
                dataIds.add(c.getDataId());
                if (c instanceof CompareCondition) {
                    String data2Id = ((CompareCondition) c).getData2Id();
                    dataIds.add(data2Id);
                }
                Set<String> prevDataIds = publishDataIdsCache.get(triggerKey);
                if (prevDataIds == null) {
                    prevDataIds = new HashSet<>();
                }
                prevDataIds.addAll(dataIds);
                publishDataIdsCache.put(triggerKey, prevDataIds);
                addPublishCache(c.getTenantId(), triggerId, dataIds);
            }
            log.debugf("Published after update=%s", publishCache.size());
            if (log.isTraceEnabled()) {
                publishCache.entrySet().stream().forEach(e -> log.tracef("Published: %s", e.getValue()));
            }
            publishDataIdsCache.endBatch(true);
            publishCache.endBatch(true);
        } catch (Exception e) {
            log.error("Failed to load conditions to create Id filters. All data being forwarded to alerting!", e);
            publishDataIdsCache.endBatch(false);
            publishCache.endBatch(false);
            return;
        }
    }

    private boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }

    public static class TriggerKey implements Serializable {
        private String tenantId;
        private String triggerId;

        public TriggerKey(String tenantId, String triggerId) {
            this.tenantId = tenantId;
            this.triggerId = triggerId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getTriggerId() {
            return triggerId;
        }

        public void setTriggerId(String triggerId) {
            this.triggerId = triggerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TriggerKey that = (TriggerKey) o;

            if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
            return triggerId != null ? triggerId.equals(that.triggerId) : that.triggerId == null;

        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "TriggerKey{" +
                    "tenantId='" + tenantId + '\'' +
                    ", triggerId='" + triggerId + '\'' +
                    '}';
        }
    }
}
