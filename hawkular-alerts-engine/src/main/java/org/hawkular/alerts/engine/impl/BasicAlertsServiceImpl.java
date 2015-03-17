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

import java.io.Reader;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

/**
 * Basic implementation for {@link org.hawkular.alerts.api.services.AlertsService}.
 * This implementation processes data asynchronously using a buffer queue.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
public class BasicAlertsServiceImpl implements AlertsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(BasicAlertsServiceImpl.class);
    private static final int DELAY;
    private static final int PERIOD;

    private final List<Data> pendingData;
    private final List<Alert> alerts;

    private final Timer wakeUpTimer;
    private TimerTask rulesTask;

    private Gson gson;
    private final String DS_NAME;
    private DataSource ds;

    @EJB
    RulesEngine rules;

    @EJB
    DefinitionsService definitions;

    @EJB
    ActionsService actions;

    /*
        Init properties
     */
    static {
        String sDelay = System.getProperty("org.hawkular.alerts.engine.DELAY");
        String sPeriod = System.getProperty("org.hawkular.alerts.engine.PERIOD");
        int dDelay = 1000;
        int dPeriod = 2000;
        try {
            dDelay = new Integer(sDelay).intValue();
            dPeriod = new Integer(sPeriod).intValue();
        } catch (Exception ignored) {
        }
        DELAY = dDelay;
        PERIOD = dPeriod;
    }

    public BasicAlertsServiceImpl() {
        log.debugf("Creating instance.");
        pendingData = new CopyOnWriteArrayList<Data>();
        alerts = new CopyOnWriteArrayList<Alert>();
        wakeUpTimer = new Timer("BasicAlertsServiceImpl-Timer");

        DS_NAME = System.getProperty("org.hawkular.alerts.engine.datasource", "java:jboss/datasources/HawkularDS");
    }

    @PostConstruct
    public void initServices() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(ConditionEval.class, new GsonAdapter<ConditionEval>());
        gson = gsonBuilder.create();

        if (ds == null) {
            try {
                InitialContext ctx = new InitialContext();
                ds = (DataSource) ctx.lookup(DS_NAME);
            } catch (NamingException e) {
                log.debugf(e.getMessage(), e);
                msgLog.errorCannotConnectWithDatasource(e.getMessage());
            }
        }

        reload();
    }

    @Override
    public void addAlerts(Collection<Alert> alerts) throws Exception {
        if (alerts == null) {
            throw new IllegalArgumentException("Alerts must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        PreparedStatement ps = null;
        StringReader sr = null;

        try {
            c = ds.getConnection();
            String sql = "INSERT INTO HWK_ALERTS_ALERTS VALUES (?,?,?)";
            ps = c.prepareStatement(sql);

            for (Alert a : alerts) {
                ps.setString(1, a.getTriggerId());
                ps.setLong(2, a.getCTime());
                sr = new StringReader(toJson(a));
                ps.setCharacterStream(3, sr);
                log.debugf("SQL: " + sql);
                ps.executeUpdate();
                sr.close();
                sr = null;
            }

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, ps, null);
            if (null != sr) {
                sr.close();
            }
        }
    }

    @Override
    public List<Alert> getAlerts(AlertsCriteria criteria) throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        log.debugf("getAlerts criteria: %s", criteria.toString());

        boolean filter = (null != criteria && criteria.hasCriteria());

        List<Alert> alerts = new ArrayList<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        Reader r = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, ctime, payload FROM HWK_ALERTS_ALERTS a");
            if (filter) {
                int filters = 0;
                sql.append(" WHERE ");
                if (isEmpty(criteria.getTriggerIds())) {
                    if (!isEmpty(criteria.getTriggerId())) {
                        sql.append(filters++ > 0 ? " AND " : " ");
                        sql.append("( triggerId = '");
                        sql.append(criteria.getTriggerId());
                        sql.append("' )");
                    }
                } else {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( triggerId IN (");
                    int entries = 0;
                    for (String triggerId : criteria.getTriggerIds()) {
                        if (isEmpty(triggerId)) {
                            continue;
                        }
                        sql.append(entries++ > 0 ? "," : "");
                        sql.append("'");
                        sql.append(triggerId);
                        sql.append("'");
                    }
                    sql.append(") )");
                }
                if (filter && null != criteria.getStartTime()) {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( ctime >= ");
                    sql.append(criteria.getStartTime());
                    sql.append(" )");
                }
                if (filter && null != criteria.getEndTime()) {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( ctime <= ");
                    sql.append(criteria.getEndTime());
                    sql.append(" )");
                }
                if (isEmpty(criteria.getTags())) {
                    Tag tag = criteria.getTag();
                    if (null != tag) {
                        sql.append(filters++ > 0 ? " AND " : " ");
                        sql.append("( EXIST ( SELECT * FROM HWL_ALERTS_TAGS WHERE");
                        if (!isEmpty(tag.getCategory())) {
                            sql.append(" category = '");
                            sql.append(tag.getCategory());
                            sql.append("' AND");
                        }
                        sql.append(" name = '");
                        sql.append(tag.getName());
                        sql.append("' ) )");
                    }
                } else {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("(");
                    int entries = 0;
                    for (Tag tag : criteria.getTags()) {
                        sql.append(entries++ > 0 ? " OR " : "");
                        sql.append("( EXIST ( SELECT * FROM HWL_ALERTS_TAGS WHERE");
                        if (!isEmpty(tag.getCategory())) {
                            sql.append(" category = '");
                            sql.append(tag.getCategory());
                            sql.append("' AND");
                        }
                        sql.append(" name = '");
                        sql.append(tag.getName());
                        sql.append("' ) )");
                    }
                    sql.append(")");
                }
            }
            sql.append(" ORDER BY a.ctime");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                Clob clob = rs.getClob(3);
                r = clob.getCharacterStream();
                Alert alert = fromJson(r, Alert.class);
                r.close();
                r = null;
                alerts.add(alert);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
            if (null != r) {
                r.close();
            }
        }

        log.debugf("Alerts Found! " + alerts);
        return alerts;
    }

    private boolean isEmpty(Collection<?> c) {
        return null == c || c.isEmpty();
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

    private String toJson(Object resource) {

        log.debugf(gson.toJson(resource));
        return gson.toJson(resource);

    }

    private <T> T fromJson(Reader json, Class<T> clazz) {

        return gson.fromJson(json, clazz);
    }

    private void close(Connection c, Statement s, ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (s != null) {
                s.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (c != null) {
                c.close();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void clear() {
        rulesTask.cancel();

        rules.clear();

        pendingData.clear();
        alerts.clear();

        rulesTask = new RulesInvoker();
        wakeUpTimer.schedule(rulesTask, DELAY, PERIOD);
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
            for (Trigger trigger : triggers) {
                if (trigger.isEnabled()) {
                    reloadTrigger(trigger);
                }
            }
        }

        Collection<Dampening> dampenings = null;
        try {
            dampenings = definitions.getAllDampenings();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            msgLog.errorDefinitionsService("Dampenings", e.getMessage());
        }
        if (dampenings != null && !dampenings.isEmpty()) {
            rules.addFacts(dampenings);
        }

        Collection<Condition> conditions = null;
        try {
            conditions = definitions.getAllConditions();
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            msgLog.errorDefinitionsService("Conditions", e.getMessage());
        }
        if (conditions != null && !conditions.isEmpty()) {
            rules.addFacts(conditions);
        }

        rules.addGlobal("log", log);
        rules.addGlobal("actions", actions);
        rules.addGlobal("alerts", alerts);

        rulesTask = new RulesInvoker();
        wakeUpTimer.schedule(rulesTask, DELAY, PERIOD);
    }

    @Override
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
        }
    }

    @Override
    public void sendData(Collection<Data> data) {
        if (data == null) {
            throw new IllegalArgumentException("Data must be not null");
        }
        pendingData.addAll(data);
    }

    @Override
    public void sendData(Data data) {
        if (data == null) {
            throw new IllegalArgumentException("Data must be not null");
        }
        pendingData.add(data);
    }

    private class RulesInvoker extends TimerTask {
        @Override
        public void run() {
            if (!pendingData.isEmpty()) {

                log.debugf("Pending data [%1$d] found. Executing rules engine.", pendingData.size());

                rules.addData(pendingData);
                pendingData.clear();

                try {
                    rules.fire();
                    addAlerts(alerts);
                    alerts.clear();

                } catch (Exception e) {
                    e.printStackTrace();
                    log.debugf("Error on rules processing: " + e);
                    msgLog.errorProcessingRules(e.getMessage());
                } finally {
                    alerts.clear();
                }
            }
        }
    }
}
