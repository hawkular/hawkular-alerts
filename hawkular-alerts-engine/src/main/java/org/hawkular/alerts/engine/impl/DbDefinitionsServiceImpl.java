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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.log.MsgLogger;

import org.jboss.logging.Logger;

/**
 * A database implementation of {@link org.hawkular.alerts.api.services.DefinitionsService}.
 *
 * @author Lucas Ponce
 */
@Singleton
public class DbDefinitionsServiceImpl implements DefinitionsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(DbDefinitionsServiceImpl.class);

    private static final String JBOSS_DATA_DIR = "jboss.server.data.dir";
    private static final String INIT_FOLDER = "hawkular-alerts";

    private Gson gson;
    private final String DS_NAME;
    private DataSource ds;
    private AlertsService alertsService;
    private boolean initialized = false;

    public DbDefinitionsServiceImpl() {
        DS_NAME = System.getProperty("org.hawkular.alerts.engine.datasource", "java:jboss/datasources/HawkularDS");
    }

    public DbDefinitionsServiceImpl(AlertsService alertsService, DataSource ds) {
        this();
        this.ds = ds;
        this.alertsService = alertsService;
    }

    @PostConstruct
    public void init() {
        try {
            gson = new GsonBuilder().create();
            if (ds == null) {
                try {
                    InitialContext ctx = new InitialContext();
                    ds = (DataSource) ctx.lookup(DS_NAME);
                } catch (NamingException e) {
                    log.debugf(e.getMessage(), e);
                    msgLog.errorCannotConnectWithDatasource(e.getMessage());
                }
            }
            if (alertsService == null) {
                try {
                    InitialContext ctx = new InitialContext();
                    alertsService = (AlertsService) ctx
                            .lookup("java:app/hawkular-alerts-engine/BasicAlertsServiceImpl");
                } catch (NamingException e) {
                    log.debugf(e.getMessage(), e);
                    msgLog.errorCannotConnectWithDatasource(e.getMessage());
                }
            }

            initDatabase();

            String data = System.getProperty(JBOSS_DATA_DIR);
            if (data == null) {
                msgLog.errorFolderNotFound(data);
                return;
            }
            String folder = data + "/" + INIT_FOLDER;
            initFiles(folder);
            initialized = true;
        } catch (Throwable t) {
            System.out.println(" !!!!!!!!!!!! " + t);
            t.printStackTrace();
        }
    }

    private void initDatabase() {
        if (ds == null) {
            log.debugf("DataSource null. Cannot init database");
            return;
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_TRIGGERS " +
                    "( triggerId VARCHAR(250) PRIMARY KEY, " +
                    "  payload VARCHAR(1024) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_CONDITIONS " +
                    "( conditionId VARCHAR(250) PRIMARY KEY," +
                    "  triggerId VARCHAR(250) NOT NULL," +
                    "  triggerMode VARCHAR(20) NOT NULL," +
                    "  className VARCHAR(250) NOT NULL," +
                    "  payload VARCHAR(1024) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_DAMPENINGS " +
                    "( dampeningId VARCHAR(250) PRIMARY KEY," +
                    "  triggerId VARCHAR(250) NOT NULL," +
                    "  triggerMode VARCHAR(20) NOT NULL," +
                    "  payload VARCHAR(1024) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_NOTIFIER_TYPES " +
                    "( notifierType VARCHAR(250) PRIMARY KEY," +
                    "  payload VARCHAR(1024) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_NOTIFIERS " +
                    "( notifierId VARCHAR(250) NOT NULL," +
                    "  notifierType VARCHAR(250)," +
                    "  payload VARCHAR(1024)," +
                    "  PRIMARY KEY(notifierId, notifierType))");

            s.close();

        } catch (SQLException e) {
            log.debugf(e.getMessage(), e);
            msgLog.errorDatabaseException(e.getMessage());
        } finally {
            close(c, s);
        }
    }

    /*
        Helper method to initialize data from files.
        It doesn't validate all possible incorrect situations.
        It expects a good file.
        Used only for demo/poc purposes.
     */
    private void initFiles(String folder) {

        if (folder == null) {
            msgLog.errorFolderMustBeNotNull();
            return;
        }
        if (ds == null) {
            msgLog.errorDatabaseException("DataSource is null. Initialization can not work.");
            return;
        }

        File initFolder = new File(folder);
        try {
            int nTriggers = getNumTable("HWK_ALERTS_TRIGGERS");
            if (nTriggers == 0) {
                initTriggers(initFolder);
            }

            int nConditions = getNumTable("HWK_ALERTS_CONDITIONS");
            if (nConditions == 0) {
                initConditions(initFolder);
            }

            int nDampenings = getNumTable("HWK_ALERTS_DAMPENINGS");
            if (nDampenings == 0) {
                initDampening(initFolder);
            }

            int nNotifiers = getNumTable("HWK_ALERTS_NOTIFIERS");
            if (nNotifiers == 0) {
                initNotifiers(initFolder);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            msgLog.errorDatabaseException("Error initializing files. Msg: " + e);
        }
    }

    private void initTriggers(File initFolder) throws Exception {
        /*
            Triggers
         */
        File triggers = new File(initFolder, "triggers.data");
        if (triggers.exists() && triggers.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(triggers.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                log.debugf(e.toString(), e);
                msgLog.errorReadingFile("triggers.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 8) {
                        String triggerId = fields[0];
                        boolean enabled = new Boolean(fields[1]).booleanValue();
                        boolean safetyEnabled = new Boolean(fields[2]).booleanValue();
                        String name = fields[3];
                        String description = fields[4];
                        TriggerTemplate.Match firingMatch = TriggerTemplate.Match.valueOf(fields[5]);
                        TriggerTemplate.Match safetyMatch = TriggerTemplate.Match.valueOf(fields[6]);
                        String[] notifiers = fields[7].split("\\|");

                        Trigger trigger = new Trigger(triggerId, name);
                        trigger.setEnabled(enabled);
                        trigger.setSafetyEnabled(safetyEnabled);
                        trigger.setDescription(description);
                        trigger.setFiringMatch(firingMatch);
                        trigger.setSafetyMatch(safetyMatch);
                        for (String notifier : notifiers) {
                            trigger.addNotifier(notifier);
                        }

                        addTrigger(trigger);
                        log.debugf("Init file - Inserting [%s]", trigger);
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("triggers.data");
        }
    }

    private void initConditions(File initFolder) throws Exception {
        /*
            Conditions
         */
        File conditions = new File(initFolder, "conditions.data");
        if (conditions.exists() && conditions.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(conditions.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                msgLog.errorReadingFile("conditions.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length > 4) {
                        String triggerId = fields[0];
                        Mode triggerMode = Mode.valueOf(fields[1]);
                        int conditionSetSize = new Integer(fields[2]).intValue();
                        int conditionSetIndex = new Integer(fields[3]).intValue();
                        String type = fields[4];
                        if (type != null && !type.isEmpty() && type.equals("threshold") && fields.length == 8) {
                            String dataId = fields[5];
                            String operator = fields[6];
                            Double threshold = new Double(fields[7]).doubleValue();

                            ThresholdCondition newCondition = new ThresholdCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(ThresholdCondition.Operator.valueOf(operator));
                            newCondition.setThreshold(threshold);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("range") && fields.length == 11) {
                            String dataId = fields[5];
                            String operatorLow = fields[6];
                            String operatorHigh = fields[7];
                            Double thresholdLow = new Double(fields[8]).doubleValue();
                            Double thresholdHigh = new Double(fields[9]).doubleValue();
                            boolean inRange = new Boolean(fields[10]).booleanValue();

                            ThresholdRangeCondition newCondition = new ThresholdRangeCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperatorLow(ThresholdRangeCondition.Operator.valueOf(operatorLow));
                            newCondition.setOperatorHigh(ThresholdRangeCondition.Operator.valueOf(operatorHigh));
                            newCondition.setThresholdLow(thresholdLow);
                            newCondition.setThresholdHigh(thresholdHigh);
                            newCondition.setInRange(inRange);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("compare") && fields.length == 9) {
                            String data1Id = fields[5];
                            String operator = fields[6];
                            Double data2Multiplier = new Double(fields[7]).doubleValue();
                            String data2Id = fields[8];

                            CompareCondition newCondition = new CompareCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setData1Id(data1Id);
                            newCondition.setOperator(CompareCondition.Operator.valueOf(operator));
                            newCondition.setData2Multiplier(data2Multiplier);
                            newCondition.setData2Id(data2Id);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("string") && fields.length == 9) {
                            String dataId = fields[5];
                            String operator = fields[6];
                            String pattern = fields[7];
                            boolean ignoreCase = new Boolean(fields[8]).booleanValue();

                            StringCondition newCondition = new StringCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(StringCondition.Operator.valueOf(operator));
                            newCondition.setPattern(pattern);
                            newCondition.setIgnoreCase(ignoreCase);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("availability") && fields.length == 7) {
                            String dataId = fields[5];
                            String operator = fields[6];

                            AvailabilityCondition newCondition = new AvailabilityCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(AvailabilityCondition.Operator.valueOf(operator));

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("conditions.data");
        }
    }

    private void initCondition(Condition condition) throws Exception {
        Collection<Condition> conditions = getTriggerConditions(condition.getTriggerId(), condition.getTriggerMode());
        conditions.add(condition);
        setConditions(condition.getTriggerId(), condition.getTriggerMode(), conditions);
    }

    private void initDampening(File initFolder) throws Exception {
        /*
            Dampening
         */
        File dampening = new File(initFolder, "dampening.data");
        if (dampening.exists() && dampening.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(dampening.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                msgLog.errorReadingFile("dampening.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 6) {
                        String triggerId = fields[0];
                        Mode triggerMode = Mode.valueOf(fields[1]);
                        String type = fields[2];
                        int evalTrueSetting = new Integer(fields[3]);
                        int evalTotalSetting = new Integer(fields[4]);
                        int evalTimeSetting = new Integer(fields[5]);

                        Dampening newDampening = new Dampening(triggerId, triggerMode, Dampening.Type.valueOf(type),
                                evalTrueSetting, evalTotalSetting, evalTimeSetting);

                        addDampening(newDampening);
                        log.debugf("Init file - Inserting [%s]", newDampening);
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("dampening.data");
        }
    }

    private void initNotifiers(File initFolder) throws Exception {
        /*
            Notifiers
         */
        File notifiers = new File(initFolder, "notifiers.data");
        if (notifiers.exists() && notifiers.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(notifiers.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                log.error(e.toString(), e);
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 3) {
                        String notifierId = fields[0];
                        String notifierType = fields[1];
                        String description = fields[2];
                        Map<String, String> newNotifier = new HashMap<String, String>();
                        newNotifier.put("NotifierID", notifierId);
                        newNotifier.put("NotifierType", notifierType);
                        newNotifier.put("description", description);

                        addNotifier(notifierId, newNotifier);
                        log.debugf("Init file - Inserting [%s]", newNotifier);
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("notifiers.data");
        }
    }

    @Override
    public Collection<Condition> getAllConditions() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }
        List<Condition> conditions = new ArrayList<Condition>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT conditionId, className, payload FROM HWK_ALERTS_CONDITIONS ")
                    .append("ORDER BY conditionId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                String className = rs.getString(2);
                Condition condition;
                if (className.equals("AvailabilityCondition")) {
                    condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                } else if (className.equals("CompareCondition")) {
                    condition = fromJson(rs.getString(3), CompareCondition.class);
                } else if (className.equals("StringCondition")) {
                    condition = fromJson(rs.getString(3), StringCondition.class);
                } else if (className.equals("ThresholdCondition")) {
                    condition = fromJson(rs.getString(3), ThresholdCondition.class);
                } else if (className.equals("ThresholdRangeCondition")) {
                    condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                } else {
                    log.debugf("Condition type: " + className + " not found");
                    condition = null;
                }
                if (condition != null) {
                    conditions.add(condition);
                }
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return conditions;
    }

    @Override
    public Condition getCondition(String conditionId) throws Exception {
        if (conditionId == null || conditionId.isEmpty()) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Condition condition = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT conditionId, className, payload FROM HWK_ALERTS_CONDITIONS "
                    + "WHERE ")
                    .append("conditionId = '").append(conditionId).append("' ")
                    .append("ORDER BY triggerId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                String className = rs.getString(2);
                if (className.equals("AvailabilityCondition")) {
                    condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                } else if (className.equals("CompareCondition")) {
                    condition = fromJson(rs.getString(3), CompareCondition.class);
                } else if (className.equals("StringCondition")) {
                    condition = fromJson(rs.getString(3), StringCondition.class);
                } else if (className.equals("ThresholdCondition")) {
                    condition = fromJson(rs.getString(3), ThresholdCondition.class);
                } else if (className.equals("ThresholdRangeCondition")) {
                    condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                } else {
                    log.debugf("Condition type: " + className + " not found");
                    condition = null;
                }
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return condition;
    }

    @Override
    public Collection<Condition> getTriggerConditions(String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<Condition> conditions = new ArrayList<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, className, payload FROM HWK_ALERTS_CONDITIONS " +
                    "WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ");
            if (null != triggerMode) {
                sql.append("AND triggerMode = '").append(triggerMode.name()).append("' ");
            }
            sql.append("ORDER BY triggerId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                String className = rs.getString(2);
                Condition condition;
                if (className.equals("AvailabilityCondition")) {
                    condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                } else if (className.equals("CompareCondition")) {
                    condition = fromJson(rs.getString(3), CompareCondition.class);
                } else if (className.equals("StringCondition")) {
                    condition = fromJson(rs.getString(3), StringCondition.class);
                } else if (className.equals("ThresholdCondition")) {
                    condition = fromJson(rs.getString(3), ThresholdCondition.class);
                } else if (className.equals("ThresholdRangeCondition")) {
                    condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                } else {
                    log.debugf("Condition type: " + className + " not found");
                    condition = null;
                }
                if (condition != null) {
                    conditions.add(condition);
                }
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return conditions;
    }

    @Override
    public Dampening getDampening(String dampeningId) throws Exception {
        if (dampeningId == null || dampeningId.isEmpty()) {
            throw new IllegalArgumentException("dampeningId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Dampening dampening = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT dampeningId, payload FROM HWK_ALERTS_DAMPENINGS WHERE ")
                    .append("dampeningId = '").append(dampeningId).append("' ");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                dampening = fromJson(rs.getString(2), Dampening.class);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return dampening;
    }

    @Override
    public Collection<Dampening> getTriggerDampenings(String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<Dampening> dampenings = new ArrayList<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, payload FROM HWK_ALERTS_DAMPENINGS " +
                    "WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ");
            if (null != triggerMode) {
                sql.append("AND triggerMode = '").append(triggerMode.name()).append("' ");
            }
            sql.append("ORDER BY triggerId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                Dampening dampening = fromJson(rs.getString(2), Dampening.class);
                dampenings.add(dampening);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return dampenings;
    }

    @Override
    public Collection<Dampening> getAllDampenings() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }
        List<Dampening> dampenings = new ArrayList<Dampening>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, payload FROM HWK_ALERTS_DAMPENINGS ")
                    .append("ORDER BY triggerId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                Dampening dampening = fromJson(rs.getString(2), Dampening.class);
                dampenings.add(dampening);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return dampenings;
    }

    @Override
    public Collection<Condition> addCondition(String triggerId, Mode triggerMode, Condition condition)
            throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must be not null");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Condition must be not null");
        }

        Collection<Condition> conditions = getTriggerConditions(triggerId, triggerMode);
        conditions.add(condition);
        int i = 0;
        for (Condition c : conditions) {
            c.setConditionSetSize(conditions.size());
            c.setConditionSetIndex(++i);
        }

        return setConditions(triggerId, triggerMode, conditions);
    }

    @Override
    public Collection<Condition> removeCondition(String conditionId) throws Exception {
        if (conditionId == null || conditionId.isEmpty()) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }

        Condition condition = getCondition(conditionId);
        if (null == condition) {
            log.debugf("Ignoring removeCondition [%s], the condition does not exist.", conditionId);
        }

        String triggerId = condition.getTriggerId();
        Trigger.Mode triggerMode = condition.getTriggerMode();
        Collection<Condition> conditions = getTriggerConditions(triggerId, triggerMode);

        int i = 0;
        int size = conditions.size() - 1;
        Collection<Condition> newConditions = new ArrayList<>(size);
        for (Condition c : conditions) {
            if (!c.getConditionId().equals(conditionId)) {
                c.setConditionSetSize(conditions.size());
                c.setConditionSetIndex(++i);
                newConditions.add(c);
            }
        }

        return setConditions(triggerId, triggerMode, newConditions);
    }

    @Override
    public Collection<Condition> updateCondition(Condition condition) throws Exception {
        if (condition == null) {
            throw new IllegalArgumentException("Condition must be not null");
        }

        String conditionId = condition.getConditionId();
        if (conditionId == null || conditionId.isEmpty()) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }

        Condition existingCondition = getCondition(conditionId);
        if (null == condition) {
            throw new IllegalArgumentException("ConditionId [" + conditionId + "] does not exist.");
        }

        String triggerId = existingCondition.getTriggerId();
        Trigger.Mode triggerMode = existingCondition.getTriggerMode();
        Collection<Condition> conditions = getTriggerConditions(triggerId, triggerMode);

        int size = conditions.size();
        Collection<Condition> newConditions = new ArrayList<>(size);
        for (Condition c : conditions) {
            if (c.getConditionId().equals(conditionId)) {
                newConditions.add(condition);
            } else {
                newConditions.add(c);
            }
        }

        return setConditions(triggerId, triggerMode, newConditions);
    }

    @Override
    public Collection<Condition> setConditions(String triggerId, Trigger.Mode triggerMode,
            Collection<Condition> conditions) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must be not null");
        }
        if (conditions == null) {
            throw new IllegalArgumentException("Conditions must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        // Get rid of the prior condition set
        removeConditions(triggerId, triggerMode);

        // Now add the new condition set
        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            int i = 0;
            for (Condition cond : conditions) {
                cond.setTriggerId(triggerId);
                cond.setTriggerMode(triggerMode);
                cond.setConditionSetSize(conditions.size());
                cond.setConditionSetIndex(++i);

                StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_CONDITIONS VALUES (")
                        .append("'").append(cond.getConditionId()).append("', ")
                        .append("'").append(cond.getTriggerId()).append("', ")
                        .append("'").append(cond.getTriggerMode().name()).append("', ")
                        .append("'").append(cond.getClass().getSimpleName()).append("', ")
                        .append("'").append(toJson(cond)).append("'")
                        .append(")");
                log.debugf("SQL: " + sql);
                s.execute(sql.toString());
            }

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(triggerId);
        }

        return conditions;
    }

    @Override
    public Dampening addDampening(Dampening dampening) throws Exception {
        if (dampening == null || dampening.getTriggerId() == null || dampening.getTriggerId().isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_DAMPENINGS VALUES (")
                    .append("'").append(dampening.getDampeningId()).append("', ")
                    .append("'").append(dampening.getTriggerId()).append("', ")
                    .append("'").append(dampening.getTriggerMode().name()).append("', ")
                    .append("'").append(toJson(dampening)).append("'")
                    .append(")");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(dampening.getTriggerId());
        }

        return dampening;
    }

    @Override
    public void addNotifier(String notifierId, Map<String, String> properties) throws Exception {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        String notifierType = properties.get("NotifierType");
        if (notifierType == null) {
            throw new IllegalArgumentException("Notifier has not NotifierType property");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_NOTIFIERS VALUES (")
                    .append("'").append(notifierId).append("', ")
                    .append("'").append(notifierType).append("', ")
                    .append("'").append(toJson(properties)).append("'")
                    .append(")");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void addNotifierType(String notifierType, Set<String> properties) throws Exception {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_NOTIFIER_TYPES VALUES (")
                    .append("'").append(notifierType).append("', ")
                    .append("'").append(toJson(properties)).append("'")
                    .append(")");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());
            s.close();
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void addTrigger(Trigger trigger) throws Exception {
        if (trigger == null || trigger.getId() == null || trigger.getId().isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_TRIGGERS VALUES (")
                    .append("'").append(trigger.getId()).append("', ")
                    .append("'").append(toJson(trigger)).append("'")
                    .append(")");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public Map<String, String> getNotifier(String notifierId) throws Exception {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Map<String, String> payload = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT notifierId, payload FROM HWK_ALERTS_NOTIFIERS WHERE ")
                    .append("notifierId = '").append(notifierId).append("'");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                payload = fromJson(rs.getString(2), Map.class);
            }
            s.close();

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return payload;
    }

    @Override
    public Collection<String> getAllNotifiers() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<String> notifiers = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT notifierId FROM HWK_ALERTS_NOTIFIERS ORDER BY notifierId ");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            notifiers = new ArrayList();
            while (rs.next()) {
                notifiers.add(rs.getString(1));
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return notifiers;
    }

    @Override
    public Collection<String> getNotifiers(String notifierType) throws Exception {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<String> notifiers = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT notifierId FROM HWK_ALERTS_NOTIFIERS WHERE ")
                    .append("notifierType = '").append(notifierType).append("' ")
                    .append("ORDER BY notifierId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            notifiers = new ArrayList();
            while (rs.next()) {
                notifiers.add(rs.getString(1));
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return notifiers;
    }

    @Override
    public Set<String> getNotifierType(String notifierType) throws Exception {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Set<String> payload = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT notifierType, payload FROM HWK_ALERTS_NOTIFIER_TYPES WHERE ")
                    .append("notifierType = '").append(notifierType).append("'");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                payload = fromJson(rs.getString(2), Set.class);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return payload != null && !payload.isEmpty() ? new HashSet(payload) : null;
    }

    @Override
    public Collection<String> getNotifierTypes() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<String> notifierTypes = new ArrayList<String>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT notifierType FROM HWK_ALERTS_NOTIFIER_TYPES ")
                    .append("ORDER BY notifierType");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                notifierTypes.add(rs.getString(1));
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return notifierTypes;
    }

    @Override
    public Trigger getTrigger(String triggerId) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Trigger trigger = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, payload FROM HWK_ALERTS_TRIGGERS WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                trigger = fromJson(rs.getString(2), Trigger.class);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return trigger;
    }

    @Override
    public Collection<Trigger> getAllTriggers() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<Trigger> triggers = new ArrayList<Trigger>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, payload FROM HWK_ALERTS_TRIGGERS ")
                    .append("ORDER BY triggerId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                Trigger trigger = fromJson(rs.getString(2), Trigger.class);
                triggers.add(trigger);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return triggers;
    }

    private void removeConditions(String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must not be null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must not be null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_CONDITIONS WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ")
                    .append(" AND triggerMode = '").append(triggerMode.name()).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void removeDampening(String dampeningId) throws Exception {
        if (dampeningId == null || dampeningId.isEmpty()) {
            throw new IllegalArgumentException("dampeningId must not be null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Dampening dampening = getDampening(dampeningId);
        if (null == dampening) {
            log.debugf("Ignoring removeDampening(" + dampeningId + "), the Dampening does not exist.");
            return;
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_DAMPENINGS WHERE ")
                    .append("dampeningId = '").append(dampeningId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(dampening.getTriggerId());
        }
    }

    @Override
    public void removeNotifier(String notifierId) throws Exception {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must not be null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_NOTIFIERS WHERE ")
                    .append("notifierId = '").append(notifierId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void removeNotifierType(String notifierType) throws Exception {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must not be null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_NOTIFIER_TYPES WHERE ")
                    .append("notifierType = '").append(notifierType).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void removeTrigger(String triggerId) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();
            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_DAMPENINGS WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

            sql = new StringBuilder("DELETE FROM HWK_ALERTS_CONDITIONS WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

            sql = new StringBuilder("DELETE FROM HWK_ALERTS_TRIGGERS WHERE ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(triggerId);
        }
    }

    @Override
    public Dampening updateDampening(Dampening dampening) throws Exception {
        if (dampening == null) {
            throw new IllegalArgumentException("DampeningId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_DAMPENINGS SET ")
                    .append("payload = '").append(toJson(dampening)).append("' ")
                    .append("WHERE dampeningId = '").append(dampening.getDampeningId()).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(dampening.getTriggerId());
        }

        return dampening;
    }

    @Override
    public void updateNotifier(String notifierId, Map<String, String> properties) throws Exception {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_NOTIFIERS SET ")
                    .append("payload = '").append(toJson(properties)).append("' ")
                    .append("WHERE notifierId = '").append(notifierId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void updateNotifierType(String notifierType, Set<String> properties) throws Exception {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_NOTIFIER_TYPES SET ")
                    .append("payload = '").append(toJson(properties)).append("' ")
                    .append("WHERE notifierType = '").append(notifierType).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public Trigger updateTrigger(Trigger trigger) throws Exception {
        if (trigger == null || trigger.getId() == null || trigger.getId().isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_TRIGGERS SET ")
                    .append("payload = '").append(toJson(trigger)).append("' ")
                    .append("WHERE triggerId = '").append(trigger.getId()).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(trigger.getId());
        }

        return trigger;
    }

    public int getNumTable(String table) throws Exception {
        if (table == null || table.isEmpty()) {
            throw new IllegalArgumentException("Table must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        int numRows = -1;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ")
                    .append(table);
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                numRows = rs.getInt(1);
            }

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        return numRows;
    }

    private String toJson(Object resource) {

        return gson.toJson(resource);

    }

    private <T> T fromJson(String json, Class<T> clazz) {

        return gson.fromJson(json, clazz);
    }

    private void close(Connection c, Statement s) {
        close(c, s, null);
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

}
