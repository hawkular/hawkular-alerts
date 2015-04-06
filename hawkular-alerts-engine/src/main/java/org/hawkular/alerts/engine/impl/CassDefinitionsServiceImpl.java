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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.jboss.logging.Logger;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

/**
 * A Cassandra implementation of {@link org.hawkular.alerts.api.services.DefinitionsService}.
 *
 * @author Lucas Ponce
 */
@Singleton
public class CassDefinitionsServiceImpl implements DefinitionsService {
    private static final String JBOSS_DATA_DIR = "jboss.server.data.dir";
    private static final String INIT_FOLDER = "hawkular-alerts";
    private static final String CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(CassDefinitionsServiceImpl.class);
    private AlertsService alertsService;
    private Session session;
    private String keyspace;
    private boolean initialized = false;

    private List<DefinitionsListener> listeners = new ArrayList<>();

    private PreparedStatement insertTrigger;
    private PreparedStatement selectTriggerConditions;
    private PreparedStatement selectTriggerConditionsTriggerMode;
    private PreparedStatement deleteConditionsMode;
    private PreparedStatement insertAvailabilityCondition;
    private PreparedStatement insertCompareCondition;
    private PreparedStatement insertStringCondition;
    private PreparedStatement insertThresholdCondition;
    private PreparedStatement insertThresholdRangeCondition;
    private PreparedStatement insertTag;
    private PreparedStatement insertDampening;
    private PreparedStatement insertAction;
    private PreparedStatement selectAllTriggers;
    private PreparedStatement selectAllConditions;
    private PreparedStatement selectAllDampenings;
    private PreparedStatement selectAllActions;
    private PreparedStatement selectTrigger;
    private PreparedStatement selectTriggerDampenings;
    private PreparedStatement selectTriggerDampeningsMode;
    private PreparedStatement deleteDampenings;
    private PreparedStatement deleteConditions;
    private PreparedStatement deleteTriggers;
    private PreparedStatement updateTrigger;
    private PreparedStatement deleteDampeningId;
    private PreparedStatement selectDampeningId;
    private PreparedStatement updateDampeningId;
    private PreparedStatement selectConditionId;
    private PreparedStatement insertActionPlugin;
    private PreparedStatement deleteActionPlugin;
    private PreparedStatement updateActionPlugin;
    private PreparedStatement selectActionPlugins;
    private PreparedStatement selectActionPlugin;
    private PreparedStatement deleteAction;
    private PreparedStatement updateAction;
    private PreparedStatement selectActionsPlugin;
    private PreparedStatement selectAction;
    private PreparedStatement selectTagsTriggers;
    private PreparedStatement insertTagsTriggers;
    private PreparedStatement updateTagsTriggers;
    private PreparedStatement selectTag;
    private PreparedStatement deleteTagsTriggers;

    public CassDefinitionsServiceImpl() {

    }

    public CassDefinitionsServiceImpl(AlertsService alertsService, Session session, String keyspace) {
        this();
        this.alertsService = alertsService;
        this.session = session;
        this.keyspace = keyspace;
    }

    @PostConstruct
    public void init() {
        try {
            if (this.keyspace == null) {
                this.keyspace = AlertProperties.getProperty(CASSANDRA_KEYSPACE, "hawkular_alerts");
            }

            if (session == null) {
                session = CassCluster.getSession();
            }

            initPreparedStatements();

            initialData();

            if (alertsService == null) {
                try {
                    InitialContext ctx = new InitialContext();
                    alertsService = (AlertsService) ctx
                            .lookup("java:app/hawkular-alerts-engine/CassAlertsServiceImpl");
                } catch (NamingException e) {
                    log.debugf(e.getMessage(), e);
                    msgLog.errorCannotWithAlertsService(e.getMessage());
                }
            }
        } catch (Throwable t) {
            msgLog.errorCannotInitializeDefinitionsService(t.getMessage());
            t.printStackTrace();
        }
    }

    private void initialData() throws IOException {
        String data = System.getProperty(JBOSS_DATA_DIR);
        if (data == null || data.isEmpty()) {
            msgLog.errorFolderNotFound(data);
            return;
        }
        String folder = data + "/" + INIT_FOLDER;
        initFiles(folder);
        initialized = true;
    }

