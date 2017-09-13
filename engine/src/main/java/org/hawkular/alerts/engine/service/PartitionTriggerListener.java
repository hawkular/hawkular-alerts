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
package org.hawkular.alerts.engine.service;

import java.util.List;
import java.util.Map;

import org.hawkular.alerts.engine.service.PartitionManager.Operation;

/**
 * A listener for reacting to partition events related to triggers.
 *
 * We have two main types of partition events:
 *
 *  - Trigger changes: node-a changes a trigger that is being computed in node-c, so in this case a partition
 *  event is sent to make node-c reload/remove trigger depending of the type of operation (add, update, remove).
 *
 *  - Partition changes: a topology is re-configured adding/removing nodes, in this case, a partition could
 *  re-organize the triggers across nodes. A node receive a list of old entries, and new entries to maintain the
 *  existing ones, remove the moved entries and reload the new ones.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface PartitionTriggerListener {

    /**
     * Invoked on the node that holds the trigger when a trigger has been added, modified or removed.
     *
     * @param operation the operation performed on the Trigger
     * @param tenantId Tenant where Trigger is stored
     * @param triggerId Trigger id
     */
    void onTriggerChange(Operation operation, String tenantId, String triggerId);

    /**
     * Invoked when the topology has changed in the partition.
     * It updates the local partition after re-calculate triggers distribution across the partition.
     *
     * A local partition has the following structure:
     *
     *      Map<String, List<String>>:
     *      {
     *          "tenant1": ["trigger1","trigger2","trigger3"],
     *          "tenant2": ["trigger4","trigger5","trigger6"]
     *      }
     *
     * @param partition a Map with the local partition of triggers hold by this node after calculation
     * @param removed a Map with the triggers removed from an old partition after calculation
     * @param added a Map with the triggers added from an old partition after calculation
     */
    void onPartitionChange(Map<String, List<String>> partition, Map<String, List<String>> removed,
                           Map<String, List<String>> added);
}
