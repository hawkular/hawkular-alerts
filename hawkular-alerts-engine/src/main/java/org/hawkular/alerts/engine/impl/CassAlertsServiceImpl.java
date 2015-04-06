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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
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
        pendingData = new CopyOnWriteArrayList<Data>();
        alerts = new CopyOnWriteArrayList<Alert>();
        pendingTimeouts = new CopyOnWriteArraySet<Dampening>();
        autoResolvedTriggers = new HashMap<Trigger, List<Set<ConditionEval>>>();
        disabledTriggers = new CopyOnWriteArraySet<Trigger>();

        wakeUpTimer = new Timer("CassAlertsServiceImpl-Timer");

        delay = new Integer(AlertProperties.getProperty(ENGINE_DELAY, "1000"));
        period = new Integer(AlertProperties.getProperty(ENGINE_PERIOD, "2000"));
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
            msgLog.errorCannotInitializeAlertsService(t.getMessage());
        }
    }

    private void initPreparedStatements() throws Exception {
        if (insertAlert == null) {
            insertAlert = session.prepare("INSERT INTO " + keyspace + ".alerts " +
                    "(alertId, payload) VALUES (?, ?) ");
        }
        if (insertAlertTrigger == null) {
            insertAlertTrigger = session.prepare("INSERT INTO " + keyspace + ".alerts_triggers " +
                    "(alertId, triggerId) VALUES (?, ?) ");
        }
        if (insertAlertCtime == null) {
            insertAlertCtime = session.prepare("INSERT INTO " + keyspace + ".alerts_ctimes " +
                    "(alertId, ctime) VALUES (?, ?) ");

        }
        if (insertAlertStatus == null) {
            insertAlertStatus = session.prepare("INSERT INTO " + keyspace + ".alerts_statuses " +
                    "(alertId, status) VALUES (?, ?) ");
        }
        if (updateAlert == null) {
            updateAlert = session.prepare("UPDATE " + keyspace + ".alerts " +
                    "SET payload = ? WHERE alertId = ? ");
        }
        if (selectAlertStatus == null) {
            selectAlertStatus = session.prepare("SELECT alertId, status FROM " + keyspace + ".alerts_statuses " +
                    "WHERE alertId = ? ALLOW FILTERING ");
        }
        if (deleteAlertStatus == null) {
            deleteAlertStatus = session.prepare("DELETE FROM " + keyspace + ".alerts_statuses " +
                    "WHERE alertId = ? AND status = ?");
        }
    }

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
            for (Alert a : alerts) {
                session.execute(insertAlert.bind(a.getAlertId(), toJson(a)));
                session.execute(insertAlertTrigger.bind(a.getAlertId(), a.getTriggerId()));
                session.execute(insertAlertCtime.bind(a.getAlertId(), a.getCtime()));
                session.execute(insertAlertStatus.bind(a.getAlertId(), a.getStatus().name()));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    public List<Alert> getAlerts(AlertsCriteria criteria) throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        boolean filter = (null != criteria && criteria.hasCriteria());

        if (filter) {
            log.debugf("getAlerts criteria: %s", criteria.toString());
        }

        List<Alert> alerts = new ArrayList<>();
        Set<String> alertIds = new HashSet<>();

        StringBuilder sAlerts = new StringBuilder("SELECT payload FROM ").append(keyspace).append(".alerts ");

        if (filter) {
            /*
                Triggers id can be passed explicitly in the criteria or indirectly via tags.
             */
            Set<String> triggerIds = new HashSet<>();
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

            /*
                Get alertIds filtered by triggerIds
             */
            Set<String> alertIdsFilteredByTriggers = new HashSet<>();
            boolean filterByTriggers = false;
            if (triggerIds.size() > 0) {
                filterByTriggers = true;
                StringBuilder sAlertsTriggers = new StringBuilder("SELECT alertId FROM ").append(keyspace)
                        .append(".alerts_triggers WHERE ");
                if (triggerIds.size() == 1) {
                    sAlertsTriggers.append("( triggerId = '");
                    sAlertsTriggers.append(triggerIds.iterator().next());
                    sAlertsTriggers.append("' )");
                } else {
                    sAlertsTriggers.append("( triggerId IN (");
                    int entries = 0;
                    for (String triggerId : triggerIds) {
                        if (isEmpty(triggerId)) {
                            continue;
                        }
                        sAlertsTriggers.append(entries++ > 0 ? "," : "");
                        sAlertsTriggers.append("'");
                        sAlertsTriggers.append(triggerId);
                        sAlertsTriggers.append("'");
                    }
                    sAlertsTriggers.append(") )");
                }
                try {
                    ResultSet rsAlertIdsByTriggerIds = session.execute(sAlertsTriggers.toString());
                    if (rsAlertIdsByTriggerIds.isExhausted()) {
                        alertIdsFilteredByTriggers.add("empty-alert");
                    } else {
                        Iterator<Row> itAlertIdsByTriggerIds = rsAlertIdsByTriggerIds.iterator();
                        while (itAlertIdsByTriggerIds.hasNext()) {
                            Row row = itAlertIdsByTriggerIds.next();
                            String alertId = row.getString("alertId");
                            alertIdsFilteredByTriggers.add(alertId);
                        }
                    }
                } catch (Exception e) {
                    msgLog.errorDatabaseException(e.getMessage());
                    throw e;
                }
            }

            /*
                Add ctime clause
             */
            Set<String> alertIdsFilteredByCtime = new HashSet<>();
            boolean filterByCtime = false;
            if (criteria.getStartTime() != null || criteria.getEndTime() != null) {
                filterByCtime = true;
                StringBuilder sAlertsCtimes = new StringBuilder("SELECT alertId FROM ").append(keyspace)
                        .append(".alerts_ctimes WHERE ");
                boolean andNeeded = false;
                if (criteria.getStartTime() != null) {
                    sAlertsCtimes.append("( ctime >= ");
                    sAlertsCtimes.append(criteria.getStartTime());
                    sAlertsCtimes.append(" )");
                    andNeeded = true;
                }
                if (criteria.getEndTime() != null) {
                    if (andNeeded) {
                        sAlertsCtimes.append(" AND ");
                    }
                    sAlertsCtimes.append("( ctime <= ");
                    sAlertsCtimes.append(criteria.getEndTime());
                    sAlertsCtimes.append(" )");
                }
                sAlertsCtimes.append(" ALLOW FILTERING ");
                try {
                    ResultSet rsAlertsCtimes = session.execute(sAlertsCtimes.toString());
                    if (rsAlertsCtimes.isExhausted()) {
                        alertIdsFilteredByCtime.add("empty-alert");
                    } else {
                        Iterator<Row> itAlertsCtimes = rsAlertsCtimes.iterator();
                        while (itAlertsCtimes.hasNext()) {
                            Row row = itAlertsCtimes.next();
                            String alertId = row.getString("alertId");
                            alertIdsFilteredByCtime.add(alertId);
                        }
                    }
                } catch (Exception e) {
                    msgLog.errorDatabaseException(e.getMessage());
                    throw e;
                }
            }

            /*
                Add statutes clause
             */
            Set<String> alertIdsFilteredByStatus = new HashSet<>();
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
                StringBuilder sAlertsStatuses = new StringBuilder("SELECT alertId FROM ").append(keyspace)
                        .append(".alerts_statuses WHERE ");
                if (statuses.size() == 1) {
                    sAlertsStatuses.append("( status = '");
                    sAlertsStatuses.append(statuses.iterator().next().name());
                    sAlertsStatuses.append("' ) ");
                } else {
                    sAlertsStatuses.append("( status IN (");
                    int entries = 0;
                    for (Alert.Status status : statuses) {
                        sAlertsStatuses.append(entries++ > 0 ? "," : "");
                        sAlertsStatuses.append("'");
                        sAlertsStatuses.append(status.name());
                        sAlertsStatuses.append("'");
                    }
                    sAlertsStatuses.append(") ) ");
                }
                try {
                    ResultSet rsAlertsStatuses = session.execute(sAlertsStatuses.toString());
                    if (rsAlertsStatuses.isExhausted()) {
                        alertIdsFilteredByStatus.add("empty-alert");
                    } else {
                        Iterator<Row> itAlertsStatuses = rsAlertsStatuses.iterator();
                        while (itAlertsStatuses.hasNext()) {
                            Row row = itAlertsStatuses.next();
                            String alertId = row.getString("alertId");
                            alertIdsFilteredByStatus.add(alertId);
                        }
                    }
                } catch (Exception e) {
                    msgLog.errorDatabaseException(e.getMessage());
                    throw e;
                }
            }

            Set<String> alertIdsFilteredByAlerts = new HashSet<>();
            boolean filterByAlerts = false;
            if (isEmpty(criteria.getAlertIds())) {
                if (!isEmpty(criteria.getAlertId())) {
                    filterByAlerts = true;
                    alertIdsFilteredByAlerts.add(criteria.getAlertId());
                }
            } else {
                filterByAlerts = true;
                alertIdsFilteredByAlerts.addAll(criteria.getAlertIds());
            }

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

            if (alertIds.size() > 0) {
                sAlerts.append("WHERE ");
                if (alertIds.size() == 1) {
                    sAlerts.append("( alertId = '");
                    sAlerts.append(alertIds.iterator().next());
                    sAlerts.append("' ) ");
                } else {
                    sAlerts.append("( alertId IN (");
                    int entries = 0;
                    for (String alertId : alertIds) {
                        if (isEmpty(alertId)) {
                            continue;
                        }
                        sAlerts.append(entries++ > 0 ? "," : "");
                        sAlerts.append("'");
                        sAlerts.append(alertId);
                        sAlerts.append("'");
                    }
                    sAlerts.append(") ) ");
                }
            }

            sAlerts.append("ALLOW FILTERING ");
        }

        log.debugf("getAlerts() - CQL: " + sAlerts.toString());

        /*
            If filtering gives the empty set we don't need to make an additional query
         */
        if (!filter || (filter && alertIds.size() > 0)) {
            try {
                ResultSet rsAlerts = session.execute(sAlerts.toString());
                Iterator<Row> itAlerts = rsAlerts.iterator();
                while (itAlerts.hasNext()) {
                    Row row = itAlerts.next();
                    String payload = row.getString("payload");
                    Alert alert = fromJson(payload, Alert.class);
                    alerts.add(alert);
                }
            } catch (Exception e) {
                msgLog.errorDatabaseException(e.getMessage());
                throw e;
            }
        }

        log.debug(alerts);

        return alerts;
    }

    private Collection<String> getTriggersIdByTags(Collection<Tag> tags) {
        Set<String> triggerIds = new HashSet<>();
        for (Tag tag : tags) {
            StringBuilder sTag = new StringBuilder("SELECT triggers FROM ").append(keyspace).append(".tags_triggers ");
            if (tag.getCategory() != null || tag.getName() != null) {
                sTag.append("WHERE ");
                if (!isEmpty(tag.getCategory())) {
                    sTag.append(" category = '").append(tag.getCategory()).append("' ");
                }
                if (!isEmpty(tag.getName())) {
                    if (!isEmpty(tag.getCategory())) {
                        sTag.append("AND ");
                    }
                    sTag.append(" name = '").append(tag.getName()).append("' ");
                    if (isEmpty(tag.getCategory())) {
                        sTag.append("ALLOW FILTERING ");
                    }
                }
                ResultSet rsTriggers = session.execute(sTag.toString());
                Iterator<Row> itTriggers = rsTriggers.iterator();
                while (itTriggers.hasNext()) {
                    Row row = itTriggers.next();
                    Set<String> triggers = row.getSet("triggers", String.class);
                    triggerIds.addAll(triggers);
                }
            }
        }
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
            for (Trigger trigger : triggers) {
                if (trigger.isEnabled()) {
                    reloadTrigger(trigger);
                }
            }
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

    public void reloadTrigger(final String triggerId) {
        if (null == triggerId) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        Trigger trigger = null;
        try {
            trigger = definitions.getTrigger(triggerId);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            msgLog.errorDefinitionsService("Trigger", e.getMessage());
        }
        if (null == trigger) {
            log.debugf("Trigger not found for triggerId [" + triggerId + "], removing from rulebase if it exists");
            Trigger doomedTrigger = new Trigger(triggerId, "doomed");
            removeTrigger(trigger);
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
                Collection<Condition> conditionSet = definitions.getTriggerConditions(trigger.getId(), null);
                Collection<Dampening> dampenings = definitions.getTriggerDampenings(trigger.getId(), null);

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
            // TODO: We may want to do this with rules, because as is, we need to loop through every Fact in
            // the rules engine doing a relatively slow check.
            final String triggerId = trigger.getId();
            rules.removeFacts(new Predicate<Object>() {
                @Override
                public boolean test(Object t) {
                    if (t instanceof Dampening) {
                        return ((Dampening) t).getTriggerId().equals(triggerId);
                    } else if (t instanceof Condition) {
                        return ((Condition) t).getTriggerId().equals(triggerId);
                    }
                    return false;
                }
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
    public void ackAlerts(Collection<String> alertIds, String ackBy, String ackNotes) throws Exception {
        if (isEmpty(alertIds)) {
            return;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToAck = getAlerts(criteria);

        for (Alert a : alertsToAck) {
            a.setStatus(Alert.Status.ACKNOWLEDGED);
            a.setAckBy(ackBy);
            a.setAckNotes(ackNotes);
            updateAlertStatus(a);
        }
    }

    @Override
    public void resolveAlerts(Collection<String> alertIds, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

        if (isEmpty(alertIds)) {
            return;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setAlertIds(alertIds);
        List<Alert> alertsToResolve = getAlerts(criteria);

        for (Alert a : alertsToResolve) {
            a.setStatus(Alert.Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedNotes(resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            updateAlertStatus(a);
        }
    }

    @Override
    public void resolveAlertsForTrigger(String triggerId, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

        if (isEmpty(triggerId)) {
            return;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId(triggerId);
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Alert.Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(criteria);

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

            ResultSet rsAlertsStatusToDelete = session.execute(selectAlertStatus.bind(alert.getAlertId()));
            Iterator<Row> itAlertsStatusToDelete = rsAlertsStatusToDelete.iterator();
            while (itAlertsStatusToDelete.hasNext()) {
                Row row = itAlertsStatusToDelete.next();
                String alertIdToDelete = row.getString("alertId");
                String statusToDelete = row.getString("status");
                session.execute(deleteAlertStatus.bind(alertIdToDelete, statusToDelete));
            }
            session.execute(insertAlertStatus.bind(alert.getAlertId(), alert.getStatus().name()));
            session.execute(updateAlert.bind(toJson(alert), alert.getAlertId()));
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
                    definitions.updateTrigger(t);

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
                        resolveAlertsForTrigger(t.getId(), "AUTO", null, entry.getValue());
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