    private void initPreparedStatements() {
        if (insertTrigger == null) {
            insertTrigger = session.prepare("INSERT INTO " + keyspace + ".triggers " +
                    "(name, description, autoDisable, autoResolve, autoResolveAlerts, actions, firingMatch, " +
                    "autoResolveMatch, id, enabled) " +
                    "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ");
        }
        if (selectTriggerConditions == null) {
            selectTriggerConditions = session.prepare("SELECT triggerId, triggerMode, type, conditionSetSize, " +
                    "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                    "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange " +
                    "FROM " + keyspace + ".conditions " +
                    "WHERE triggerId = ? ORDER BY triggerMode, type, conditionId ");
        }
        if (selectTriggerConditionsTriggerMode == null) {
            selectTriggerConditionsTriggerMode = session.prepare("SELECT triggerId, triggerMode, type, " +
                    "conditionSetSize, conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, " +
                    "pattern, ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange " +
                    "FROM " + keyspace + ".conditions " +
                    "WHERE triggerId = ? AND triggerMode = ? ORDER BY triggerMode, type, conditionId ");
        }
        if (deleteConditionsMode == null) {
            deleteConditionsMode = session.prepare("DELETE FROM " + keyspace + ".conditions " +
                    "WHERE triggerId = ? AND triggerMode = ? ");
        }
        if (insertAvailabilityCondition == null) {
            insertAvailabilityCondition = session.prepare("INSERT INTO " + keyspace + ".conditions " +
                    "(triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, dataId, " +
                    "operator) VALUES (?, ?, 'AVAILABILITY', ?, ?, ?, ?, ?) ");
        }
        if (insertCompareCondition == null) {
            insertCompareCondition = session.prepare("INSERT INTO " + keyspace + ".conditions " +
                    "(triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, dataId, " +
                    "operator, data2Id, data2Multiplier) VALUES (?, ?, 'COMPARE', ?, ?, ?, ?, ?, ?, ?) ");
        }
        if (insertStringCondition == null) {
            insertStringCondition = session.prepare("INSERT INTO " + keyspace + ".conditions " +
                    "(triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, dataId, " +
                    "operator, pattern, ignoreCase) VALUES (?, ?, 'STRING', ?, ?, ?, ?, ?, ?, ?) ");
        }
        if (insertThresholdCondition == null) {
            insertThresholdCondition = session.prepare("INSERT INTO " + keyspace + ".conditions " +
                    "(triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, dataId, " +
                    "operator, threshold) VALUES (?, ?, 'THRESHOLD', ?, ?, ?, ?, ?, ?) ");
        }
        if (insertThresholdRangeCondition == null) {
            insertThresholdRangeCondition = session.prepare("INSERT INTO " + keyspace + ".conditions " +
                    "(triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, dataId, " +
                    "operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange) VALUES (?, ?, 'RANGE', ?, ?, " +
                    "?, ?, ?, ?, ?, ?, ?) ");
        }
        if (insertTag == null) {
            insertTag = session.prepare("INSERT INTO " + keyspace + ".tags " +
                    "(triggerId, category, name, visible) VALUES (?, ?, ?, ?) ");
        }
        if (insertDampening == null) {
            insertDampening = session.prepare("INSERT INTO " + keyspace + ".dampenings " +
                    "(triggerId, triggerMode, type, evalTrueSetting, evalTotalSetting, evalTimeSetting, " +
                    "dampeningId) VALUES (?, ?, ?, ?, ?, ?, ?) ");
        }
        if (insertAction == null) {
            insertAction = session.prepare("INSERT INTO " + keyspace + ".actions " +
                    "(actionId, actionPlugin, properties) VALUES (?, ?, ?) ");
        }
        if (selectAllTriggers == null) {
            selectAllTriggers = session.prepare("SELECT name, description, autoDisable, autoResolve, " +
                    "autoResolveAlerts, actions, firingMatch, autoResolveMatch, id, enabled " +
                    "FROM " + keyspace + ".triggers ");
        }
        if (selectAllConditions == null) {
            selectAllConditions = session.prepare("SELECT triggerId, triggerMode, type, conditionSetSize, " +
                    "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                    "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange " +
                    "FROM " + keyspace + ".conditions ");
        }
        if (selectAllDampenings == null) {
            selectAllDampenings = session.prepare("SELECT triggerId, triggerMode, type, evalTrueSetting, " +
                    "evalTotalSetting, evalTimeSetting, dampeningId FROM " + keyspace + ".dampenings ");
        }
        if (selectAllActions == null) {
            selectAllActions = session.prepare("SELECT actionId " +
                    "FROM " + keyspace + ".actions ");
        }
        if (selectTrigger == null) {
            selectTrigger = session.prepare("SELECT name, description, autoDisable, autoResolve, " +
                    "autoResolveAlerts, actions, firingMatch, autoResolveMatch, id, enabled " +
                    "FROM " + keyspace + ".triggers WHERE id = ? ");
        }
        if (selectTriggerDampenings == null) {
            selectTriggerDampenings = session.prepare("SELECT triggerId, triggerMode, type, evalTrueSetting, " +
                    "evalTotalSetting, evalTimeSetting, dampeningId FROM " + keyspace + ".dampenings " +
                    "WHERE triggerId = ? ");
        }
        if (selectTriggerDampeningsMode == null) {
            selectTriggerDampeningsMode = session.prepare("SELECT triggerId, triggerMode, type, evalTrueSetting, " +
                    "evalTotalSetting, evalTimeSetting, dampeningId FROM " + keyspace + ".dampenings " +
                    "WHERE triggerId = ? and triggerMode = ? ");
        }
        if (deleteDampenings == null) {
            deleteDampenings = session.prepare("DELETE FROM " + keyspace + ".dampenings WHERE triggerId = ? ");
        }
        if (deleteConditions == null) {
            deleteConditions = session.prepare("DELETE FROM " + keyspace + ".conditions WHERE triggerId = ? ");
        }
        if (deleteTriggers == null) {
            deleteTriggers = session.prepare("DELETE FROM " + keyspace + ".triggers WHERE id = ? ");
        }
        if (updateTrigger == null) {
            updateTrigger = session.prepare("UPDATE " + keyspace + ".triggers " +
                    "SET name = ?, description = ?, autoDisable = ?, autoResolve = ?, autoResolveAlerts = ?, " +
                    "actions = ?, firingMatch = ?, autoResolveMatch = ?, enabled = ? " +
                    "WHERE id = ? ");
        }
        if (deleteDampeningId == null) {
            deleteDampeningId = session.prepare("DELETE FROM " + keyspace + ".dampenings " +
                    "WHERE triggerId = ? AND triggerMode = ? AND dampeningId = ? ");
        }
        if (selectDampeningId == null) {
            selectDampeningId = session.prepare("SELECT triggerId, triggerMode, type, evalTrueSetting, " +
                    "evalTotalSetting, evalTimeSetting, dampeningId FROM " + keyspace + ".dampenings " +
                    "WHERE dampeningId = ? ");
        }
        if (updateDampeningId == null) {
            updateDampeningId = session.prepare("UPDATE " + keyspace + ".dampenings " +
                    "SET type = ?, evalTrueSetting = ?, evalTotalSetting = ?, evalTimeSetting = ? " +
                    "WHERE triggerId = ? AND triggerMode = ? AND dampeningId = ? ");
        }
        if (selectConditionId == null) {
            selectConditionId = session.prepare("SELECT triggerId, triggerMode, type, conditionSetSize, " +
                    "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                    "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange " +
                    "FROM " + keyspace + ".conditions WHERE conditionId = ? ");
        }
        if (insertActionPlugin == null) {
            insertActionPlugin = session.prepare("INSERT INTO " + keyspace + ".action_plugins (actionPlugin, " +
                    "properties) VALUES (?, ?) ");
        }
        if (deleteActionPlugin == null) {
            deleteActionPlugin = session.prepare("DELETE FROM " + keyspace + ".action_plugins WHERE actionPlugin = ? ");
        }
        if (updateActionPlugin == null) {
            updateActionPlugin = session.prepare("UPDATE " + keyspace + ".action_plugins " +
                    "SET properties = ? WHERE actionPlugin = ? ");
        }
        if (selectActionPlugins == null) {
            selectActionPlugins = session.prepare("SELECT actionPlugin FROM " + keyspace + ".action_plugins ");
        }
        if (selectActionPlugin == null) {
            selectActionPlugin = session.prepare("SELECT properties FROM " + keyspace + ".action_plugins " +
                    "WHERE actionPlugin = ? ");
        }
        if (deleteAction == null) {
            deleteAction = session.prepare("DELETE FROM " + keyspace + ".actions WHERE actionId = ? ");
        }
        if (updateAction == null) {
            updateAction = session.prepare("UPDATE " + keyspace + ".actions " +
                    "SET properties = ? " +
                    "WHERE actionId = ?");
        }
        if (selectActionsPlugin == null) {
            selectActionsPlugin = session.prepare("SELECT actionId FROM " + keyspace + ".actions " +
                    "WHERE actionPlugin = ? ");
        }
        if (selectAction == null) {
            selectAction = session.prepare("SELECT properties FROM " + keyspace + ".actions WHERE actionId = ? ");
        }
        if (selectTagsTriggers == null) {
            selectTagsTriggers = session.prepare("SELECT triggers FROM " + keyspace + ".tags_triggers " +
                    "WHERE category = ? AND name = ? ");
        }
        if (insertTagsTriggers == null) {
            insertTagsTriggers = session.prepare("INSERT INTO " + keyspace + ".tags_triggers " +
                    "(category, name, triggers) VALUES (?, ?, ?) ");
        }
        if (updateTagsTriggers == null) {
            updateTagsTriggers = session.prepare("UPDATE " + keyspace + ".tags_triggers " +
                    "SET triggers = ? " +
                    "WHERE category = ? AND name = ? ");
        }
        if (selectTag == null) {
            selectTag = session.prepare("SELECT category, name FROM " + keyspace + ".tags " +
                    "WHERE triggerId = ? ");
        }
        if (deleteTagsTriggers == null) {
            deleteTagsTriggers = session.prepare("DELETE FROM " + keyspace + ".tags_triggers " +
                    "WHERE category = ? AND name = ? ");
        }
    }

