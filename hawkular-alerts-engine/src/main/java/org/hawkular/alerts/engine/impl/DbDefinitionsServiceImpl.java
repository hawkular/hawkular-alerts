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
import javax.ejb.EJB;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsEvent.EventType;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.jboss.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A database implementation of {@link org.hawkular.alerts.api.services.DefinitionsService}.
 *
 * @author Lucas Ponce
 */
public class DbDefinitionsServiceImpl implements DefinitionsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(DbDefinitionsServiceImpl.class);

    private static final String JBOSS_DATA_DIR = "jboss.server.data.dir";
    private static final String INIT_FOLDER = "hawkular-alerts";

    private Gson gson;
    private final String DS_NAME;
    private DataSource ds;
    private boolean initialized = false;

    private List<DefinitionsListener> listeners = new ArrayList<>();

    @EJB
    AlertsEngine alertsEngine;

    public DbDefinitionsServiceImpl() {
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

            initDatabase();

            String data = System.getProperty(JBOSS_DATA_DIR);
            if (data == null) {
                msgLog.errorFolderNotFound(JBOSS_DATA_DIR);
                return;
            }
            String folder = data + "/" + INIT_FOLDER;
            initFiles(folder);
            initialized = true;
        } catch (Throwable t) {
            msgLog.errorCannotInitializeDefinitionsService(t.getMessage());
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
                    "( tenantId VARCHAR(250) NOT NULL, " +
                    "  triggerId VARCHAR2(250) NOT NULL, " +
                    "  payload VARCHAR2(1024)," +
                    "  PRIMARY KEY(tenantId, triggerId) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_CONDITIONS " +
                    "( tenantId  VARCHAR(250) NOT NULL, " +
                    "  conditionId VARCHAR2(250) NOT NULL," +
                    "  triggerId VARCHAR2(250) NOT NULL," +
                    "  triggerMode VARCHAR2(20) NOT NULL," +
                    "  className VARCHAR2(250) NOT NULL," +
                    "  payload VARCHAR2(1024)," +
                    "  PRIMARY KEY(tenantId, conditionId) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_DAMPENINGS " +
                    "( tenantId VARCHAR(250) NOT NULL, " +
                    "  dampeningId VARCHAR2(250) NOT NULL," +
                    "  triggerId VARCHAR2(250) NOT NULL," +
                    "  triggerMode VARCHAR2(20) NOT NULL," +
                    "  payload VARCHAR2(1024)," +
                    "  PRIMARY KEY(tenantId, dampeningId) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_ACTION_PLUGINS " +
                    "( actionPlugin VARCHAR(250) PRIMARY KEY," +
                    "  payload VARCHAR(1024) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_ACTIONS " +
                    "( tenantId VARCHAR(250) NOT NULL," +
                    "  actionId VARCHAR(250) NOT NULL," +
                    "  actionPlugin VARCHAR(250)," +
                    "  payload VARCHAR(1024)," +
                    "  PRIMARY KEY(tenantId, actionId, actionPlugin))");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_TAGS " +
                    "( tenantId VARCHAR2(250) NOT NULL," +
                    "  triggerId VARCHAR2(250) NOT NULL, " +
                    "  category VARCHAR2(250)," +
                    "  name VARCHAR2(1024) NOT NULL, " +
                    "  visible BOOLEAN NOT NULL, " +
                    "  PRIMARY KEY(tenantId, triggerId, category, name) )");

            s.execute("CREATE TABLE IF NOT EXISTS HWK_ALERTS_ALERTS " +
                    "( tenantId VARCHAR2(250) NOT NULL, " +
                    "  alertId VARCHAR2(300) NOT NULL, " +
                    "  triggerId VARCHAR2(250) NOT NULL, " +
                    "  ctime long NOT NULL," +
                    "  status VARCHAR2(20) NOT NULL," +
                    "  payload CLOB, " +
                    "  PRIMARY KEY(tenantId, alertId) )"
                    );

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

            int nActions = getNumTable("HWK_ALERTS_ACTIONS");
            if (nActions == 0) {
                initActions(initFolder);
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
                msgLog.warningReadingFile("triggers.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 11) {
                        String tenantId = fields[0];
                        String triggerId = fields[1];
                        boolean enabled = Boolean.parseBoolean(fields[2]);
                        String name = fields[3];
                        String description = fields[4];
                        boolean autoDisable = Boolean.parseBoolean(fields[5]);
                        boolean autoResolve = Boolean.parseBoolean(fields[6]);
                        boolean autoResolveAlerts = Boolean.parseBoolean(fields[7]);
                        TriggerTemplate.Match firingMatch = TriggerTemplate.Match.valueOf(fields[8]);
                        TriggerTemplate.Match autoResolveMatch = TriggerTemplate.Match.valueOf(fields[9]);
                        String[] notifiers = fields[10].split("\\|");

                        Trigger trigger = new Trigger(triggerId, name);
                        trigger.setEnabled(enabled);
                        trigger.setAutoDisable(autoDisable);
                        trigger.setAutoResolve(autoResolve);
                        trigger.setAutoResolveAlerts(autoResolveAlerts);
                        trigger.setDescription(description);
                        trigger.setFiringMatch(firingMatch);
                        trigger.setAutoResolveMatch(autoResolveMatch);
                        trigger.setTenantId(tenantId);
                        for (String notifier : notifiers) {
                            String[] actions = notifier.split("#");
                            String actionPlugin = actions[0];
                            String actionId = actions[1];
                            trigger.addAction(actionPlugin, actionId);
                        }

                        addTrigger(tenantId, trigger);
                        log.debugf("Init file - Inserting [%s]", trigger);
                    }
                }
            }
        } else {
            msgLog.warningFileNotFound("triggers.data");
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
                msgLog.warningReadingFile("conditions.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length > 5) {
                        String tenantId = fields[0];
                        String triggerId = fields[1];
                        Trigger.Mode triggerMode = Trigger.Mode.valueOf(fields[2]);
                        int conditionSetSize = Integer.parseInt(fields[3]);
                        int conditionSetIndex = Integer.parseInt(fields[4]);
                        String type = fields[5];
                        if (type != null && !type.isEmpty() && type.equals("threshold") && fields.length == 9) {
                            String dataId = fields[6];
                            String operator = fields[7];
                            Double threshold = Double.parseDouble(fields[8]);

                            ThresholdCondition newCondition = new ThresholdCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(ThresholdCondition.Operator.valueOf(operator));
                            newCondition.setThreshold(threshold);
                            newCondition.setTenantId(tenantId);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("range") && fields.length == 12) {
                            String dataId = fields[6];
                            String operatorLow = fields[7];
                            String operatorHigh = fields[8];
                            Double thresholdLow = Double.parseDouble(fields[9]);
                            Double thresholdHigh = Double.parseDouble(fields[10]);
                            boolean inRange = Boolean.parseBoolean(fields[11]);

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
                            newCondition.setTenantId(tenantId);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("compare") && fields.length == 10) {
                            String dataId = fields[6];
                            String operator = fields[7];
                            Double data2Multiplier = Double.parseDouble(fields[8]);
                            String data2Id = fields[9];

                            CompareCondition newCondition = new CompareCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(CompareCondition.Operator.valueOf(operator));
                            newCondition.setData2Multiplier(data2Multiplier);
                            newCondition.setData2Id(data2Id);
                            newCondition.setTenantId(tenantId);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("string") && fields.length == 10) {
                            String dataId = fields[6];
                            String operator = fields[7];
                            String pattern = fields[8];
                            boolean ignoreCase = Boolean.parseBoolean(fields[9]);

                            StringCondition newCondition = new StringCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(StringCondition.Operator.valueOf(operator));
                            newCondition.setPattern(pattern);
                            newCondition.setIgnoreCase(ignoreCase);
                            newCondition.setTenantId(tenantId);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("availability") && fields.length == 8) {
                            String dataId = fields[6];
                            String operator = fields[7];

                            AvailabilityCondition newCondition = new AvailabilityCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(AvailabilityCondition.Operator.valueOf(operator));
                            newCondition.setTenantId(tenantId);

                            initCondition(newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                    }
                }
            }
        } else {
            msgLog.warningFileNotFound("conditions.data");
        }
    }

    private void initCondition(Condition condition) throws Exception {
        Collection<Condition> conditions = getTriggerConditions(condition.getTenantId(), condition.getTriggerId(),
                condition.getTriggerMode());
        conditions.add(condition);
        setConditions(condition.getTenantId(), condition.getTriggerId(), condition.getTriggerMode(), conditions);
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
                msgLog.warningReadingFile("dampening.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 7) {
                        String tenantId = fields[0];
                        String triggerId = fields[1];
                        Trigger.Mode triggerMode = Trigger.Mode.valueOf(fields[2]);
                        String type = fields[3];
                        int evalTrueSetting = Integer.parseInt(fields[4]);
                        int evalTotalSetting = Integer.parseInt(fields[5]);
                        int evalTimeSetting = Integer.parseInt(fields[6]);

                        Dampening newDampening = new Dampening(triggerId, triggerMode, Dampening.Type.valueOf(type),
                                evalTrueSetting, evalTotalSetting, evalTimeSetting);

                        addDampening(tenantId, newDampening);
                        log.debugf("Init file - Inserting [%s]", newDampening);
                    }

                }
            }
        } else {
            msgLog.warningFileNotFound("dampening.data");
        }
    }

    private void initActions(File initFolder) throws Exception {
        /*
            Actions
         */
        File actions = new File(initFolder, "actions.data");
        if (actions.exists() && actions.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(actions.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                log.error(e.toString(), e);
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length > 3) {
                        String tenantId = fields[0];
                        String actionPlugin = fields[1];
                        String actionId = fields[2];

                        Map<String, String> newAction = new HashMap<>();
                        newAction.put("tenantId", tenantId);
                        newAction.put("actionPlugin", actionPlugin);
                        newAction.put("actionId", actionId);

                        for (int i = 3; i < fields.length; i++) {
                            String property = fields[i];
                            String[] properties = property.split("=");
                            if (properties.length == 2) {
                                newAction.put(properties[0], properties[1]);
                            }
                        }
                        addAction(tenantId, actionPlugin, actionId, newAction);
                        log.debugf("Init file - Inserting [%s]", newAction);
                    }

                }
            }
        } else {
            msgLog.warningFileNotFound("actions.data");
        }
    }

    @Override
    public Collection<Condition> getAllConditions() throws Exception {
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

            StringBuilder sql = new StringBuilder("SELECT conditionId, className, payload FROM HWK_ALERTS_CONDITIONS ")
                    .append("ORDER BY conditionId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                String className = rs.getString(2);
                Condition condition;
                switch (className) {
                    case "AvailabilityCondition":
                        condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                        break;
                    case "CompareCondition":
                        condition = fromJson(rs.getString(3), CompareCondition.class);
                        break;
                    case "StringCondition":
                        condition = fromJson(rs.getString(3), StringCondition.class);
                        break;
                    case "ThresholdCondition":
                        condition = fromJson(rs.getString(3), ThresholdCondition.class);
                        break;
                    case "ThresholdRangeCondition":
                        condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                        break;
                    default:
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
    public Collection<Condition> getConditions(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
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

            StringBuilder sql = new StringBuilder("SELECT conditionId, className, payload FROM HWK_ALERTS_CONDITIONS ")
                    .append("WHERE tenantId = '").append(tenantId).append("' ")
                    .append("ORDER BY conditionId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                String className = rs.getString(2);
                Condition condition;
                switch (className) {
                    case "AvailabilityCondition":
                        condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                        break;
                    case "CompareCondition":
                        condition = fromJson(rs.getString(3), CompareCondition.class);
                        break;
                    case "StringCondition":
                        condition = fromJson(rs.getString(3), StringCondition.class);
                        break;
                    case "ThresholdCondition":
                        condition = fromJson(rs.getString(3), ThresholdCondition.class);
                        break;
                    case "ThresholdRangeCondition":
                        condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                        break;
                    default:
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
    public Condition getCondition(String tenantId, String conditionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(conditionId)) {
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("conditionId = '").append(conditionId).append("' ")
                    .append("ORDER BY triggerId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            if (rs.next()) {
                String className = rs.getString(2);
                switch (className) {
                    case "AvailabilityCondition":
                        condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                        break;
                    case "CompareCondition":
                        condition = fromJson(rs.getString(3), CompareCondition.class);
                        break;
                    case "StringCondition":
                        condition = fromJson(rs.getString(3), StringCondition.class);
                        break;
                    case "ThresholdCondition":
                        condition = fromJson(rs.getString(3), ThresholdCondition.class);
                        break;
                    case "ThresholdRangeCondition":
                        condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                        break;
                    default:
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
    public Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Trigger.Mode triggerMode)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
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
                switch (className) {
                    case "AvailabilityCondition":
                        condition = fromJson(rs.getString(3), AvailabilityCondition.class);
                        break;
                    case "CompareCondition":
                        condition = fromJson(rs.getString(3), CompareCondition.class);
                        break;
                    case "StringCondition":
                        condition = fromJson(rs.getString(3), StringCondition.class);
                        break;
                    case "ThresholdCondition":
                        condition = fromJson(rs.getString(3), ThresholdCondition.class);
                        break;
                    case "ThresholdRangeCondition":
                        condition = fromJson(rs.getString(3), ThresholdRangeCondition.class);
                        break;
                    default:
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
    public Dampening getDampening(String tenantId, String dampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampeningId)) {
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
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
    public Collection<Dampening> getTriggerDampenings(String tenantId, String triggerId, Trigger.Mode triggerMode)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
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
        List<Dampening> dampenings = new ArrayList<>();
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
    public Collection<Dampening> getDampenings(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
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

            StringBuilder sql = new StringBuilder("SELECT triggerId, payload FROM HWK_ALERTS_DAMPENINGS ")
                    .append("WHERE tenantId = '").append(tenantId).append("' ")
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
    public Collection<Condition> addCondition(String tenantId, String triggerId, Mode triggerMode, Condition condition)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must be not null");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Condition must be not null");
        }
        condition.setTenantId(tenantId);

        Collection<Condition> conditions = getTriggerConditions(tenantId, triggerId, triggerMode);
        conditions.add(condition);
        int i = 0;
        for (Condition c : conditions) {
            c.setConditionSetSize(conditions.size());
            c.setConditionSetIndex(++i);
        }

        return setConditions(tenantId, triggerId, triggerMode, conditions);
    }

    @Override
    public Collection<Condition> removeCondition(String tenantId, String conditionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(conditionId)) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }

        Condition condition = getCondition(tenantId, conditionId);
        if (null == condition) {
            log.debugf("Ignoring removeCondition [%s], the condition does not exist.", conditionId);
            return null;
        }

        String triggerId = condition.getTriggerId();
        Trigger.Mode triggerMode = condition.getTriggerMode();
        Collection<Condition> conditions = getTriggerConditions(tenantId, triggerId, triggerMode);

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

        return setConditions(tenantId, triggerId, triggerMode, newConditions);
    }

    @Override
    public Collection<Condition> updateCondition(String tenantId, Condition condition) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (condition == null) {
            throw new IllegalArgumentException("Condition must be not null");
        }

        String conditionId = condition.getConditionId();
        if (isEmpty(conditionId)) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }

        Condition existingCondition = getCondition(tenantId, conditionId);
        if (null == existingCondition) {
            throw new IllegalArgumentException("ConditionId [" + conditionId + "] in tenant [ " + tenantId +
                    " does not exist.");
        }

        String triggerId = existingCondition.getTriggerId();
        Trigger.Mode triggerMode = existingCondition.getTriggerMode();
        Collection<Condition> conditions = getTriggerConditions(tenantId, triggerId, triggerMode);

        int size = conditions.size();
        Collection<Condition> newConditions = new ArrayList<>(size);
        for (Condition c : conditions) {
            if (c.getConditionId().equals(conditionId)) {
                newConditions.add(condition);
            } else {
                newConditions.add(c);
            }
        }

        return setConditions(tenantId, triggerId, triggerMode, newConditions);
    }

    @Override
    public Collection<Condition> setConditions(String tenantId, String triggerId, Trigger.Mode triggerMode,
            Collection<Condition> conditions) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
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
        removeConditions(tenantId, triggerId, triggerMode);

        // Now add the new condition set
        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();
            List<String> dataIds = new ArrayList<>(2);

            int i = 0;
            for (Condition cond : conditions) {
                cond.setTriggerId(triggerId);
                cond.setTriggerMode(triggerMode);
                cond.setConditionSetSize(conditions.size());
                cond.setConditionSetIndex(++i);

                StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_CONDITIONS VALUES (")
                        .append("'").append(cond.getTenantId()).append("', ")
                        .append("'").append(cond.getConditionId()).append("', ")
                        .append("'").append(cond.getTriggerId()).append("', ")
                        .append("'").append(cond.getTriggerMode().name()).append("', ")
                        .append("'").append(cond.getClass().getSimpleName()).append("', ")
                        .append("'").append(toJson(cond)).append("'")
                        .append(")");
                log.debugf("SQL: " + sql);
                s.execute(sql.toString());

                // generate the automatic dataId tags for search
                dataIds.add(cond.getDataId());
                if (cond instanceof CompareCondition) {
                    dataIds.add(((CompareCondition) cond).getData2Id());
                }
                for (String dataId : dataIds) {
                    insertTag(c, s, cond.getTenantId(), cond.getTriggerId(), "dataId", dataId, false);
                }
                dataIds.clear();
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(tenantId, triggerId);
        }

        notifyListeners(DefinitionsEvent.EventType.CONDITION_CHANGE);

        return conditions;
    }

    @Override
    public Dampening addDampening(String tenantId, Dampening dampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (dampening == null) {
            throw new IllegalArgumentException("Dampening must be not null");
        }
        String triggerId = dampening.getTriggerId();
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        dampening.setTenantId(tenantId);
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_DAMPENINGS VALUES (")
                    .append("'").append(dampening.getTenantId()).append("', ")
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

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(EventType.DAMPENING_CHANGE);

        return dampening;
    }

    @Override
    public void addAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        properties.put("tenantId", tenantId);
        properties.put("actionPlugin", actionPlugin);
        properties.put("actionId", actionId);

        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_ACTIONS VALUES (")
                    .append("'").append(tenantId).append("', ")
                    .append("'").append(actionId).append("', ")
                    .append("'").append(actionPlugin).append("', ")
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
    public void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
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

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_ACTION_PLUGINS VALUES (")
                    .append("'").append(actionPlugin).append("', ")
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
    public void addTrigger(String tenantId, Trigger trigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(trigger)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        trigger.setTenantId(tenantId);

        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_TRIGGERS VALUES (")
                    .append("'").append(tenantId).append("', ")
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

        notifyListeners(EventType.TRIGGER_CHANGE);
    }

    @Override
    public Trigger copyTrigger(String tenantId, String triggerId, Map<String, String> dataIdMap) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (dataIdMap == null || dataIdMap.isEmpty()) {
            throw new IllegalArgumentException("DataIdMap must be not null");
        }

        Trigger trigger = getTrigger(tenantId, triggerId);
        if (trigger == null) {
            throw new IllegalArgumentException("Trigger not found for triggerId [" + triggerId + "] in tenant " +
                    tenantId);
        }
        // ensure we have a 1-1 mapping for the dataId substitution
        Set<String> dataIdTokens = new HashSet<>();
        Collection<Condition> conditions = getTriggerConditions(tenantId, triggerId, null);
        for (Condition c : conditions) {
            if (c instanceof CompareCondition) {
                dataIdTokens.add(c.getDataId());
                dataIdTokens.add(((CompareCondition) c).getData2Id());
            } else {
                dataIdTokens.add(c.getDataId());
            }
        }
        if (!dataIdTokens.equals(dataIdMap.keySet())) {
            throw new IllegalArgumentException(
                    "DataIdMap must contain the exact dataIds (keyset) expected by the condition set. Expected: "
                            + dataIdMap.keySet() + ", dataIdMap: " + dataIdMap.keySet());
        }
        Collection<Dampening> dampenings = getTriggerDampenings(tenantId, triggerId, null);

        Trigger newTrigger = new Trigger(trigger.getName());
        newTrigger.setName(trigger.getName());
        newTrigger.setDescription(trigger.getDescription());
        newTrigger.setFiringMatch(trigger.getFiringMatch());
        newTrigger.setAutoResolveMatch(trigger.getAutoResolveMatch());
        newTrigger.setActions(trigger.getActions());
        newTrigger.setTenantId(trigger.getTenantId());

        addTrigger(tenantId, newTrigger);

        for (Condition c : conditions) {
            Condition newCondition = null;
            if (c instanceof ThresholdCondition) {
                newCondition = new ThresholdCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((ThresholdCondition) c).getOperator(), ((ThresholdCondition) c).getThreshold());

            } else if (c instanceof ThresholdRangeCondition) {
                newCondition = new ThresholdRangeCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((ThresholdRangeCondition) c).getOperatorLow(),
                        ((ThresholdRangeCondition) c).getOperatorHigh(),
                        ((ThresholdRangeCondition) c).getThresholdLow(),
                        ((ThresholdRangeCondition) c).getThresholdHigh(),
                        ((ThresholdRangeCondition) c).isInRange());

            } else if (c instanceof AvailabilityCondition) {
                newCondition = new AvailabilityCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((AvailabilityCondition) c).getOperator());

            } else if (c instanceof CompareCondition) {
                newCondition = new CompareCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((CompareCondition) c).getOperator(),
                        ((CompareCondition) c).getData2Multiplier(),
                        dataIdMap.get(((CompareCondition) c).getData2Id()));

            } else if (c instanceof StringCondition) {
                newCondition = new StringCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((StringCondition) c).getOperator(), ((StringCondition) c).getPattern(),
                        ((StringCondition) c).isIgnoreCase());
            }
            if (newCondition != null) {
                newCondition.setTenantId(newTrigger.getTenantId());
                addCondition(newTrigger.getTenantId(), newTrigger.getId(), newCondition.getTriggerMode(), newCondition);
            }
        }

        for (Dampening d : dampenings) {
            Dampening newDampening = new Dampening(newTrigger.getId(), d.getTriggerMode(), d.getType(),
                    d.getEvalTrueSetting(), d.getEvalTotalSetting(), d.getEvalTimeSetting());
            newDampening.setTenantId(newTrigger.getTenantId());

            addDampening(newDampening.getTenantId(), newDampening);
        }

        return newTrigger;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getAction(String tenantId, String actionPlugin, String actionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("ActionId must be not null");
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

            StringBuilder sql = new StringBuilder("SELECT actionId, payload FROM HWK_ALERTS_ACTIONS WHERE ")
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("actionPlugin = '").append(actionPlugin).append("' AND ")
                    .append("actionId = '").append(actionId).append("'");
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
    public Map<String, Map<String, Set<String>>> getAllActions() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Map<String, Map<String, Set<String>>> actions = new HashMap<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT tenantId, actionPlugin, actionId FROM HWK_ALERTS_ACTIONS ")
                    .append("ORDER BY tenantId, actionPlugin, actionId ");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                String tenantId = rs.getString(1);
                String actionPlugin = rs.getString(2);
                String actionId = rs.getString(3);
                if (actions.get(tenantId) == null) {
                    actions.put(tenantId, new HashMap<>());
                }
                if (actions.get(tenantId).get(actionPlugin) == null) {
                    actions.get(tenantId).put(actionPlugin, new HashSet<>());
                }
                actions.get(tenantId).get(actionPlugin).add(actionId);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return actions;
    }

    @Override
    public Map<String, Set<String>> getActions(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Map<String, Set<String>> actions = new HashMap<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT actionPlugin, actionId FROM HWK_ALERTS_ACTIONS ")
                    .append("WHERE tenantId = '").append(tenantId).append("' ")
                    .append("ORDER BY actionId ");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                String actionPlugin = rs.getString(1);
                String actionId = rs.getString(2);
                if (actions.get(actionPlugin) == null) {
                    actions.put(actionPlugin, new HashSet<>());
                }
                actions.get(actionPlugin).add(actionId);
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return actions;
    }

    @Override
    public Collection<String> getActions(String tenantId, String actionPlugin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<String> actions = null;
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT actionId FROM HWK_ALERTS_ACTIONS WHERE ")
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("actionPlugin = '").append(actionPlugin).append("' ")
                    .append("ORDER BY actionId");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            actions = new ArrayList<>();
            while (rs.next()) {
                actions.add(rs.getString(1));
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return actions;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getActionPlugin(String actionPlugin) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
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

            StringBuilder sql = new StringBuilder("SELECT actionPlugin, payload FROM HWK_ALERTS_ACTION_PLUGINS WHERE ")
                    .append("actionPlugin = '").append(actionPlugin).append("'");
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
    public Collection<String> getActionPlugins() throws Exception {
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<String> actionPlugins = new ArrayList<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT actionPlugin FROM HWK_ALERTS_ACTION_PLUGINS ")
                    .append("ORDER BY actionPlugin");
            log.debugf("SQL: " + sql);
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                actionPlugins.add(rs.getString(1));
            }
        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s, rs);
        }

        return actionPlugins;
    }

    @Override
    public Trigger getTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
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

        List<Trigger> triggers = new ArrayList<>();
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

    @Override
    public Collection<Trigger> getTriggers(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<Trigger> triggers = new ArrayList<>();
        Connection c = null;
        Statement s = null;
        ResultSet rs = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("SELECT triggerId, payload FROM HWK_ALERTS_TRIGGERS ")
                    .append("WHERE tenantId = '").append(tenantId).append("' ")
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

    private void removeConditions(String tenantId, String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("triggerId = '").append(triggerId).append("' ")
                    .append(" AND triggerMode = '").append(triggerMode.name()).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

            // if removing conditions remove the automatically-added dataId tags
            deleteTags(c, tenantId, triggerId, "dataId", null);

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    @Override
    public void removeDampening(String tenantId, String dampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampeningId)) {
            throw new IllegalArgumentException("dampeningId must not be null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Dampening dampening = getDampening(tenantId, dampeningId);
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("dampeningId = '").append(dampeningId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(EventType.DAMPENING_CHANGE);
    }

    @Override
    public void removeAction(String tenantId, String actionPlugin, String actionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("ActionId must be not null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_ACTIONS WHERE ")
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("actionPlugin = '").append(actionPlugin).append("' AND ")
                    .append("actionId = '").append(actionId).append("' ");
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
    public void removeActionPlugin(String actionPlugin) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("ActionPlugin must not be null");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_ACTION_PLUGINS WHERE ")
                    .append("actionPlugin = '").append(actionPlugin).append("' ");
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
    public void removeTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
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
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

            sql = new StringBuilder("DELETE FROM HWK_ALERTS_CONDITIONS WHERE ")
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

            sql = new StringBuilder("DELETE FROM HWK_ALERTS_TAGS WHERE ")
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

            sql = new StringBuilder("DELETE FROM HWK_ALERTS_TRIGGERS WHERE ")
                    .append("tenantId = '").append(tenantId).append("' AND ")
                    .append("triggerId = '").append(triggerId).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(tenantId, triggerId);
        }

        notifyListeners(EventType.TRIGGER_CHANGE);
    }

    @Override
    public Dampening updateDampening(String tenantId, Dampening dampening) throws Exception {
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

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_DAMPENINGS ")
                    .append("SET payload = '").append(toJson(dampening)).append("' ")
                    .append("WHERE tenantId = '").append(tenantId).append("' AND ")
                    .append("dampeningId = '").append(dampening.getDampeningId()).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(EventType.DAMPENING_CHANGE);

        return dampening;
    }

    @Override
    public void updateAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("ActionId must be not null");
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

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_ACTIONS SET ")
                    .append("payload = '").append(toJson(properties)).append("' ")
                    .append("WHERE tenantId = '").append(tenantId).append("' AND ")
                    .append("actionPlugin = '").append(actionPlugin).append("' AND ")
                    .append("actionId = '").append(actionId).append("' ");
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
    public void updateActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
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

            StringBuilder sql = new StringBuilder("UPDATE HWK_ALERTS_ACTION_PLUGINS SET ")
                    .append("payload = '").append(toJson(properties)).append("' ")
                    .append("WHERE actionPlugin = '").append(actionPlugin).append("' ");
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
    public Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(trigger)) {
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
                    .append("WHERE tenantId = '").append(tenantId).append("' AND ")
                    .append("triggerId = '").append(trigger.getId()).append("' ");
            log.debugf("SQL: " + sql);
            s.execute(sql.toString());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(trigger.getTenantId(), trigger.getId());
        }

        notifyListeners(EventType.TRIGGER_CHANGE);

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
        ResultSet rs;
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

    @Override
    public void addTag(String tenantId, Tag tag) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (tag == null) {
            throw new IllegalArgumentException("Tag must be not null");
        }
        if (isEmpty(tag.getTriggerId())) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }
        if (isEmpty(tag.getName())) {
            throw new IllegalArgumentException("Tag Name must be not null or empty");
        }
        tag.setTenantId(tenantId);

        // Now add the tag
        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            insertTag(c, s, tag.getTenantId(), tag.getTriggerId(), tag.getCategory(), tag.getName(), tag.isVisible());

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    private void insertTag(Connection c, Statement s, String tenantId, String triggerId, String category, String name,
            boolean visible) throws Exception {

        // If the desired Tag already exists just return
        if (!getTags(s, tenantId, triggerId, category, name).isEmpty()) {
            return;
        }

        StringBuilder sql = new StringBuilder("INSERT INTO HWK_ALERTS_TAGS VALUES (");
        sql.append("'").append(tenantId).append("', ")
                .append("'").append(triggerId).append("', ");
        if (isEmpty(category)) {
            sql.append("NULL, ");
        } else {
            sql.append("'").append(category).append("', ");
        }
        sql.append("'").append(name).append("', ");
        sql.append("'").append(String.valueOf(visible)).append("' ");
        sql.append(")");
        log.debugf("SQL: " + sql);
        s = c.createStatement();
        s.execute(sql.toString());
    }

    @Override
    public void removeTags(String tenantId, String triggerId, String category, String name) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }

        // Now remove the tag(s)
        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            deleteTags(c, tenantId, triggerId, category, name);

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }
    }

    private void deleteTags(Connection c, String tenantId, String triggerId, String category, String name)
            throws Exception {
        StringBuilder sql = new StringBuilder("DELETE FROM HWK_ALERTS_TAGS WHERE ");
        sql.append("tenantId = '").append(tenantId).append("' AND ")
                .append("triggerId = '").append(triggerId).append("' ");
        if (!isEmpty(category)) {
            sql.append("AND category = '").append(category).append("' ");
        }
        if (!isEmpty(name)) {
            sql.append("AND name = '").append(name).append("' ");
        }
        log.debugf("SQL: " + sql);
        Statement s = c.createStatement();
        s.executeUpdate(sql.toString());
        s.close();
    }

    @Override
    public List<Tag> getTriggerTags(String tenantId, String triggerId, String category) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }
        if (ds == null) {
            throw new Exception("DataSource is null");
        }

        List<Tag> tags = null;
        Connection c = null;
        Statement s = null;
        try {
            c = ds.getConnection();
            s = c.createStatement();

            tags = getTags(s, tenantId, triggerId, category, null);

        } catch (SQLException e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            close(c, s);
        }

        return tags;
    }

    private List<Tag> getTags(Statement s, String tenantId, String triggerId, String category,
            String name) throws Exception {

        StringBuilder sql = new StringBuilder("SELECT triggerId, category, name, visible FROM HWK_ALERTS_TAGS ");
        sql.append("WHERE tenantId = '").append(tenantId).append("' AND ")
                .append(" triggerId = '").append(triggerId).append("' ");

        if (!isEmpty(category)) {
            sql.append("AND category = '").append(category).append("' ");
        }
        if (!isEmpty(name)) {
            sql.append("AND name = '").append(name).append("' ");
        }

        sql.append("ORDER BY category, name");
        log.debugf("SQL: " + sql);

        List<Tag> tags = new ArrayList<>();
        ResultSet rs = null;
        try {
            rs = s.executeQuery(sql.toString());
            while (rs.next()) {
                Tag tag = new Tag(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4));
                tag.setTenantId(tenantId);
                tags.add(tag);
            }
        } finally {
            if (null != rs) {
                rs.close();
            }
        }

        return tags;
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

    private boolean isEmpty(Trigger trigger) {
        return trigger == null || trigger.getId() == null || trigger.getId().trim().isEmpty();
    }

    private void notifyListeners(EventType eventType) {
        DefinitionsEvent de = new DefinitionsEvent(eventType);
        for (DefinitionsListener dl : listeners) {
            log.debugf("Notified Listener %s", eventType.name());
            dl.onChange(de);
        }
    }

    @Override
    public void registerListener(DefinitionsListener listener) {
        listeners.add(listener);
    }

}
