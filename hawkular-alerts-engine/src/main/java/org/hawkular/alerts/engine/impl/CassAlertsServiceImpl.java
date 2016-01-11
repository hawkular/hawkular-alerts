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
package org.hawkular.alerts.engine.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.AlertComparator.Field;
import org.hawkular.alerts.api.model.paging.EventComparator;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.jboss.logging.Logger;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.util.concurrent.Futures;

/**
 * Cassandra implementation for {@link org.hawkular.alerts.api.services.AlertsService}.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Local(AlertsService.class)
@Stateless
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class CassAlertsServiceImpl implements AlertsService {

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(CassAlertsServiceImpl.class);

    private Session session;

    @EJB
    AlertsEngine alertsEngine;

    @EJB
    DefinitionsService definitionsService;

    @EJB
    ActionsService actionsService;

    public CassAlertsServiceImpl() {
    }

    @Override
    public void addAlerts(Collection<Alert> alerts) throws Exception {
        if (alerts == null) {
            throw new IllegalArgumentException("Alerts must be not null");
        }
        if (alerts.isEmpty()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Adding " + alerts.size() + " alerts");
        }
        session = CassCluster.getSession();
        PreparedStatement insertAlert = CassStatement.get(session, CassStatement.INSERT_ALERT);
        PreparedStatement insertAlertTrigger = CassStatement.get(session, CassStatement.INSERT_ALERT_TRIGGER);
        PreparedStatement insertAlertCtime = CassStatement.get(session, CassStatement.INSERT_ALERT_CTIME);
        PreparedStatement insertAlertStatus = CassStatement.get(session, CassStatement.INSERT_ALERT_STATUS);
        PreparedStatement insertAlertSeverity = CassStatement.get(session, CassStatement.INSERT_ALERT_SEVERITY);
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            alerts.stream().forEach(a -> {
                futures.add(session.executeAsync(insertAlert.bind(a.getTenantId(), a.getAlertId(),
                        JsonUtil.toJson(a))));
                futures.add(session.executeAsync(insertAlertTrigger.bind(a.getTenantId(),
                        a.getAlertId(),
                        a.getTriggerId())));
                futures.add(session.executeAsync(insertAlertCtime.bind(a.getTenantId(),
                        a.getAlertId(), a.getCtime())));
                futures.add(session.executeAsync(insertAlertStatus.bind(a.getTenantId(),
                        a.getAlertId(),
                        a.getStatus().name())));
                futures.add(session.executeAsync(insertAlertSeverity.bind(a.getTenantId(),
                        a.getAlertId(),
                        a.getSeverity().name())));

                a.getTags().entrySet().stream().forEach(tag -> {
                    futures.add(session.executeAsync(insertTag.bind(a.getTenantId(), TagType.ALERT.name(),
                            tag.getKey(), tag.getValue(), a.getId())));
                });
            });
            /*
                main method is synchronous so we need to wait until futures are completed
             */
            Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        // Every Alert has a corresponding Event
        List<Event> events = alerts.stream()
                .map(Event::new)
                .collect(Collectors.toList());
        persistEvents(events);
    }

    @Override
    public void persistEvents(Collection<Event> events) throws Exception {
        if (events == null) {
            throw new IllegalArgumentException("Events must be not null");
        }
        if (events.isEmpty()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Adding " + events.size() + " events");
        }
        session = CassCluster.getSession();
        PreparedStatement insertEvent = CassStatement.get(session, CassStatement.INSERT_EVENT);
        PreparedStatement insertEventCategory = CassStatement.get(session, CassStatement.INSERT_EVENT_CATEGORY);
        PreparedStatement insertEventCtime = CassStatement.get(session, CassStatement.INSERT_EVENT_CTIME);
        PreparedStatement insertEventTrigger = CassStatement.get(session, CassStatement.INSERT_EVENT_TRIGGER);
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            events.stream().forEach(e -> {
                futures.add(session.executeAsync(insertEvent.bind(e.getTenantId(), e.getId(),
                        JsonUtil.toJson(e))));
                futures.add(session.executeAsync(insertEventCategory.bind(e.getTenantId(), e.getCategory(),
                        e.getId())));
                futures.add(session.executeAsync(insertEventCtime.bind(e.getTenantId(), e.getCtime(),
                        e.getId())));
                if (null != e.getTrigger()) {
                    futures.add(session.executeAsync(insertEventTrigger.bind(e.getTenantId(), e.getTrigger().getId(),
                            e.getId())));
                }
                e.getTags().entrySet().stream().forEach(tag -> {
                    futures.add(session.executeAsync(insertTag.bind(e.getTenantId(), TagType.EVENT.name(),
                            tag.getKey(), tag.getValue(), e.getId())));
                });
            });
            /*
                main method is synchronous so we need to wait until futures are completed
             */
            Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void addNote(String tenantId, String alertId, String user, String text) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertId)) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        if (isEmpty(user) || isEmpty(text)) {
            throw new IllegalArgumentException("user or text must be not null");
        }

        Alert alert = getAlert(tenantId, alertId, false);
        if (alert == null) {
            return;
        }

        alert.addNote(user, text);

        session = CassCluster.getSession();
        PreparedStatement updateAlert = CassStatement.get(session, CassStatement.UPDATE_ALERT);
        if (updateAlert == null) {
            throw new RuntimeException("updateAlert PreparedStatement is null");
        }
        try {
            session.execute(updateAlert.bind(JsonUtil.toJson(alert), alert.getTenantId(), alert.getAlertId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Alert getAlert(String tenantId, String alertId, boolean thin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertId)) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        session = CassCluster.getSession();
        PreparedStatement selectAlert = CassStatement.get(session, CassStatement.SELECT_ALERT);
        if (selectAlert == null) {
            throw new RuntimeException("selectAlert PreparedStatement is null");
        }
        Alert alert = null;
        try {
            ResultSet rsAlert = session.execute(selectAlert.bind(tenantId, alertId));
            Iterator<Row> itAlert = rsAlert.iterator();
            if (itAlert.hasNext()) {
                Row row = itAlert.next();
                alert = JsonUtil.fromJson(row.getString("payload"), Alert.class, thin);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return alert;
    }

    @Override
    public Event getEvent(String tenantId, String eventId, boolean thin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(eventId)) {
            throw new IllegalArgumentException("EventId must be not null");
        }
        session = CassCluster.getSession();
        PreparedStatement selectEvent = CassStatement.get(session, CassStatement.SELECT_EVENT);
        if (selectEvent == null) {
            throw new RuntimeException("selectEvent PreparedStatement is null");
        }
        Event event = null;
        try {
            ResultSet rsEvent = session.execute(selectEvent.bind(tenantId, eventId));
            Iterator<Row> itEvent = rsEvent.iterator();
            if (itEvent.hasNext()) {
                Row row = itEvent.next();
                event = JsonUtil.fromJson(row.getString("payload"), Event.class, thin);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return event;
    }

    // TODO (jshaughn) The DB-Level filtering approach implemented below is a best-practice for dealing
    // with Cassandra.  It's basically a series of queries, one for each filter, with a progressive
    // intersection of the resulting ID set.  This will work well in most cases but we may want to consider
    // an optimization for dealing with large Alert populations.  Certain filters dealing with low-cardinality
    // values, like status and severity, could start pulling a large number if alert ids.  If we have reduced the
    // result set to a small number, via the more narrowing filters, (TBD via perf tests, a threshold that makes
    // sense), we may want to pull the resulting alerts and apply the low-cardinality filters here in the code,
    // in a post-fetch step. For example, if we have filters "ctime > 123" and "status == Resolved", and the ctime
    // filter returns 10 alertIds. We may want to pull the 10 alerts and apply the status filter in the code. For
    // large Alert history, the status filter applied to the DB could return a huge set of ids.
    @Override
    public Page<Alert> getAlerts(String tenantId, AlertsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        session = CassCluster.getSession();
        boolean filter = (null != criteria && criteria.hasCriteria());
        boolean thin = (null != criteria && criteria.isThin());

        if (filter && log.isDebugEnabled()) {
            log.debug("getAlerts criteria: " + criteria.toString());
        }

        List<Alert> alerts = new ArrayList<>();
        Set<String> alertIds = new HashSet<>();
        boolean activeFilter = false;

        try {
            if (filter) {
                /*
                    Get alertsIds explicitly added into the criteria. Start with these as there is no query involved
                */
                if (criteria.hasAlertIdCriteria()) {
                    Set<String> alertIdsFilteredByAlerts = filterByAlerts(criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByAlerts);
                        if (alertIds.isEmpty()) {
                            return new Page<>(alerts, pager, 0);
                        }
                    } else {
                        alertIds.addAll(alertIdsFilteredByAlerts);
                    }
                    activeFilter = true;
                }

                /*
                    Get alertIds via tags
                */
                if (criteria.hasTagCriteria()) {
                    Set<String> alertIdsFilteredByTags = getIdsByTags(tenantId, TagType.ALERT, criteria.getTags());
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByTags);
                        if (alertIds.isEmpty()) {
                            return new Page<>(alerts, pager, 0);
                        }
                    } else {
                        alertIds.addAll(alertIdsFilteredByTags);
                    }
                    activeFilter = true;
                }

                /*
                    Get alertIds filtered by triggerIds clause
                 */
                if (criteria.hasTriggerIdCriteria()) {
                    Set<String> alertIdsFilteredByTriggers = filterByTriggers(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByTriggers);
                        if (alertIds.isEmpty()) {
                            return new Page<>(alerts, pager, 0);
                        }
                    } else {
                        alertIds.addAll(alertIdsFilteredByTriggers);
                    }
                    activeFilter = true;
                }

                /*
                    Get alertsIds filtered by time clause
                 */
                if (criteria.hasCTimeCriteria()) {
                    Set<String> alertIdsFilteredByTime = filterByCTime(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByTime);
                        if (alertIds.isEmpty()) {
                            return new Page<>(alerts, pager, 0);
                        }
                    } else {
                        alertIds.addAll(alertIdsFilteredByTime);
                    }
                    activeFilter = true;
                }

                /*
                     Get alertsIds filtered by severities clause
                */
                if (criteria.hasSeverityCriteria()) {
                    Set<String> alertIdsFilteredBySeverity = filterBySeverities(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredBySeverity);
                        if (alertIds.isEmpty()) {
                            return new Page<>(alerts, pager, 0);
                        }
                    } else {
                        alertIds.addAll(alertIdsFilteredBySeverity);
                    }
                    activeFilter = true;
                }

                /*
                    Get alertsIds filtered by statuses clause
                 */
                if (criteria.hasStatusCriteria()) {
                    Set<String> alertIdsFilteredByStatus = filterByStatuses(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByStatus);
                        if (alertIds.isEmpty()) {
                            return new Page<>(alerts, pager, 0);
                        }
                    } else {
                        alertIds.addAll(alertIdsFilteredByStatus);
                    }
                    activeFilter = true;
                }

                /*
                    If we have reached this point then we have at least 1 filtered alertId, so now
                    get the resulting Alerts...
                 */
                PreparedStatement selectAlertsByTenantAndAlert = CassStatement
                        .get(session, CassStatement.SELECT_ALERT);
                List<ResultSetFuture> futures = alertIds.stream().map(alertId ->
                        session.executeAsync(selectAlertsByTenantAndAlert.bind(tenantId, alertId)))
                        .collect(Collectors.toList());
                List<ResultSet> rsAlerts = Futures.allAsList(futures).get();
                rsAlerts.stream().forEach(r -> {
                    for (Row row : r) {
                        String payload = row.getString("payload");
                        Alert alert = JsonUtil.fromJson(payload, Alert.class, thin);
                        alerts.add(alert);
                    }
                });

            } else {
                /*
                    Get all alerts - Single query
                 */
                PreparedStatement selectAlertsByTenant = CassStatement.get(session,
                        CassStatement.SELECT_ALERTS_BY_TENANT);
                ResultSet rsAlerts = session.execute(selectAlertsByTenant.bind(tenantId));
                for (Row row : rsAlerts) {
                    String payload = row.getString("payload");
                    Alert alert = JsonUtil.fromJson(payload, Alert.class, thin);
                    alerts.add(alert);
                }
            }

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return preparePage(alerts, pager);
    }

    private Page<Alert> preparePage(List<Alert> alerts, Pager pager) {
        if (pager != null) {
            if (pager.getOrder() != null
                    && !pager.getOrder().isEmpty()
                    && pager.getOrder().get(0).getField() == null) {
                pager = Pager.builder()
                        .withPageSize(pager.getPageSize())
                        .withStartPage(pager.getPageNumber())
                        .orderBy(Field.ALERT_ID.getText(), Order.Direction.DESCENDING).build();
            }
            List<Alert> ordered = alerts;
            if (pager.getOrder() != null) {
                pager.getOrder().stream().filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            AlertComparator comparator = new AlertComparator(o.getField(), o.getDirection());
                            Collections.sort(ordered, comparator);
                        });
            }
            if (!pager.isLimited() || ordered.size() < pager.getStart()) {
                pager = new Pager(0, ordered.size(), pager.getOrder());
                return new Page(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            pager = Pager.builder().withPageSize(alerts.size()).orderBy(Field.ALERT_ID.getText(),
                    Order.Direction.ASCENDING).build();
            return new Page(alerts, pager, alerts.size());
        }
    }

    private Set<String> filterByAlerts(AlertsCriteria criteria) {
        Set<String> result = Collections.EMPTY_SET;
        if (isEmpty(criteria.getAlertIds())) {
            if (!isEmpty(criteria.getAlertId())) {
                result = new HashSet<>(1);
                result.add(criteria.getAlertId());
            }
        } else {
            result = new HashSet<>();
            result.addAll(criteria.getAlertIds());
        }
        return result;
    }

    private Set<String> filterByTriggers(String tenantId, AlertsCriteria criteria) throws Exception {
        Set<String> result = Collections.EMPTY_SET;
        Set<String> triggerIds = extractTriggerIds(tenantId, criteria);

        if (triggerIds.size() > 0) {
            List<ResultSetFuture> futures = new ArrayList<>();
            PreparedStatement selectAlertsTriggers = CassStatement.get(session, CassStatement.SELECT_ALERT_TRIGGER);

            for (String triggerId : triggerIds) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                futures.add(session.executeAsync(selectAlertsTriggers.bind(tenantId, triggerId)));
            }
            List<ResultSet> rsAlertIdsByTriggerIds = Futures.allAsList(futures).get();

            Set<String> alertIds = new HashSet<>();
            rsAlertIdsByTriggerIds.stream().forEach(r -> {
                for (Row row : r) {
                    String alertId = row.getString("alertId");
                    alertIds.add(alertId);
                }
            });
            result = alertIds;
        }

        return result;
    }

    private Set<String> extractTriggerIds(String tenantId, AlertsCriteria criteria) {

        boolean hasTriggerId = !isEmpty(criteria.getTriggerId());
        boolean hasTriggerIds = !isEmpty(criteria.getTriggerIds());

        Set<String> triggerIds = hasTriggerId || hasTriggerIds ? new HashSet<>() : Collections.EMPTY_SET;

        if (!hasTriggerIds) {
            if (hasTriggerId) {
                triggerIds.add(criteria.getTriggerId());
            }
        } else {
            for (String triggerId : criteria.getTriggerIds()) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                triggerIds.add(triggerId);
            }
        }

        return triggerIds;
    }

    private Set<String> filterByCTime(String tenantId, AlertsCriteria criteria) throws Exception {
        Set<String> result = Collections.EMPTY_SET;

        if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
            result = new HashSet<>();

            BoundStatement boundCtime;
            if (criteria.getStartTime() != null && criteria.getEndTime() != null) {
                PreparedStatement selectAlertCTimeStartEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_CTIME_START_END);
                boundCtime = selectAlertCTimeStartEnd.bind(tenantId, criteria.getStartTime(),
                        criteria.getEndTime());
            } else if (criteria.getStartTime() != null) {
                PreparedStatement selectAlertCTimeStart = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_CTIME_START);
                boundCtime = selectAlertCTimeStart.bind(tenantId, criteria.getStartTime());
            } else {
                PreparedStatement selectAlertCTimeEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_CTIME_END);
                boundCtime = selectAlertCTimeEnd.bind(tenantId, criteria.getEndTime());
            }

            ResultSet rsAlertsCtimes = session.execute(boundCtime);
            for (Row row : rsAlertsCtimes) {
                String alertId = row.getString("alertId");
                result.add(alertId);
            }
        }
        return result;
    }

    private Set<String> filterByStatuses(String tenantId, AlertsCriteria criteria) throws Exception {
        Set<String> result = Collections.EMPTY_SET;

        Set<Alert.Status> statuses = new HashSet<>();
        if (isEmpty(criteria.getStatusSet())) {
            if (criteria.getStatus() != null) {
                statuses.add(criteria.getStatus());
            }
        } else {
            statuses.addAll(criteria.getStatusSet());
        }

        if (statuses.size() > 0) {
            PreparedStatement selectAlertStatusByTenantAndStatus = CassStatement.get(session,
                    CassStatement.SELECT_ALERT_STATUS);
            List<ResultSetFuture> futures = statuses.stream().map(status ->
                    session.executeAsync(selectAlertStatusByTenantAndStatus.bind(tenantId, status.name())))
                    .collect(Collectors.toList());
            List<ResultSet> rsAlertStatuses = Futures.allAsList(futures).get();

            Set<String> alertIds = new HashSet<>();
            rsAlertStatuses.stream().forEach(r -> {
                for (Row row : r) {
                    String alertId = row.getString("alertId");
                    alertIds.add(alertId);
                }
            });
            result = alertIds;
        }
        return result;
    }

    private Set<String> filterBySeverities(String tenantId, AlertsCriteria criteria)
            throws Exception {

        Set<String> result = Collections.EMPTY_SET;

        Set<Severity> severities = new HashSet<>();
        if (isEmpty(criteria.getSeverities())) {
            if (criteria.getSeverity() != null) {
                severities.add(criteria.getSeverity());
            }
        } else {
            severities.addAll(criteria.getSeverities());
        }

        if (severities.size() > 0) {
            PreparedStatement selectAlertSeverityByTenantAndSeverity = CassStatement.get(session,
                    CassStatement.SELECT_ALERT_SEVERITY);
            List<ResultSetFuture> futures = severities.stream().map(severity ->
                    session.executeAsync(selectAlertSeverityByTenantAndSeverity.bind(tenantId, severity.name())))
                    .collect(Collectors.toList());
            List<ResultSet> rsAlertSeverities = Futures.allAsList(futures).get();

            Set<String> alertIds = new HashSet<>();
            rsAlertSeverities.stream().forEach(r -> {
                for (Row row : r) {
                    String alertId = row.getString("alertId");
                    alertIds.add(alertId);
                }
            });
            result = alertIds;
        }

        return result;
    }

    private Set<String> filterByEvents(EventsCriteria criteria) {
        Set<String> result = Collections.EMPTY_SET;
        if (isEmpty(criteria.getEventIds())) {
            if (!isEmpty(criteria.getEventId())) {
                result = new HashSet<>(1);
                result.add(criteria.getEventId());
            }
        } else {
            result = new HashSet<>();
            result.addAll(criteria.getEventIds());
        }
        return result;
    }

    private Set<String> getIdsByTags(String tenantId, TagType tagType, Map<String, String> tags)
            throws Exception {
        Set<String> ids = new HashSet<>();
        List<ResultSetFuture> futures = new ArrayList<>();
        PreparedStatement selectTagsByName = CassStatement.get(session, CassStatement.SELECT_TAGS_BY_NAME);
        PreparedStatement selectTagsByNameAndValue = CassStatement.get(session,
                CassStatement.SELECT_TAGS_BY_NAME_AND_VALUE);

        for (Map.Entry<String, String> tag : tags.entrySet()) {
            boolean nameOnly = "*".equals(tag.getValue());
            BoundStatement bs = nameOnly ?
                    selectTagsByName.bind(tenantId, tagType.name(), tag.getKey()) :
                    selectTagsByNameAndValue.bind(tenantId, tagType.name(), tag.getKey(), tag.getValue());
            futures.add(session.executeAsync(bs));
        }
        List<ResultSet> rsTags = Futures.allAsList(futures).get();
        rsTags.stream().forEach(r -> {
            for (Row row : r) {
                ids.add(row.getString("id"));
            }
        });
        return ids;
    }

    // TODO (jshaughn) The DB-Level filtering approach implemented below is a best-practice for dealing
    // with Cassandra.  It's basically a series of queries, one for each filter, with a progressive
    // intersection of the resulting ID set.  This will work well in most cases but we may want to consider
    // an optimization for dealing with large Event populations.  Certain filters dealing with low-cardinality
    // values, like category, could start pulling a large number of event ids.  If we have reduced the
    // result set to a small number, via the more narrowing filters, (TBD via perf tests, a threshold that makes
    // sense), we may want to pull the resulting events and apply the low-cardinality filters here in the code,
    // in a post-fetch step. For example, if we have filters "ctime > 123" and "category == Alert", and the ctime
    // filter returns 10 eventIds. We may want to pull the 10 events and apply the category filter in the code. For
    // large Event history, the category filter applied to the DB could return a huge set of ids.
    @Override
    public Page<Event> getEvents(String tenantId, EventsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        session = CassCluster.getSession();
        boolean filter = (null != criteria && criteria.hasCriteria());
        boolean thin = (null != criteria && criteria.isThin());

        if (filter && log.isDebugEnabled()) {
            log.debug("getEvents criteria: " + criteria.toString());
        }

        List<Event> events = new ArrayList<>();
        Set<String> eventIds = new HashSet<>();
        boolean activeFilter = false;

        try {
            if (filter) {
                /*
                    Get eventIds explicitly added into the criteria. Start with these as there is no query involved
                */
                if (criteria.hasEventIdCriteria()) {
                    Set<String> idsFilteredByEvents = filterByEvents(criteria);
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByEvents);
                        if (eventIds.isEmpty()) {
                            return new Page<>(events, pager, 0);
                        }
                    } else {
                        eventIds.addAll(idsFilteredByEvents);
                    }
                    activeFilter = true;
                }

                /*
                    Get eventIds via tags
                */
                if (criteria.hasTagCriteria()) {
                    Set<String> idsFilteredByTags = getIdsByTags(tenantId, TagType.EVENT, criteria.getTags());
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByTags);
                        if (eventIds.isEmpty()) {
                            return new Page<>(events, pager, 0);
                        }
                    } else {
                        eventIds.addAll(idsFilteredByTags);
                    }
                    activeFilter = true;
                }

                /*
                    Get eventIds filtered by triggerIds clause
                 */
                if (criteria.hasTriggerIdCriteria()) {
                    Set<String> idsFilteredByTriggers = filterByTriggers(tenantId, criteria);
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByTriggers);
                        if (eventIds.isEmpty()) {
                            return new Page<>(events, pager, 0);
                        }
                    } else {
                        eventIds.addAll(idsFilteredByTriggers);
                    }
                    activeFilter = true;
                }

                /*
                    Get alertsIds filtered by time clause
                 */
                if (criteria.hasCTimeCriteria()) {
                    Set<String> idsFilteredByTime = filterByCTime(tenantId, criteria);
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByTime);
                        if (eventIds.isEmpty()) {
                            return new Page<>(events, pager, 0);
                        }
                    } else {
                        eventIds.addAll(idsFilteredByTime);
                    }
                    activeFilter = true;
                }

                /*
                     Get alertsIds filtered by categories clause
                */
                if (criteria.hasCategoryCriteria()) {
                    Set<String> idsFilteredByCategory = filterByCategories(tenantId, criteria);
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByCategory);
                        if (eventIds.isEmpty()) {
                            return new Page<>(events, pager, 0);
                        }
                    } else {
                        eventIds.addAll(idsFilteredByCategory);
                    }
                    activeFilter = true;
                }

                /*
                    If we have reached this point then we have at least 1 filtered alertId, so now
                    get the resulting Alerts...
                 */
                PreparedStatement selectEvent = CassStatement
                        .get(session, CassStatement.SELECT_EVENT);
                List<ResultSetFuture> futures = eventIds.stream().map(id ->
                        session.executeAsync(selectEvent.bind(tenantId, id)))
                        .collect(Collectors.toList());
                List<ResultSet> rsEvents = Futures.allAsList(futures).get();
                rsEvents.stream().forEach(r -> {
                    for (Row row : r) {
                        String payload = row.getString("payload");
                        Event event = JsonUtil.fromJson(payload, Event.class, thin);
                        events.add(event);
                    }
                });

            } else {
                /*
                    Get all events for the tenant - We could pull all events and toss those outside the tenant
                    but perhaps slightly better is to get the valid partitions and then concurrently query each...

                PreparedStatement selectPartitionsEvents = CassStatement.get(session,
                        CassStatement.SELECT_PARTITIONS_EVENTS);
                PreparedStatement selectEventsByPartition = CassStatement.get(session,
                        CassStatement.SELECT_EVENTS_BY_PARTITION);

                Set<String> categories = new HashSet<>();
                ResultSet rsPartitions = session.execute(selectPartitionsEvents.bind());
                for (Row row : rsPartitions) {
                    if (tenantId.equals(row.getString("tenantId"))) {
                        categories.add(row.getString("category"));
                    }
                }

                List<ResultSetFuture> futures = categories.stream().map(category ->
                        session.executeAsync(selectEventsByPartition.bind(tenantId, category)))
                        .collect(Collectors.toList());
                List<ResultSet> rsEvents = Futures.allAsList(futures).get();
                rsEvents.stream().forEach(r -> {
                    for (Row row : r) {
                        String payload = row.getString("payload");
                        Event event = JsonUtil.fromJson(payload, Event.class, thin);
                        events.add(event);
                    }
                });
                */

                /*
                Get all events - Single query
                */
                PreparedStatement selectEventsByTenant = CassStatement.get(session,
                        CassStatement.SELECT_EVENTS_BY_TENANT);
                ResultSet rsEvents = session.execute(selectEventsByTenant.bind(tenantId));
                for (Row row : rsEvents) {
                    String payload = row.getString("payload");
                    Event event = JsonUtil.fromJson(payload, Event.class, thin);
                    events.add(event);
                }

            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return prepareEventsPage(events, pager);
    }

    private Page<Event> prepareEventsPage(List<Event> events, Pager pager) {
        if (pager != null) {
            if (pager.getOrder() != null
                    && !pager.getOrder().isEmpty()
                    && pager.getOrder().get(0).getField() == null) {
                pager = Pager.builder()
                        .withPageSize(pager.getPageSize())
                        .withStartPage(pager.getPageNumber())
                        .orderBy(EventComparator.Field.ID.getName(), Order.Direction.DESCENDING).build();
            }
            List<Event> ordered = events;
            if (pager.getOrder() != null) {
                pager.getOrder()
                        .stream()
                        .filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            EventComparator comparator = new EventComparator(o.getField(), o.getDirection());
                            Collections.sort(ordered, comparator);
                        });
            }
            if (!pager.isLimited() || ordered.size() < pager.getStart()) {
                pager = new Pager(0, ordered.size(), pager.getOrder());
                return new Page(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            pager = Pager.builder().withPageSize(events.size()).orderBy(EventComparator.Field.ID.getName(),
                    Order.Direction.ASCENDING).build();
            return new Page(events, pager, events.size());
        }
    }

    private Set<String> filterByTriggers(String tenantId, EventsCriteria criteria) throws Exception {
        Set<String> result = Collections.EMPTY_SET;
        Set<String> triggerIds = extractTriggerIds(tenantId, criteria);

        if (triggerIds.size() > 0) {
            List<ResultSetFuture> futures = new ArrayList<>();
            PreparedStatement selectEventsTriggers = CassStatement.get(session, CassStatement.SELECT_EVENT_TRIGGER);

            for (String triggerId : triggerIds) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                futures.add(session.executeAsync(selectEventsTriggers.bind(tenantId, triggerId)));
            }
            List<ResultSet> rsIdsByTriggerIds = Futures.allAsList(futures).get();

            Set<String> eventIds = new HashSet<>();
            rsIdsByTriggerIds.stream().forEach(r -> {
                for (Row row : r) {
                    String eventId = row.getString("id");
                    eventIds.add(eventId);
                }
            });
            result = eventIds;
        }

        return result;
    }

    private Set<String> extractTriggerIds(String tenantId, EventsCriteria criteria) {

        boolean hasTriggerId = !isEmpty(criteria.getTriggerId());
        boolean hasTriggerIds = !isEmpty(criteria.getTriggerIds());

        Set<String> triggerIds = hasTriggerId || hasTriggerIds ? new HashSet<>() : Collections.EMPTY_SET;

        if (!hasTriggerIds) {
            if (hasTriggerId) {
                triggerIds.add(criteria.getTriggerId());
            }
        } else {
            for (String triggerId : criteria.getTriggerIds()) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                triggerIds.add(triggerId);
            }
        }

        return triggerIds;
    }

    private Set<String> filterByCTime(String tenantId, EventsCriteria criteria) throws Exception {
        Set<String> result = Collections.EMPTY_SET;

        if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
            result = new HashSet<>();

            BoundStatement boundCtime;
            if (criteria.getStartTime() != null && criteria.getEndTime() != null) {
                PreparedStatement selectEventCTimeStartEnd = CassStatement.get(session,
                        CassStatement.SELECT_EVENT_CTIME_START_END);
                boundCtime = selectEventCTimeStartEnd.bind(tenantId, criteria.getStartTime(),
                        criteria.getEndTime());
            } else if (criteria.getStartTime() != null) {
                PreparedStatement selectEventCTimeStart = CassStatement.get(session,
                        CassStatement.SELECT_EVENT_CTIME_START);
                boundCtime = selectEventCTimeStart.bind(tenantId, criteria.getStartTime());
            } else {
                PreparedStatement selectEventCTimeEnd = CassStatement.get(session,
                        CassStatement.SELECT_EVENT_CTIME_END);
                boundCtime = selectEventCTimeEnd.bind(tenantId, criteria.getEndTime());
            }

            ResultSet rsIdsCtimes = session.execute(boundCtime);
            for (Row row : rsIdsCtimes) {
                String eventId = row.getString("id");
                result.add(eventId);
            }
        }
        return result;
    }

    private Set<String> filterByCategories(String tenantId, EventsCriteria criteria) throws Exception {
        Set<String> result = Collections.EMPTY_SET;

        Set<String> categories = new HashSet<>();
        if (isEmpty(criteria.getCategories())) {
            if (criteria.getCategory() != null) {
                categories.add(criteria.getCategory());
            }
        } else {
            categories.addAll(criteria.getCategories());
        }

        if (categories.size() > 0) {
            PreparedStatement selectEventCategory = CassStatement.get(session,
                    CassStatement.SELECT_EVENT_CATEGORY);
            List<ResultSetFuture> futures = categories.stream().map(category ->
                    session.executeAsync(selectEventCategory.bind(tenantId, category)))
                    .collect(Collectors.toList());
            List<ResultSet> rsAlertStatuses = Futures.allAsList(futures).get();

            Set<String> eventIds = new HashSet<>();
            rsAlertStatuses.stream().forEach(r -> {
                for (Row row : r) {
                    String eventId = row.getString("id");
                    eventIds.add(eventId);
                }
            });
            result = eventIds;
        }
        return result;
    }

    @Override
    public void ackAlerts(String tenantId, Collection<String> alertIds, String ackBy, String ackNotes)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            return;
        }

        if (isEmpty(ackBy)) {
            ackBy = "unknown";
        }
        if (isEmpty(ackNotes)) {
            ackNotes = "none";
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToAck = getAlerts(tenantId, criteria, null);

        for (Alert a : alertsToAck) {
            a.setStatus(Alert.Status.ACKNOWLEDGED);
            a.setAckBy(ackBy);
            a.setAckTime(System.currentTimeMillis());
            a.addNote(ackBy, ackNotes);
            updateAlertStatus(a);
            sendAction(a);
        }
    }

    @Override
    public int deleteAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (null == criteria) {
            throw new IllegalArgumentException("Criteria must be not null");
        }

        // no need to fetch the evalSets to perform the necessary deletes
        criteria.setThin(true);
        List<Alert> alertsToDelete = getAlerts(tenantId, criteria, null);

        if (alertsToDelete.isEmpty()) {
            return 0;
        }

        PreparedStatement deleteAlert = CassStatement.get(session, CassStatement.DELETE_ALERT);
        PreparedStatement deleteAlertCtime = CassStatement.get(session, CassStatement.DELETE_ALERT_CTIME);
        PreparedStatement deleteAlertSeverity = CassStatement.get(session, CassStatement.DELETE_ALERT_SEVERITY);
        PreparedStatement deleteAlertStatus = CassStatement.get(session, CassStatement.DELETE_ALERT_STATUS);
        PreparedStatement deleteAlertTrigger = CassStatement.get(session, CassStatement.DELETE_ALERT_TRIGGER);
        if (deleteAlert == null || deleteAlertCtime == null || deleteAlertSeverity == null
                || deleteAlertStatus == null || deleteAlertTrigger == null) {
            throw new RuntimeException("delete*Alerts PreparedStatement is null");
        }

        for (Alert a : alertsToDelete) {
            String id = a.getAlertId();
            List<ResultSetFuture> futures = new ArrayList<>();
            futures.add(session.executeAsync(deleteAlert.bind(tenantId, id)));
            futures.add(session.executeAsync(deleteAlertCtime.bind(tenantId, a.getCtime(), id)));
            futures.add(session.executeAsync(deleteAlertSeverity.bind(tenantId, a.getSeverity().name(), id)));
            futures.add(session.executeAsync(deleteAlertStatus.bind(tenantId, a.getStatus().name(), id)));
            futures.add(session.executeAsync(deleteAlertTrigger.bind(tenantId, a.getTriggerId(), id)));
            Futures.allAsList(futures).get();
        }

        return alertsToDelete.size();
    }

    @Override
    public int deleteEvents(String tenantId, EventsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (null == criteria) {
            throw new IllegalArgumentException("Criteria must be not null");
        }

        // no need to fetch the evalSets to perform the necessary deletes
        criteria.setThin(true);
        List<Event> eventsToDelete = getEvents(tenantId, criteria, null);

        if (eventsToDelete.isEmpty()) {
            return 0;
        }

        PreparedStatement deleteEvent = CassStatement.get(session, CassStatement.DELETE_EVENT);
        PreparedStatement deleteEventCategory = CassStatement.get(session, CassStatement.DELETE_EVENT_CATEGORY);
        PreparedStatement deleteEventCTime = CassStatement.get(session, CassStatement.DELETE_EVENT_CTIME);
        PreparedStatement deleteEventTrigger = CassStatement.get(session, CassStatement.DELETE_EVENT_TRIGGER);
        if (deleteEvent == null || deleteEventCTime == null || deleteEventCategory == null
                || deleteEventTrigger == null) {
            throw new RuntimeException("delete*Events PreparedStatement is null");
        }

        for (Event e : eventsToDelete) {
            String id = e.getId();
            List<ResultSetFuture> futures = new ArrayList<>();
            futures.add(session.executeAsync(deleteEvent.bind(tenantId, id)));
            futures.add(session.executeAsync(deleteEventCategory.bind(tenantId, e.getCategory(), id)));
            futures.add(session.executeAsync(deleteEventCTime.bind(tenantId, e.getCtime(), id)));
            if (null != e.getTrigger()) {
                futures.add(session.executeAsync(deleteEventTrigger.bind(tenantId, e.getTrigger().getId(), id)));
            }
            Futures.allAsList(futures).get();
        }

        return eventsToDelete.size();
    }

    @Override
    public void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            return;
        }

        if (isEmpty(resolvedBy)) {
            resolvedBy = "unknown";
        }
        if (isEmpty(resolvedNotes)) {
            resolvedNotes = "none";
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        // resolve the alerts
        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedTime(System.currentTimeMillis());
            a.addNote(resolvedBy, resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            updateAlertStatus(a);
            sendAction(a);
        }

        // gather the triggerIds of the triggers we need to check for resolve options
        Set<String> triggerIds = alertsToResolve.stream().map(a -> a.getTriggerId()).collect(Collectors.toSet());

        // handle resolve options
        triggerIds.stream().forEach(tid -> handleResolveOptions(tenantId, tid, true));
    }

    @Override
    public void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        if (isEmpty(resolvedBy)) {
            resolvedBy = "unknown";
        }
        if (isEmpty(resolvedNotes)) {
            resolvedNotes = "none";
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId(triggerId);
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Alert.Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedTime(System.currentTimeMillis());
            a.addNote(resolvedBy, resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            updateAlertStatus(a);
            sendAction(a);
        }

        handleResolveOptions(tenantId, triggerId, false);
    }

    private Alert updateAlertStatus(Alert alert) throws Exception {
        if (alert == null || alert.getAlertId() == null || alert.getAlertId().isEmpty()) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        session = CassCluster.getSession();
        try {
            // we need to delete the current status index entry, and enter the new one. And update the
            // alert.  We can do all of this concurrently/async because each call operates on a different key;
            PreparedStatement deleteAlertStatus = CassStatement.get(session,
                    CassStatement.DELETE_ALERT_STATUS);
            PreparedStatement insertAlertStatus = CassStatement.get(session,
                    CassStatement.INSERT_ALERT_STATUS);
            PreparedStatement updateAlert = CassStatement.get(session,
                    CassStatement.UPDATE_ALERT);

            List<ResultSetFuture> futures = new ArrayList<>();
            for (Alert.Status statusToDelete : EnumSet.complementOf(EnumSet.of(alert.getStatus()))) {
                futures.add(session.executeAsync(deleteAlertStatus.bind(alert.getTenantId(), statusToDelete,
                        alert.getAlertId())));
            }
            futures.add(session.executeAsync(insertAlertStatus.bind(alert.getTenantId(), alert.getAlertId(), alert
                    .getStatus().name())));
            futures.add(session.executeAsync(updateAlert.bind(JsonUtil.toJson(alert), alert.getTenantId(),
                    alert.getAlertId())));

            List<ResultSet> rsAlertsStatusToDelete = Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return alert;
    }

    private void handleResolveOptions(String tenantId, String triggerId, boolean checkIfAllResolved) {

        try {
            Trigger trigger = definitionsService.getTrigger(tenantId, triggerId);
            if (null == trigger) {
                return;
            }

            boolean setEnabled = trigger.isAutoEnable() && !trigger.isEnabled();
            boolean setFiring = trigger.isAutoResolve();

            // Only reload the trigger if it is not already in firing mode, otherwise we could lose partial matching.
            // This is a rare case because a trigger with autoResolve=true will not be in firing mode with an
            // unresolved trigger. But it is possible, either by mistake, or timing,  for a client to try and
            // resolve an already-resolved alert.
            if (setFiring) {
                Trigger loadedTrigger = alertsEngine.getLoadedTrigger(trigger);
                if (null != loadedTrigger && Mode.FIRING == loadedTrigger.getMode()) {
                    if (log.isDebugEnabled()) {
                        log.debug("Ignoring setFiring, loaded Trigger already in firing mode " +
                                loadedTrigger.toString());
                    }
                    setFiring = false;
                }
            }

            if (!(setEnabled || setFiring)) {
                return;
            }

            boolean allResolved = true;
            if (checkIfAllResolved) {
                AlertsCriteria ac = new AlertsCriteria();
                ac.setTriggerId(triggerId);
                ac.setStatusSet(EnumSet.complementOf(EnumSet.of(Alert.Status.RESOLVED)));
                Page<Alert> unresolvedAlerts = getAlerts(tenantId, ac, new Pager(0, 1, Order.unspecified()));
                allResolved = unresolvedAlerts.isEmpty();
            }

            if (!allResolved) {
                if (log.isDebugEnabled()) {
                    log.debug("Ignoring resolveOptions, not all Alerts for Trigger " + trigger.toString() +
                            " are resolved");
                }
                return;
            }

            // Either update the trigger, which implicitly reloads the trigger (and as such resets to firing mode)
            // or perform an explicit reload to reset to firing mode.
            if (setEnabled) {
                trigger.setEnabled(true);
                definitionsService.updateTrigger(tenantId, trigger);
            } else {
                alertsEngine.reloadTrigger(tenantId, triggerId);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
        }

    }

    @Override
    public void sendData(Data data) throws Exception {
        alertsEngine.sendData(data);
    }

    @Override
    public void sendData(Collection<Data> data) throws Exception {
        alertsEngine.sendData(data);
    }

    @Override
    public void addEvents(Collection<Event> events) throws Exception {
        if (null == events || events.isEmpty()) {
            return;
        }
        persistEvents(events);
        alertsEngine.sendEvents(events);
    }

    private void sendAction(Alert a) {
        if (actionsService != null && a != null && a.getTrigger() != null && a.getTrigger().getActions() != null) {
            Map<String, Set<String>> actions = a.getTrigger().getActions();
            for (String actionPlugin : actions.keySet()) {
                for (String actionId : actions.get(actionPlugin)) {
                    Action action = new Action(a.getTrigger().getTenantId(), actionPlugin, actionId, a);
                    actionsService.send(action);
                }
            }
        }
    }

    private boolean isEmpty(Collection<?> c) {
        return null == c || c.isEmpty();
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

}