    private void initFiles(String folder) {
        if (folder == null) {
            msgLog.errorFolderMustBeNotNull();
            return;
        }
        if (session == null) {
            msgLog.errorDatabaseException("Cassandra session is null. Initialization can not work.");
            return;
        }

        File fFolder = new File(folder);
        if (!fFolder.exists()) {
            log.debugf("Data folder doesn't exits. Skipping initialization.");
            return;
        }

        try {
            initTriggers(fFolder);
            initConditions(fFolder);
            initDampenings(fFolder);
            initActions(fFolder);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            msgLog.errorDatabaseException("Error initializing files. Msg: " + e);
        }

    }

    private void initTriggers(File fFolder) throws Exception {
        File triggers = new File(fFolder, "triggers.data");
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
                    if (fields.length == 10) {
                        String triggerId = fields[0];
                        boolean enabled = new Boolean(fields[1]).booleanValue();
                        String name = fields[2];
                        String description = fields[3];
                        boolean autoDisable = new Boolean(fields[4]).booleanValue();
                        boolean autoResolve = new Boolean(fields[5]).booleanValue();
                        boolean autoResolveAlerts = new Boolean(fields[6]).booleanValue();
                        TriggerTemplate.Match firingMatch = TriggerTemplate.Match.valueOf(fields[7]);
                        TriggerTemplate.Match autoResolveMatch = TriggerTemplate.Match.valueOf(fields[8]);
                        String[] notifiers = fields[9].split("\\|");

                        Trigger trigger = new Trigger(triggerId, name);
                        trigger.setEnabled(enabled);
                        trigger.setAutoDisable(autoDisable);
                        trigger.setAutoResolve(autoResolve);
                        trigger.setAutoResolveAlerts(autoResolveAlerts);
                        trigger.setDescription(description);
                        trigger.setFiringMatch(firingMatch);
                        trigger.setAutoResolveMatch(autoResolveMatch);
                        for (String notifier : notifiers) {
                            trigger.addAction(notifier);
                        }

                        addTrigger(trigger);
                        log.debugf("Init file - Inserting [%s]", trigger);
                    }
                }
            }
        } else {
            msgLog.warningFileNotFound("triggers.data");
        }
    }

    private void initConditions(File initFolder) throws Exception {
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
                    if (fields.length > 4) {
                        String triggerId = fields[0];
                        Trigger.Mode triggerMode = Trigger.Mode.valueOf(fields[1]);
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
                            String dataId = fields[5];
                            String operator = fields[6];
                            Double data2Multiplier = new Double(fields[7]).doubleValue();
                            String data2Id = fields[8];

                            CompareCondition newCondition = new CompareCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
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
            msgLog.warningFileNotFound("conditions.data");
        }
    }

    private void initCondition(Condition condition) throws Exception {
        Collection<Condition> conditions = getTriggerConditions(condition.getTriggerId(), condition.getTriggerMode());
        conditions.add(condition);
        setConditions(condition.getTriggerId(), condition.getTriggerMode(), conditions);
    }

    private void initDampenings(File initFolder) throws Exception {
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
                    if (fields.length == 6) {
                        String triggerId = fields[0];
                        Trigger.Mode triggerMode = Trigger.Mode.valueOf(fields[1]);
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
            msgLog.warningFileNotFound("dampening.data");
        }
    }

    private void initActions(File initFolder) throws Exception {
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
                    if (fields.length > 2) {
                        String actionId = fields[0];
                        String actionPlugin = fields[1];

                        Map<String, String> newAction = new HashMap<String, String>();
                        newAction.put("actionId", actionId);
                        newAction.put("actionPlugin", actionPlugin);

                        for (int i = 2; i < fields.length; i++) {
                            String property = fields[i];
                            String[] properties = property.split("=");
                            if (properties.length == 2) {
                                newAction.put(properties[0], properties[1]);
                            }
                        }
                        addAction(actionId, newAction);
                        log.debugf("Init file - Inserting [%s]", newAction);
                    }
                }
            }
        } else {
            msgLog.warningFileNotFound("actions.data");
        }
    }

    @Override
    public void addAction(String actionId, Map<String, String> properties)
            throws Exception {
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        String actionPlugin = properties.get("actionPlugin");
        if (actionPlugin == null) {
            throw new IllegalArgumentException("Action has not actionPlugin property");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (insertAction == null) {
            throw new RuntimeException("insertAction PreparedStatement is null");
        }

        try {
            session.execute(insertAction.bind(actionId, actionPlugin, properties));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void addTrigger(Trigger trigger) throws Exception {
        if (trigger == null || trigger.getId() == null || trigger.getId().isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (insertTrigger == null) {
            throw new RuntimeException("insertTrigger PreparedStatement is null");
        }
        try {
            session.execute(insertTrigger.bind(trigger.getName(), trigger.getDescription(),
                    trigger.isAutoDisable(), trigger.isAutoResolve(), trigger.isAutoResolveAlerts(),
                    trigger.getActions(), trigger.getFiringMatch().name(), trigger.getAutoResolveMatch().name(),
                    trigger.getId(), trigger.isEnabled()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeTrigger(String triggerId) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (deleteDampenings == null || deleteConditions == null || deleteTriggers == null) {
            throw new RuntimeException("delete*Triggers PreparedStatement is null");
        }
        try {
            session.execute(deleteDampenings.bind(triggerId));
            session.execute(deleteConditions.bind(triggerId));
            deleteTags(triggerId, null, null);
            session.execute(deleteTriggers.bind(triggerId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Trigger updateTrigger(Trigger trigger) throws Exception {
        if (trigger == null || trigger.getId() == null || trigger.getId().isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (updateTrigger == null) {
            throw new RuntimeException("updateTrigger PreparedStatement is null");
        }
        try {
            session.execute(updateTrigger.bind(trigger.getName(), trigger.getDescription(), trigger.isAutoDisable(),
                    trigger.isAutoResolve(), trigger.isAutoResolveAlerts(), trigger.getActions(),
                    trigger.getFiringMatch().name(), trigger.getAutoResolveMatch().name(), trigger.isEnabled(),
                    trigger.getId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(trigger.getId());
        }

        notifyListeners(DefinitionsEvent.EventType.TRIGGER_CHANGE);

        return trigger;
    }

    @Override
    public Trigger getTrigger(String triggerId) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectTrigger == null) {
            throw new RuntimeException("selectTrigger PreparedStatement is null");
        }
        Trigger trigger = null;
        try {
            ResultSet rsTrigger = session.execute(selectTrigger.bind(triggerId));
            Iterator<Row> itTrigger = rsTrigger.iterator();
            if (itTrigger.hasNext()) {
                Row row = itTrigger.next();
                trigger = new Trigger();
                trigger.setName(row.getString("name"));
                trigger.setDescription(row.getString("description"));
                trigger.setAutoDisable(row.getBool("autoDisable"));
                trigger.setAutoResolve(row.getBool("autoResolve"));
                trigger.setAutoResolveAlerts(row.getBool("autoResolveAlerts"));
                trigger.setActions(row.getSet("actions", String.class));
                trigger.setFiringMatch(TriggerTemplate.Match.valueOf(row.getString("firingMatch")));
                trigger.setAutoResolveMatch(TriggerTemplate.Match.valueOf(row.getString("autoResolveMatch")));
                trigger.setId(row.getString("id"));
                trigger.setEnabled(row.getBool("enabled"));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return trigger;
    }

    @Override
    public Collection<Trigger> getAllTriggers() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectAllTriggers == null) {
            throw new RuntimeException("selectAllTriggers PreparedStatement is null");
        }
        List<Trigger> triggers = new ArrayList<>();
        try {
            ResultSet rsTriggers = session.execute(selectAllTriggers.bind());
            Iterator<Row> itTriggers = rsTriggers.iterator();
            while (itTriggers.hasNext()) {
                Row row = itTriggers.next();
                Trigger trigger = new Trigger();
                trigger.setName(row.getString("name"));
                trigger.setDescription(row.getString("description"));
                trigger.setAutoDisable(row.getBool("autoDisable"));
                trigger.setAutoResolve(row.getBool("autoResolve"));
                trigger.setAutoResolveAlerts(row.getBool("autoResolveAlerts"));
                trigger.setActions(row.getSet("actions", String.class));
                trigger.setFiringMatch(TriggerTemplate.Match.valueOf(row.getString("firingMatch")));
                trigger.setAutoResolveMatch(TriggerTemplate.Match.valueOf(row.getString("autoResolveMatch")));
                trigger.setId(row.getString("id"));
                trigger.setEnabled(row.getBool("enabled"));
                triggers.add(trigger);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return triggers;
    }

    @Override
    public Trigger copyTrigger(String triggerId, Map<String, String> dataIdMap) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (dataIdMap == null || dataIdMap.isEmpty()) {
            throw new IllegalArgumentException("DataIdMap must be not null");
        }
        Trigger trigger = getTrigger(triggerId);
        if (trigger == null) {
            throw new IllegalArgumentException("Trigger not found for triggerId [" + triggerId + "]");
        }
        // ensure we have a 1-1 mapping for the dataId substitution
        Set<String> dataIdTokens = new HashSet<>();
        Collection<Condition> conditions = getTriggerConditions(triggerId, null);
        for (Condition c : conditions) {
            if (c instanceof CompareCondition) {
                dataIdTokens.add(((CompareCondition) c).getDataId());
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
        Collection<Dampening> dampenings = getTriggerDampenings(triggerId, null);

        Trigger newTrigger = new Trigger(trigger.getName());
        newTrigger.setName(trigger.getName());
        newTrigger.setDescription(trigger.getDescription());
        newTrigger.setFiringMatch(trigger.getFiringMatch());
        newTrigger.setAutoResolveMatch(trigger.getAutoResolveMatch());
        newTrigger.setActions(trigger.getActions());

        addTrigger(newTrigger);

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
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(((CompareCondition) c)
                        .getDataId()),
                        ((CompareCondition) c).getOperator(),
                        ((CompareCondition) c).getData2Multiplier(),
                        dataIdMap.get(((CompareCondition) c).getData2Id()));

            } else if (c instanceof StringCondition) {
                newCondition = new StringCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((StringCondition) c).getOperator(), ((StringCondition) c).getPattern(),
                        ((StringCondition) c).isIgnoreCase());
            }

            addCondition(newTrigger.getId(), newCondition.getTriggerMode(), newCondition);
        }

        for (Dampening d : dampenings) {
            Dampening newDampening = new Dampening(newTrigger.getId(), d.getTriggerMode(), d.getType(),
                    d.getEvalTrueSetting(), d.getEvalTotalSetting(), d.getEvalTimeSetting());

            addDampening(newDampening);
        }

        return newTrigger;
    }

    @Override
    public Dampening addDampening(Dampening dampening) throws Exception {
        if (dampening == null || dampening.getTriggerId() == null || dampening.getTriggerId().isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (insertDampening == null) {
            throw new RuntimeException("insertDampening PreparedStatement is null");
        }

        try {
            session.execute(insertDampening.bind(dampening.getTriggerId(), dampening.getTriggerMode().name(),
                    dampening.getType().name(), dampening.getEvalTrueSetting(), dampening.getEvalTotalSetting(),
                    dampening.getEvalTimeSetting(), dampening.getDampeningId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(dampening.getTriggerId());
        }

        notifyListeners(DefinitionsEvent.EventType.DAMPENING_CHANGE);

        return dampening;
    }

    @Override
    public void removeDampening(String dampeningId) throws Exception {
        if (dampeningId == null || dampeningId.isEmpty()) {
            throw new IllegalArgumentException("dampeningId must not be null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (deleteDampeningId == null) {
            throw new RuntimeException("deleteDampeningId PreparedStatement is null");
        }

        Dampening dampening = getDampening(dampeningId);
        if (dampening == null) {
            log.debugf("Ignoring removeDampening(" + dampeningId + "), the Dampening does not exist.");
            return;
        }

        try {
            session.execute(deleteDampeningId.bind(dampening.getTriggerId(),
                    dampening.getTriggerMode().name(), dampeningId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(dampening.getTriggerId());
        }

        notifyListeners(DefinitionsEvent.EventType.DAMPENING_CHANGE);
    }

    @Override
    public Dampening updateDampening(Dampening dampening) throws Exception {
        if (dampening == null || dampening.getDampeningId() == null || dampening.getDampeningId().isEmpty()) {
            throw new IllegalArgumentException("DampeningId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (updateDampeningId == null) {
            throw new RuntimeException("updateDampeningId PreparedStatement is null");
        }

        try {
            session.execute(updateDampeningId.bind(dampening.getType().name(), dampening.getEvalTrueSetting(),
                    dampening.getEvalTotalSetting(), dampening.getEvalTimeSetting(), dampening.getTriggerId(),
                    dampening.getTriggerMode().name(), dampening.getDampeningId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsService) {
            alertsService.reloadTrigger(dampening.getTriggerId());
        }

        notifyListeners(DefinitionsEvent.EventType.DAMPENING_CHANGE);

        return dampening;
    }

    @Override
    public Dampening getDampening(String dampeningId) throws Exception {
        if (dampeningId == null || dampeningId.isEmpty()) {
            throw new IllegalArgumentException("dampeningId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectDampeningId == null) {
            throw new RuntimeException("selectDampeningId PreparedStatement is null");
        }

        Dampening dampening = null;
        try {
            ResultSet rsDampening = session.execute(selectDampeningId.bind(dampeningId));
            Iterator<Row> itDampening = rsDampening.iterator();
            if (itDampening.hasNext()) {
                Row row = itDampening.next();
                dampening = new Dampening();
                dampening.setTriggerId(row.getString("triggerId"));
                dampening.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                dampening.setType(Dampening.Type.valueOf(row.getString("type")));
                dampening.setEvalTrueSetting(row.getInt("evalTrueSetting"));
                dampening.setEvalTotalSetting(row.getInt("evalTotalSetting"));
                dampening.setEvalTimeSetting(row.getLong("evalTimeSetting"));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return dampening;
    }

    @Override
    public Collection<Dampening> getTriggerDampenings(String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectTriggerDampenings == null || selectTriggerDampeningsMode == null) {
            throw new RuntimeException("selectTriggerDampenings* PreparedStatement is null");
        }
        List<Dampening> dampenings = new ArrayList<>();
        try {
            ResultSet rsDampenings;
            if (triggerMode == null) {
                rsDampenings = session.execute(selectTriggerDampenings.bind(triggerId));
            } else {
                rsDampenings = session.execute(selectTriggerDampeningsMode.bind(triggerId, triggerMode.name()));
            }
            mapDampenings(rsDampenings, dampenings);
        } catch(Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return dampenings;
    }

    @Override
    public Collection<Dampening> getAllDampenings() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectAllDampenings == null) {
            throw new RuntimeException("selectAllDampenings PreparedStatement is null");
        }
        List<Dampening> dampenings = new ArrayList<>();
        try {
            ResultSet rsDampenings = session.execute(selectAllDampenings.bind());
            mapDampenings(rsDampenings, dampenings);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return dampenings;
    }

    private void mapDampenings(ResultSet rsDampenings, List<Dampening> dampenings) throws Exception {
        Iterator<Row> itDampenings = rsDampenings.iterator();
        while (itDampenings.hasNext()) {
            Row row = itDampenings.next();
            Dampening dampening = new Dampening();
            dampening.setTriggerId(row.getString("triggerId"));
            dampening.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
            dampening.setType(Dampening.Type.valueOf(row.getString("type")));
            dampening.setEvalTrueSetting(row.getInt("evalTrueSetting"));
            dampening.setEvalTotalSetting(row.getInt("evalTotalSetting"));
            dampening.setEvalTimeSetting(row.getLong("evalTimeSetting"));
            dampenings.add(dampening);
        }
    }

    @Override
    public Collection<Condition> addCondition(String triggerId, Trigger.Mode triggerMode, Condition condition)
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (insertAvailabilityCondition == null
                || insertCompareCondition == null
                || insertStringCondition == null
                || insertThresholdCondition == null
                || insertThresholdRangeCondition == null) {
            throw new RuntimeException("insert*Condition PreparedStatement is null");
        }
        // Get rid of the prior condition set
        removeConditions(triggerId, triggerMode);

        // Now add the new condition set
        try {
            List<String> dataIds = new ArrayList<>(2);

            int i = 0;
            for (Condition cond : conditions) {
                cond.setTriggerId(triggerId);
                cond.setTriggerMode(triggerMode);
                cond.setConditionSetSize(conditions.size());
                cond.setConditionSetIndex(++i);

                dataIds.add(cond.getDataId());

                if (cond instanceof AvailabilityCondition) {

                    AvailabilityCondition aCond = (AvailabilityCondition)cond;
                    session.execute(insertAvailabilityCondition.bind(aCond.getTriggerId(),
                            aCond.getTriggerMode().name(), aCond.getConditionSetSize(), aCond.getConditionSetIndex(),
                            aCond.getConditionId(), aCond.getDataId(), aCond.getOperator().name()));

                } else if (cond instanceof CompareCondition) {

                    CompareCondition cCond = (CompareCondition)cond;
                    dataIds.add(cCond.getData2Id());
                    session.execute(insertCompareCondition.bind(cCond.getTriggerId(), cCond.getTriggerMode().name(),
                            cCond.getConditionSetSize(), cCond.getConditionSetIndex(), cCond.getConditionId(),
                            cCond.getDataId(), cCond.getOperator().name(), cCond.getData2Id(),
                            cCond.getData2Multiplier()));

                } else if (cond instanceof StringCondition) {

                    StringCondition sCond = (StringCondition)cond;
                    session.execute(insertStringCondition.bind(sCond.getTriggerId(), sCond.getTriggerMode().name(),
                            sCond.getConditionSetSize(), sCond.getConditionSetIndex(), sCond.getConditionId(),
                            sCond.getDataId(), sCond.getOperator().name(), sCond.getPattern(), sCond.isIgnoreCase()));

                } else if (cond instanceof ThresholdCondition) {

                    ThresholdCondition tCond = (ThresholdCondition)cond;
                    session.execute(insertThresholdCondition.bind(tCond.getTriggerId(), tCond.getTriggerMode().name(),
                            tCond.getConditionSetSize(), tCond.getConditionSetIndex(), tCond.getConditionId(),
                            tCond.getDataId(), tCond.getOperator().name(), tCond.getThreshold()));

                } else if (cond instanceof ThresholdRangeCondition) {

                    ThresholdRangeCondition rCond = (ThresholdRangeCondition)cond;
                    session.execute(insertThresholdRangeCondition.bind(rCond.getTriggerId(),
                            rCond.getTriggerMode().name(), rCond.getConditionSetSize(), rCond.getConditionSetIndex(),
                            rCond.getConditionId(), rCond.getDataId(), rCond.getOperatorLow().name(),
                            rCond.getOperatorHigh().name(), rCond.getThresholdLow(), rCond.getThresholdHigh(),
                            rCond.isInRange()));

                }

                // generate the automatic dataId tags for search
                for (String dataId : dataIds) {
                    insertTag(cond.getTriggerId(), "dataId", dataId, false);
                }
                dataIds.clear();
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && alertsService != null) {
            alertsService.reloadTrigger(triggerId);
        }

        notifyListeners(DefinitionsEvent.EventType.CONDITION_CHANGE);

        return conditions;
    }

    private void insertTag(String triggerId, String category, String name, boolean visible)
            throws Exception {

        // If the desired Tag already exists just return
        if (!getTags(triggerId, category, name).isEmpty()) {
            return;
        }

        session.execute(insertTag.bind(triggerId, category, name, visible));
        insertTriggerByTagIndex(category, name, triggerId);
    }

    private void insertTriggerByTagIndex(String category, String name, String triggerId) {
        Set<String> triggers = getTriggersByTags(category, name);
        triggers = new HashSet<>(triggers);
        if (triggers.isEmpty()) {
            triggers.add(triggerId);
            session.execute(insertTagsTriggers.bind(category, name, triggers));
        } else {
            if (!triggers.contains(triggerId)) {
                triggers.add(triggerId);
                session.execute(updateTagsTriggers.bind(triggers, category, name));
            }
        }
    }

    private Set<String> getTriggersByTags(String category, String name) {
        Set triggerTags = new HashSet<>();

        ResultSet rsTriggersTags = session.execute(selectTagsTriggers.bind(category, name));
        Iterator<Row> itTriggersTags = rsTriggersTags.iterator();
        if (itTriggersTags.hasNext()) {
            Row row = itTriggersTags.next();
            triggerTags = row.getSet("triggers", String.class);
        }
        return triggerTags;
    }

    private List<Tag> getTags(String triggerId, String category, String name)
            throws Exception {
        StringBuilder sTags = new StringBuilder("SELECT triggerId, category, name, visible ")
                .append("FROM ").append(keyspace).append(".tags ")
                .append("WHERE triggerId = '").append(triggerId).append("' ");
        if (category != null && !category.trim().isEmpty()) {
            sTags.append("AND category = '").append(category).append("' ");
        }
        if (name != null && !name.trim().isEmpty()) {
            sTags.append("AND name = '").append(name).append("' ");
        }
        sTags.append("ORDER BY category, name");

        List<Tag> tags = new ArrayList<>();
        ResultSet rsTags = session.execute(sTags.toString());
        Iterator<Row> itTags = rsTags.iterator();
        while (itTags.hasNext()) {
            Row row = itTags.next();
            Tag tag = new Tag();
            tag.setTriggerId(row.getString("triggerId"));
            tag.setCategory(row.getString("category"));
            tag.setName(row.getString("name"));
            tag.setVisible(row.getBool("visible"));
            tags.add(tag);
        }

        return tags;
    }

    private void notifyListeners(DefinitionsEvent.EventType eventType) {
        DefinitionsEvent de = new DefinitionsEvent(eventType);
        for (DefinitionsListener dl : listeners) {
            log.debugf("Notified Listener %s", eventType.name());
            dl.onChange(de);
        }
    }

    private void removeConditions(String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must not be null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must not be null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (deleteConditionsMode == null) {
            throw new RuntimeException("deleteConditionsMode PreparedStatement is null");
        }
        try {
            session.execute(deleteConditionsMode.bind(triggerId, triggerMode.name()));

            // if removing conditions remove the automatically-added dataId tags
            deleteTags(triggerId, "dataId", null);

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private void deleteTags(String triggerId, String category, String name) throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        StringBuilder sTags = new StringBuilder("DELETE FROM ").append(keyspace).append(".tags ")
                .append("WHERE triggerId = '").append(triggerId).append("' ");
        if (category != null && !category.trim().isEmpty()) {
            sTags.append(" AND category = '").append(category).append("' ");
        }
        if (name != null && !name.trim().isEmpty()) {
            sTags.append(" AND name = '").append(name).append("' ");
        }
        try {
            deleteTriggerByTagIndex(triggerId, category, name);
            session.execute(sTags.toString());
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private void deleteTriggerByTagIndex(String triggerId, String category, String name) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            return;
        }
        List<Tag> tags = null;
        if (category == null || name == null) {
            tags = getTriggerTags(triggerId, category);
        } else {
            tags = new ArrayList<Tag>();
            Tag singleTag = new Tag();
            singleTag.setCategory(category);
            singleTag.setName(name);
            tags.add(singleTag);
        }

        for (Tag tag : tags) {
            Set<String> triggers = getTriggersByTags(tag.getCategory(), tag.getName());
            if (triggers.size() > 1) {
                Set<String> updateTriggers = new HashSet<>(triggers);
                updateTriggers.remove(triggerId);
                session.execute(updateTagsTriggers.bind(triggers, tag.getCategory(), tag.getName()));
            } else {
                session.execute(deleteTagsTriggers.bind(tag.getCategory(), tag.getName()));
            }
        }
    }

    @Override
    public Condition getCondition(String conditionId) throws Exception {
        if (conditionId == null || conditionId.isEmpty()) {
            throw new IllegalArgumentException("conditionId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectConditionId == null) {
            throw new RuntimeException("selectConditionId PreparedStatement is null");
        }
        Condition condition = null;
        try {
            ResultSet rsCondition = session.execute(selectConditionId.bind(conditionId));
            Iterator<Row> itCondition = rsCondition.iterator();
            if (itCondition.hasNext()) {
                Row row = itCondition.next();
                condition = mapCondition(row);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return condition;
    }

    @Override
    public Collection<Condition> getTriggerConditions(String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("triggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectTriggerConditions == null || selectTriggerConditionsTriggerMode == null) {
            throw new RuntimeException("selectTriggerConditions* PreparedStatement is null");
        }
        List<Condition> conditions = new ArrayList<>();
        try {
            ResultSet rsConditions;
            if (triggerMode == null) {
                rsConditions = session.execute(selectTriggerConditions.bind(triggerId));
            } else {
                rsConditions = session.execute(selectTriggerConditionsTriggerMode.bind(triggerId, triggerMode.name()));
            }
            mapConditions(rsConditions, conditions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return conditions;
    }

    @Override
    public Collection<Condition> getAllConditions() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectAllConditions == null) {
            throw new RuntimeException("selectAllConditions PreparedStatement is null");
        }
        List<Condition> conditions = new ArrayList<Condition>();
        try {
            ResultSet rsConditions = session.execute(selectAllConditions.bind());
            mapConditions(rsConditions, conditions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return conditions;
    }

    private void mapConditions(ResultSet rsConditions, List<Condition> conditions) throws Exception {
        Iterator<Row> itConditions = rsConditions.iterator();
        while (itConditions.hasNext()) {
            Row row = itConditions.next();
            Condition condition = mapCondition(row);
            if (condition != null) {
                conditions.add(condition);
            }
        }
    }

    private Condition mapCondition(Row row) throws Exception {
        Condition condition = null;
        String type = row.getString("type");
        if (type != null && !type.isEmpty()) {
            if (type.equals(Condition.Type.AVAILABILITY.name())) {
                AvailabilityCondition aCondition = new AvailabilityCondition();
                aCondition.setTriggerId(row.getString("triggerId"));
                aCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                aCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                aCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                aCondition.setDataId(row.getString("dataId"));
                aCondition.setOperator(AvailabilityCondition.Operator.valueOf(row.getString("operator")));
                condition = aCondition;
            } else if (type.equals(Condition.Type.COMPARE.name())) {
                CompareCondition cCondition = new CompareCondition();
                cCondition.setTriggerId(row.getString("triggerId"));
                cCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                cCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                cCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                cCondition.setDataId(row.getString("dataId"));
                cCondition.setOperator(CompareCondition.Operator.valueOf(row.getString("operator")));
                cCondition.setData2Id(row.getString("data2Id"));
                cCondition.setData2Multiplier(row.getDouble("data2Multiplier"));
                condition = cCondition;
            } else if (type.equals(Condition.Type.STRING.name())) {
                StringCondition sCondition = new StringCondition();
                sCondition.setTriggerId(row.getString("triggerId"));
                sCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                sCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                sCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                sCondition.setDataId(row.getString("dataId"));
                sCondition.setOperator(StringCondition.Operator.valueOf(row.getString("operator")));
                sCondition.setPattern(row.getString("pattern"));
                sCondition.setIgnoreCase(row.getBool("ignoreCase"));
                condition = sCondition;
            } else if (type.equals(Condition.Type.THRESHOLD.name())) {
                ThresholdCondition tCondition = new ThresholdCondition();
                tCondition.setTriggerId(row.getString("triggerId"));
                tCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                tCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                tCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                tCondition.setDataId(row.getString("dataId"));
                tCondition.setOperator(ThresholdCondition.Operator.valueOf(row.getString("operator")));
                tCondition.setThreshold(row.getDouble("threshold"));
                condition = tCondition;
            } else if (type.equals(Condition.Type.RANGE.name())) {
                ThresholdRangeCondition rCondition = new ThresholdRangeCondition();
                rCondition.setTriggerId(row.getString("triggerId"));
                rCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                rCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                rCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                rCondition.setDataId(row.getString("dataId"));
                rCondition.setOperatorLow(ThresholdRangeCondition.Operator.valueOf(row.getString
                        ("operatorLow")));
                rCondition.setOperatorHigh(ThresholdRangeCondition.Operator.valueOf(row.getString
                        ("operatorHigh")));
                rCondition.setThresholdLow(row.getDouble("thresholdLow"));
                rCondition.setThresholdHigh(row.getDouble("thresholdHigh"));
                rCondition.setInRange(row.getBool("inRange"));
                condition = rCondition;
            } else {
                log.debugf("Wrong condition type found: " + type);
            }
        } else {
            log.debugf("Wrong condition type: null or empty");
        }
        return condition;
    }

    @Override
    public void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (insertActionPlugin == null) {
            throw new RuntimeException("insertActionPlugin PreparedStatement is null");
        }
        try {
            session.execute(insertActionPlugin.bind(actionPlugin, properties));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeActionPlugin(String actionPlugin) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (deleteActionPlugin == null) {
            throw new RuntimeException("deleteActionPlugin PreparedStatement is null");
        }
        try {
            session.execute(deleteActionPlugin.bind(actionPlugin));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (updateActionPlugin == null) {
            throw new RuntimeException("updateActionPlugin PreparedStatement is null");
        }
        try {
            session.execute(updateActionPlugin.bind(properties, actionPlugin));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Collection<String> getActionPlugins() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectActionPlugins == null) {
            throw new RuntimeException("selectActionPlugins PreparedStatement is null");
        }
        ArrayList<String> actionPlugins = new ArrayList<>();
        try {
            ResultSet rsActionPlugins = session.execute(selectActionPlugins.bind());
            Iterator<Row> itActionPlugins = rsActionPlugins.iterator();
            while (itActionPlugins.hasNext()) {
                Row row = itActionPlugins.next();
                actionPlugins.add(row.getString("actionPlugin"));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return actionPlugins;
    }

    @Override
    public Set<String> getActionPlugin(String actionPlugin) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectActionPlugin == null) {
            throw new RuntimeException("selectActionPlugin PreparedStatement is null");
        }
        Set<String> properties = null;
        try {
            ResultSet rsActionPlugin = session.execute(selectActionPlugin.bind(actionPlugin));
            Iterator<Row> itActionPlugin = rsActionPlugin.iterator();
            if (itActionPlugin.hasNext()) {
                Row row = itActionPlugin.next();
                properties = row.getSet("properties", String.class);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return properties;
    }

    @Override
    public void removeAction(String actionId) throws Exception {
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (deleteAction == null) {
            throw new RuntimeException("deleteAction PreparedStatement is null");
        }
        try {
            session.execute(deleteAction.bind(actionId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void updateAction(String actionId, Map<String, String> properties) throws Exception {
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("ActionId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (updateAction == null) {
            throw new RuntimeException("updateAction PreparedStatement is null");
        }
        try {
            session.execute(updateAction.bind(properties, actionId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Collection<String> getAllActions() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectAllActions == null) {
            throw new RuntimeException("selectAllActions PreparedStatement is null");
        }
        List<String> actions = new ArrayList();
        try {
            ResultSet rsActions = session.execute(selectAllActions.bind());
            Iterator<Row> itActions = rsActions.iterator();
            while (itActions.hasNext()) {
                Row row = itActions.next();
                actions.add(row.getString("actionId"));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return actions;
    }

    @Override
    public Collection<String> getActions(String actionPlugin) throws Exception {
        if (actionPlugin == null || actionPlugin.isEmpty()) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectActionsPlugin == null) {
            throw new RuntimeException("selectActionsPlugin PreparedStatement is null");
        }
        ArrayList<String> actions = new ArrayList<>();
        try {
            ResultSet rsActions = session.execute(selectActionsPlugin.bind(actionPlugin));
            Iterator<Row> itActions = rsActions.iterator();
            while (itActions.hasNext()) {
                Row row = itActions.next();
                actions.add(row.getString("actionId"));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return actions;
    }

    @Override
    public Map<String, String> getAction(String actionId) throws Exception {
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        if (selectAction == null) {
            throw new RuntimeException("selectAction PreparedStatement is null");
        }
        Map<String, String> properties = null;
        try {
            ResultSet rsAction = session.execute(selectAction.bind(actionId));
            Iterator<Row> itAction = rsAction.iterator();
            if (itAction.hasNext()) {
                Row row = itAction.next();
                properties = row.getMap("properties", String.class, String.class);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return properties;
    }

    @Override
    public void addTag(Tag tag) throws Exception {
        if (tag == null) {
            throw new IllegalArgumentException("Tag must be not null");
        }
        if (tag.getTriggerId() == null || tag.getTriggerId().isEmpty()) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }
        if (tag.getName() == null || tag.getName().isEmpty()) {
            throw new IllegalArgumentException("Tag Name must be not null or empty");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        try {
            insertTag(tag.getTriggerId(), tag.getCategory(), tag.getName(), tag.isVisible());
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeTags(String triggerId, String category, String name) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }
        try {
            deleteTags(triggerId, category, name);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Tag> getTriggerTags(String triggerId, String category) throws Exception {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }

        List<Tag> tags;
        try {
            tags = getTags(triggerId, category, null);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return tags;
    }

    @Override
    public void registerListener(DefinitionsListener listener) {
        listeners.add(listener);
    }
}
