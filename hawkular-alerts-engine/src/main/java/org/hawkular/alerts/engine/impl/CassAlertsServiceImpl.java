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

import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.EQ;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.IN;
import static org.hawkular.alerts.engine.tags.ExpressionTagQueryParser.ExpressionTagResolver.NEQ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Alert.Status;
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
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.engine.impl.IncomingDataManagerImpl.IncomingData;
import org.hawkular.alerts.engine.impl.IncomingDataManagerImpl.IncomingEvents;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.engine.service.IncomingDataManager;
import org.hawkular.alerts.engine.tags.ExpressionTagQueryParser;
import org.hawkular.alerts.log.MsgLogger;
import org.jboss.logging.Logger;

import com.datastax.driver.core.BatchStatement;
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
public class CassAlertsServiceImpl implements AlertsService {

    private static final String CRITERIA_NO_QUERY_SIZE = "hawkular-alerts.criteria-no-query-size";
    private static final String CRITERIA_NO_QUERY_SIZE_ENV = "CRITERIA_NO_QUERY_SIZE";
    private static final String CRITERIA_NO_QUERY_SIZE_DEFAULT = "200";

    /*
        These parameters control the number of statements used for a batch.
        Batches are used for queries on same partition only.
        The real size of the batch may vary as it depends of the size in bytes of the whole query.
        But on our case and for simplifying we are counting the number of statements.
     */
    private static final String BATCH_SIZE = "hawkular-alerts.batch-size";
    private static final String BATCH_SIZE_ENV = "BATCH_SIZE";
    private static final String BATCH_SIZE_DEFAULT = "10";

    private static final MsgLogger msgLog = MsgLogger.LOGGER;
    private static final Logger log = Logger.getLogger(CassAlertsServiceImpl.class);

    private int criteriaNoQuerySize;
    private int batchSize;
    private final BatchStatement.Type batchType = BatchStatement.Type.LOGGED;

    AlertsEngine alertsEngine;

    DefinitionsService definitionsService;

    ActionsService actionsService;

    IncomingDataManager incomingDataManager;

    PropertiesService properties;

    Session session;

    private ExecutorService executor;

    public CassAlertsServiceImpl() {
    }

    public void init() {
        criteriaNoQuerySize = Integer.valueOf(properties.getProperty(CRITERIA_NO_QUERY_SIZE,
                CRITERIA_NO_QUERY_SIZE_ENV, CRITERIA_NO_QUERY_SIZE_DEFAULT));
        batchSize = Integer.valueOf(properties.getProperty(BATCH_SIZE,
                BATCH_SIZE_ENV, BATCH_SIZE_DEFAULT));
    }

    public AlertsEngine getAlertsEngine() {
        return alertsEngine;
    }

    public void setAlertsEngine(AlertsEngine alertsEngine) {
        this.alertsEngine = alertsEngine;
    }

