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

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.paging.AlertComparator;
import org.hawkular.alerts.api.model.paging.AlertComparator.Field;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
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
@Stateless
@TransactionAttribute(value= TransactionAttributeType.NOT_SUPPORTED)
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

    @PostConstruct
    public void initServices() {
        try {
            if (session == null) {
                session = CassCluster.getSession();
            }
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                t.printStackTrace();
            }
            msgLog.errorCannotInitializeAlertsService(t.getMessage());
        }
    }

    @Override
    public void addAlerts(Collection<Alert> alerts) throws Exception {
        if (alerts == null) {
            throw new IllegalArgumentException("Alerts must be not null");
        }
        session = CassCluster.getSession();
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertAlert = CassStatement.get(session, CassStatement.INSERT_ALERT);
        PreparedStatement insertAlertTrigger = CassStatement.get(session, CassStatement.INSERT_ALERT_TRIGGER);
        PreparedStatement insertAlertCtime = CassStatement.get(session, CassStatement.INSERT_ALERT_CTIME);
        PreparedStatement insertAlertStatus = CassStatement.get(session, CassStatement.INSERT_ALERT_STATUS);
        PreparedStatement insertAlertSeverity = CassStatement.get(session, CassStatement.INSERT_ALERT_SEVERITY);
        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            alerts.stream()
                    .forEach(a -> {
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
    public Alert getAlert(String tenantId, String alertId, boolean thin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertId)) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        session = CassCluster.getSession();
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        boolean filter = (null != criteria && criteria.hasCriteria());
        boolean thin = (null != criteria && criteria.isThin());

        if (filter) {
            log.debugf("getAlerts criteria: %s", criteria.toString());
        }

        List<Alert> alerts = new ArrayList<>();
        Set<String> alertIds = new HashSet<>();

        try {
            if (filter) {
                /*
                    Get alertsIds explicitly added into the criteria. Start with these as there is no query involved
                */
                Set<String> alertIdsFilteredByAlerts = new HashSet<>();
                boolean filterByAlerts = filterByAlerts(alertIdsFilteredByAlerts, criteria);
                if (filterByAlerts) {
                    alertIds.addAll(alertIdsFilteredByAlerts);
                }

                /*
                    Get alertIds filtered by triggerIds clause
                 */
                Set<String> alertIdsFilteredByTriggers = new HashSet<>();
                boolean filterByTriggers = filterByTriggers(tenantId, alertIdsFilteredByTriggers, criteria);
                if (filterByTriggers) {
                    if (alertIds.isEmpty()) {
                        alertIds.addAll(alertIdsFilteredByTriggers);
                    } else {
                        alertIds.retainAll(alertIdsFilteredByTriggers);
                    }
                    if (alertIds.isEmpty()) {
                        return new Page<>(alerts, pager, 0);
                    }
                }

                /*
                    Get alertsIds filtered by ctime clause
                 */
                Set<String> alertIdsFilteredByCtime = new HashSet<>();
                boolean filterByCtime = filterByCtime(tenantId, alertIdsFilteredByCtime, criteria);
                if (filterByCtime) {
                    if (alertIds.isEmpty()) {
                        alertIds.addAll(alertIdsFilteredByCtime);
                    } else {
                        alertIds.retainAll(alertIdsFilteredByCtime);
                    }
                    if (alertIds.isEmpty()) {
                        return new Page<>(alerts, pager, 0);
                    }
                }

                /*
                Get alertsIds filtered by severities clause
                */
                Set<String> alertIdsFilteredBySeverity = new HashSet<>();
                boolean filterBySeverity = filterBySeverities(tenantId, alertIdsFilteredBySeverity, criteria);
                if (filterBySeverity) {
                    if (alertIds.isEmpty()) {
                        alertIds.addAll(alertIdsFilteredBySeverity);
                    } else {
                        alertIds.retainAll(alertIdsFilteredBySeverity);
                    }
                    if (alertIds.isEmpty()) {
                        return new Page<>(alerts, pager, 0);
                    }
                }

                /*
                    Get alertsIds filtered by statuses clause
                 */
                Set<String> alertIdsFilteredByStatus = new HashSet<>();
                boolean filterByStatus = filterByStatuses(tenantId, alertIdsFilteredByStatus, criteria);
                if (filterByStatus) {
                    if (alertIds.isEmpty()) {
                        alertIds.addAll(alertIdsFilteredByStatus);
                    } else {
                        alertIds.retainAll(alertIdsFilteredByStatus);
                    }

                    if (alertIds.isEmpty()) {
                        return new Page<>(alerts, pager, 0);
                    }
                }
            }

            if (!filter) {
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
            } else {
                /*
                    We have a filter, so we are going to perform several queries with alertsIds filtering
                 */
                PreparedStatement selectAlertsByTenantAndAlert = CassStatement.get(session,
                        CassStatement.SELECT_ALERT);
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
                            AlertComparator comparator = new AlertComparator(Field.getField(o.getField()),
                                    o.getDirection());
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

    /*
        Trigger ids can be passed explicitly in the criteria or indirectly via tags.
        This helper method extracts the list of triggers id and populates the set passed as argument.

        **Note** currently explicitTriggerIds and triggerIds determined via tags are joined together (treated as one
        triggerId filter). I'm not sure this is right but it seems to make the most sense.
     */
    private boolean extractTriggerIds(Set<String> triggerIds, AlertsCriteria criteria) throws Exception {
        boolean hasTriggerId = !isEmpty(criteria.getTriggerId());
        boolean hasTriggerIds = !isEmpty(criteria.getTriggerIds());
        boolean hasTag = null != criteria.getTag();
        boolean hasTags = !isEmpty(criteria.getTags());

        /*
            Explicit trigger ids
         */
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

        /*
            Indirect trigger ids by tags
         */
        if (hasTag || hasTags) {
            Set<Tag> tags = new HashSet<>();
            if (hasTags) {
                tags.addAll(criteria.getTags());
            }
            if (hasTag) {
                tags.add(criteria.getTag());
            }
            triggerIds.addAll(getTriggersIdByTags(tags));
        }

        // Return true if any trigger or tag criteria was specified, regardless of whether it results in any
        // triggerIds, because tags may not actually result in triggerIds but that does not mean the filter was
        // not specified. In that case the overall fetch of alerts should return no alerts.
        return hasTriggerId || hasTriggerIds || hasTag || hasTags;
    }

    private boolean filterByTriggers(String tenantId, Set<String> alertsId, AlertsCriteria criteria) throws Exception {
        Set<String> triggerIds = new HashSet<>();
        boolean filterByTriggers = extractTriggerIds(triggerIds, criteria);

        if (triggerIds.size() > 0) {
            List<ResultSetFuture> futures = new ArrayList<>();
            PreparedStatement selectAlertsTriggers = CassStatement.get(session, CassStatement.SELECT_ALERTS_TRIGGERS);

            for (String triggerId : triggerIds) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                futures.add(session.executeAsync(selectAlertsTriggers.bind(tenantId, triggerId)));
            }

            List<ResultSet> rsAlertIdsByTriggerIds = Futures.allAsList(futures).get();

            rsAlertIdsByTriggerIds.stream().forEach(r -> {
                for (Row row : r) {
                    String alertId = row.getString("alertId");
                    alertsId.add(alertId);
                }
            });
            /*
                If there is not alertId but we have triggersId means that we have an empty result.
                So we need to sure a alertId to mark that we have an empty result for future joins.
             */
            if (alertsId.isEmpty()) {
                alertsId.add("no-result-fake-alert-id");
            }
        }

        return filterByTriggers;
    }

    private boolean filterByCtime(String tenantId, Set<String> alertsId, AlertsCriteria criteria) throws Exception {
        boolean filterByCtime = false;
        if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
            filterByCtime = true;

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
            if (rsAlertsCtimes.isExhausted()) {
                alertsId.add("no-result-fake-alert-id");
            } else {
                for (Row row : rsAlertsCtimes) {
                    String alertId = row.getString("alertId");
                    alertsId.add(alertId);
                }
            }
        }
        return filterByCtime;
    }

    private boolean filterByStatuses(String tenantId, Set<String> alertsId, AlertsCriteria criteria) throws Exception {
        boolean filterByStatus = false;
        Set<Alert.Status> statuses = new HashSet<>();
        if (isEmpty(criteria.getStatusSet())) {
            if (criteria.getStatus() != null) {
                statuses.add(criteria.getStatus());
            }
        } else {
            statuses.addAll(criteria.getStatusSet());
        }

        if (statuses.size() > 0) {
            filterByStatus = true;
            PreparedStatement selectAlertStatusByTenantAndStatus = CassStatement.get(session,
                    CassStatement.SELECT_ALERT_STATUS_BY_TENANT_AND_STATUS);
            List<ResultSetFuture> futures = statuses.stream().map(status ->
                    session.executeAsync(selectAlertStatusByTenantAndStatus.bind(tenantId, status.name())))
                    .collect(Collectors.toList());

            List<ResultSet> rsAlertStatuses = Futures.allAsList(futures).get();
            rsAlertStatuses.stream().forEach(r -> {
                for (Row row : r) {
                    String alertId = row.getString("alertId");
                    alertsId.add(alertId);
                }
            });
            /*
                If there is not alertId but we have triggersId means that we have an empty result.
                So we need to sure a alertId to mark that we have an empty result for future joins.
             */
            if (alertsId.isEmpty()) {
                alertsId.add("no-result-fake-alert-id");
            }
        }
        return filterByStatus;
    }

    private boolean filterBySeverities(String tenantId, Set<String> alertsId, AlertsCriteria criteria)
            throws Exception {
        boolean filterBySeverity = false;
        Set<Severity> severities = new HashSet<>();
        if (isEmpty(criteria.getSeverities())) {
            if (criteria.getSeverity() != null) {
                severities.add(criteria.getSeverity());
            }
        } else {
            severities.addAll(criteria.getSeverities());
        }

        if (severities.size() > 0) {
            filterBySeverity = true;
            PreparedStatement selectAlertSeverityByTenantAndSeverity = CassStatement.get(session,
                    CassStatement.SELECT_ALERT_SEVERITY_BY_TENANT_AND_SEVERITY);
            List<ResultSetFuture> futures = severities.stream().map(severity ->
                    session.executeAsync(selectAlertSeverityByTenantAndSeverity.bind(tenantId, severity.name())))
                    .collect(Collectors.toList());

            List<ResultSet> rsAlertSeverities = Futures.allAsList(futures).get();
            rsAlertSeverities.stream().forEach(r -> {
                for (Row row : r) {
                    String alertId = row.getString("alertId");
                    alertsId.add(alertId);
                }
            });
            /*
                If there is not alertId but we have triggersId means that we have an empty result.
                So we need to sure a alertId to mark that we have an empty result for future joins.
             */
            if (alertsId.isEmpty()) {
                alertsId.add("no-result-fake-alert-id");
            }
        }
        return filterBySeverity;
    }

    private boolean filterByAlerts(Set<String> alertsId, AlertsCriteria criteria) {
        boolean filterByAlerts = false;
        if (isEmpty(criteria.getAlertIds())) {
            if (!isEmpty(criteria.getAlertId())) {
                filterByAlerts = true;
                alertsId.add(criteria.getAlertId());
            }
        } else {
            filterByAlerts = true;
            alertsId.addAll(criteria.getAlertIds());
        }
        return filterByAlerts;
    }

    private Collection<String> getTriggersIdByTags(Collection<Tag> tags) throws Exception {
        Set<String> triggerIds = new HashSet<>();
        List<ResultSetFuture> futures = new ArrayList<>();
        PreparedStatement selectTagsTriggersByCategoryAndName = CassStatement.get(session,
                CassStatement.SELECT_TAGS_TRIGGERS_BY_CATEGORY_AND_NAME);
        PreparedStatement selectTagsTriggersByCategory = CassStatement.get(session,
                CassStatement.SELECT_TAGS_TRIGGERS_BY_CATEGORY);
        PreparedStatement selectTagsTriggersByName = CassStatement.get(session,
                CassStatement.SELECT_TAGS_TRIGGERS_BY_NAME);

        for (Tag tag : tags) {
            if (tag.getCategory() != null || tag.getName() != null) {
                BoundStatement boundTag;
                if (!isEmpty(tag.getCategory()) && !isEmpty(tag.getName())) {
                    boundTag = selectTagsTriggersByCategoryAndName.bind(tag.getTenantId(), tag.getCategory(),
                            tag.getName());
                } else if (!isEmpty(tag.getCategory())) {
                    boundTag = selectTagsTriggersByCategory.bind(tag.getTenantId(), tag.getCategory());
                } else {
                    boundTag = selectTagsTriggersByName.bind(tag.getTenantId(), tag.getName());
                }
                futures.add(session.executeAsync(boundTag));
            }
        }
        List<ResultSet> rsTriggers = Futures.allAsList(futures).get();
        rsTriggers.stream().forEach(r -> {
            for (Row row : r) {
                Set<String> triggers = row.getSet("triggers", String.class);
                triggerIds.addAll(triggers);
            }
        });
        return triggerIds;
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

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToAck = getAlerts(tenantId, criteria, null);

        for (Alert a : alertsToAck) {
            a.setStatus(Alert.Status.ACKNOWLEDGED);
            a.setAckBy(ackBy);
            a.setAckTime(System.currentTimeMillis());
            a.setAckNotes(ackNotes);
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
    public void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            return;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        // resolve the alerts
        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedTime(System.currentTimeMillis());
            a.setResolvedNotes(resolvedNotes);
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

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId(triggerId);
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Alert.Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedTime(System.currentTimeMillis());
            a.setResolvedNotes(resolvedNotes);
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        try {
            /*
                Not sure if these queries can be wrapped in an async way as they have dependencies with results.
                Async pattern could bring race hazards here.
             */
            PreparedStatement selectAlertStatus = CassStatement.get(session,
                    CassStatement.SELECT_ALERT_STATUS);
            PreparedStatement insertAlertStatus = CassStatement.get(session,
                    CassStatement.INSERT_ALERT_STATUS);
            PreparedStatement deleteAlertStatus = CassStatement.get(session,
                    CassStatement.DELETE_ALERT_STATUS);
            PreparedStatement updateAlert = CassStatement.get(session,
                    CassStatement.UPDATE_ALERT);

            List<ResultSetFuture> futures = new ArrayList<>();
            futures.add(session.executeAsync(selectAlertStatus.bind(alert.getTenantId(), Alert.Status.OPEN.name(),
                    alert.getAlertId())));
            futures.add(session.executeAsync(selectAlertStatus.bind(alert.getTenantId(),
                    Alert.Status.ACKNOWLEDGED.name(), alert.getAlertId())));
            futures.add(session.executeAsync(selectAlertStatus.bind(alert.getTenantId(), Alert.Status.RESOLVED.name(),
                    alert.getAlertId())));

            List<ResultSet> rsAlertsStatusToDelete = Futures.allAsList(futures).get();
            rsAlertsStatusToDelete.stream().forEach(r -> {
                for (Row row : r) {
                    String alertIdToDelete = row.getString("alertId");
                    String statusToDelete = row.getString("status");
                    session.execute(deleteAlertStatus.bind(alert.getTenantId(), statusToDelete, alertIdToDelete));
                }
            });
            session.execute(insertAlertStatus.bind(alert.getTenantId(), alert.getAlertId(), alert.getStatus().name()));
            session.execute(updateAlert.bind(JsonUtil.toJson(alert), alert.getTenantId(), alert.getAlertId()));
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
                    log.debugf("Ignoring setFiring, loaded Trigger already in firing mode %s", loadedTrigger);
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
                log.debugf("Ignoring resolveOptions, not all Alerts for Trigger %s are resolved", trigger);
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
