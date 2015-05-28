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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Row;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.rules.RulesEngine;
import org.jboss.logging.Logger;
import com.datastax.driver.core.Session;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Cassandra implementation for {@link org.hawkular.alerts.api.services.AlertsService}.
 * This implementation processes data asynchronously using a buffer queue.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
public class CassAlertsServiceImpl implements AlertsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(CassAlertsServiceImpl.class);
    private static final String ENGINE_DELAY = "hawkular-alerts.engine-delay";
    private static final String ENGINE_PERIOD = "hawkular-alerts.engine-period";
    private static final String CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";
    private int delay;
    private int period;

    private Session session;
    private String keyspace;

    private Gson gson;

    private PreparedStatement insertAlert;
    private PreparedStatement insertAlertTrigger;
    private PreparedStatement insertAlertCtime;
    private PreparedStatement insertAlertStatus;
    private PreparedStatement updateAlert;
    private PreparedStatement selectAlertStatus;
    private PreparedStatement deleteAlertStatus;
    private PreparedStatement selectAlertsByTenant;
    private PreparedStatement selectAlertsByTenantAndAlert;
    private PreparedStatement selectAlertsTriggers;
    private PreparedStatement selectAlertCTimeStartEnd;
    private PreparedStatement selectAlertCTimeStart;
    private PreparedStatement selectAlertCTimeEnd;
    private PreparedStatement selectAlertStatusByTenantAndStatus;
    private PreparedStatement selectTagsTriggersByCategoryAndName;
    private PreparedStatement selectTagsTriggersByCategory;
    private PreparedStatement selectTagsTriggersByName;

    private final List<Data> pendingData;
    private final List<Alert> alerts;
    private final Set<Dampening> pendingTimeouts;
    private final Map<Trigger, List<Set<ConditionEval>>> autoResolvedTriggers;
    private final Set<Trigger> disabledTriggers;


    private final Timer wakeUpTimer;
    private TimerTask rulesTask;

    @EJB
    RulesEngine rules;

    @EJB
    DefinitionsService definitions;

    @EJB
    ActionsService actions;

    public CassAlertsServiceImpl() {
        pendingData = new CopyOnWriteArrayList<>();
        alerts = new CopyOnWriteArrayList<>();
        pendingTimeouts = new CopyOnWriteArraySet<>();
        autoResolvedTriggers = new HashMap<>();
        disabledTriggers = new CopyOnWriteArraySet<>();

        wakeUpTimer = new Timer("CassAlertsServiceImpl-Timer");

        delay = new Integer(AlertProperties.getProperty(ENGINE_DELAY, "1000"));
        period = new Integer(AlertProperties.getProperty(ENGINE_PERIOD, "2000"));
    }

    @SuppressWarnings("unused")
    public ActionsService getActions() {
        return actions;
    }

    public void setActions(ActionsService actions) {
        this.actions = actions;
    }

    public DefinitionsService getDefinitions() {
        return definitions;
    }

    public void setDefinitions(DefinitionsService definitions) {
        this.definitions = definitions;
    }

    public RulesEngine getRules() {
        return rules;
    }

    public void setRules(RulesEngine rules) {
        this.rules = rules;
    }

    @PostConstruct
    public void initServices() {
        try {
            if (this.keyspace == null) {
                this.keyspace = AlertProperties.getProperty(CASSANDRA_KEYSPACE, "hawkular_alerts");
            }

            if (session == null) {
                session = CassCluster.getSession();
            }

            initPreparedStatements();

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeHierarchyAdapter(ConditionEval.class, new GsonAdapter<ConditionEval>());
            gson = gsonBuilder.create();

            reload();
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                t.printStackTrace();
            }
            msgLog.errorCannotInitializeAlertsService(t.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (session != null) {
            session.close();
        }
    }

    private void initPreparedStatements() throws Exception {
        if (insertAlert == null) {
            insertAlert = session.prepare("INSERT INTO " + keyspace + ".alerts " +
                    "(tenantId, alertId, payload) VALUES (?, ?, ?) ");
        }
        if (insertAlertTrigger == null) {
            insertAlertTrigger = session.prepare("INSERT INTO " + keyspace + ".alerts_triggers " +
                    "(tenantId, alertId, triggerId) VALUES (?, ?, ?) ");
        }
        if (insertAlertCtime == null) {
            insertAlertCtime = session.prepare("INSERT INTO " + keyspace + ".alerts_ctimes " +
                    "(tenantId, alertId, ctime) VALUES (?, ?, ?) ");

        }
        if (insertAlertStatus == null) {
            insertAlertStatus = session.prepare("INSERT INTO " + keyspace + ".alerts_statuses " +
                    "(tenantId, alertId, status) VALUES (?, ?, ?) ");
        }
        if (updateAlert == null) {
            updateAlert = session.prepare("UPDATE " + keyspace + ".alerts " +
                    "SET payload = ? WHERE tenantId = ? AND alertId = ? ");
        }
        if (selectAlertStatus == null) {
            selectAlertStatus = session.prepare("SELECT alertId, status FROM " + keyspace + ".alerts_statuses " +
                    "WHERE tenantId = ? AND status = ? AND alertId = ? ");
        }
        if (deleteAlertStatus == null) {
            deleteAlertStatus = session.prepare("DELETE FROM " + keyspace + ".alerts_statuses " +
                    "WHERE tenantId = ? AND status = ? AND alertId = ? ");
        }
        if (selectAlertsByTenant == null) {
            selectAlertsByTenant = session.prepare("SELECT payload FROM " + keyspace + ".alerts " +
                    "WHERE tenantId = ? ");
        }
        if (selectAlertsByTenantAndAlert == null) {
            selectAlertsByTenantAndAlert = session.prepare("SELECT payload FROM " + keyspace + ".alerts " +
                    "WHERE tenantId = ? AND alertId = ? ");
        }
        if (selectAlertsTriggers == null) {
            selectAlertsTriggers = session.prepare("SELECT alertId FROM " + keyspace + ".alerts_triggers " +
                    "WHERE tenantId = ? AND " + "triggerId = ? ");
        }
        if (selectAlertCTimeStartEnd == null) {
            selectAlertCTimeStartEnd = session.prepare("SELECT alertId FROM " + keyspace + ".alerts_ctimes " +
                    "WHERE tenantId = ? AND ctime >= ? AND ctime <= ?");
        }
        if (selectAlertCTimeStart == null) {
            selectAlertCTimeStart = session.prepare("SELECT alertId FROM " + keyspace + ".alerts_ctimes " +
                    "WHERE tenantId = ? AND ctime >= ?");
        }
        if (selectAlertCTimeEnd == null) {
            selectAlertCTimeEnd = session.prepare("SELECT alertId FROM " + keyspace + ".alerts_ctimes " +
                    "WHERE tenantId = ? AND ctime <= ?");
        }
        if (selectAlertStatusByTenantAndStatus == null) {
            selectAlertStatusByTenantAndStatus = session.prepare("SELECT alertId FROM " + keyspace + "" +
                    ".alerts_statuses WHERE tenantId = ? AND status = ?");
        }
        if (selectTagsTriggersByCategoryAndName == null) {
            selectTagsTriggersByCategoryAndName = session.prepare("SELECT triggers FROM " + keyspace + "" +
                    ".tags_triggers WHERE tenantId = ? AND category = ? AND name = ?");
        }
        if (selectTagsTriggersByCategory == null) {
            selectTagsTriggersByCategory = session.prepare("SELECT triggers FROM " + keyspace + "" +
                    ".tags_triggers WHERE tenantId = ? AND category = ?");
        }
        if (selectTagsTriggersByName == null) {
            selectTagsTriggersByName = session.prepare("SELECT triggers FROM " + keyspace + "" +
                    ".tags_triggers WHERE tenantId = ? AND name = ?");
        }
    }

    @Override
    public void addAlerts(Collection<Alert> alerts) throws Exception {
        if (alerts == null) {
            throw new IllegalArgumentException("Alerts must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (insertAlert == null) {
            throw new RuntimeException("insertAlert PreparedStatement is null");
        }
        try {
            List<ResultSetFuture> futures = new ArrayList<>();
            alerts.stream().forEach(a -> {
                futures.add(session.executeAsync(insertAlert.bind(a.getTenantId(), a.getAlertId(), toJson(a))));
                futures.add(session.executeAsync(insertAlertTrigger.bind(a.getTenantId(), a.getAlertId(),
                        a.getTriggerId())));
                futures.add(session.executeAsync(insertAlertCtime.bind(a.getTenantId(), a.getAlertId(), a.getCtime())));
                futures.add(session.executeAsync(insertAlertStatus.bind(a.getTenantId(), a.getAlertId(),
                        a.getStatus().name())));
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
    public List<Alert> getAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        boolean filter = (null != criteria && criteria.hasCriteria());

        if (filter) {
            log.debugf("getAlerts criteria: %s", criteria.toString());
        }

        List<Alert> alerts = new ArrayList<>();
        Set<String> alertIds = new HashSet<>();

        try {
            if (filter) {
                /*
                    Get alertIds filtered by triggerIds clause
                 */
                Set<String> alertIdsFilteredByTriggers = new HashSet<>();
                boolean filterByTriggers = filterByTriggers(tenantId, alertIdsFilteredByTriggers, criteria);

                /*
                    Get alertsIds filtered by ctime clause
                 */
                Set<String> alertIdsFilteredByCtime = new HashSet<>();
                boolean filterByCtime = filterByCtime(tenantId, alertIdsFilteredByCtime, criteria);

                /*
                    Get alertsIds filtered by statutes clause
                 */
                Set<String> alertIdsFilteredByStatus = new HashSet<>();
                boolean filterByStatus = filterByStatuses(tenantId, alertIdsFilteredByStatus, criteria);

                /*
                    Get alertsIds explicitly added into the criteria
                 */
                Set<String> alertIdsFilteredByAlerts = new HashSet<>();
                boolean filterByAlerts = filterByAlerts(alertIdsFilteredByAlerts, criteria);

                /*
                    Join of all filters
                 */
                boolean firstJoin = true;
                if (filterByTriggers) {
                    alertIds.addAll(alertIdsFilteredByTriggers);
                    firstJoin = false;
                }
                if (filterByCtime) {
                    if (firstJoin) {
                        alertIds.addAll(alertIdsFilteredByCtime);
                    } else {
                        alertIds.retainAll(alertIdsFilteredByCtime);
                    }
                    firstJoin = false;
                }
                if (filterByStatus) {
                    if (firstJoin) {
                        alertIds.addAll(alertIdsFilteredByStatus);
                    } else {
                        alertIds.retainAll(alertIdsFilteredByStatus);
                    }
                    firstJoin = false;
                }
                if (filterByAlerts) {
                    if (firstJoin) {
                        alertIds.addAll(alertIdsFilteredByAlerts);
                    } else {
                        alertIds.retainAll(alertIdsFilteredByAlerts);
                    }
                }
            }

            if (!filter) {
                /*
                    Get all alerts - Single query
                 */
                ResultSet rsAlerts = session.execute(selectAlertsByTenant.bind(tenantId));
                for (Row row : rsAlerts) {
                    String payload = row.getString("payload");
                    Alert alert = fromJson(payload, Alert.class);
                    alerts.add(alert);
                }
            } else {
                /*
                    We have a filter, so we are going to perform several queries with alertsIds filtering
                 */
                List<ResultSetFuture> futures = alertIds.stream().map(alertId ->
                        session.executeAsync(selectAlertsByTenantAndAlert.bind(tenantId, alertId)))
                        .collect(Collectors.toList());
                List <ResultSet> rsAlerts = Futures.allAsList(futures).get();
                rsAlerts.stream().forEach(r -> {
                    for (Row row : r) {
                        String payload = row.getString("payload");
                        Alert alert = fromJson(payload, Alert.class);
                        alerts.add(alert);
                    }
                });
            }

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return alerts;
    }

    /*
        Trigger ids can be passed explicitly in the criteria or indirectly via tags.
        This helper method extracts the list of triggers id and populates the set passed as argument.
     */
    private void extractTriggersId(Set<String> triggerIds, AlertsCriteria criteria) throws Exception {
        /*
            Explicit trigger ids
         */
        if (isEmpty(criteria.getTriggerIds())) {
            if (!isEmpty(criteria.getTriggerId())) {
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
        if (!isEmpty(criteria.getTags()) || criteria.getTag() != null) {
            Set<Tag> tags = new HashSet<>();
            if (criteria.getTags() != null)  {
                tags.addAll(criteria.getTags());
            }
            Tag tag = criteria.getTag();
            if (tag != null) {
                tags.add(tag);
            }
            triggerIds.addAll(getTriggersIdByTags(tags));
        }

    }

    private boolean filterByTriggers(String tenantId, Set<String> alertsId, AlertsCriteria criteria) throws Exception {
        Set<String> triggerIds = new HashSet<>();
        extractTriggersId(triggerIds, criteria);

        boolean filterByTriggers = false;

        if (triggerIds.size() > 0) {
            filterByTriggers = true;
            List<ResultSetFuture> futures = new ArrayList<>();

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
                boundCtime = selectAlertCTimeStartEnd.bind(tenantId, criteria.getStartTime(),
                        criteria.getEndTime());
            } else if (criteria.getStartTime() != null) {
                boundCtime = selectAlertCTimeStart.bind(tenantId, criteria.getStartTime());
            } else {
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

    public void clear() {
        rulesTask.cancel();

        rules.clear();

        pendingData.clear();
        alerts.clear();
        pendingTimeouts.clear();
        autoResolvedTriggers.clear();
        disabledTriggers.clear();

        rulesTask = new RulesInvoker();
        wakeUpTimer.schedule(rulesTask, delay, period);
    }

    @Override
    public void reload() {
        rules.reset();
        if (rulesTask != null) {
            rulesTask.cancel();
        }

        Collection<Trigger> triggers = null;
        try {
            triggers = definitions.getAllTriggers();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            msgLog.errorDefinitionsService("Triggers", e.getMessage());
        }
        if (triggers != null && !triggers.isEmpty()) {
            triggers.stream().filter(Trigger::isEnabled).forEach(this::reloadTrigger);
        }

        rules.addGlobal("log", log);
        rules.addGlobal("actions", actions);
        rules.addGlobal("alerts", alerts);
        rules.addGlobal("pendingTimeouts", pendingTimeouts);
        rules.addGlobal("autoResolvedTriggers", autoResolvedTriggers);
        rules.addGlobal("disabledTriggers", disabledTriggers);

        rulesTask = new RulesInvoker();
        wakeUpTimer.schedule(rulesTask, delay, period);
    }

    public void reloadTrigger(final String tenantId, final String triggerId) {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        Trigger trigger = null;
        try {
            trigger = definitions.getTrigger(tenantId, triggerId);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            msgLog.errorDefinitionsService("Trigger", e.getMessage());
        }
        if (null == trigger) {
            log.debugf("Trigger not found for triggerId [" + triggerId + "], removing from rulebase if it exists");
            Trigger doomedTrigger = new Trigger(triggerId, "doomed");
            removeTrigger(doomedTrigger);
            return;
        }

        reloadTrigger(trigger);
    }

    private void reloadTrigger(Trigger trigger) {
        if (null == trigger) {
            throw new IllegalArgumentException("Trigger must be not null");
        }

        // Look for the Trigger in the rules engine, if it is there then remove everything about it
        removeTrigger(trigger);

        if (trigger.isEnabled()) {
            try {
                Collection<Condition> conditionSet = definitions.getTriggerConditions(trigger.getTenantId(),
                        trigger.getId(), null);
                Collection<Dampening> dampenings = definitions.getTriggerDampenings(trigger.getTenantId(),
                        trigger.getId(), null);

                rules.addFact(trigger);
                rules.addFacts(conditionSet);
                if (!dampenings.isEmpty()) {
                    rules.addFacts(dampenings);
                }
            } catch (Exception e) {
                log.debugf(e.getMessage(), e);
                msgLog.errorDefinitionsService("Conditions/Dampening", e.getMessage());
            }
        }
    }

    private void removeTrigger(Trigger trigger) {
        if (null != rules.getFact(trigger)) {
            // First remove the related Trigger facts from the engine
            rules.removeFact(trigger);

            // then remove everything else.
            // We may want to do this with rules, because as is, we need to loop through every Fact in
            // the rules engine doing a relatively slow check.
            final String triggerId = trigger.getId();
            rules.removeFacts(t -> {
                if (t instanceof Dampening) {
                    return ((Dampening) t).getTriggerId().equals(triggerId);
                } else if (t instanceof Condition) {
                    return ((Condition) t).getTriggerId().equals(triggerId);
                }
                return false;
            });
        } else {
            log.debugf("Trigger not found. Not removed from rulebase %s", trigger);
        }
    }

    public void sendData(Collection<Data> data) {
        if (data == null) {
            throw new IllegalArgumentException("Data must be not null");
        }
        pendingData.addAll(data);
    }

    public void sendData(Data data) {
        if (data == null) {
            throw new IllegalArgumentException("Data must be not null");
        }
        pendingData.add(data);
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
        List<Alert> alertsToAck = getAlerts(tenantId, criteria);

        for (Alert a : alertsToAck) {
            a.setStatus(Alert.Status.ACKNOWLEDGED);
            a.setAckBy(ackBy);
            a.setAckNotes(ackNotes);
            updateAlertStatus(a);
        }
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
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria);

        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedNotes(resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            updateAlertStatus(a);
        }
    }

    @Override
    public void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

        if (isEmpty(triggerId)) {
            return;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId(triggerId);
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Alert.Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria);

        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedNotes(resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            updateAlertStatus(a);
        }
    }

    private Alert updateAlertStatus(Alert alert) throws Exception {
        if (alert == null || alert.getAlertId() == null || alert.getAlertId().isEmpty()) {
            throw new IllegalArgumentException("AlertId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        try {
            /*
                Not sure if these queries can be wrapped in an async way as they have dependencies with results.
                Async pattern could bring race hazards here.
             */
            ResultSet rsAlertsStatusToDelete = session.execute(selectAlertStatus.bind(alert.getTenantId(),
                    alert.getStatus().name(), alert.getAlertId()));
            for (Row row : rsAlertsStatusToDelete) {
                String alertIdToDelete = row.getString("alertId");
                String statusToDelete = row.getString("status");
                session.execute(deleteAlertStatus.bind(alert.getTenantId(), statusToDelete, alertIdToDelete));
            }
            session.execute(insertAlertStatus.bind(alert.getTenantId(), alert.getAlertId(), alert.getStatus().name()));
            session.execute(updateAlert.bind(toJson(alert), alert.getTenantId(), alert.getAlertId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return alert;
    }

    private String toJson(Object resource) {

        log.debugf(gson.toJson(resource));
        return gson.toJson(resource);

    }

    private <T> T fromJson(String json, Class<T> clazz) {

        return gson.fromJson(json, clazz);
    }

    private boolean isEmpty(Collection<?> c) {
        return null == c || c.isEmpty();
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }


    private class RulesInvoker extends TimerTask {
        @Override
        public void run() {
            int numTimeouts = checkPendingTimeouts();
            if (!pendingData.isEmpty() || numTimeouts > 0) {

                log.debugf("Executing rules engine on [%1d] datums and [%2d] dampening timeouts.", pendingData.size(),
                        numTimeouts);

                try {
                    if (pendingData.isEmpty()) {
                        rules.fireNoData();

                    } else {
                        rules.addData(pendingData);
                        pendingData.clear();
                    }

                    rules.fire();
                    addAlerts(alerts);
                    alerts.clear();
                    handleDisabledTriggers();
                    handleAutoResolvedTriggers();

                } catch (Exception e) {
                    e.printStackTrace();
                    log.debugf("Error on rules processing: " + e);
                    msgLog.errorProcessingRules(e.getMessage());
                } finally {
                    alerts.clear();
                }
            }
        }

        private int checkPendingTimeouts() {
            if (pendingTimeouts.isEmpty()) {
                return 0;
            }

            long now = System.currentTimeMillis();
            Set<Dampening> timeouts = null;
            for (Dampening d : pendingTimeouts) {
                if (now < d.getTrueEvalsStartTime() + d.getEvalTimeSetting()) {
                    continue;
                }

                d.setSatisfied(true);
                try {
                    log.debugf("Dampening Timeout Hit! %s", d.toString());
                    rules.updateFact(d);
                    if (null == timeouts) {
                        timeouts = new HashSet<>();
                    }
                    timeouts.add(d);
                } catch (Exception e) {
                    log.error("Unable to update Dampening Fact on Timeout! " + d.toString(), e);
                }

            }

            if (null == timeouts) {
                return 0;
            }

            pendingTimeouts.removeAll(timeouts);
            return timeouts.size();
        }
    }

    private void handleDisabledTriggers() {
        try {
            for (Trigger t : disabledTriggers) {
                try {
                    t.setEnabled(false);
                    definitions.updateTrigger(t.getTenantId(), t);

                } catch (Exception e) {
                    log.errorf("Failed to persist updated trigger. Could not autoDisable %s", t);
                }
            }
        } finally {
            disabledTriggers.clear();
        }
    }

    private void handleAutoResolvedTriggers() {
        try {
            for (Map.Entry<Trigger, List<Set<ConditionEval>>> entry : autoResolvedTriggers.entrySet()) {
                Trigger t = entry.getKey();
                try {
                    if (t.isAutoResolveAlerts()) {
                        resolveAlertsForTrigger(t.getTenantId(), t.getId(), "AUTO", null, entry.getValue());
                    }
                } catch (Exception e) {
                    log.errorf("Failed to resolve Alerts. Could not AutoResolve alerts for trigger %s", t);
                }
            }
        } finally {
            autoResolvedTriggers.clear();
        }
    }


}