    public DefinitionsService getDefinitionsService() {
        return definitionsService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public ActionsService getActionsService() {
        return actionsService;
    }

    public void setActionsService(ActionsService actionsService) {
        this.actionsService = actionsService;
    }

    public IncomingDataManager getIncomingDataManager() {
        return incomingDataManager;
    }

    public void setIncomingDataManager(IncomingDataManager incomingDataManager) {
        this.incomingDataManager = incomingDataManager;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public void setExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    public void setProperties(PropertiesService properties) {
        this.properties = properties;
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
        PreparedStatement insertAlert = CassStatement.get(session, CassStatement.INSERT_ALERT);
        PreparedStatement insertAlertTrigger = CassStatement.get(session, CassStatement.INSERT_ALERT_TRIGGER);
        PreparedStatement insertAlertCtime = CassStatement.get(session, CassStatement.INSERT_ALERT_CTIME);
        PreparedStatement insertAlertStime = CassStatement.get(session, CassStatement.INSERT_ALERT_STIME);
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            BatchStatement batch = new BatchStatement(batchType);
            int i = 0;
            for(Alert a : alerts) {
                batch.add(insertAlert.bind(a.getTenantId(), a.getAlertId(), JsonUtil.toJson(a)));
                batch.add(insertAlertTrigger.bind(a.getTenantId(), a.getAlertId(), a.getTriggerId()));
                batch.add(insertAlertCtime.bind(a.getTenantId(), a.getAlertId(), a.getCtime()));
                batch.add(insertAlertStime.bind(a.getTenantId(), a.getAlertId(), a.getCurrentLifecycle().getStime()));

                a.getTags().entrySet().stream().forEach(tag -> {
                    batch.add(insertTag.bind(a.getTenantId(), TagType.ALERT.name(),
                            tag.getKey(), tag.getValue(), a.getId()));
                });
                i += batch.size();
                if (i > batchSize) {
                    futures.add(session.executeAsync(batch));
                    batch.clear();
                    i = 0;
                }
            }
            if (batch.size() > 0) {
                futures.add(session.executeAsync(batch));
            }
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
        PreparedStatement insertEvent = CassStatement.get(session, CassStatement.INSERT_EVENT);
        PreparedStatement insertEventCategory = CassStatement.get(session, CassStatement.INSERT_EVENT_CATEGORY);
        PreparedStatement insertEventCtime = CassStatement.get(session, CassStatement.INSERT_EVENT_CTIME);
        PreparedStatement insertEventTrigger = CassStatement.get(session, CassStatement.INSERT_EVENT_TRIGGER);
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            BatchStatement batch = new BatchStatement(batchType);
            int i = 0;
            for (Event e : events) {
                batch.add(insertEvent.bind(e.getTenantId(), e.getId(), JsonUtil.toJson(e)));
                batch.add(insertEventCategory.bind(e.getTenantId(), e.getCategory(), e.getId()));
                batch.add(insertEventCtime.bind(e.getTenantId(), e.getCtime(), e.getId()));
                if (null != e.getTrigger()) {
                    batch.add(insertEventTrigger.bind(e.getTenantId(), e.getTrigger().getId(), e.getId()));
                }
                e.getTags().entrySet().stream().forEach(tag -> {
                    batch.add(insertTag.bind(e.getTenantId(), TagType.EVENT.name(), tag.getKey(), tag.getValue(),
                            e.getId()));
                });
                i += batch.size();
                if (i > batchSize) {
                    futures.add(session.executeAsync(batch));
                    batch.clear();
                    i = 0;
                }
            }
            if (batch.size() > 0) {
                futures.add(session.executeAsync(batch));
            }
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
    public void addAlertTags(String tenantId, Collection<String> alertIds, Map<String, String> tags) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            throw new IllegalArgumentException("AlertIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        // Only tag existing alerts
        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        Page<Alert> existingAlerts = getAlerts(tenantId, criteria, null);

        PreparedStatement updateAlert = CassStatement.get(session, CassStatement.UPDATE_ALERT);
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            BatchStatement batch = new BatchStatement(batchType);
            int i = 0;
            for (Alert a : existingAlerts) {
                tags.entrySet().stream().forEach(tag -> {
                    a.addTag(tag.getKey(), tag.getValue());
                    batch.add(insertTag.bind(tenantId, TagType.ALERT.name(), tag.getKey(), tag.getValue(), a.getId()));
                });
                batch.add(updateAlert.bind(JsonUtil.toJson(a), tenantId, a.getAlertId()));
                i += batch.size();
                if (i > batchSize) {
                    futures.add(session.executeAsync(batch));
                    batch.clear();
                    i = 0;
                }
            }
            if (batch.size() > 0) {
                futures.add(session.executeAsync(batch));
            }
            Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void addEventTags(String tenantId, Collection<String> eventIds, Map<String, String> tags) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(eventIds)) {
            throw new IllegalArgumentException("EventIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        // Only tag existing events
        EventsCriteria criteria = new EventsCriteria();
        criteria.setEventIds(eventIds);
        Page<Event> existingEvents = getEvents(tenantId, criteria, null);

        PreparedStatement updateEvent = CassStatement.get(session, CassStatement.UPDATE_EVENT);
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            BatchStatement batch = new BatchStatement(batchType);
            int i = 0;
            for (Event e : existingEvents) {
                tags.entrySet().stream().forEach(tag -> {
                    e.addTag(tag.getKey(), tag.getValue());
                    batch.add(insertTag.bind(tenantId, TagType.EVENT.name(), tag.getKey(), tag.getValue(), e.getId()));
                });
                batch.add(updateEvent.bind(JsonUtil.toJson(e), tenantId, e.getId()));

            }
            if (batch.size() > 0) {
                futures.add(session.executeAsync(batch));
            }
            Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeAlertTags(String tenantId, Collection<String> alertIds, Collection<String> tags)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(alertIds)) {
            throw new IllegalArgumentException("AlertIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        // Only untag existing alerts
        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        Page<Alert> existingAlerts = getAlerts(tenantId, criteria, null);

        PreparedStatement updateAlert = CassStatement.get(session, CassStatement.UPDATE_ALERT);
        PreparedStatement deleteTag = CassStatement.get(session, CassStatement.DELETE_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            BatchStatement batch = new BatchStatement(batchType);
            int i = 0;
            for (Alert a : existingAlerts) {
                tags.stream().forEach(tag -> {
                    if (a.getTags().containsKey(tag)) {
                        batch.add(deleteTag.bind(tenantId, TagType.ALERT.name(), tag, a.getTags().get(tag), a.getId()));
                        a.removeTag(tag);
                    }
                });
                batch.add(updateAlert.bind(JsonUtil.toJson(a), tenantId, a.getAlertId()));
                i += batch.size();
                if (i > batchSize) {
                    futures.add(session.executeAsync(batch));
                    batch.clear();
                    i = 0;
                }
            }
            if (batch.size() > 0) {
                futures.add(session.executeAsync(batch));
            }
            Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeEventTags(String tenantId, Collection<String> eventIds, Collection<String> tags)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(eventIds)) {
            throw new IllegalArgumentException("EventIds must be not null");
        }
        if (isEmpty(tags)) {
            throw new IllegalArgumentException("Tags must be not null");
        }

        // Only untag existing events
        EventsCriteria criteria = new EventsCriteria();
        criteria.setEventIds(eventIds);
        Page<Event> existingEvents = getEvents(tenantId, criteria, null);

        PreparedStatement updateEvent = CassStatement.get(session, CassStatement.UPDATE_EVENT);
        PreparedStatement deleteTag = CassStatement.get(session, CassStatement.DELETE_TAG);

        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            BatchStatement batch = new BatchStatement(batchType);
            int i = 0;
            for (Event e : existingEvents) {
                tags.stream().forEach(tag -> {
                    if (e.getTags().containsKey(tag)) {
                        batch.add(deleteTag.bind(tenantId, TagType.EVENT.name(), tag, e.getTags().get(tag), e.getId()));
                        e.removeTag(tag);
                    }
                });
                batch.add(updateEvent.bind(JsonUtil.toJson(e), tenantId, e.getId()));
                i += batch.size();
                if (i > batchSize) {
                    futures.add(session.executeAsync(batch));
                    batch.clear();
                    i = 0;
                }
            }
            if (batch.size() > 0) {
                futures.add(session.executeAsync(batch));
            }
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

    // The DB-Level filtering approach implemented below is a best-practice for dealing
    // with Cassandra.  It's basically a series of queries, one for each filter, with a progressive
    // intersection of the resulting ID set.  This works well in most cases but not when filtering on
    // low-cardinality fields, where the indexing is poor and the number of returned IDs could be vast for
    // a single one, or set, of those few field values.  So, we leave those filters (status, severity) to the end,
    // and then filter manually on the fetched set of Alerts.
    @Override
    public Page<Alert> getAlerts(String tenantId, AlertsCriteria criteria, Pager pager) throws Exception {
        return getAlerts(Collections.singleton(tenantId), criteria, pager);
    }

    /*
        TODO (lponce)
        This is a convenience method to have a first cross-tenant query.
        First implementation is sequential.
        Probably we could use an executor to send parallel tasks but we need to count with synchronization and
        ordering to be deterministic.
        So there are some space for future improvements here.
        The goal of this first implementation is to offer the feature.
     */
    @Override
    public Page<Alert> getAlerts(Set<String> tenantIds, AlertsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantIds)) {
            throw new IllegalArgumentException("TenantIds must be not null");
        }
        List<Alert> alerts = new ArrayList<>();
        if (tenantIds.size() == 1) {
            alerts.addAll(getAlerts(tenantIds.iterator().next(), criteria));
        } else {
            TreeSet<String> orderedTenantIds = new TreeSet<>(tenantIds);
            List<Future> futures = new ArrayList<>();
            orderedTenantIds.stream().forEach(tenantId -> {
                futures.add(executor.submit(() -> {
                    try {
                        List<Alert> tenantAlerts = getAlerts(tenantId, criteria);
                        synchronized (alerts) {
                            alerts.addAll(tenantAlerts);
                        }
                    } catch (Exception e) {
                        msgLog.errorDatabaseException(e.getMessage());
                    }
                }));
            });
            futures.stream().forEach(f -> {
                try {
                    f.get();
                } catch (Exception e) {
                    msgLog.errorDatabaseException(e.getMessage());
                }
            });
        }
        if (alerts.isEmpty()) {
            return new Page<>(alerts, pager, 0);
        } else {
            return preparePage(alerts, pager);
        }
    }

    private List<Alert> getAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
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
                    Get alertIds explicitly added into the criteria. Start with these as there is no query involved
                 */
                if (criteria.hasAlertIdCriteria()) {
                    Set<String> alertIdsFilteredByAlerts = filterByAlerts(criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByAlerts);
                    } else {
                        alertIds.addAll(alertIdsFilteredByAlerts);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
                    }
                    activeFilter = true;
                }

                /*
                    Get alertIds via tagQuery
                 */
                if (criteria.hasTagQueryCriteria()) {
                    Set<String> alertIdsFilteredByTagQuery = getIdsByTagQuery(tenantId, TagType.ALERT,
                            criteria.getTagQuery());
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByTagQuery);
                    } else {
                        alertIds.addAll(alertIdsFilteredByTagQuery);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
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
                    } else {
                        alertIds.addAll(alertIdsFilteredByTriggers);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
                    }
                    activeFilter = true;
                }

                /*
                    Get alertIds filtered by time clause
                 */
                if (criteria.hasCTimeCriteria()) {
                    Set<String> alertIdsFilteredByTime = filterByCTime(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByTime);
                    } else {
                        alertIds.addAll(alertIdsFilteredByTime);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
                    }
                    activeFilter = true;
                }

