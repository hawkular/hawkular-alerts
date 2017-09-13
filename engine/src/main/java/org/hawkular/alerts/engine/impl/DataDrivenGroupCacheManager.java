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
package org.hawkular.alerts.engine.impl;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerType;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;

/**
 * A helper class to keep track of DataDrivenGroup
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class DataDrivenGroupCacheManager {
    private final MsgLogger log = MsgLogging.getMsgLogger(DataDrivenGroupCacheManager.class);

    private static final String DATA_DRIVEN_TRIGGERS_ENABLED = "hawkular-alerts.data-driven-triggers-enabled";
    private static final String DATA_DRIVEN_TRIGGERS_ENABLED_DEFAULT = "true";

    private boolean dataDrivenTriggersEnabled;

    // The sources with member triggers for the dataId.
    // - null if dataId is not used in a group trigger condition
    // - EmptySet if no source members are yet defined
    Map<CacheKey, Set<String>> sourcesMap = new HashMap<>();
    // The group triggerIds relevant to the dataId, null if none
    Map<CacheKey, Set<String>> triggersMap = new HashMap<>();

    private volatile boolean updateRequested = false;
    private volatile boolean updating = false;

    DefinitionsService definitions;

    public void setDefinitions(DefinitionsService definitions) {
        this.definitions = definitions;
    }

    public void init() {
        dataDrivenTriggersEnabled = new Boolean(HawkularProperties.getProperty(DATA_DRIVEN_TRIGGERS_ENABLED,
                DATA_DRIVEN_TRIGGERS_ENABLED_DEFAULT));

        log.infof("Data-driven Group Triggers enabled: %s", dataDrivenTriggersEnabled);

        if (dataDrivenTriggersEnabled) {

            requestCacheUpdate();

            definitions.registerListener(e -> {
                    requestCacheUpdate();
            }, DefinitionsEvent.Type.TRIGGER_CONDITION_CHANGE);
        }
    }

    // Just run updateCache one time if multiple requests come in while an update is already in progress...
    private void requestCacheUpdate() {
        log.debug("Cache update requested");
        if (updateRequested) {
            log.debug("Cache update, redundant request ignored.");
            return;
        }

        updateRequested = true;

        if (!updating) {
            updateCache();
        }
    }

    private synchronized void updateCache() {
        log.debug("Updating cache...");

        try {
            updating = true;

            while (updateRequested) {
                updateRequested = false;
                log.debug("Cache update in progress..");

                Collection<Trigger> allTriggers = definitions.getAllTriggers();
                Set<Trigger> ddGroupTriggers = new HashSet<>();
                for (Trigger t : allTriggers) {
                    if (TriggerType.DATA_DRIVEN_GROUP == t.getType()) {
                        ddGroupTriggers.add(t);
                    }
                }

                log.debugf("Updating [%s] data-driven triggers out of [%s] total triggers...", ddGroupTriggers.size(),
                        allTriggers.size());

                for (Trigger groupTrigger : ddGroupTriggers) {
                    String tenantId = groupTrigger.getTenantId();
                    Set<String> sources = new HashSet<>();
                    for (Trigger memberTrigger : definitions.getMemberTriggers(tenantId, groupTrigger.getId(),
                            false)) {
                        sources.add(memberTrigger.getSource());
                    }
                    for (Condition c : definitions.getTriggerConditions(tenantId, groupTrigger.getId(), null)) {
                        CacheKey key = new CacheKey(tenantId, c.getDataId());
                        sourcesMap.put(key, sources);
                        Set<String> triggers = triggersMap.get(key);
                        if (null == triggers) {
                            triggers = new HashSet<>();
                        }
                        triggers.add(groupTrigger.getId());
                        triggersMap.put(key, triggers);

                        if (c instanceof CompareCondition) {
                            key = new CacheKey(tenantId, ((CompareCondition) c).getData2Id());
                            sourcesMap.put(key, sources);

                            triggers = triggersMap.get(key);
                            if (null == triggers) {
                                triggers = new HashSet<>();
                            }
                            triggers.add(groupTrigger.getId());
                            triggersMap.put(key, triggers);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("FAILED to updateCache. Unable to generate data-driven member triggers!", e);
            sourcesMap = new HashMap<>();
        } finally {
            log.debugf("Cache updates complete. sourceMap: %s", sourcesMap);
            updating = false;
        }
    }

    public boolean isCacheActive() {
        return !sourcesMap.isEmpty();
    }

    public Set<String> needsSourceMember(String tenantId, String dataId, String source) {
        if (isEmpty(source, dataId, tenantId) || Data.SOURCE_NONE.equals(source)) {
            return Collections.emptySet();
        }

        CacheKey key = new CacheKey(tenantId, dataId);

        // if the dataId is not relevant to any group triggers just return empty set
        if (null == triggersMap.get(key)) {
            return Collections.emptySet();
        }

        // if the dataId is relevant to group triggers but the source is already known just return empty set
        Set<String> sources = sourcesMap.get(key);
        if (sources.contains(source)) {
            return Collections.emptySet();
        }

        // otherwise, return the triggers that need a member for this source
        return triggersMap.get(key);
    }

    private static class CacheKey {
        private String tenantId;
        private String dataId;

        public CacheKey(String tenantId, String dataId) {
            super();
            this.tenantId = tenantId;
            this.dataId = dataId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getDataId() {
            return dataId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((dataId == null) ? 0 : dataId.hashCode());
            result = prime * result + ((tenantId == null) ? 0 : tenantId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (dataId == null) {
                if (other.dataId != null)
                    return false;
            } else if (!dataId.equals(other.dataId))
                return false;
            if (tenantId == null) {
                if (other.tenantId != null)
                    return false;
            } else if (!tenantId.equals(other.tenantId))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "CacheKey [" + tenantId + ":" + dataId + "]";
        }

    }
}
