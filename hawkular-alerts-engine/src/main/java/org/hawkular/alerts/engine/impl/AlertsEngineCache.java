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
package org.hawkular.alerts.engine.impl;

import java.util.HashSet;
import java.util.Set;

/**
 * Auxiliary cache for AlertsEngine implementation.
 *
 * It stores a lightweight cache with active dataIds and pointers to triggerIds and conditionIds for the current node.
 * This cache allows to filter unnecessary data that is processed by the RulesEngine.
 *
 * This scenario works on single or distributed deployments.
 * In distributed deployments this helps to avoid unnecessary processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertsEngineCache {

    /**
     * It represents the Triggers -> Conditions relation hold on this node.
     * A single dataId can be part of several triggers or even several conditions.
     */
    private Set<DataEntry> activeDataEntries;

    /**
     * A cache of the dataIds hold. Used to filter if a data has a dataId on this node or not.
     */
    private Set<String> activeDataIds;

    public AlertsEngineCache() {
        activeDataEntries = new HashSet<>();
        activeDataIds = new HashSet<>();
    }

    /**
     * Check if a specific dataId is active on this node
     *
     * @param dataId to check if it has triggers deployed on this node
     * @return true if it is active
     *         false otherwise
     */
    public boolean isDataIdActive(String dataId) {
        return dataId != null && activeDataIds.contains(dataId);
    }

    /**
     * Register a new DataEntry (triggerId,conditionId,dataId)
     *
     * @param dataEntry to register on this node
     */
    public void add(DataEntry dataEntry) {
        activeDataEntries.add(dataEntry);
        if (!activeDataIds.contains(dataEntry.dataId)) {
            activeDataIds.add(dataEntry.dataId);
        }
    }

    /**
     * Remove all DataEntry for a specified trigger.
     *
     * @param triggerId to remove
     */
    public void remove(String tenantId, String triggerId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must be not null");
        }
        if (triggerId == null) {
            throw new IllegalArgumentException("triggerId must be not null");
        }
        Set<DataEntry> dataEntriesToRemove = new HashSet<>();
        activeDataEntries.stream().forEach(e -> {
            if (e.getTenantId().equals(tenantId) && e.getTriggerId().equals(triggerId)) {
                dataEntriesToRemove.add(e);
            }
        });
        activeDataEntries.removeAll(dataEntriesToRemove);
        Set<String> dataIdToCheck = new HashSet<>();
        dataEntriesToRemove.stream().forEach(e -> {
            dataIdToCheck.add(e.getDataId());
        });
        Set<String> dataIdToRemove = new HashSet<>();
        dataIdToCheck.stream().forEach(dataId -> {
            boolean found = false;
            for (DataEntry entry : activeDataEntries) {
                if (entry.getDataId().equals(dataId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                dataIdToRemove.add(dataId);
            }
        });
        activeDataIds.removeAll(dataIdToRemove);
    }

    /**
     * Clear all cache entries.
     */
    public void clear() {
        activeDataEntries.clear();
        activeDataIds.clear();
    }

    public static class DataEntry {
        String tenantId;
        String triggerId;
        String conditionId;
        String dataId;

        public DataEntry(String tenantId, String triggerId, String conditionId, String dataId) {
            if (tenantId == null) {
                throw new NullPointerException("triggerId must be not null");
            }
            if (triggerId == null) {
                throw new NullPointerException("triggerId must be not null");
            }
            if (conditionId == null) {
                throw new NullPointerException("conditionId must be not null");
            }
            if (dataId == null) {
                throw new NullPointerException("dataId must be not null");
            }
            this.tenantId = tenantId;
            this.triggerId = triggerId;
            this.conditionId = conditionId;
            this.dataId = dataId;
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

        public String getConditionId() {
            return conditionId;
        }

        public void setConditionId(String conditionId) {
            this.conditionId = conditionId;
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

            DataEntry dataEntry = (DataEntry) o;

            if (tenantId != null ? !tenantId.equals(dataEntry.tenantId) : dataEntry.tenantId != null) return false;
            if (triggerId != null ? !triggerId.equals(dataEntry.triggerId) : dataEntry.triggerId != null) return false;
            if (conditionId != null ? !conditionId.equals(dataEntry.conditionId) : dataEntry.conditionId != null)
                return false;
            return !(dataId != null ? !dataId.equals(dataEntry.dataId) : dataEntry.dataId != null);

        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
            result = 31 * result + (conditionId != null ? conditionId.hashCode() : 0);
            result = 31 * result + (dataId != null ? dataId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "DataEntry" + '[' +
                    "tenantId='" + tenantId + '\'' +
                    ", triggerId='" + triggerId + '\'' +
                    ", conditionId='" + conditionId + '\'' +
                    ", dataId='" + dataId + '\'' +
                    ']';
        }
    }

}
