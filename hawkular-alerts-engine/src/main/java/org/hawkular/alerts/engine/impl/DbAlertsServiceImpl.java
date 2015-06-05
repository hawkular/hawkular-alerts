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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.Alert.Status;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.jboss.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Basic implementation for {@link org.hawkular.alerts.api.services.AlertsService}.
 * This implementation processes data asynchronously using a buffer queue.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class DbAlertsServiceImpl implements AlertsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(DbAlertsServiceImpl.class);

    private Gson gson;
    private final String DS_NAME;
    private DataSource ds;

    @EJB
    AlertsEngine alertsEngine;

    public DbAlertsServiceImpl() {
        log.debugf("Creating instance.");

        DS_NAME = System.getProperty("org.hawkular.alerts.engine.datasource", "java:jboss/datasources/HawkularDS");
    }

    public DataSource getDs() {
        return ds;
    }

    public void setDs(DataSource ds) {
        this.ds = ds;
    }

    public AlertsEngine getAlertsEngine() {
        return alertsEngine;
    }

    public void setAlertsEngine(AlertsEngine alertsEngine) {
        this.alertsEngine = alertsEngine;
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
            String sql = "INSERT INTO HWK_ALERTS_ALERTS VALUES (?,?,?,?,?,?,?)";
            ps = c.prepareStatement(sql);

            for (Alert a : alerts) {
                ps.setString(1, a.getTenantId());
                ps.setString(2, a.getAlertId());
                ps.setString(3, a.getTriggerId());
                ps.setLong(4, a.getCtime());
                ps.setString(5, a.getStatus().name());
                ps.setString(6, a.getSeverity().name());
                sr = new StringReader(toJson(a));
                ps.setCharacterStream(7, sr);
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
    public List<Alert> getAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }
        if (criteria != null) {
            log.debugf("getAlerts criteria: %s", criteria.toString());
        }

        boolean filter = (null != criteria && criteria.hasCriteria());

        List<Alert> alerts = new ArrayList<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        Reader r = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT a.triggerId, a.ctime, a.payload FROM HWK_ALERTS_ALERTS a");
            if (filter) {
                int filters = 0;
                sql.append(" WHERE ");
                if (isEmpty(criteria.getAlertIds())) {
                    if (!isEmpty(criteria.getAlertId())) {
                        sql.append(filters++ > 0 ? " AND " : " ");
                        sql.append("( a.alertId = '");
                        sql.append(criteria.getAlertId());
                        sql.append("' )");
                    }
                } else {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( a.alertId IN (");
                    int entries = 0;
                    for (String alertId : criteria.getAlertIds()) {
                        if (isEmpty(alertId)) {
                            continue;
                        }
                        sql.append(entries++ > 0 ? "," : "");
                        sql.append("'");
                        sql.append(alertId);
                        sql.append("'");
                    }
                    sql.append(") )");
                }
                if (isEmpty(criteria.getTriggerIds())) {
                    if (!isEmpty(criteria.getTriggerId())) {
                        sql.append(filters++ > 0 ? " AND " : " ");
                        sql.append("( a.triggerId = '");
                        sql.append(criteria.getTriggerId());
                        sql.append("' )");
                    }
                } else {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( a.triggerId IN (");
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
                if (isEmpty(criteria.getStatusSet())) {
                    if (null != criteria.getStatus()) {
                        sql.append(filters++ > 0 ? " AND " : " ");
                        sql.append("( a.status = '");
                        sql.append(criteria.getStatus().name());
                        sql.append("' )");
                    }
                } else {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( a.status IN (");
                    int entries = 0;
                    for (Alert.Status status : criteria.getStatusSet()) {
                        sql.append(entries++ > 0 ? "," : "");
                        sql.append("'");
                        sql.append(status.name());
                        sql.append("'");
                    }
                    sql.append(") )");
                }
                if (isEmpty(criteria.getSeverities())) {
                    if (null != criteria.getSeverity()) {
                        sql.append(filters++ > 0 ? " AND " : " ");
                        sql.append("( a.severity = '");
                        sql.append(criteria.getSeverity().name());
                        sql.append("' )");
                    }
                } else {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( a.severity IN (");
                    int entries = 0;
                    for (Severity severity : criteria.getSeverities()) {
                        sql.append(entries++ > 0 ? "," : "");
                        sql.append("'");
                        sql.append(severity.name());
                        sql.append("'");
                    }
                    sql.append(") )");
                }
                if (null != criteria.getStartTime()) {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( a.ctime >= ");
                    sql.append(criteria.getStartTime());
                    sql.append(" )");
                }
                if (null != criteria.getEndTime()) {
                    sql.append(filters++ > 0 ? " AND " : " ");
                    sql.append("( a.ctime <= ");
                    sql.append(criteria.getEndTime());
                    sql.append(" )");
                }
                if (isEmpty(criteria.getTags())) {
                    Tag tag = criteria.getTag();
                    if (null != tag) {
                        sql.append(filters > 0 ? " AND " : " ");
                        sql.append("( EXISTS ( SELECT * FROM HWK_ALERTS_TAGS t WHERE t.triggerId = a.triggerId ");
                        if (!isEmpty(tag.getCategory()) && !isEmpty(tag.getName())) {
                            sql.append(" AND t.category = '");
                            sql.append(tag.getCategory());
                            sql.append("' AND t.name = '");
                            sql.append(tag.getName());
                            sql.append("' ");

                        } else if (!isEmpty(tag.getCategory())) {
                            sql.append(" AND t.category = '");
                            sql.append(tag.getCategory());
                            sql.append("' ");
                        } else if (!isEmpty(tag.getName())) {
                            sql.append(" AND t.name = '");
                            sql.append(tag.getName());
                            sql.append("' ");
                        }
                        sql.append(" ) )");
                    }
                } else {
                    sql.append(filters > 0 ? " AND " : " ");
                    sql.append("(");
                    int entries = 0;
                    for (Tag tag : criteria.getTags()) {
                        sql.append(entries++ > 0 ? " OR " : "");
                        sql.append("( EXISTS ( SELECT * FROM HWK_ALERTS_TAGS t WHERE t.triggerId = a.triggerId ");
                        if (!isEmpty(tag.getCategory()) && !isEmpty(tag.getName())) {
                            sql.append(" AND t.category = '");
                            sql.append(tag.getCategory());
                            sql.append("' AND t.name = '");
                            sql.append(tag.getName());
                            sql.append("' ");

                        } else if (!isEmpty(tag.getCategory())) {
                            sql.append(" AND t.category = '");
                            sql.append(tag.getCategory());
                            sql.append("' ");
                        } else if (!isEmpty(tag.getName())) {
                            sql.append(" AND t.name = '");
                            sql.append(tag.getName());
                            sql.append("' ");
                        }
                        sql.append(" ) )");
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

        log.debugf(alerts.isEmpty() ? "No Alerts Found" : "Alerts Found! " + alerts);
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
            a.setStatus(Status.ACKNOWLEDGED);
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
            a.setStatus(Status.RESOLVED);
            a.setResolvedBy(resolvedBy);
            a.setResolvedNotes(resolvedNotes);
            a.setResolvedEvalSets(resolvedEvalSets);
            updateAlertStatus(a);
        }
    }

    @Override
    public void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes,
            List<Set<ConditionEval>> resolvedEvalSets) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            return;
        }

        AlertsCriteria criteria = new AlertsCriteria();
        criteria.setTriggerId(triggerId);
        criteria.setStatusSet(EnumSet.complementOf(EnumSet.of(Alert.Status.RESOLVED)));
        List<Alert> alertsToResolve = getAlerts(tenantId, criteria);

        for (Alert a : alertsToResolve) {
            a.setStatus(Status.RESOLVED);
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
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        PreparedStatement ps = null;
        StringReader sr = null;
        try {
            c = ds.getConnection();

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_ALERTS SET ")
                    .append("status = '").append(alert.getStatus().name()).append("', ")
                    .append("payload = ? ")
                    .append("WHERE alertId = '").append(alert.getAlertId()).append("' ");
            log.debugf("SQL: " + sql);
            ps = c.prepareStatement(sql.toString());
            sr = new StringReader(toJson(alert));
            ps.setCharacterStream(1, sr);
            log.debugf("SQL: " + sql);
            ps.executeUpdate();
            sr.close();
            sr = null;

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, ps, null);
            if (null != sr) {
                sr.close();
            }
        }

        return alert;
    }

    @Override
    public void sendData(Data data) throws Exception {
        alertsEngine.sendData(data);
    }

    @Override
    public void sendData(Collection<Data> data) throws Exception {
        alertsEngine.sendData(data);
    }

}
