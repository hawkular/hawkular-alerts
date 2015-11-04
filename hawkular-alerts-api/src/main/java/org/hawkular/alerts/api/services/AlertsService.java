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

import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;

/**
 * Interface that allows to send data to the alerts engine and check resulting state.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface AlertsService {

    /**
     * The alerts must already have been added. Set the alerts to ACKNOWLEDGED status. The ackTime will be set to the
     * system time.
     * @param tenantId Tenant where alerts are stored
     * @param alertIds Alerts to be acknowledged.
     * @param ackBy Optional. Typically the user acknowledging the alerts. "unknown" if not specified.
     * @param ackNotes Optional notes about the acknowledgement. "none" if not specified.
     * @throws Exception any problem
     */
    void ackAlerts(String tenantId, Collection<String> alertIds, String ackBy, String ackNotes) throws Exception;

    /**
     * Persist the provided alerts. Note that every alert will also get a corresponding event.
     * @param alerts Set of unpersisted Alerts.
     * @throws Exception any problem
     */
    void addAlerts(Collection<Alert> alerts) throws Exception;

    /**
     * Persist the provided events.
     * @param events Set of unpersisted Events.
     * @throws Exception any problem
     */
    void addEvents(Collection<Event> events) throws Exception;

    /**
     * Add a note on an existing Alert.
     * If alertId doesn't exist then the note is ignored.
     * @param tenantId Tenant where alerts are stored
     * @param alertId Alert to be added a new note
     * @param user The user adding the note
     * @param text The content of the note
     * @throws Exception any problem
     */
    void addNote(String tenantId, String alertId, String user, String text) throws Exception;

    /**
     * Delete the requested Alerts, as described by the provided criteria.
     * @param tenantId Tenant where alerts are stored
     * @param criteria specifying the Alerts to be deleted. Not null.
     * @returns the number of alerts deleted
     * @throws Exception any problem
     */
    int deleteAlerts(String tenantId, AlertsCriteria criteria) throws Exception;

    /**
     * Delete the requested Events, as described by the provided criteria.
     * @param tenantId Tenant where alerts are stored
     * @param criteria specifying the Events to be deleted. Not null.
     * @returns the number of events deleted
     * @throws Exception any problem
     */
    int deleteEvents(String tenantId, EventsCriteria criteria) throws Exception;

    /**
     * @param tenantId Tenant where alerts are stored
     * @param alertId the Alert to get.
     * @param thin If true don't include evalSets and resolveEvalSets in the returned Alert
     * @return the Alert or null if not found.
     * @throws Exception any problem
     */
    Alert getAlert(String tenantId, String alertId, boolean thin) throws Exception;

    /**
     * @param tenantId Tenant where alerts are stored
     * @param criteria If null returns all alerts (not recommended)
     * @param pager Paging requirement for fetching alerts. Optional. Return all if null.
     * @return NotNull, can be empty.
     * @throws Exception any problem
     */
    Page<Alert> getAlerts(String tenantId, AlertsCriteria criteria, Pager pager) throws Exception;

    /**
     * @param tenantId Tenant where events are stored
     * @param eventId the Event to get.
     * @param thin If true don't include evalSets in the returned Event
     * @return the Event or null if not found.
     * @throws Exception any problem
     */
    Event getEvent(String tenantId, String eventId, boolean thin) throws Exception;

    /**
     * @param tenantId Tenant where events are stored
     * @param criteria If null returns all events (not recommended)
     * @param pager Paging requirement for fetching events. Optional. Return all if null.
     * @return NotNull, can be empty.
     * @throws Exception any problem
     */
    Page<Event> getEvents(String tenantId, EventsCriteria criteria, Pager pager) throws Exception;

    /**
     * The alerts must already have been added. Set the alerts to RESOLVED status. The resolvedTime will be set to the
     * system time.  If the call leaves the trigger with no unresolved alerts then:<br>
     * - If the trigger has <code>autoEnable=true</code> it will be enabled, as needed.<br>
     * - If the trigger has <code>autoResolve=true</code> it will be set to firing mode, as needed.
     * @param tenantId Tenant where alerts are stored
     * @param alertIds Alerts to be acknowledged.
     * @param resolvedBy Optional. Typically the user resolving the alerts. "unknown" if not specified.
     * @param resolvedNotes Optional notes about the resolution. "none" if not specified.
     * @param resolvedEvalSets Optional. Typically the evalSets leading to an auto-resolved alert.
     * @throws Exception any problem
     */
    void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception;

    /**
     * Set unresolved alerts for the provided trigger to RESOLVED status. The resolvedTime will be set to the
     * system time.<br>
     * - If the trigger has <code>autoEnable=true</code> it will be enabled, as needed.<br>
     * - If the trigger has <code>autoResolve=true</code> it will be set to firing mode, as needed.
     * @param tenantId Tenant where alerts are stored
     * @param triggerId Tenant where alerts are stored
     * @param resolvedBy Optional. Typically the user resolving the alerts. "unknown" if not specified.
     * @param resolvedNotes Optional notes about the resolution. "none" if not specified.
     * @param resolvedEvalSets Optional. Typically the evalSets leading to an auto-resolved alert.
     * @throws Exception any problem
     */
    void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception;

    /**
     * Send data into the alerting system for evaluation.
     *
     * @param data Not Null.  The data to be evaluated by the alerting engine.
     * @throws Exception any problem.
     */
    void sendData(Data data) throws Exception;

    /**
     * Send data into the alerting system for evaluation.
     *
     * @param data Not Null.  The data to be evaluated by the alerting engine.
     * @throws Exception any problem.
     */
    void sendData(Collection<Data> data) throws Exception;

    /**
     * Send event into the alerting system for evaluation.
     * Events are persisted after inference.
     * addEvents() only persist events but not put it on the engine for evaluation.
     *
     * @param event Not Null. The events to be avaluated and persisted by the alerting engine.
     * @throws Exception any problem
     */
    void sendEvent(Event event) throws Exception;

    /**
     * Send event into the alerting system for evaluation.
     * Events are persisted after inference.
     * addEvents() only persist events but not put it on the engine for evaluation.
     *
     * @param events Not Null. The events to be avaluated and persisted by the alerting engine.
     * @throws Exception any problem
     */
    void sendEvents(Collection<Event> events) throws Exception;

}
