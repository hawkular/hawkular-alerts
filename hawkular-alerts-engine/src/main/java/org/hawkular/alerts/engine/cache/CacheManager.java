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
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.filter.CacheKey;
import org.infinispan.Cache;
import org.infinispan.CacheSet;
import org.jboss.logging.Logger;

/**
 * Manages the cache of globally active dataIds. Incoming Data and Events with Ids not in the cache will be filtered
 * away.  Only Data and Events with active Ids will be forwarded (published) to the engine for evaluation.
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

    private static final String DISABLE_PUBLISH_FILTERING_PROP = "hawkular-alerts.disable-publish-filtering";
    private static final String DISABLE_PUBLISH_FILTERING_ENV = "DISABLE_PUBLISH_FILTERING";
    private static final String RESET_PUBLISH_CACHE_PROP = "hawkular-alerts.reset-publish-cache";
    private static final String RESET_PUBLISH_CACHE_ENV = "RESET_PUBLISH_CACHE";

    @EJB
    PropertiesService properties;

    @EJB
    DefinitionsService definitions;

    // Note, Only the cache key is being used to today. The values are currently set to "" and reserved for future use.
    @Resource(lookup = "java:jboss/infinispan/cache/hawkular-alerts/publish")
    private Cache<CacheKey, String> publishCache;

    @PostConstruct
    public void init() {
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
                CacheKey cacheKey = new CacheKey(c.getTenantId(), c.getDataId());
                if (!activeKeys.contains(cacheKey)) {
                    activeKeys.add(cacheKey);
                    if (!currentlyPublished.contains(cacheKey)) {
                        publish(cacheKey);
                    }
                }
                if (c instanceof CompareCondition) {
                    String data2Id = ((CompareCondition) c).getData2Id();
                    CacheKey cacheKey2 = new CacheKey(c.getTenantId(), data2Id);
                    if (!activeKeys.contains(cacheKey2)) {
                        activeKeys.add(cacheKey2);
                        if (!currentlyPublished.contains(cacheKey2)) {
                            publish(cacheKey2);
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

    private void publish(CacheKey cacheKey) {
        if (cacheKey != null && publishCache != null) {
            // This logic assumes that alerting is the only writer for the shared publishCache
            log.debugf("Publishing:%s ", cacheKey);
            publishCache.put(cacheKey, "");

            //            This alternative logic assumes that more writers can add/remove entries into the shared publishCache.
            //
            //            Set<String> updatedSubscribers;
            //            if (publishCache.containsKey(metricId)) {
            //                Set<String> subscribers = (Set<String>) publishCache.get(metricId);
            //                if (!subscribers.contains(ALERTING)) {
            //                    updatedSubscribers = new HashSet<>(subscribers);
            //                    updatedSubscribers.add(ALERTING);
            //                    publishCache.put(metricId, updatedSubscribers);
            //                }
            //            } else {
            //                updatedSubscribers = new HashSet<>();
            //                updatedSubscribers.add(ALERTING);
            //                publishCache.put(metricId, updatedSubscribers);
            //            }
        }
    }

    private void unpublish(Set<CacheKey> cacheKeys) {
        for (CacheKey cacheKey : cacheKeys) {
            if (cacheKey != null && publishCache != null) {
                //This logic assumes that alerting is the only writer for the shared publishCache
                log.debugf("UN-Publishing:%s ", cacheKey);
                publishCache.remove(cacheKey);

                //                This alternative logic assumes that more writers can add/remove entries into the shared publishCache.
                //
                //                if (publishCache.containsKey(metricId)) {
                //                    Set<String> updatedSubscribers;
                //                    Set<String> subscribers = (Set<String>) publishCache.get(metricId);
                //                    if (subscribers.contains(ALERTING)) {
                //                        updatedSubscribers = new HashSet<>(subscribers);
                //                        updatedSubscribers.remove(ALERTING);
                //                        if (updatedSubscribers.isEmpty()) {
                //                            publishCache.remove(metricId);
                //                        } else {
                //                            publishCache.put(metricId, updatedSubscribers);
                //                        }
                //                    }
                //                }
            }
        }
    }
}
