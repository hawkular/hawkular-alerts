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
package org.hawkular.alerts.api.services;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;

/**
 * Interface that allows to send data to the alerts engine and check resulting state.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface AlertsService {

    void sendData(Data data);

    void sendData(Collection<Data> data);

    /**
     * Reset session state.
     */
    void clear();

    /**
     * Reload all Triggers.
     */
    void reload();

    /**
     * Reload the specified Trigger.  Removes any existing definition from the engine.  If enabled then loads the firing
     * condition set and dampening.  If safetyEnabled then also loads the safety condition set and dampening.
     * @param tenantId Tenant where Trigger is stored
     * @param triggerId Trigger id to be reloaded
     */
    void reloadTrigger(String tenantId, String triggerId);

    /**
     * @param tenantId Tenant where alerts are stored
     * @param criteria If null returns all alerts (not recommended)
     * @return NotNull, can be empty.
     * @throws Exception any problem
     */
    List<Alert> getAlerts(String tenantId, AlertsCriteria criteria) throws Exception;

    /**
     * Persist the provided alerts.
     * @param alerts Set of unpersisted Alerts.
     * @throws Exception any problem
     */
    void addAlerts(Collection<Alert> alerts) throws Exception;

    /**
     * The alerts must already have been added. Set the alerts to ACKNOWLEDGED status. The ackTime will be set to the
     * system time.
     * @param tenantId Tenant where alerts are stored
     * @param alertIds Alerts to be acknowledged.
     * @param ackBy Optional. Typically the user acknowledging the alerts.
     * @param ackNotes Optional notes about the acknowledgement.
     * @throws Exception any problem
     */
    void ackAlerts(String tenantId, Collection<String> alertIds, String ackBy, String ackNotes) throws Exception;

    /**
     * The alerts must already have been added. Set the alerts to RESOLVED status. The resolvedTime will be set to the
     * system time.
     * @param tenantId Tenant where alerts are stored
     * @param alertIds Alerts to be acknowledged.
     * @param resolvedBy Optional. Typically the user resolving the alerts.
     * @param resolvedNotes Optional notes about the resolution.
     * @param resolvedEvalSets Optional. Typically the evalSets leading to an auto-resolved alert.
     * @throws Exception any problem
     */
    void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception;

    /**
     * Set unresolved alerts for the provided trigger to RESOLVED status. The resolvedTime will be set to the
     * system time.
     * @param tenantId Tenant where alerts are stored
     * @param triggerId Tenant where alerts are stored
     * @param resolvedBy Optional. Typically the user resolving the alerts.
     * @param resolvedNotes Optional notes about the resolution.
     * @param resolvedEvalSets Optional. Typically the evalSets leading to an auto-resolved alert.
     * @throws Exception any problem
     */
    void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception;

}
