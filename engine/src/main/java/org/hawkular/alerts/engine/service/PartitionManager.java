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

import java.util.Collection;
import java.util.Map;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;

/**
 * Interface that defines an abstract API with the clustering services used by the engine.
 * In a no distributed scenario, a single node holds all triggers to process in the AlertsEngine.
 *
 * In distributed scenarios triggers are partitioned across nodes in a consistent hashing algorithm.
 *
 * PartitionManager is responsible to notify when a trigger has been added, modified, removed, in order to
 * update the AlertsEngine state.
 *
 * PartionManager is responsible to detect changes on the topology to reconfigure the partition of the triggers in a
 * transparent way when a new node is added/removed from the cluster.
 *
 * PartitionManager is also responsible to notify when a new data has been received, in order to deliver to the
 * AlertsEngine node where trigger is active.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface PartitionManager {

    enum Operation {

        /**
         * A new trigger has been added
         */
        ADD,

        /**
         * An existing trigger or any of its related conditions or dampenings have been modified.
         */
        UPDATE,

        /**
         * An existing trigger has been removed
         */
        REMOVE
    }

    /**
     * Detects if PartitionManager is deployed on a distributed scenario.
     * PartitionManager is always present to the engine, but only active on ha profile like defined on standalone-ha.xml
     * {@see PartitionTriggerListener} and {@see PartitionDataListener} are ignored on non-distributed scenario.
     * {@see PartitionManager#notifyTrigger}, {@see PartitionManager#notifyData} and
     * {@see PartitionManager#notifyEvent} are ignored on non-distributed scenario.
     *
     * @return true if PartitionManager is distributed.
     *         false otherwise
     */
    boolean isDistributed();

    /**
     * Show additional information about partition status.
     * In distributed scenarios
     *  - getStatus().get("currentNode") returns a string with the identifier of the current node
     *  - getStatus().get("members") returns a string with a list comma identifiers of the topology nodes
     *    at the moment of the call
     * In standalone scenarios getStatus() returns an empty map.
     *
     * @return Map with currentNode and members information for distributed scenarios
     */
    Map<String, String> getStatus();

    /**
     * Notify partition manager when a trigger, dampening or condition has been added,updated or removed.
     * PartitionManager will assign the trigger to a node a will notify all nodes with the change.
     *
     * @param operation type of operation performed on the trigger
     * @param tenantId Tenant where Trigger is stored
     * @param triggerId Trigger id
     */
    void notifyTrigger(Operation operation, String tenantId, String triggerId);

    /**
     * Register a listener to process partition events linked with triggers.
     *
     * @param triggerListener the listener
     */
    void registerTriggerListener(PartitionTriggerListener triggerListener);

    /**
     * Notify partition manager when a new collection of data has been received.
     *
     * @param data the new data received by the engine
     */
    void notifyData(Collection<Data> data);

    /**
     * Notify partition manager when a new collection of events has been received.
     *
     * @param events the new events received by the engine
     */
    void notifyEvents(Collection<Event> events);

    /**
     * Register a listener to process partition events linked with data or events.
     *
     * @param dataListener the listener
     */
    void registerDataListener(PartitionDataListener dataListener);
}
