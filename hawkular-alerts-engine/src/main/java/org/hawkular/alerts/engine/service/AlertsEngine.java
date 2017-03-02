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

import java.util.TreeSet;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Interface that allows to send data to the alerts engine and check resulting state. All methods are LockType.WRITE
 * unless otherwise specified.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface AlertsEngine {

    /**
     * Reset session state.
     */
    void clear();

    /**
     * @param trigger the trigger for which the loaded version is requested.
     * @return the Trigger with the current engine state, or null if the trigger is not found in the engine
     */
    Trigger getLoadedTrigger(Trigger trigger);

    /**
     * Send data into the alerting system for evaluation. This method has LockType.READ.
     *
     * @param data Not Null.  The data to be evaluated by the alerting engine.
     * @throws Exception any problem.
     */
    void sendData(TreeSet<Data> data) throws Exception;

    /**
     * Send event into the alerting system for evaluation. Events are persisted after inference.
     * This method has LockType.READ.
     *
     * @param events Not Null. The events to be evaluated and persisted by the alerting engine.
     * @throws Exception any problem
     */
    void sendEvents(TreeSet<Event> events) throws Exception;

    /**
     * Reload all Triggers.
     */
    void reload();

    /**
     * Notify AlertsEngine that a new trigger is going to be loaded.
     * This method is used for distributed scenarios, where the AlertsEngine can distribute the trigger on a node.
     * For non-distributed scenarios, this call has not effect as trigger is loaded on local AlertsEngine on new
     * additions or updates of dampening,conditions or existing triggers.
     *
     * @param tenantId Tenant where Trigger is stored
     * @param triggerId Trigger id to be reloaded
     */
    void addTrigger(String tenantId, String triggerId);

    /**
     * Reload the specified Trigger.  Removes any existing definition from the engine.  If enabled then loads the
     * firing and autoResolve condition sets and dampening.
     * @param tenantId Tenant where Trigger is stored
     * @param triggerId Trigger id to be reloaded
     */
    void reloadTrigger(String tenantId, String triggerId);

    /**
     * Remove the specified Trigger from the engine.
     * @param tenantId Tenant where Trigger is stored
     * @param triggerId Trigger id to be removed
     */
    void removeTrigger(String tenantId, String triggerId);
}
