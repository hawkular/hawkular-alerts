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
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.EJB;
import javax.ejb.Singleton;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Tag;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsEvent.EventType;
import org.hawkular.alerts.api.services.DefinitionsListener;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;

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
    private ObjectMapper objectMapper = new ObjectMapper();
    private Session session;
    private String keyspace;
    private boolean initialized = false;

    private Map<DefinitionsListener, Set<EventType>> listeners = new HashMap<>();

    @EJB
    AlertsEngine alertsEngine;

    public CassDefinitionsServiceImpl() {
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
            if (this.keyspace == null) {
                this.keyspace = AlertProperties.getProperty(CASSANDRA_KEYSPACE, "hawkular_alerts");
            }

            if (session == null) {
                session = CassCluster.getSession();
            }

            /*
                Initial data works here because we are in a singleton
             */
            initialData();

        } catch (Throwable t) {
            msgLog.errorCannotInitializeDefinitionsService(t.getMessage());
            t.printStackTrace();
        }
    }

    // Note, the cassandra cluster shutdown should be performed only once and is performed here because
    // this is a singleton bean.  If it becomes Stateless this login will need to be moved.
    @PreDestroy
    public void shutdown() {
        CassCluster.shutdown();
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
        File triggersFile = new File(fFolder, "triggers-data.json");
        if (triggersFile.exists() && triggersFile.isFile()) {
            Map<String, Object> triggers = objectMapper.readValue(triggersFile, Map.class);
            if (triggers != null && !triggers.isEmpty() && triggers.get("triggers") != null) {
                List<Map<String, Object>> aTriggers = (List<Map<String, Object>>)triggers.get("triggers");
                for (Map<String, Object> t : aTriggers) {
                    String tenantId = (String)t.get("tenantId");
                    String triggerId = (String)t.get("triggerId");
                    boolean enabled = (Boolean)t.get("enabled");
                    String name = (String)t.get("name");
                    String description = (String)t.get("description");
                    boolean autoDisable = (Boolean)t.get("autoDisable");
                    boolean autoEnable = (Boolean)t.get("autoEnable");
                    boolean autoResolve = (Boolean)t.get("autoResolve");
                    boolean autoResolveAlerts = (Boolean)t.get("autoResolveAlerts");
                    Severity severity = Severity.valueOf((String)t.get("severity"));
                    TriggerTemplate.Match firingMatch = TriggerTemplate.Match.valueOf((String)t.get("firingMatch"));
                    TriggerTemplate.Match autoResolveMatch = TriggerTemplate.Match
                            .valueOf((String)t.get("autoResolveMatch"));
                    List<Map<String, String>> actions = (List<Map<String, String>>)t.get("actions");
                    Map<String, String> context = (Map<String, String>)t.get("context");

                    Trigger trigger = new Trigger(triggerId, name);
                    trigger.setEnabled(enabled);
                    trigger.setAutoDisable(autoDisable);
                    trigger.setAutoEnable(autoEnable);
                    trigger.setAutoResolve(autoResolve);
                    trigger.setAutoResolveAlerts(autoResolveAlerts);
                    trigger.setSeverity(severity);
                    trigger.setDescription(description);
                    trigger.setFiringMatch(firingMatch);
                    trigger.setAutoResolveMatch(autoResolveMatch);
                    trigger.setTenantId(tenantId);
                    for (Map<String, String> action : actions) {
                        trigger.addAction(action.get("actionPlugin"), action.get("actionId"));
                    }
                    trigger.setContext(context);
                    addTrigger(tenantId, trigger);
                    log.debugf("Init file - Inserting [%s]", trigger);
                }
            }
        } else {
            msgLog.warningFileNotFound("triggers-data.json");
        }
    }

    private void initConditions(File initFolder) throws Exception {
        File conditionsFile = new File(initFolder, "conditions-data.json");
        if (conditionsFile.exists() && conditionsFile.isFile()) {
            Map<String, Object> conditions = objectMapper.readValue(conditionsFile, Map.class);
            if (conditions != null && !conditions.isEmpty() && conditions.get("conditions") != null) {
                List<Map<String, Object>> aConditions = (List<Map<String, Object>>)conditions.get("conditions");
                for (Map<String, Object> c : aConditions) {
                    String tenantId = (String)c.get("tenantId");
                    String triggerId = (String)c.get("triggerId");
                    Trigger.Mode triggerMode = Trigger.Mode.valueOf((String)c.get("triggerMode"));
                    int conditionSetSize = (Integer)c.get("conditionSetSize");
                    int conditionSetIndex = (Integer)c.get("conditionSetIndex");
                    String type = (String)c.get("type");
                    if (type != null && !type.isEmpty() && type.equals("threshold")) {
                        String dataId = (String)c.get("dataId");
                        String operator = (String)c.get("operator");
                        Double threshold = (Double)c.get("threshold");

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
                    if (type != null && !type.isEmpty() && type.equals("range")) {
                        String dataId = (String)c.get("dataId");
                        String operatorLow = (String)c.get("operatorLow");
                        String operatorHigh = (String)c.get("operatorHigh");
                        Double thresholdLow = (Double)c.get("thresholdLow");
                        Double thresholdHigh = (Double)c.get("thresholdHigh");
                        boolean inRange = (Boolean)c.get("inRange");

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
                    if (type != null && !type.isEmpty() && type.equals("compare")) {
                        String dataId = (String)c.get("dataId");
                        String operator = (String)c.get("operator");
                        Double data2Multiplier = (Double)c.get("data2Multiplier");
                        String data2Id = (String)c.get("data2Id");

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
                    if (type != null && !type.isEmpty() && type.equals("string")) {
                        String dataId = (String)c.get("dataId");
                        String operator = (String)c.get("operator");
                        String pattern = (String)c.get("pattern");
                        boolean ignoreCase = (Boolean)c.get("ignoreCase");

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
                    if (type != null && !type.isEmpty() && type.equals("availability")) {
                        String dataId = (String)c.get("dataId");
                        String operator = (String)c.get("operator");

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
        } else {
            msgLog.warningFileNotFound("conditions-data.json");
        }
    }

    private void initCondition(Condition condition) throws Exception {
        Collection<Condition> conditions = getTriggerConditions(condition.getTenantId(), condition.getTriggerId(),
                condition.getTriggerMode());
        conditions.add(condition);
        setConditions(condition.getTenantId(), condition.getTriggerId(), condition.getTriggerMode(), conditions);
    }

    private void initDampenings(File initFolder) throws Exception {
        File dampeningFile = new File(initFolder, "dampening-data.json");
        if (dampeningFile.exists() && dampeningFile.isFile()) {
            Map<String, Object> dampenings = objectMapper.readValue(dampeningFile, Map.class);
            if (dampenings != null && !dampenings.isEmpty() && dampenings.get("dampenings") != null) {
                List<Map<String, Object>> aDampenings = (List<Map<String, Object>>)dampenings.get("dampenings");
                for (Map<String, Object> d : aDampenings) {
                    String tenantId = (String)d.get("tenantId");
                    String triggerId = (String)d.get("triggerId");
                    Trigger.Mode triggerMode = Trigger.Mode.valueOf((String)d.get("triggerMode"));
                    String type = (String)d.get("type");
                    int evalTrueSetting = (Integer)d.get("evalTrueSetting");
                    int evalTotalSetting = (Integer)d.get("evalTotalSetting");
                    long evalTimeSetting = (Integer)d.get("evalTimeSetting");

                    Dampening newDampening = new Dampening(triggerId, triggerMode, Dampening.Type.valueOf(type),
                            evalTrueSetting, evalTotalSetting, evalTimeSetting);

                    addDampening(tenantId, newDampening);
                    log.debugf("Init file - Inserting [%s]", newDampening);
                }
            }
        } else {
            msgLog.warningFileNotFound("dampening-data.json");
        }
    }

    private void initActions(File initFolder) throws Exception {
        File actionsFile = new File(initFolder, "actions-data.json");
        if (actionsFile.exists() && actionsFile.isFile()) {
            Map<String, Object> actions = objectMapper.readValue(actionsFile, Map.class);
            if (actions != null && !actions.isEmpty() && actions.get("actions") != null) {
                List<Map<String, Object>> aActions = (List)actions.get("actions");
                for (Map<String, Object> a : aActions) {
                    Map<String, String> newAction = new HashMap<>();
                    String tenantId = (String)a.get("tenantId");
                    newAction.put("tenantId", tenantId);
                    String actionPlugin = (String)a.get("actionPlugin");
                    newAction.put("actionPlugin", actionPlugin);
                    String actionId = (String)a.get("actionId");
                    newAction.put("actionId", actionId);
                    Map<String, String> properties = (Map<String, String>)a.get("properties");
                    newAction.putAll(properties);
                    addAction(tenantId, actionPlugin, actionId, newAction);
                    log.debugf("Init file - Inserting [%s]", newAction);
                }
            }
        } else {
            msgLog.warningFileNotFound("actions-data.json");
        }
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
            throw new IllegalArgumentException("ActionId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        properties.put("actionId", actionId);
        properties.put("actionPlugin", actionPlugin);
        properties.put("tenantId", tenantId);

        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertAction = CassStatement.get(session, CassStatement.INSERT_ACTION);
        if (insertAction == null) {
            throw new RuntimeException("insertAction PreparedStatement is null");
        }

        try {
            session.execute(insertAction.bind(tenantId, actionPlugin, actionId, properties));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
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
        checkTenantId(tenantId, trigger);
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertTrigger = CassStatement.get(session, CassStatement.INSERT_TRIGGER);
        if (insertTrigger == null) {
            throw new RuntimeException("insertTrigger PreparedStatement is null");
        }

        try {
            session.execute(insertTrigger.bind(trigger.getName(), trigger.getDescription(),
                    trigger.isAutoDisable(), trigger.isAutoEnable(), trigger.isAutoResolve(),
                    trigger.isAutoResolveAlerts(), trigger.getSeverity().name(),
                    trigger.getFiringMatch().name(), trigger.getAutoResolveMatch().name(),
                    trigger.getId(), trigger.isEnabled(), trigger.getTenantId(), trigger.getContext()));

            insertTriggerActions(trigger);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        notifyListeners(DefinitionsEvent.EventType.TRIGGER_CREATE);
    }

    private void insertTriggerActions(Trigger trigger) throws Exception {
        PreparedStatement insertTriggerActions = CassStatement.get(session, CassStatement.INSERT_TRIGGER_ACTIONS);
        if (insertTriggerActions == null) {
            throw new RuntimeException("insertTriggerActions PreparedStatement is null");
        }
        if (trigger.getActions() != null) {
            List<ResultSetFuture> futures = trigger.getActions().keySet().stream()
                    .filter(actionPlugin -> trigger.getActions().get(actionPlugin) != null &&
                            !trigger.getActions().get(actionPlugin).isEmpty())
                    .map(actionPlugin -> session.executeAsync(insertTriggerActions.bind(trigger.getTenantId(),
                            trigger.getId(), actionPlugin, trigger.getActions().get(actionPlugin))))
                    .collect(Collectors.toList());
            Futures.allAsList(futures).get();
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement deleteDampenings = CassStatement.get(session, CassStatement.DELETE_DAMPENINGS);
        PreparedStatement deleteConditions = CassStatement.get(session, CassStatement.DELETE_CONDITIONS);
        PreparedStatement deleteTrigger = CassStatement.get(session, CassStatement.DELETE_TRIGGER);
        if (deleteDampenings == null || deleteConditions == null || deleteTrigger == null) {
            throw new RuntimeException("delete*Triggers PreparedStatement is null");
        }
        try {
            deleteTags(tenantId, triggerId, null, null);
            List<ResultSetFuture> futures = new ArrayList<>();
            futures.add(session.executeAsync(deleteDampenings.bind(tenantId, triggerId)));
            futures.add(session.executeAsync(deleteConditions.bind(tenantId, triggerId)));
            futures.add(session.executeAsync(deleteTrigger.bind(tenantId, triggerId)));
            Futures.allAsList(futures).get();
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        notifyListeners(DefinitionsEvent.EventType.TRIGGER_REMOVE);
    }

    @Override
    public Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(trigger)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        checkTenantId(tenantId, trigger);
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement updateTrigger = CassStatement.get(session, CassStatement.UPDATE_TRIGGER);
        if (updateTrigger == null) {
            throw new RuntimeException("updateTrigger PreparedStatement is null");
        }
        try {
            session.execute(updateTrigger.bind(trigger.getName(), trigger.getDescription(), trigger.isAutoDisable(),
                    trigger.isAutoEnable(), trigger.isAutoResolve(), trigger.isAutoResolveAlerts(),
                    trigger.getSeverity().name(), trigger.getFiringMatch().name(),
                    trigger.getAutoResolveMatch().name(), trigger.isEnabled(), trigger.getTenantId(),
                    trigger.getId()));
            deleteTriggerActions(trigger);
            insertTriggerActions(trigger);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(trigger.getTenantId(), trigger.getId());
        }

        notifyListeners(DefinitionsEvent.EventType.TRIGGER_UPDATE);

        return trigger;
    }

    private void deleteTriggerActions(Trigger trigger) throws Exception {
        PreparedStatement deleteTriggerActions = CassStatement.get(session, CassStatement.DELETE_TRIGGER_ACTIONS);
        if (deleteTriggerActions == null) {
            throw new RuntimeException("updateTrigger PreparedStatement is null");
        }
        session.execute(deleteTriggerActions.bind(trigger.getTenantId(), trigger.getId()));
    }

    @Override
    public Trigger getTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectTrigger = CassStatement.get(session, CassStatement.SELECT_TRIGGER);
        if (selectTrigger == null) {
            throw new RuntimeException("selectTrigger PreparedStatement is null");
        }
        Trigger trigger = null;
        try {
            ResultSet rsTrigger = session.execute(selectTrigger.bind(tenantId, triggerId));
            Iterator<Row> itTrigger = rsTrigger.iterator();
            if (itTrigger.hasNext()) {
                Row row = itTrigger.next();
                trigger = mapTrigger(row);
                selectTriggerActions(trigger);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return trigger;
    }

    // TODO: This fetch perform cross-tenant fetch and may be inefficient at scale
    @Override
    public Collection<Trigger> getAllTriggers() throws Exception {
        return selectTriggers(null);
    }

    @Override
    public Collection<Trigger> getTriggers(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }

        return selectTriggers(tenantId);
    }

    private Collection<Trigger> selectTriggers(String tenantId) throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }

        PreparedStatement selectTriggers = isEmpty(tenantId) ?
                CassStatement.get(session, CassStatement.SELECT_TRIGGERS_ALL) :
                CassStatement.get(session, CassStatement.SELECT_TRIGGERS_TENANT);
        if (null == selectTriggers) {
            throw new RuntimeException("selectTriggersTenant PreparedStatement is null");
        }

        List<Trigger> triggers = new ArrayList<>();
        try {
            ResultSet rsTriggers = session.execute(isEmpty(tenantId) ?
                    selectTriggers.bind() :
                    selectTriggers.bind(tenantId));
            for (Row row : rsTriggers) {
                Trigger trigger = mapTrigger(row);
                selectTriggerActions(trigger);
                triggers.add(trigger);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return triggers;
    }

    // TODO: This performs a cross-tenant fetch and may be inefficient at scale
    @Override
    public Collection<Trigger> getAllTriggersByTag(String category, String name) throws Exception {
        if (isEmpty(name)) {
            throw new IllegalArgumentException("Name must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }

        try {
            // first, get all the partitions for tags (i.e. tenants)
            BoundStatement bs = CassStatement.get(session, CassStatement.SELECT_PARTITIONS_TAGS).bind();
            Set<String> tenants = new HashSet<>();
            for (Row row : session.execute(bs)) {
                tenants.add(row.getString("tenantId"));
            }

            // next, get all of the tagged triggerIds
            PreparedStatement selectTagsTriggers = isEmpty(category) ?
                    CassStatement.get(session, CassStatement.SELECT_TAGS_TRIGGERS_BY_NAME) :
                    CassStatement.get(session, CassStatement.SELECT_TAGS_TRIGGERS_BY_CATEGORY_AND_NAME);
            if (selectTagsTriggers == null) {
                throw new RuntimeException("selectTagsTriggersByTag PreparedStatement is null");
            }

            Map<String, Set<String>> tenantTriggerIdsMap = new HashMap<>();
            List<ResultSetFuture> futures = isEmpty(category) ?
                    tenants.stream()
                            .map(tenantId -> session.executeAsync(selectTagsTriggers.bind(tenantId, name)))
                            .collect(Collectors.toList()) :
                    tenants.stream()
                            .map(tenantId -> session.executeAsync(selectTagsTriggers.bind(tenantId, category, name)))
                            .collect(Collectors.toList());
            List<ResultSet> rsTriggerIds = Futures.allAsList(futures).get();
            rsTriggerIds.stream().forEach(rs -> {
                for (Row row : rs) {
                    String tenantId = row.getString("tenantId");
                    Set<String> triggerIds = row.getSet("triggers", String.class);
                    Set<String> storedTriggerIds = tenantTriggerIdsMap.get(tenantId);
                    if (null != storedTriggerIds) {
                        triggerIds.addAll(storedTriggerIds);
                    }
                    tenantTriggerIdsMap.put(tenantId, triggerIds);
                }
            });

            // Now, generate a cross-tenant result set if Triggers using the tenantIds and triggerIds
            List<Trigger> triggers = new ArrayList<>();
            PreparedStatement selectTrigger = CassStatement.get(session, CassStatement.SELECT_TRIGGER);
            for (Map.Entry<String, Set<String>> entry : tenantTriggerIdsMap.entrySet()) {
                String tenantId = entry.getKey();
                Set<String> triggerIds = entry.getValue();
                futures = triggerIds.stream().map(triggerId ->
                        session.executeAsync(selectTrigger.bind(tenantId, triggerId)))
                        .collect(Collectors.toList());
                List<ResultSet> rsTriggers = Futures.allAsList(futures).get();
                rsTriggers.stream().forEach(r -> {
                    for (Row row : r) {
                        triggers.add(mapTrigger(row));
                    }
                });
            }

            return triggers;

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Collection<Trigger> getTriggersByTag(String tenantId, String category, String name) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("tenantId must be not null");
        }
        if (isEmpty(category) && isEmpty(name)) {
            throw new IllegalArgumentException("Category and Name can not both be null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }

        try {
            Set<String> triggerIds = getTriggerIdsByTag(tenantId, category, name);

            // Now, generate a result set of Triggers using the triggerIds
            List<Trigger> triggers = new ArrayList<>(triggerIds.size());
            PreparedStatement selectTrigger = CassStatement.get(session, CassStatement.SELECT_TRIGGER);
            List<ResultSetFuture> futures = triggerIds.stream().map(triggerId ->
                    session.executeAsync(selectTrigger.bind(tenantId, triggerId)))
                    .collect(Collectors.toList());
            List<ResultSet> rsTriggers = Futures.allAsList(futures).get();
            rsTriggers.stream().forEach(r -> {
                for (Row row : r) {
                    triggers.add(mapTrigger(row));
                }
            });

            return triggers;

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private void selectTriggerActions(Trigger trigger) throws Exception {
        if (trigger == null) {
            throw new IllegalArgumentException("Trigger must be not null");
        }
        PreparedStatement selectTriggerActions = CassStatement.get(session, CassStatement.SELECT_TRIGGER_ACTIONS);
        if (selectTriggerActions == null) {
            throw new RuntimeException("selectTriggerActions PreparedStatement is null");
        }
        ResultSet rsTriggerActions = session
                .execute(selectTriggerActions.bind(trigger.getTenantId(), trigger.getId()));
        for (Row row : rsTriggerActions) {
            String actionPlugin = row.getString("actionPlugin");
            Set<String> actions = row.getSet("actions", String.class);
            trigger.addActions(actionPlugin, actions);
        }
    }

    private Trigger mapTrigger(Row row) {
        Trigger trigger = new Trigger();

        trigger.setName(row.getString("name"));
        trigger.setDescription(row.getString("description"));
        trigger.setAutoDisable(row.getBool("autoDisable"));
        trigger.setAutoEnable(row.getBool("autoEnable"));
        trigger.setAutoResolve(row.getBool("autoResolve"));
        trigger.setAutoResolveAlerts(row.getBool("autoResolveAlerts"));
        trigger.setSeverity(Severity.valueOf(row.getString("severity")));
        trigger.setFiringMatch(TriggerTemplate.Match.valueOf(row.getString("firingMatch")));
        trigger.setAutoResolveMatch(TriggerTemplate.Match.valueOf(row.getString("autoResolveMatch")));
        trigger.setId(row.getString("id"));
        trigger.setEnabled(row.getBool("enabled"));
        trigger.setTenantId(row.getString("tenantId"));
        trigger.setContext(row.getMap("context", String.class, String.class));

        return trigger;
    }

    @Override
    public Trigger copyTrigger(String tenantId, String triggerId, Map<String, String> dataIdMap) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (isEmpty(dataIdMap)) {
            throw new IllegalArgumentException("DataIdMap must be not null");
        }
        Trigger trigger = getTrigger(tenantId, triggerId);
        if (trigger == null) {
            throw new IllegalArgumentException("Trigger not found for tenantId [ " + tenantId + "] and triggerId [" +
                    triggerId + "]");
        }
        // ensure we have a 1-1 mapping for the dataId substitution
        Set<String> dataIdTokens = new HashSet<>();
        Collection<Condition> conditions = getTriggerConditions(tenantId, triggerId, null);
        for (Condition c : conditions) {
            if (c instanceof CompareCondition) {
                dataIdTokens.add(c.getDataId());
                dataIdTokens.add(((CompareCondition)c).getData2Id());
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
        newTrigger.setSeverity(trigger.getSeverity());
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
                        ((ThresholdCondition)c).getOperator(), ((ThresholdCondition)c).getThreshold());

            } else if (c instanceof ThresholdRangeCondition) {
                newCondition = new ThresholdRangeCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((ThresholdRangeCondition)c).getOperatorLow(),
                        ((ThresholdRangeCondition)c).getOperatorHigh(),
                        ((ThresholdRangeCondition)c).getThresholdLow(),
                        ((ThresholdRangeCondition)c).getThresholdHigh(),
                        ((ThresholdRangeCondition)c).isInRange());

            } else if (c instanceof AvailabilityCondition) {
                newCondition = new AvailabilityCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((AvailabilityCondition)c).getOperator());

            } else if (c instanceof CompareCondition) {
                newCondition = new CompareCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((CompareCondition)c).getOperator(),
                        ((CompareCondition)c).getData2Multiplier(),
                        dataIdMap.get(((CompareCondition)c).getData2Id()));

            } else if (c instanceof StringCondition) {
                newCondition = new StringCondition(newTrigger.getId(), c.getTriggerMode(),
                        c.getConditionSetSize(), c.getConditionSetIndex(), dataIdMap.get(c.getDataId()),
                        ((StringCondition)c).getOperator(), ((StringCondition)c).getPattern(),
                        ((StringCondition)c).isIgnoreCase());
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

            addDampening(newTrigger.getTenantId(), newDampening);
        }

        return newTrigger;
    }

    @Override
    public Dampening addDampening(String tenantId, Dampening dampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampening)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        checkTenantId(tenantId, dampening);
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertDampening = CassStatement.get(session, CassStatement.INSERT_DAMPENING);
        if (insertDampening == null) {
            throw new RuntimeException("insertDampening PreparedStatement is null");
        }

        try {
            session.execute(insertDampening.bind(dampening.getTriggerId(), dampening.getTriggerMode().name(),
                    dampening.getType().name(), dampening.getEvalTrueSetting(), dampening.getEvalTotalSetting(),
                    dampening.getEvalTimeSetting(), dampening.getDampeningId(), dampening.getTenantId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(DefinitionsEvent.EventType.DAMPENING_CHANGE);

        return dampening;
    }

    @Override
    public void removeDampening(String tenantId, String dampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampeningId)) {
            throw new IllegalArgumentException("dampeningId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement deleteDampeningId = CassStatement.get(session, CassStatement.DELETE_DAMPENING_ID);
        if (deleteDampeningId == null) {
            throw new RuntimeException("deleteDampeningId PreparedStatement is null");
        }

        Dampening dampening = getDampening(tenantId, dampeningId);
        if (dampening == null) {
            log.debugf("Ignoring removeDampening(" + dampeningId + "), the Dampening does not exist.");
            return;
        }

        try {
            session.execute(deleteDampeningId.bind(dampening.getTenantId(), dampening.getTriggerId(),
                    dampening.getTriggerMode().name(), dampeningId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(DefinitionsEvent.EventType.DAMPENING_CHANGE);
    }

    @Override
    public Dampening updateDampening(String tenantId, Dampening dampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampening)) {
            throw new IllegalArgumentException("DampeningId must be not null");
        }
        checkTenantId(tenantId, dampening);
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement updateDampeningId = CassStatement.get(session, CassStatement.UPDATE_DAMPENING_ID);
        if (updateDampeningId == null) {
            throw new RuntimeException("updateDampeningId PreparedStatement is null");
        }

        try {
            session.execute(updateDampeningId.bind(dampening.getType().name(), dampening.getEvalTrueSetting(),
                    dampening.getEvalTotalSetting(), dampening.getEvalTimeSetting(), dampening.getTenantId(),
                    dampening.getTriggerId(), dampening.getTriggerMode().name(), dampening.getDampeningId()));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(DefinitionsEvent.EventType.DAMPENING_CHANGE);

        return dampening;
    }

    @Override
    public Dampening getDampening(String tenantId, String dampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampeningId)) {
            throw new IllegalArgumentException("DampeningId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectDampeningId = CassStatement.get(session, CassStatement.SELECT_DAMPENING_ID);
        if (selectDampeningId == null) {
            throw new RuntimeException("selectDampeningId PreparedStatement is null");
        }

        Dampening dampening = null;
        try {
            ResultSet rsDampening = session.execute(selectDampeningId.bind(tenantId, dampeningId));
            Iterator<Row> itDampening = rsDampening.iterator();
            if (itDampening.hasNext()) {
                Row row = itDampening.next();
                dampening = mapDampening(row);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectTriggerDampenings = CassStatement
                .get(session, CassStatement.SELECT_TRIGGER_DAMPENINGS);
        PreparedStatement selectTriggerDampeningsMode = CassStatement.get(session,
                CassStatement.SELECT_TRIGGER_DAMPENINGS_MODE);
        if (selectTriggerDampenings == null || selectTriggerDampeningsMode == null) {
            throw new RuntimeException("selectTriggerDampenings* PreparedStatement is null");
        }
        List<Dampening> dampenings = new ArrayList<>();
        try {
            ResultSet rsDampenings;
            if (triggerMode == null) {
                rsDampenings = session.execute(selectTriggerDampenings.bind(tenantId, triggerId));
            } else {
                rsDampenings = session.execute(selectTriggerDampeningsMode.bind(tenantId, triggerId,
                        triggerMode.name()));
            }
            mapDampenings(rsDampenings, dampenings);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return dampenings;
    }

    // TODO: This getAll* fetches are cross-tenant fetch and may be inefficient at scale
    @Override
    public Collection<Dampening> getAllDampenings() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectDampeningsAll = CassStatement.get(session, CassStatement.SELECT_DAMPENINGS_ALL);
        if (selectDampeningsAll == null) {
            throw new RuntimeException("selectDampeningsAll PreparedStatement is null");
        }
        List<Dampening> dampenings = new ArrayList<>();
        try {
            ResultSet rsDampenings = session.execute(selectDampeningsAll.bind());
            mapDampenings(rsDampenings, dampenings);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return dampenings;
    }

    @Override
    public Collection<Dampening> getDampenings(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectDampeningsByTenant = CassStatement.get(session,
                CassStatement.SELECT_DAMPENINGS_BY_TENANT);
        if (selectDampeningsByTenant == null) {
            throw new RuntimeException("selectDampeningsByTenant PreparedStatement is null");
        }
        List<Dampening> dampenings = new ArrayList<>();
        try {
            ResultSet rsDampenings = session.execute(selectDampeningsByTenant.bind(tenantId));
            mapDampenings(rsDampenings, dampenings);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return dampenings;
    }

    private void mapDampenings(ResultSet rsDampenings, List<Dampening> dampenings) throws Exception {
        for (Row row : rsDampenings) {
            Dampening dampening = mapDampening(row);
            dampenings.add(dampening);
        }
    }

    private Dampening mapDampening(Row row) {
        Dampening dampening = new Dampening();
        dampening.setTenantId(row.getString("tenantId"));
        dampening.setTriggerId(row.getString("triggerId"));
        dampening.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
        dampening.setType(Dampening.Type.valueOf(row.getString("type")));
        dampening.setEvalTrueSetting(row.getInt("evalTrueSetting"));
        dampening.setEvalTotalSetting(row.getInt("evalTotalSetting"));
        dampening.setEvalTimeSetting(row.getLong("evalTimeSetting"));
        return dampening;
    }

    @Override
    public Collection<Condition> addCondition(String tenantId, String triggerId, Trigger.Mode triggerMode,
            Condition condition) throws Exception {
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
            throw new IllegalArgumentException("ConditionId [" + conditionId + "] on tenant " + tenantId +
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertConditionAvailability = CassStatement.get(session,
                CassStatement.INSERT_CONDITION_AVAILABILITY);
        PreparedStatement insertConditionCompare = CassStatement.get(session, CassStatement.INSERT_CONDITION_COMPARE);
        PreparedStatement insertConditionExternal = CassStatement
                .get(session, CassStatement.INSERT_CONDITION_EXTERNAL);
        PreparedStatement insertConditionString = CassStatement.get(session, CassStatement.INSERT_CONDITION_STRING);
        PreparedStatement insertConditionThreshold = CassStatement.get(session,
                CassStatement.INSERT_CONDITION_THRESHOLD);
        PreparedStatement insertConditionThresholdRange = CassStatement.get(session,
                CassStatement.INSERT_CONDITION_THRESHOLD_RANGE);
        if (insertConditionAvailability == null
                || insertConditionCompare == null
                || insertConditionExternal == null
                || insertConditionString == null
                || insertConditionThreshold == null
                || insertConditionThresholdRange == null) {
            throw new RuntimeException("insert*Condition PreparedStatement is null");
        }
        // Get rid of the prior condition set
        removeConditions(tenantId, triggerId, triggerMode);

        // Now add the new condition set
        try {
            List<String> dataIds = new ArrayList<>(2);

            List<ResultSetFuture> futures = new ArrayList<>();

            int i = 0;
            for (Condition cond : conditions) {
                cond.setTenantId(tenantId);
                cond.setTriggerId(triggerId);
                cond.setTriggerMode(triggerMode);
                cond.setConditionSetSize(conditions.size());
                cond.setConditionSetIndex(++i);

                dataIds.add(cond.getDataId());

                if (cond instanceof AvailabilityCondition) {

                    AvailabilityCondition aCond = (AvailabilityCondition)cond;
                    futures.add(session.executeAsync(insertConditionAvailability.bind(aCond.getTenantId(), aCond
                            .getTriggerId(),
                            aCond.getTriggerMode().name(), aCond.getConditionSetSize(), aCond.getConditionSetIndex(),
                            aCond.getConditionId(), aCond.getDataId(), aCond.getOperator().name())));

                } else if (cond instanceof CompareCondition) {

                    CompareCondition cCond = (CompareCondition)cond;
                    dataIds.add(cCond.getData2Id());
                    futures.add(session.executeAsync(insertConditionCompare.bind(cCond.getTenantId(),
                            cCond.getTriggerId(), cCond.getTriggerMode().name(), cCond.getConditionSetSize(),
                            cCond.getConditionSetIndex(), cCond.getConditionId(), cCond.getDataId(),
                            cCond.getOperator().name(), cCond.getData2Id(), cCond.getData2Multiplier())));

                } else if (cond instanceof ExternalCondition) {

                    ExternalCondition eCond = (ExternalCondition)cond;
                    futures.add(session.executeAsync(insertConditionExternal.bind(eCond.getTenantId(), eCond
                            .getTriggerId(), eCond.getTriggerMode().name(), eCond.getConditionSetSize(),
                            eCond.getConditionSetIndex(), eCond.getConditionId(), eCond.getDataId(),
                            eCond.getSystemId(), eCond.getExpression())));

                } else if (cond instanceof StringCondition) {

                    StringCondition sCond = (StringCondition)cond;
                    futures.add(session.executeAsync(insertConditionString.bind(sCond.getTenantId(), sCond
                            .getTriggerId(), sCond.getTriggerMode().name(), sCond.getConditionSetSize(),
                            sCond.getConditionSetIndex(), sCond.getConditionId(), sCond.getDataId(),
                            sCond.getOperator().name(), sCond.getPattern(), sCond.isIgnoreCase())));

                } else if (cond instanceof ThresholdCondition) {

                    ThresholdCondition tCond = (ThresholdCondition)cond;
                    futures.add(session.executeAsync(insertConditionThreshold.bind(tCond.getTenantId(),
                            tCond.getTriggerId(), tCond.getTriggerMode().name(), tCond.getConditionSetSize(),
                            tCond.getConditionSetIndex(), tCond.getConditionId(), tCond.getDataId(),
                            tCond.getOperator().name(), tCond.getThreshold())));

                } else if (cond instanceof ThresholdRangeCondition) {

                    ThresholdRangeCondition rCond = (ThresholdRangeCondition)cond;
                    futures.add(session.executeAsync(insertConditionThresholdRange.bind(rCond.getTenantId(),
                            rCond.getTriggerId(), rCond.getTriggerMode().name(), rCond.getConditionSetSize(),
                            rCond.getConditionSetIndex(), rCond.getConditionId(), rCond.getDataId(),
                            rCond.getOperatorLow().name(), rCond.getOperatorHigh().name(), rCond.getThresholdLow(),
                            rCond.getThresholdHigh(), rCond.isInRange())));

                } else {
                    throw new IllegalArgumentException("Unexpected ConditionType: " + cond);
                }

                // generate the automatic dataId tags for search
                for (String dataId : dataIds) {
                    insertTag(cond.getTenantId(), cond.getTriggerId(), "dataId", dataId, false);
                }
                dataIds.clear();
            }
            Futures.allAsList(futures).get();

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (initialized && alertsEngine != null) {
            alertsEngine.reloadTrigger(tenantId, triggerId);
        }

        notifyListeners(DefinitionsEvent.EventType.CONDITION_CHANGE);

        return conditions;
    }

    private void insertTag(String tenantId, String triggerId, String category, String name, boolean visible)
            throws Exception {

        // Do not store null values for category as it is a query-able field. Although secondary indexes allow
        // nulls they are not really a good idea, use empty string to minimally make the rows queryable.
        if (null == category) {
            category = "";
        }

        // If the desired Tag already exists just return
        if (!getTags(tenantId, triggerId, category, name).isEmpty()) {
            return;
        }
        PreparedStatement insertTag = CassStatement.get(session, CassStatement.INSERT_TAG);
        if (insertTag == null) {
            throw new RuntimeException("insertTag PreparedStatement is null");
        }
        session.execute(insertTag.bind(tenantId, triggerId, category, name, visible));
        insertTriggerByTagIndex(tenantId, category, name, triggerId);
    }

    private void insertTriggerByTagIndex(String tenantId, String category, String name, String triggerId) {
        Set<String> triggers = getTriggerIdsByTag(tenantId, category, name);
        triggers = new HashSet<>(triggers);
        if (triggers.isEmpty()) {
            triggers.add(triggerId);
            PreparedStatement insertTagsTriggers = CassStatement.get(session, CassStatement.INSERT_TAGS_TRIGGERS);
            if (insertTagsTriggers == null) {
                throw new RuntimeException("insertTagsTriggers PreparedStatement is null");
            }
            session.execute(insertTagsTriggers.bind(tenantId, category, name, triggers));
        } else {
            if (!triggers.contains(triggerId)) {
                triggers.add(triggerId);
                PreparedStatement updateTagsTriggers = CassStatement.get(session, CassStatement.UPDATE_TAGS_TRIGGERS);
                if (updateTagsTriggers == null) {
                    throw new RuntimeException("updateTagsTriggers PreparedStatement is null");
                }
                session.execute(updateTagsTriggers.bind(triggers, tenantId, name));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Set<String> getTriggerIdsByTag(String tenantId, String category, String name) {
        Set triggerTags = new HashSet<>();

        PreparedStatement selectTagsTriggers = null;
        if (isEmpty(category)) {
            selectTagsTriggers = CassStatement.get(session, CassStatement.SELECT_TAGS_TRIGGERS_BY_NAME);
        } else if (isEmpty(name)) {
            selectTagsTriggers = CassStatement.get(session, CassStatement.SELECT_TAGS_TRIGGERS_BY_CATEGORY);
        } else {
            selectTagsTriggers = CassStatement.get(session, CassStatement.SELECT_TAGS_TRIGGERS_BY_CATEGORY_AND_NAME);
        }
        if (selectTagsTriggers == null) {
            throw new RuntimeException("selectTagsTriggers PreparedStatement is null");
        }
        BoundStatement bs = null;
        if (isEmpty(category)) {
            bs = selectTagsTriggers.bind(tenantId, name);
        } else if (isEmpty(name)) {
            bs = selectTagsTriggers.bind(tenantId, category);
        } else {
            bs = selectTagsTriggers.bind(tenantId, category, name);
        }
        ResultSet rsTriggersTags = session.execute(bs);
        for (Row row : rsTriggersTags) {
            triggerTags = row.getSet("triggers", String.class);
        }
        return triggerTags;
    }

    private List<Tag> getTags(String tenantId, String triggerId, String category, String name)
            throws Exception {
        BoundStatement boundTags;
        if (!isEmpty(category) && !isEmpty(name)) {
            PreparedStatement selectTagsByCategoryAndName = CassStatement.get(session,
                    CassStatement.SELECT_TAGS_BY_CATEGORY_AND_NAME);
            if (selectTagsByCategoryAndName == null) {
                throw new RuntimeException("selectTagsByCategoryAndName PreparedStatement is null");
            }
            boundTags = selectTagsByCategoryAndName.bind(tenantId, triggerId, category, name);
        } else if (!isEmpty(category)) {
            PreparedStatement selectTagsByCategory = CassStatement.get(session, CassStatement.SELECT_TAGS_BY_CATEGORY);
            if (selectTagsByCategory == null) {
                throw new RuntimeException("selectTagsByCategory PreparedStatement is null");
            }
            boundTags = selectTagsByCategory.bind(tenantId, triggerId, category);
        } else if (!isEmpty(name)) {
            PreparedStatement selectTagsByName = CassStatement.get(session, CassStatement.SELECT_TAGS_BY_NAME);
            if (selectTagsByName == null) {
                throw new RuntimeException("selectTagsByName PreparedStatement is null");
            }
            boundTags = selectTagsByName.bind(tenantId, triggerId, name);
        } else {
            PreparedStatement selectTags = CassStatement.get(session, CassStatement.SELECT_TAGS);
            boundTags = selectTags.bind(tenantId, triggerId);
        }

        List<Tag> tags = new ArrayList<>();
        ResultSet rsTags = session.execute(boundTags);
        for (Row row : rsTags) {
            Tag tag = new Tag();
            tag.setTenantId(row.getString("tenantId"));
            tag.setTriggerId(row.getString("triggerId"));
            tag.setCategory(row.getString("category"));
            tag.setName(row.getString("name"));
            tag.setVisible(row.getBool("visible"));
            tags.add(tag);
        }
        return tags;
    }

    private void removeConditions(String tenantId, String triggerId, Trigger.Mode triggerMode) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must not be null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must not be null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must not be null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement deleteConditionsMode = CassStatement.get(session, CassStatement.DELETE_CONDITIONS_MODE);
        if (deleteConditionsMode == null) {
            throw new RuntimeException("deleteConditionsMode PreparedStatement is null");
        }
        try {
            session.execute(deleteConditionsMode.bind(tenantId, triggerId, triggerMode.name()));

            // if removing conditions remove the automatically-added dataId tags
            deleteTags(tenantId, triggerId, "dataId", null);

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private void deleteTags(String tenantId, String triggerId, String category, String name) throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        BoundStatement boundTags;
        if (!isEmpty(name)) {
            PreparedStatement deleteTagsByName = CassStatement.get(session, CassStatement.DELETE_TAGS_BY_NAME);
            if (deleteTagsByName == null) {
                throw new RuntimeException("deleteTagsByName PreparedStatement is null");
            }
            boundTags = deleteTagsByName.bind(tenantId, triggerId, name);
        } else {
            PreparedStatement deleteTags = CassStatement.get(session, CassStatement.DELETE_TAGS);
            if (deleteTags == null) {
                throw new RuntimeException("deleteTags PreparedStatement is null");
            }
            boundTags = deleteTags.bind(tenantId, triggerId);
        }
        try {
            deleteTriggerByTagIndex(tenantId, triggerId, category, name);
            session.execute(boundTags);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private void deleteTriggerByTagIndex(String tenantId, String triggerId, String category, String name)
            throws Exception {
        List<Tag> tags;
        if (category == null || name == null) {
            tags = getTriggerTags(tenantId, triggerId, category);
        } else {
            tags = new ArrayList<>();
            Tag singleTag = new Tag();
            singleTag.setTenantId(tenantId);
            singleTag.setCategory(category);
            singleTag.setName(name);
            tags.add(singleTag);
        }

        for (Tag tag : tags) {
            Set<String> triggers = getTriggerIdsByTag(tag.getTenantId(), tag.getCategory(), tag.getName());
            if (triggers.size() > 1) {
                Set<String> updateTriggers = new HashSet<>(triggers);
                updateTriggers.remove(triggerId);
                PreparedStatement updateTagsTriggers = CassStatement.get(session, CassStatement.UPDATE_TAGS_TRIGGERS);
                if (updateTagsTriggers == null) {
                    throw new RuntimeException("updateTagsTriggers PreparedStatement is null");
                }
                session.execute(updateTagsTriggers.bind(triggers, tag.getTenantId(), tag.getName()));
            } else {
                PreparedStatement deleteTagsTriggers = CassStatement.get(session, CassStatement.DELETE_TAGS_TRIGGERS);
                if (deleteTagsTriggers == null) {
                    throw new RuntimeException("deleteTagsTriggers PreparedStatement is null");
                }
                session.execute(deleteTagsTriggers.bind(tag.getTenantId(), tag.getName()));
            }
        }
    }

    @Override
    public Condition getCondition(String tenantId, String conditionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(conditionId)) {
            throw new IllegalArgumentException("conditionId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectConditionId = CassStatement.get(session, CassStatement.SELECT_CONDITION_ID);
        if (selectConditionId == null) {
            throw new RuntimeException("selectConditionId PreparedStatement is null");
        }
        Condition condition = null;
        try {
            ResultSet rsCondition = session.execute(selectConditionId.bind(tenantId, conditionId));
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
    public Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Trigger.Mode triggerMode)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("triggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectTriggerConditions = CassStatement
                .get(session, CassStatement.SELECT_TRIGGER_CONDITIONS);
        PreparedStatement selectTriggerConditionsTriggerMode = CassStatement.get(session,
                CassStatement.SELECT_TRIGGER_CONDITIONS_TRIGGER_MODE);
        if (selectTriggerConditions == null || selectTriggerConditionsTriggerMode == null) {
            throw new RuntimeException("selectTriggerConditions* PreparedStatement is null");
        }
        List<Condition> conditions = new ArrayList<>();
        try {
            ResultSet rsConditions;
            if (triggerMode == null) {
                rsConditions = session.execute(selectTriggerConditions.bind(tenantId, triggerId));
            } else {
                rsConditions = session.execute(selectTriggerConditionsTriggerMode.bind(tenantId, triggerId,
                        triggerMode.name()));
            }
            mapConditions(rsConditions, conditions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return conditions;
    }

    // TODO: This getAll* fetches are cross-tenant fetch and may be inefficient at scale
    @Override
    public Collection<Condition> getAllConditions() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectConditionsAll = CassStatement.get(session, CassStatement.SELECT_CONDITIONS_ALL);
        if (selectConditionsAll == null) {
            throw new RuntimeException("selectConditionsAll PreparedStatement is null");
        }
        List<Condition> conditions = new ArrayList<>();
        try {
            ResultSet rsConditions = session.execute(selectConditionsAll.bind());
            mapConditions(rsConditions, conditions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return conditions;
    }

    @Override
    public Collection<Condition> getConditions(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectConditionsByTenant = CassStatement.get(session,
                CassStatement.SELECT_CONDITIONS_BY_TENANT);
        if (selectConditionsByTenant == null) {
            throw new RuntimeException("selectConditionsByTenant PreparedStatement is null");
        }
        List<Condition> conditions = new ArrayList<>();
        try {
            ResultSet rsConditions = session.execute(selectConditionsByTenant.bind(tenantId));
            mapConditions(rsConditions, conditions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return conditions;
    }

    private void mapConditions(ResultSet rsConditions, List<Condition> conditions) throws Exception {
        for (Row row : rsConditions) {
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
            switch (Condition.Type.valueOf(type)) {
                case AVAILABILITY:
                    AvailabilityCondition aCondition = new AvailabilityCondition();
                    aCondition.setTenantId(row.getString("tenantId"));
                    aCondition.setTriggerId(row.getString("triggerId"));
                    aCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                    aCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                    aCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                    aCondition.setDataId(row.getString("dataId"));
                    aCondition.setOperator(AvailabilityCondition.Operator.valueOf(row.getString("operator")));
                    condition = aCondition;
                    break;
                case COMPARE:
                    CompareCondition cCondition = new CompareCondition();
                    cCondition.setTenantId(row.getString("tenantId"));
                    cCondition.setTriggerId(row.getString("triggerId"));
                    cCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                    cCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                    cCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                    cCondition.setDataId(row.getString("dataId"));
                    cCondition.setOperator(CompareCondition.Operator.valueOf(row.getString("operator")));
                    cCondition.setData2Id(row.getString("data2Id"));
                    cCondition.setData2Multiplier(row.getDouble("data2Multiplier"));
                    condition = cCondition;
                    break;
                case EXTERNAL:
                    ExternalCondition eCondition = new ExternalCondition();
                    eCondition.setTenantId(row.getString("tenantId"));
                    eCondition.setTriggerId(row.getString("triggerId"));
                    eCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                    eCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                    eCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                    eCondition.setDataId(row.getString("dataId"));
                    eCondition.setSystemId(row.getString("operator"));
                    eCondition.setExpression(row.getString("pattern"));
                    condition = eCondition;
                    break;
                case RANGE:
                    ThresholdRangeCondition rCondition = new ThresholdRangeCondition();
                    rCondition.setTenantId(row.getString("tenantId"));
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
                    break;
                case STRING:
                    StringCondition sCondition = new StringCondition();
                    sCondition.setTenantId(row.getString("tenantId"));
                    sCondition.setTriggerId(row.getString("triggerId"));
                    sCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                    sCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                    sCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                    sCondition.setDataId(row.getString("dataId"));
                    sCondition.setOperator(StringCondition.Operator.valueOf(row.getString("operator")));
                    sCondition.setPattern(row.getString("pattern"));
                    sCondition.setIgnoreCase(row.getBool("ignoreCase"));
                    condition = sCondition;
                    break;
                case THRESHOLD:
                    ThresholdCondition tCondition = new ThresholdCondition();
                    tCondition.setTenantId(row.getString("tenantId"));
                    tCondition.setTriggerId(row.getString("triggerId"));
                    tCondition.setTriggerMode(Trigger.Mode.valueOf(row.getString("triggerMode")));
                    tCondition.setConditionSetSize(row.getInt("conditionSetSize"));
                    tCondition.setConditionSetIndex(row.getInt("conditionSetIndex"));
                    tCondition.setDataId(row.getString("dataId"));
                    tCondition.setOperator(ThresholdCondition.Operator.valueOf(row.getString("operator")));
                    tCondition.setThreshold(row.getDouble("threshold"));
                    condition = tCondition;
                    break;
                default:
                    log.debugf("Unexpected condition type found: " + type);
                    break;
            }
        } else {
            log.debugf("Invalid condition type: null or empty");
        }
        return condition;
    }

    @Override
    public void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertActionPlugin = CassStatement.get(session, CassStatement.INSERT_ACTION_PLUGIN);
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
    public void addActionPlugin(String actionPlugin, Map<String, String> defaultProperties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (defaultProperties == null || defaultProperties.isEmpty()) {
            throw new IllegalArgumentException("defaultProperties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement insertActionPluginDefaulProperties = CassStatement.get(session,
                CassStatement.INSERT_ACTION_PLUGIN_DEFAULT_PROPERTIES);
        if (insertActionPluginDefaulProperties == null) {
            throw new RuntimeException("insertDefaulPropertiesActionPlugin PreparedStatement is null");
        }
        try {
            Set<String> properties = defaultProperties.keySet();
            session.execute(insertActionPluginDefaulProperties.bind(actionPlugin, properties, defaultProperties));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeActionPlugin(String actionPlugin) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement deleteActionPlugin = CassStatement.get(session, CassStatement.DELETE_ACTION_PLUGIN);
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
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement updateActionPlugin = CassStatement.get(session, CassStatement.UPDATE_ACTION_PLUGIN);
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
    public void updateActionPlugin(String actionPlugin, Map<String, String> defaultProperties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (defaultProperties == null || defaultProperties.isEmpty()) {
            throw new IllegalArgumentException("defaultProperties must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement updateDefaultPropertiesActionPlugin = CassStatement.get(session,
                CassStatement.UPDATE_ACTION_PLUGIN_DEFAULT_PROPERTIES);
        if (updateDefaultPropertiesActionPlugin == null) {
            throw new RuntimeException("updateDefaultPropertiesActionPlugin PreparedStatement is null");
        }
        try {
            Set<String> properties = defaultProperties.keySet();
            session.execute(updateDefaultPropertiesActionPlugin.bind(properties, defaultProperties, actionPlugin));
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
        PreparedStatement selectActionPlugins = CassStatement.get(session, CassStatement.SELECT_ACTION_PLUGINS);
        if (selectActionPlugins == null) {
            throw new RuntimeException("selectActionPlugins PreparedStatement is null");
        }
        ArrayList<String> actionPlugins = new ArrayList<>();
        try {
            ResultSet rsActionPlugins = session.execute(selectActionPlugins.bind());
            for (Row row : rsActionPlugins) {
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
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectActionPlugin = CassStatement.get(session, CassStatement.SELECT_ACTION_PLUGIN);
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
    public Map<String, String> getDefaultActionPlugin(String actionPlugin) {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectActionPluginDefaultProperties = CassStatement.get(session,
                CassStatement.SELECT_ACTION_PLUGIN_DEFAULT_PROPERTIES);
        if (selectActionPluginDefaultProperties == null) {
            throw new RuntimeException("selectDefaultPropertiesActionPlugin PreparedStatement is null");
        }
        Map<String, String> defaultProperties = null;
        try {
            ResultSet rsActionPlugin = session.execute(selectActionPluginDefaultProperties.bind(actionPlugin));
            Iterator<Row> itActionPlugin = rsActionPlugin.iterator();
            if (itActionPlugin.hasNext()) {
                Row row = itActionPlugin.next();
                defaultProperties = row.getMap("defaultProperties", String.class, String.class);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return defaultProperties;
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
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement deleteAction = CassStatement.get(session, CassStatement.DELETE_ACTION);
        if (deleteAction == null) {
            throw new RuntimeException("deleteAction PreparedStatement is null");
        }
        try {
            session.execute(deleteAction.bind(tenantId, actionPlugin, actionId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
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
        properties.put("actionId", actionId);
        properties.put("actionPlugin", actionPlugin);

        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement updateAction = CassStatement.get(session, CassStatement.UPDATE_ACTION);
        if (updateAction == null) {
            throw new RuntimeException("updateAction PreparedStatement is null");
        }
        try {
            session.execute(updateAction.bind(properties, tenantId, actionPlugin, actionId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    // TODO: This getAll* fetches are cross-tenant fetch and may be inefficient at scale
    @Override
    public Map<String, Map<String, Set<String>>> getAllActions() throws Exception {
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectActionsAll = CassStatement.get(session, CassStatement.SELECT_ACTIONS_ALL);
        if (selectActionsAll == null) {
            throw new RuntimeException("selectActionsAll PreparedStatement is null");
        }
        Map<String, Map<String, Set<String>>> actions = new HashMap<>();
        try {
            ResultSet rsActions = session.execute(selectActionsAll.bind());
            for (Row row : rsActions) {
                String tenantId = row.getString("tenantId");
                String actionPlugin = row.getString("actionPlugin");
                String actionId = row.getString("actionId");
                if (actions.get(tenantId) == null) {
                    actions.put(tenantId, new HashMap<>());
                }
                if (actions.get(tenantId).get(actionPlugin) == null) {
                    actions.get(tenantId).put(actionPlugin, new HashSet<>());
                }
                actions.get(tenantId).get(actionPlugin).add(actionId);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return actions;
    }

    @Override
    public Map<String, Set<String>> getActions(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectActionsByTenant = CassStatement.get(session,
                CassStatement.SELECT_ACTIONS_BY_TENANT);
        if (selectActionsByTenant == null) {
            throw new RuntimeException("selectActionsByTenant PreparedStatement is null");
        }
        Map<String, Set<String>> actions = new HashMap<>();
        try {
            ResultSet rsActions = session.execute(selectActionsByTenant.bind(tenantId));
            for (Row row : rsActions) {
                String actionPlugin = row.getString("actionPlugin");
                String actionId = row.getString("actionId");
                if (actions.get(actionPlugin) == null) {
                    actions.put(actionPlugin, new HashSet<>());
                }
                actions.get(actionPlugin).add(actionId);
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return actions;
    }

    @Override
    public Collection<String> getActions(String tenantId, String actionPlugin) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectActionsPlugin = CassStatement.get(session, CassStatement.SELECT_ACTIONS_PLUGIN);
        if (selectActionsPlugin == null) {
            throw new RuntimeException("selectActionsPlugin PreparedStatement is null");
        }
        ArrayList<String> actions = new ArrayList<>();
        try {
            ResultSet rsActions = session.execute(selectActionsPlugin.bind(tenantId, actionPlugin));
            for (Row row : rsActions) {
                actions.add(row.getString("actionId"));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return actions;
    }

    @Override
    public Map<String, String> getAction(String tenantId, String actionPlugin, String actionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        PreparedStatement selectAction = CassStatement.get(session, CassStatement.SELECT_ACTION);
        if (selectAction == null) {
            throw new RuntimeException("selectAction PreparedStatement is null");
        }
        Map<String, String> properties = null;
        try {
            ResultSet rsAction = session.execute(selectAction.bind(tenantId, actionPlugin, actionId));
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
    public void addTag(String tenantId, Tag tag) throws Exception {
        if (tag == null) {
            throw new IllegalArgumentException("Tag must be not null");
        }
        if (isEmpty(tag.getTriggerId())) {
            throw new IllegalArgumentException("Tag TriggerId must be not null or empty");
        }
        if (isEmpty(tag.getName())) {
            throw new IllegalArgumentException("Tag Name must be not null or empty");
        }
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null or empty");
        }
        checkTenantId(tenantId, tag);
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }
        try {
            insertTag(tag.getTenantId(), tag.getTriggerId(), tag.getCategory(), tag.getName(), tag.isVisible());
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public void removeTags(String tenantId, String triggerId, String category, String name) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        try {
            deleteTags(tenantId, triggerId, category, name);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public List<Tag> getTriggerTags(String tenantId, String triggerId, String category) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (session == null) {
            throw new RuntimeException("Cassandra session is null");
        }

        List<Tag> tags;
        try {
            tags = getTags(tenantId, triggerId, category, null);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return tags;
    }

    @Override
    public void registerListener(DefinitionsListener listener, EventType eventType, EventType... eventTypes) {
        EnumSet<EventType> types = EnumSet.of(eventType, eventTypes);
        log.debugf("Registering listeners %s for event types", listener, types);
        listeners.put(listener, types);
    }

    private void notifyListeners(EventType eventType) {
        DefinitionsEvent de = new DefinitionsEvent(eventType);
        log.debugf("Notifying applicable listeners %s of event %s", listeners, eventType.name());
        for (Map.Entry<DefinitionsListener, Set<EventType>> me : listeners.entrySet()) {
            if (me.getValue().contains(eventType)) {
                log.debugf("Notified Listener %s", eventType.name());
                me.getKey().onChange(de);
            }
        }
    }

    private boolean isEmpty(Trigger trigger) {
        return trigger == null || trigger.getId() == null || trigger.getId().trim().isEmpty();
    }

    private boolean isEmpty(Dampening dampening) {
        return dampening == null || dampening.getTriggerId() == null || dampening.getTriggerId().trim().isEmpty() ||
                dampening.getDampeningId() == null || dampening.getDampeningId().trim().isEmpty();
    }

    private boolean isEmpty(String id) {
        return id == null || id.trim().isEmpty();
    }

    public boolean isEmpty(Map<String, String> map) {
        return map == null || map.isEmpty();
    }

    /*
        The attribute tenantId is part of the Trigger object.
        But it is also important to have it specifically on the services calls.
        So, in case that a tenantId parameter does not match with trigger.tenantId attribute,
        this last one will be overwritten with the parameter.
     */
    private void checkTenantId(String tenantId, Object obj) {
        if (isEmpty(tenantId)) {
            return;
        }
        if (obj == null) {
            return;
        }
        if (obj instanceof Trigger) {
            Trigger trigger = (Trigger)obj;
            if (trigger.getTenantId() == null || !trigger.getTenantId().equals(tenantId)) {
                trigger.setTenantId(tenantId);
            }
        } else if (obj instanceof Dampening) {
            Dampening dampening = (Dampening)obj;
            if (dampening.getTenantId() == null || !dampening.getTenantId().equals(tenantId)) {
                dampening.setTenantId(tenantId);
            }
        } else if (obj instanceof Tag) {
            Tag tag = (Tag)obj;
            if (tag.getTenantId() == null || !tag.getTenantId().equals(tenantId)) {
                tag.setTenantId(tenantId);
            }
        }
    }

}