                /*
                    Get alertIds filtered by resolved time clause
                 */
                if (criteria.hasResolvedTimeCriteria()) {
                    Set<String> alertIdsFilteredByResolvedTime = filterByResolvedTime(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByResolvedTime);
                    } else {
                        alertIds.addAll(alertIdsFilteredByResolvedTime);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
                    }
                    activeFilter = true;
                }

                /*
                    Get alertIds filtered by ack time clause
                 */
                if (criteria.hasAckTimeCriteria()) {
                    Set<String> alertIdsFilteredByAckTime = filterByAckTime(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByAckTime);
                    } else {
                        alertIds.addAll(alertIdsFilteredByAckTime);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
                    }
                    activeFilter = true;
                }

                /*
                    Get alertsIds filteres by status time clause
                 */
                if (criteria.hasStatusTimeCriteria()) {
                    Set<String> alertIdsFilteredByStatusTime = filterByStatusTime(tenantId, criteria);
                    if (activeFilter) {
                        alertIds.retainAll(alertIdsFilteredByStatusTime);
                    } else {
                        alertIds.addAll(alertIdsFilteredByStatusTime);
                    }
                    if (alertIds.isEmpty()) {
                        return alerts;
                    }
                    activeFilter = true;
                }

                /*
                    Below this point we filter manually  because the remaining filters have a low cardinality of
                    values, and are not efficiently handled with database indexes and the intersection-based approach.
                    Fetch the alerts now and proceed.
                 */
                if (activeFilter) {
                    PreparedStatement selectAlertsByTenantAndAlert = CassStatement
                            .get(session, CassStatement.SELECT_ALERT);
                    List<ResultSetFuture> futures = alertIds.stream()
                            .map(alertId -> session.executeAsync(selectAlertsByTenantAndAlert.bind(tenantId, alertId)))
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
                    // This is the worst-case scenario of criteria featuring only manual filtering.  Generate a
                    // warning because clients should be discouraged from using such vague criteria.
                    log.warnf("Only supplying Severity and/or Status can be slow and return large Sets: %s",
                            criteria);
                    fetchAllAlerts(tenantId, thin, alerts);
                }

                /*
                     filter by severities
                 */
                if (criteria.hasSeverityCriteria()) {
                    filterBySeverities(tenantId, criteria, alerts);
                    if (alerts.isEmpty()) {
                        return alerts;
                    }
                }

                /*
                    filter by statuses
                 */
                if (criteria.hasStatusCriteria()) {
                    filterByStatuses(tenantId, criteria, alerts);
                    if (alerts.isEmpty()) {
                        return alerts;
                    }
                }
            } else {
                /*
                    Get all alerts - Single query
                 */
                fetchAllAlerts(tenantId, thin, alerts);
            }

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return alerts;
    }

    private void fetchAllAlerts(String tenantId, boolean thin, Collection<Alert> alerts) {
        PreparedStatement selectAlertsByTenant = CassStatement.get(session,
                CassStatement.SELECT_ALERTS_BY_TENANT);
        ResultSet rsAlerts = session.execute(selectAlertsByTenant.bind(tenantId));
        for (Row row : rsAlerts) {
            String payload = row.getString("payload");
            Alert alert = JsonUtil.fromJson(payload, Alert.class, thin);
            alerts.add(alert);
        }
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
                pager.getOrder().stream()
                        .filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            AlertComparator comparator = new AlertComparator(o.getField(), o.getDirection());
                            Collections.sort(ordered, comparator);
                        });
            }
            if (!pager.isLimited() || ordered.size() < pager.getStart()) {
                pager = new Pager(0, ordered.size(), pager.getOrder());
                return new Page<>(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page<>(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page<>(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            Field defaultField = Field.ALERT_ID;
            Order.Direction defaultDirection = Order.Direction.ASCENDING;
            AlertComparator comparator = new AlertComparator(defaultField.getText(), defaultDirection.ASCENDING);
            pager = Pager.builder().withPageSize(alerts.size()).orderBy(defaultField.getText(), defaultDirection)
                    .build();
            Collections.sort(alerts, comparator);
            return new Page<>(alerts, pager, alerts.size());
        }
    }

    private Set<String> filterByAlerts(AlertsCriteria criteria) {
        Set<String> result = Collections.emptySet();
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
        Set<String> result = Collections.emptySet();
        Set<String> triggerIds = extractTriggerIds(tenantId, criteria);

        if (triggerIds.size() > 0) {
            PreparedStatement selectAlertsTriggers = CassStatement.get(session, CassStatement.SELECT_ALERT_TRIGGER);
            List<ResultSetFuture> futures = triggerIds.stream()
                    .map(triggerId -> session.executeAsync(selectAlertsTriggers.bind(tenantId, triggerId)))
                    .collect(Collectors.toList());
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

        Set<String> triggerIds = hasTriggerId || hasTriggerIds ? new HashSet<>() : Collections.emptySet();

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
        Set<String> result = Collections.emptySet();

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

    private void filterBySeverities(String tenantId, AlertsCriteria criteria, Collection<Alert> alerts)
            throws Exception {
        Set<Severity> severities = new HashSet<>();
        if (isEmpty(criteria.getSeverities())) {
            if (criteria.getSeverity() != null) {
                severities.add(criteria.getSeverity());
            }
        } else {
            severities.addAll(criteria.getSeverities());
        }

        if (severities.size() > 0) {
            for (Iterator<Alert> i = alerts.iterator(); i.hasNext();) {
                Alert a = i.next();
                if (!severities.contains(a.getSeverity())) {
                    i.remove();
                }
            }
        }
    }

    private void filterByStatuses(String tenantId, AlertsCriteria criteria, Collection<Alert> alerts)
            throws Exception {
        Set<Status> statusSet = new HashSet<>();
        if (isEmpty(criteria.getStatusSet())) {
            if (criteria.getStatus() != null) {
                statusSet.add(criteria.getStatus());
            }
        } else {
            statusSet.addAll(criteria.getStatusSet());
        }

        if (statusSet.size() > 0) {
            for (Iterator<Alert> i = alerts.iterator(); i.hasNext();) {
                Alert a = i.next();
                if (!statusSet.contains(a.getStatus())) {
                    i.remove();
                }
            }
        }
    }

    private Set<String> filterByStatusTime(String tenantId, AlertsCriteria criteria) throws Exception {
        Set<String> result = Collections.emptySet();

        if (criteria.getStartStatusTime() != null || criteria.getEndStatusTime() != null) {
            result = new HashSet<>();

            BoundStatement boundStime;
            if (criteria.getStartStatusTime() != null && criteria.getEndStatusTime() != null) {
                PreparedStatement selectAlertSTimeStartEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_STIME_START_END);
                boundStime = selectAlertSTimeStartEnd.bind(tenantId, criteria.getStartStatusTime(),
                        criteria.getEndStatusTime());
            } else if (criteria.getStartStatusTime() != null) {
                PreparedStatement selectAlertSTimeStart = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_STIME_START);
                boundStime = selectAlertSTimeStart.bind(tenantId, criteria.getStartStatusTime());
            } else {
                PreparedStatement selectAlertSTimeEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_STIME_END);
                boundStime = selectAlertSTimeEnd.bind(tenantId, criteria.getEndStatusTime());
            }

            ResultSet rsAlertsStimes = session.execute(boundStime);
            for (Row row : rsAlertsStimes) {
                String alertId = row.getString("alertId");
                result.add(alertId);
            }
        }
        return result;
    }

    private Set<String> filterByEvents(EventsCriteria criteria) {
        Set<String> result = Collections.emptySet();
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

    private Set<String> filterByResolvedTime(String tenantId, AlertsCriteria criteria) throws Exception {
        Set<String> result = Collections.emptySet();

        if (criteria.getStartResolvedTime() != null || criteria.getEndResolvedTime() != null) {
            result = new HashSet<>();

            BoundStatement boundLifecycleTime;
            if (criteria.getStartResolvedTime() != null && criteria.getEndResolvedTime() != null) {
                PreparedStatement selectAlertLifecycleStartEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_LIFECYCLE_START_END);
                boundLifecycleTime = selectAlertLifecycleStartEnd.bind(tenantId, Status.RESOLVED.name(),
                        criteria.getStartResolvedTime(), criteria.getEndResolvedTime());
            } else if (criteria.getStartResolvedTime() != null) {
                PreparedStatement selectAlertLifecycleStart = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_LIFECYCLE_START);
                boundLifecycleTime = selectAlertLifecycleStart.bind(tenantId, Status.RESOLVED.name(),
                        criteria.getStartResolvedTime());
            } else {
                PreparedStatement selectAlertLifecycleEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_LIFECYCLE_END);
                boundLifecycleTime = selectAlertLifecycleEnd.bind(tenantId, criteria.getEndResolvedTime());
            }

            ResultSet rsAlertsLifecycleTimes = session.execute(boundLifecycleTime);
            for (Row row : rsAlertsLifecycleTimes) {
                String alertId = row.getString("alertId");
                result.add(alertId);
            }
        }
        return result;
    }

    private Set<String> filterByAckTime(String tenantId, AlertsCriteria criteria) throws Exception {
        Set<String> result = Collections.emptySet();

        if (criteria.getStartAckTime() != null || criteria.getEndAckTime() != null) {
            result = new HashSet<>();

            BoundStatement boundLifecycleTime;
            if (criteria.getStartAckTime() != null && criteria.getEndAckTime() != null) {
                PreparedStatement selectAlertLifecycleStartEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_LIFECYCLE_START_END);
                boundLifecycleTime = selectAlertLifecycleStartEnd.bind(tenantId, Status.ACKNOWLEDGED.name(),
                        criteria.getStartAckTime(), criteria.getEndAckTime());
            } else if (criteria.getStartAckTime() != null) {
                PreparedStatement selectAlertLifecycleStart = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_LIFECYCLE_START);
                boundLifecycleTime = selectAlertLifecycleStart.bind(tenantId, Status.ACKNOWLEDGED.name(),
                        criteria.getStartAckTime());
            } else {
                PreparedStatement selectAlertLifecycleEnd = CassStatement.get(session,
                        CassStatement.SELECT_ALERT_LIFECYCLE_END);
                boundLifecycleTime = selectAlertLifecycleEnd.bind(tenantId, criteria.getEndAckTime());
            }

            ResultSet rsAlertsLifecycleTimes = session.execute(boundLifecycleTime);
            for (Row row : rsAlertsLifecycleTimes) {
                String alertId = row.getString("alertId");
                result.add(alertId);
            }
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
            BoundStatement bs = nameOnly ? selectTagsByName.bind(tenantId, tagType.name(), tag.getKey())
                    : selectTagsByNameAndValue.bind(tenantId, tagType.name(), tag.getKey(), tag.getValue());
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

    private static class TagValue {
        private String tenantId;
        private String tag;
        private String value;
        private String id;

        public TagValue(String tenantId, String tag, String value, String id) {
            this.tenantId = tenantId;
            this.tag = tag;
            this.value = value;
            this.id = id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getTag() {
            return tag;
        }

        public String getValue() {
            return value;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TagValue tagValue = (TagValue) o;

            if (tenantId != null ? !tenantId.equals(tagValue.tenantId) : tagValue.tenantId != null) return false;
            if (tag != null ? !tag.equals(tagValue.tag) : tagValue.tag != null) return false;
            if (value != null ? !value.equals(tagValue.value) : tagValue.value != null) return false;
            return id != null ? id.equals(tagValue.id) : tagValue.id == null;
        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (tag != null ? tag.hashCode() : 0);
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (id != null ? id.hashCode() : 0);
            return result;
        }
    }

    private Set<TagValue> getTagValueByTagName(String tenantId, TagType tagType, String tagName)
        throws Exception {
        Set<TagValue> tagValues = new HashSet<>();
        PreparedStatement selectTagsByName = CassStatement.get(session, CassStatement.SELECT_TAGS_BY_NAME);
        session.executeAsync(selectTagsByName.bind(tenantId, tagType.name(), tagName)).get().all() .forEach(r -> {
            tagValues.add(new TagValue(tenantId, tagName, r.getString("value"), r.getString("id")));
        });
        return tagValues;
    }

    private boolean filterTagValue(String op, String regexps, String value) {
        if (op.equals(EQ) || op.equals(NEQ)) {
            boolean matches;
            String regexp;
            if (regexps.equals("'*'")) {
                matches = true;
            } else if (regexps.charAt(0) == '\'') {
                regexp = regexps.substring(1, regexps.length() - 1);
                matches = value.matches(regexp);
            } else {
                regexp = regexps;
                matches = value.equals(regexp);
            }
            return op.equals(EQ) ? matches : !matches;
        } else {
            String array = regexps.substring(1, regexps.length() - 1);
            String[] items = array.split(",");
            for (String item : items) {
                if (item.equals("'*'")) {
                    return op.equals(IN) ? true : false;
                }
                String regexp = item.charAt(0) == '\'' ? item.substring(1, item.length() - 1) : item;
                if (value.matches(regexp)) {
                    return op.equals(IN) ? true : false;
                }
            }
            return op.equals(IN) ? false : true;
        }
    }

    private Set<String> getIdsByTagQuery(String tenantId, TagType tagType, String tagQuery)
            throws Exception {
        ExpressionTagQueryParser parser = new ExpressionTagQueryParser(tokens -> {
            Set<String> result = new HashSet<>();
            if (tokens != null) {
                String tag;
                Map<String, String> map = new HashMap<>();
                if (tokens.size() == 1) {
                    // tag
                    tag = tokens.get(0);
                    map.put(tag, "*");
                    Set<String> idsByTag = getIdsByTags(tenantId, tagType, map);
                    result = idsByTag;
                } else if (tokens.size() == 2) {
                    // not tag
                    tag = tokens.get(1);
                    Set<String> allIds;
                    if (TagType.ALERT.equals(tagType)) {
                        allIds = getAllAlertIds(tenantId);
                    } else {
                        allIds = getAllEventIds(tenantId);
                    }
                    map.put(tag, "*");
                    Set<String> idsByTag = getIdsByTags(tenantId, tagType, map);
                    allIds.removeAll(idsByTag);
                    result = allIds;
                } else {
                    tag = tokens.get(0);
                    String op;
                    String regexp;
                    if (tokens.size() == 3) {
                        op = tokens.get(1);
                        regexp = tokens.get(2);
                    } else {
                        // not in [array]
                        op = tokens.get(1) + tokens.get(2);
                        regexp = tokens.get(3);
                    }
                    Set<TagValue> tagValues = getTagValueByTagName(tenantId, tagType, tag);
                    result = tagValues.stream()
                            .filter(tagValue -> filterTagValue(op, regexp, tagValue.getValue()))
                            .map(tagValue -> tagValue.getId())
                            .collect(Collectors.toSet());
                }
            }
            return result;
        });
        return parser.resolve(tagQuery);
    }

    private Set<String> getAllAlertIds(String tenantId) throws Exception {
        Set<String> ids = new HashSet<>();
        PreparedStatement selectAlertIdsByTenant = CassStatement.get(session, CassStatement.SELECT_ALERT_IDS_BY_TENANT);
        ResultSetFuture future = session.executeAsync(selectAlertIdsByTenant.bind(tenantId));
        future.get().all().stream().forEach(r -> ids.add(r.getString("alertId")));
        return ids;
    }

    private Set<String> getAllEventIds(String tenantId) throws Exception {
        Set<String> ids = new HashSet<>();
        PreparedStatement selectEventIdsByTenant = CassStatement.get(session, CassStatement.SELECT_EVENT_IDS_BY_TENANT);
        ResultSetFuture future = session.executeAsync(selectEventIdsByTenant.bind(tenantId));
        future.get().all().stream().forEach(r -> ids.add(r.getString("id")));
        return ids;
    }

    // The DB-Level filtering approach implemented below is a best-practice for dealing
    // with Cassandra.  It's basically a series of queries, one for each filter, with a progressive
    // intersection of the resulting ID set.  This works well in most cases but not when filtering on
    // low-cardinality fields, where the indexing is poor and the number of returned IDs could be vast for
    // a single one, or set, of those few field values.  Event.category *may* have low cardinality, so
    // although it is indexed, we leave it to the end and then optionally filter it manually depending on
    // the current result set size.
    @Override
    public Page<Event> getEvents(String tenantId, EventsCriteria criteria, Pager pager) throws Exception {
        return getEvents(Collections.singleton(tenantId), criteria, pager);
    }

    @Override
    public Page<Event> getEvents(Set<String> tenantIds, EventsCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantIds)) {
            throw new IllegalArgumentException("TenantIds must be not null");
        }
        List<Event> events = new ArrayList<>();
        if (tenantIds.size() == 1) {
            events.addAll(getEvents(tenantIds.iterator().next(), criteria));
        } else {
            TreeSet<String> orderedTenantIds = new TreeSet<>(tenantIds);
            List<Future> futures = new ArrayList<>();
            orderedTenantIds.stream().forEach(tenantId -> {
                futures.add(executor.submit(() -> {
                    try {
                        List<Event> tenantEvents = getEvents(tenantId, criteria);
                        synchronized (events) {
                            events.addAll(tenantEvents);
                        }
                    } catch (Exception e) {
                        msgLog.errorDatabaseException(e.getMessage());
                    }
                }));
            });
            futures.stream().forEach(f -> {
                try {
                    f.get();
                } catch (Exception e) {
                    msgLog.errorDatabaseException(e.getMessage());
                }
            });
        }
        if (events.isEmpty()) {
            return new Page<>(events, pager, 0);
        } else {
            return prepareEventsPage(events, pager);
        }
    }

    private List<Event> getEvents(String tenantId, EventsCriteria criteria) throws Exception {
        boolean filter = (null != criteria && criteria.hasCriteria());
        boolean thin = (null != criteria && criteria.isThin());
        int noQuerySize = (null == criteria || null == criteria.getCriteriaNoQuerySize()) ? criteriaNoQuerySize
                : criteria.getCriteriaNoQuerySize().intValue();

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
                    } else {
                        eventIds.addAll(idsFilteredByEvents);
                    }
                    if (eventIds.isEmpty()) {
                        return events;
                    }
                    activeFilter = true;
                }

                /*
                    Get eventIds via tagQuery
                 */
                if (criteria.hasTagQueryCriteria()) {
                    Set<String> idsFilteredByTagQuery = getIdsByTagQuery(tenantId, TagType.EVENT,
                            criteria.getTagQuery());
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByTagQuery);
                    } else {
                        eventIds.addAll(idsFilteredByTagQuery);
                    }
                    if (eventIds.isEmpty()) {
                        return events;
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
                    } else {
                        eventIds.addAll(idsFilteredByTriggers);
                    }
                    if (eventIds.isEmpty()) {
                        return events;
                    }
                    activeFilter = true;
                }

                /*
                    Get eventtIds filtered by time clause
                 */
                if (criteria.hasCTimeCriteria()) {
                    Set<String> idsFilteredByTime = filterByCTime(tenantId, criteria);
                    if (activeFilter) {
                        eventIds.retainAll(idsFilteredByTime);
                    } else {
                        eventIds.addAll(idsFilteredByTime);
                    }
                    if (eventIds.isEmpty()) {
                        return events;
                    }
                    activeFilter = true;
                }

                /*
                   Below this point we prefer manual filtering if the result set is small enough. If so, fetch the
                   events now and filter manually. Otherwise, don't fetch and perform further filtering by query.
                */
                if (activeFilter && (eventIds.size() <= noQuerySize)) {
                    fetchEvents(tenantId, eventIds, thin, events);
                }

                if (criteria.hasCategoryCriteria()) {
                    if (events.isEmpty()) {
                        // filter by query and intersection
                        Set<String> idsFilteredByCategory = filterByCategories(tenantId, criteria);
                        if (activeFilter) {
                            eventIds.retainAll(idsFilteredByCategory);
                        } else {
                            eventIds.addAll(idsFilteredByCategory);
                        }
                        if (eventIds.isEmpty()) {
                            return events;
                        }
                        activeFilter = true;

                    } else {
                        // filter manually
                        filterByCategories(tenantId, criteria, events);
                        if (events.isEmpty()) {
                            return events;
                        }
                    }
                }

                /*
                    If we have reached this point then we have at least 1 filtered eventId. If we have
                    not already fetched the resulting events, do so now.
                 */
                if (events.isEmpty()) {
                    fetchEvents(tenantId, eventIds, thin, events);
                }

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

        return events;
    }

    private void fetchEvents(String tenantId, Set<String> eventIds, boolean thin, List<Event> events)
            throws Exception {
        PreparedStatement selectEvent = CassStatement
                .get(session, CassStatement.SELECT_EVENT);
        List<ResultSetFuture> futures = eventIds.stream()
                .map(id -> session.executeAsync(selectEvent.bind(tenantId, id)))
                .collect(Collectors.toList());
        List<ResultSet> rsEvents = Futures.allAsList(futures).get();
        rsEvents.stream().forEach(r -> {
            for (Row row : r) {
                String payload = row.getString("payload");
                Event event = JsonUtil.fromJson(payload, Event.class, thin);
                events.add(event);
            }
        });
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
                return new Page<>(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page<>(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page<>(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            EventComparator.Field defaultField = EventComparator.Field.ID;
            Order.Direction defaultDirection = Order.Direction.ASCENDING;
            pager = Pager.builder().withPageSize(events.size()).orderBy(defaultField.getName(),
                    defaultDirection).build();
            EventComparator comparator = new EventComparator(defaultField.getName(), defaultDirection);
            Collections.sort(events, comparator);
            return new Page<>(events, pager, events.size());
        }
    }

    private Set<String> filterByTriggers(String tenantId, EventsCriteria criteria) throws Exception {
        Set<String> result = Collections.emptySet();
        Set<String> triggerIds = extractTriggerIds(tenantId, criteria);

        if (triggerIds.size() > 0) {
            PreparedStatement selectEventsTriggers = CassStatement.get(session, CassStatement.SELECT_EVENT_TRIGGER);

            List<ResultSetFuture> futures = new ArrayList<>();
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

        Set<String> triggerIds = hasTriggerId || hasTriggerIds ? new HashSet<>() : Collections.emptySet();

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
        Set<String> result = Collections.emptySet();

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
        Set<String> result = Collections.emptySet();

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
            List<ResultSetFuture> futures = categories.stream()
                    .map(category -> session.executeAsync(selectEventCategory.bind(tenantId, category)))
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

    private void filterByCategories(String tenantId, EventsCriteria criteria, Collection<Event> events)
            throws Exception {
        Set<String> categories = new HashSet<>();
        if (isEmpty(criteria.getCategories())) {
            if (criteria.getCategory() != null) {
                categories.add(criteria.getCategory());
            }
        } else {
            categories.addAll(criteria.getCategories());
        }

        if (categories.size() > 0) {
            for (Iterator<Event> i = events.iterator(); i.hasNext();) {
                Event e = i.next();
                if (!categories.contains(e.getCategory())) {
                    i.remove();
                }
            }
        }
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
            a.addNote(ackBy, ackNotes);
            a.addLifecycle(Status.ACKNOWLEDGED, ackBy, System.currentTimeMillis());
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
        PreparedStatement deleteAlertTrigger = CassStatement.get(session, CassStatement.DELETE_ALERT_TRIGGER);
        PreparedStatement deleteAlertLifecycle = CassStatement.get(session, CassStatement.DELETE_ALERT_LIFECYCLE);
        PreparedStatement deleteAlertStime = CassStatement.get(session, CassStatement.DELETE_ALERT_STIME);
        if (deleteAlert == null || deleteAlertCtime == null || deleteAlertTrigger == null
                || deleteAlertLifecycle == null) {
            throw new RuntimeException("delete*Alerts PreparedStatement is null");
        }

        List<ResultSetFuture> futures = new ArrayList<>();
        int i = 0;
        BatchStatement batch = new BatchStatement(batchType);
        for (Alert a : alertsToDelete) {
            String id = a.getAlertId();
            batch.add(deleteAlert.bind(tenantId, id));
            batch.add(deleteAlertCtime.bind(tenantId, a.getCtime(), id));
            batch.add(deleteAlertTrigger.bind(tenantId, a.getTriggerId(), id));
            a.getLifecycle().stream().forEach(l -> {
                batch.add(deleteAlertLifecycle.bind(tenantId, l.getStatus().name(), l.getStime(), a.getAlertId()));
                batch.add(deleteAlertStime.bind(tenantId, l.getStime(), a.getAlertId()));
            });
            i += batch.size();
            if (i > batchSize) {
                futures.add(session.executeAsync(batch));
                batch.clear();
                i = 0;
            }
        }
        if (batch.size() > 0) {
            futures.add(session.executeAsync(batch));
        }
        Futures.allAsList(futures).get();

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

        List<ResultSetFuture> futures = new ArrayList<>();
        int i = 0;
        BatchStatement batch = new BatchStatement(batchType);
        for (Event e : eventsToDelete) {
            String id = e.getId();
            batch.add(deleteEvent.bind(tenantId, id));
            batch.add(deleteEventCategory.bind(tenantId, e.getCategory(), id));
            batch.add(deleteEventCTime.bind(tenantId, e.getCtime(), id));
            if (null != e.getTrigger()) {
                batch.add(deleteEventTrigger.bind(tenantId, e.getTrigger().getId(), id));
            }
            i += batch.size();
            if (i > batchSize) {
                futures.add(session.executeAsync(batch));
                batch.clear();
                i = 0;
            }
        }
        if (batch.size() > 0) {
            futures.add(session.executeAsync(batch));
        }
        Futures.allAsList(futures).get();

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
            a.addNote(resolvedBy, resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            a.addLifecycle(Status.RESOLVED, resolvedBy, System.currentTimeMillis());
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
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria, null);

        for (Alert a : alertsToResolve) {
            a.addNote(resolvedBy, resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            a.addLifecycle(Status.RESOLVED, resolvedBy, System.currentTimeMillis());
            updateAlertStatus(a);
            sendAction(a);
        }

        handleResolveOptions(tenantId, triggerId, false);
    }

    private Alert updateAlertStatus(Alert alert) throws Exception {
        if (alert == null || alert.getAlertId() == null || alert.getAlertId().isEmpty()) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        try {
            PreparedStatement insertAlertLifecycle = CassStatement.get(session,
                    CassStatement.INSERT_ALERT_LIFECYCLE);
            PreparedStatement insertAlertStime = CassStatement.get(session,
                    CassStatement.INSERT_ALERT_STIME);
            PreparedStatement updateAlert = CassStatement.get(session,
                    CassStatement.UPDATE_ALERT);

            List<ResultSetFuture> futures = new ArrayList<>();
            Alert.LifeCycle lifecycle = alert.getCurrentLifecycle();
            if (lifecycle != null) {
                futures.add(session.executeAsync(insertAlertLifecycle.bind(alert.getTenantId(), alert.getAlertId(),
                        lifecycle.getStatus().name(), lifecycle.getStime())));
                futures.add(session.executeAsync(insertAlertStime.bind(alert.getTenantId(), alert.getAlertId(),
                        lifecycle.getStime())));
            }
            futures.add(session.executeAsync(updateAlert.bind(JsonUtil.toJson(alert), alert.getTenantId(),
                    alert.getAlertId())));

            Futures.allAsList(futures).get();

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
                ac.setStatusSet(EnumSet.complementOf(EnumSet.of(Status.RESOLVED)));
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
    public void sendData(Collection<Data> data) throws Exception {
        sendData(data, false);
    }

    @Override
    public void sendData(Collection<Data> data, boolean ignoreFiltering) throws Exception {
        if (isEmpty(data)) {
            return;
        }

        incomingDataManager.bufferData(new IncomingData(data, !ignoreFiltering));
    }

    @Override
    public void addEvents(Collection<Event> events) throws Exception {
        if (null == events || events.isEmpty()) {
            return;
        }
        persistEvents(events);
        sendEvents(events);
    }

    @Override
    public void sendEvents(Collection<Event> events) throws Exception {

        sendEvents(events, false);
    }

    @Override
    public void sendEvents(Collection<Event> events, boolean ignoreFiltering) throws Exception {
        if (isEmpty(events)) {
            return;
        }

        incomingDataManager.bufferEvents(new IncomingEvents(events, !ignoreFiltering));
    }

    private void sendAction(Alert a) {
        if (actionsService != null && a != null && a.getTrigger() != null) {
            actionsService.send(a.getTrigger(), a);
        }
    }

    private boolean isEmpty(Map<?, ?> m) {
        return null == m || m.isEmpty();
    }

    private boolean isEmpty(Collection<?> c) {
        return null == c || c.isEmpty();
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

}