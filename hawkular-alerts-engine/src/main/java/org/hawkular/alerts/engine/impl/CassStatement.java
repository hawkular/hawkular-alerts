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

import java.util.HashMap;
import java.util.Map;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

/**
 * PreparedStatements need to be prepared only one time for the Datastax driver.  Avoid overhead and warnings by
 * caching the PreparedStatements in one place.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CassStatement {
    private static final String CASSANDRA_KEYSPACE = "hawkular-alerts.cassandra-keyspace";

    private static final String keyspace;

    private static final Map<String, PreparedStatement> statementMap = new HashMap<>();

    public static final String DELETE_ALERT_STATUS;
    public static final String INSERT_ALERT;
    public static final String INSERT_ALERT_TRIGGER;
    public static final String INSERT_ALERT_CTIME;
    public static final String INSERT_ALERT_STATUS;
    public static final String SELECT_ALERT_STATUS;
    public static final String SELECT_ALERTS_BY_TENANT;
    public static final String SELECT_ALERTS_BY_TENANT_AND_ALERT;
    public static final String SELECT_ALERTS_TRIGGERS;
    public static final String SELECT_ALERT_CTIME_START_END;
    public static final String SELECT_ALERT_CTIME_START;
    public static final String SELECT_ALERT_CTIME_END;
    public static final String SELECT_ALERT_STATUS_BY_TENANT_AND_STATUS;
    public static final String SELECT_TAGS_TRIGGERS_BY_CATEGORY_AND_NAME;
    public static final String SELECT_TAGS_TRIGGERS_BY_CATEGORY;
    public static final String SELECT_TAGS_TRIGGERS_BY_NAME;
    public static final String UPDATE_ALERT;

    public static final String INSERT_TRIGGER;
    public static final String INSERT_TRIGGER_ACTIONS;
    public static final String SELECT_TRIGGER_CONDITIONS;
    public static final String SELECT_TRIGGER_CONDITIONS_TRIGGER_MODE;
    public static final String DELETE_CONDITIONS_MODE;
    public static final String INSERT_AVAILABILITY_CONDITION;
    public static final String INSERT_COMPARE_CONDITION;
    public static final String INSERT_STRING_CONDITION;
    public static final String INSERT_THRESHOLD_CONDITION;
    public static final String INSERT_THRESHOLD_RANGE_CONDITION;
    public static final String INSERT_TAG;
    public static final String INSERT_DAMPENING;
    public static final String INSERT_ACTION;
    public static final String SELECT_ALL_TRIGGERS;
    public static final String SELECT_TRIGGER_ACTIONS;
    public static final String SELECT_TENANT_TRIGGERS;
    public static final String SELECT_ALL_CONDITIONS;
    public static final String SELECT_ALL_CONDITIONS_BY_TENANT;
    public static final String SELECT_ALL_DAMPENINGS;
    public static final String SELECT_ALL_DAMPENINGS_BY_TENANT;
    public static final String SELECT_ALL_ACTIONS;
    public static final String SELECT_ALL_ACTIONS_BY_TENANT;
    public static final String SELECT_TRIGGER;
    public static final String SELECT_TRIGGER_DAMPENINGS;
    public static final String SELECT_TRIGGER_DAMPENINGS_MODE;
    public static final String DELETE_DAMPENINGS;
    public static final String DELETE_CONDITIONS;
    public static final String DELETE_TRIGGERS;
    public static final String UPDATE_TRIGGER;
    public static final String DELETE_TRIGGER_ACTIONS;
    public static final String DELETE_DAMPENING_ID;
    public static final String SELECT_DAMPENING_ID;
    public static final String UPDATE_DAMPENING_ID;
    public static final String SELECT_CONDITION_ID;
    public static final String INSERT_ACTION_PLUGIN;
    public static final String DELETE_ACTION_PLUGIN;
    public static final String UPDATE_ACTION_PLUGIN;
    public static final String SELECT_ACTION_PLUGINS;
    public static final String SELECT_ACTION_PLUGIN;
    public static final String DELETE_ACTION;
    public static final String UPDATE_ACTION;
    public static final String SELECT_ACTIONS_PLUGIN;
    public static final String SELECT_ACTION;
    public static final String INSERT_TAGS_TRIGGERS;
    public static final String UPDATE_TAGS_TRIGGERS;
    public static final String DELETE_TAGS_TRIGGERS;
    public static final String SELECT_TAGS;
    public static final String SELECT_TAGS_BY_CATEGORY_AND_NAME;
    public static final String SELECT_TAGS_BY_CATEGORY;
    public static final String SELECT_TAGS_BY_NAME;
    public static final String DELETE_TAGS;
    public static final String DELETE_TAGS_BY_NAME;

    static {
        keyspace = AlertProperties.getProperty(CASSANDRA_KEYSPACE, "hawkular_alerts");

        DELETE_ALERT_STATUS = "DELETE FROM " + keyspace + ".alerts_statuses "
                + "WHERE tenantId = ? AND status = ? AND alertId = ? ";

        INSERT_ALERT = "INSERT INTO " + keyspace + ".alerts " + "(tenantId, alertId, payload) VALUES (?, ?, ?) ";

        INSERT_ALERT_TRIGGER = "INSERT INTO " + keyspace + ".alerts_triggers "
                + "(tenantId, alertId, triggerId) VALUES (?, ?, ?) ";

        INSERT_ALERT_CTIME = "INSERT INTO " + keyspace + ".alerts_ctimes "
                + "(tenantId, alertId, ctime) VALUES (?, ?, ?) ";

        INSERT_ALERT_STATUS = "INSERT INTO " + keyspace + ".alerts_statuses "
                + "(tenantId, alertId, status) VALUES (?, ?, ?) ";

        SELECT_ALERT_STATUS = "SELECT alertId, status FROM " + keyspace + ".alerts_statuses "
                + "WHERE tenantId = ? AND status = ? AND alertId = ? ";

        SELECT_ALERTS_BY_TENANT = "SELECT payload FROM " + keyspace + ".alerts " + "WHERE tenantId = ? ";

        SELECT_ALERTS_BY_TENANT_AND_ALERT = "SELECT payload FROM " + keyspace + ".alerts "
                + "WHERE tenantId = ? AND alertId = ? ";

        SELECT_ALERTS_TRIGGERS = "SELECT alertId FROM " + keyspace + ".alerts_triggers " + "WHERE tenantId = ? AND "
                + "triggerId = ? ";

        SELECT_ALERT_CTIME_START_END = "SELECT alertId FROM " + keyspace + ".alerts_ctimes "
                + "WHERE tenantId = ? AND ctime >= ? AND ctime <= ? ";

        SELECT_ALERT_CTIME_START = "SELECT alertId FROM " + keyspace + ".alerts_ctimes "
                + "WHERE tenantId = ? AND ctime >= ? ";

        SELECT_ALERT_CTIME_END = "SELECT alertId FROM " + keyspace + ".alerts_ctimes "
                + "WHERE tenantId = ? AND ctime <= ? ";

        SELECT_ALERT_STATUS_BY_TENANT_AND_STATUS = "SELECT alertId FROM " + keyspace + ""
                + ".alerts_statuses WHERE tenantId = ? AND status = ? ";

        SELECT_TAGS_TRIGGERS_BY_CATEGORY_AND_NAME = "SELECT triggers FROM " + keyspace + ""
                + ".tags_triggers WHERE tenantId = ? AND category = ? AND name = ? ";

        SELECT_TAGS_TRIGGERS_BY_CATEGORY = "SELECT triggers FROM " + keyspace + ""
                + ".tags_triggers WHERE tenantId = ? AND category = ? ";

        SELECT_TAGS_TRIGGERS_BY_NAME = "SELECT triggers FROM " + keyspace + ""
                + ".tags_triggers WHERE tenantId = ? AND name = ? ";

        UPDATE_ALERT = "UPDATE " + keyspace + ".alerts " + "SET payload = ? WHERE tenantId = ? AND alertId = ? ";

        INSERT_TRIGGER = "INSERT INTO " + keyspace + ".triggers " +
                "(name, description, autoDisable, autoResolve, autoResolveAlerts, firingMatch, " +
                "autoResolveMatch, id, enabled, tenantId) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";

        INSERT_TRIGGER_ACTIONS = "INSERT INTO " + keyspace + ".triggers_actions " +
                "(tenantId, triggerId, actionPlugin, actions) VALUES (?, ?, ?, ?) ";

        SELECT_TRIGGER_CONDITIONS = "SELECT triggerId, triggerMode, type, conditionSetSize, " +
                "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange, tenantId"
                +
                " FROM " + keyspace + ".conditions " +
                "WHERE tenantId = ? AND triggerId = ? ORDER BY triggerId, triggerMode, type";

        SELECT_TRIGGER_CONDITIONS_TRIGGER_MODE = "SELECT triggerId, triggerMode, type, " +
                "conditionSetSize, conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, " +
                "pattern, ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange,"
                +
                " tenantId " +
                "FROM " + keyspace + ".conditions " +
                "WHERE tenantId = ? AND triggerId = ? AND triggerMode = ? " +
                "ORDER BY triggerId, triggerMode, type";

        DELETE_CONDITIONS_MODE = "DELETE FROM " + keyspace + ".conditions " +
                "WHERE tenantId = ? AND triggerId = ? AND triggerMode = ? ";

        INSERT_AVAILABILITY_CONDITION = "INSERT INTO " + keyspace + ".conditions " +
                "(tenantId, triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, " +
                "dataId, operator) VALUES (?, ?, ?, 'AVAILABILITY', ?, ?, ?, ?, ?) ";

        INSERT_COMPARE_CONDITION = "INSERT INTO " + keyspace + ".conditions " +
                "(tenantId, triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, " +
                "dataId, operator, data2Id, data2Multiplier) VALUES (?, ?, ?, 'COMPARE', ?, ?, ?, ?, ?, ?, ?) ";

        INSERT_STRING_CONDITION = "INSERT INTO " + keyspace + ".conditions " +
                "(tenantId, triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, " +
                "dataId, operator, pattern, ignoreCase) VALUES (?, ?, ?, 'STRING', ?, ?, ?, ?, ?, ?, ?) ";

        INSERT_THRESHOLD_CONDITION = "INSERT INTO " + keyspace + ".conditions " +
                "(tenantId, triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, " +
                "dataId, operator, threshold) VALUES (?, ?, ?, 'THRESHOLD', ?, ?, ?, ?, ?, ?) ";

        INSERT_THRESHOLD_RANGE_CONDITION = "INSERT INTO " + keyspace + ".conditions " +
                "(tenantId, triggerId, triggerMode, type, conditionSetSize, conditionSetIndex, conditionId, " +
                "dataId, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange) " +
                "VALUES (?, ?, ?, 'RANGE', ?, ?, ?, ?, ?, ?, ?, ?, ?) ";

        INSERT_TAG = "INSERT INTO " + keyspace + ".tags " +
                "(tenantId, triggerId, category, name, visible) VALUES (?, ?, ?, ?, ?) ";

        INSERT_DAMPENING = "INSERT INTO " + keyspace + ".dampenings " +
                "(triggerId, triggerMode, type, evalTrueSetting, evalTotalSetting, evalTimeSetting, " +
                "dampeningId, tenantId) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ";

        INSERT_ACTION = "INSERT INTO " + keyspace + ".actions " +
                "(tenantId, actionPlugin, actionId, properties) VALUES (?, ?, ?, ?) ";

        SELECT_ALL_TRIGGERS = "SELECT name, description, autoDisable, autoResolve, " +
                "autoResolveAlerts, firingMatch, autoResolveMatch, id, enabled, tenantId " +
                "FROM " + keyspace + ".triggers ";

        SELECT_TRIGGER_ACTIONS = "SELECT tenantId, triggerId, actionPlugin, actions " +
                "FROM " + keyspace + ".triggers_actions " +
                "WHERE tenantId = ? AND triggerId = ? ";

        SELECT_TENANT_TRIGGERS = "SELECT name, description, autoDisable, autoResolve, " +
                "autoResolveAlerts, firingMatch, autoResolveMatch, id, enabled, tenantId " +
                "FROM " + keyspace + ".triggers WHERE tenantId = ? ";

        SELECT_ALL_CONDITIONS = "SELECT triggerId, triggerMode, type, conditionSetSize, " +
                "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange," +
                "tenantId FROM " + keyspace + ".conditions ";

        SELECT_ALL_CONDITIONS_BY_TENANT = "SELECT triggerId, triggerMode, type, conditionSetSize, " +
                "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange," +
                "tenantId FROM " + keyspace + ".conditions WHERE tenantId = ? ";

        SELECT_ALL_DAMPENINGS = "SELECT tenantId, triggerId, triggerMode, type, evalTrueSetting, " +
                "evalTotalSetting, evalTimeSetting, dampeningId FROM " + keyspace + ".dampenings ";

        SELECT_ALL_DAMPENINGS_BY_TENANT = "SELECT tenantId, triggerId, triggerMode, type, " +
                "evalTrueSetting, " +
                "evalTotalSetting, evalTimeSetting, dampeningId FROM " + keyspace + ".dampenings " +
                "WHERE tenantId = ? ";

        SELECT_ALL_ACTIONS = "SELECT tenantId, actionPlugin, actionId " +
                "FROM " + keyspace + ".actions ";

        SELECT_ALL_ACTIONS_BY_TENANT = "SELECT actionPlugin, actionId " +
                "FROM " + keyspace + ".actions WHERE tenantId = ? ";

        SELECT_TRIGGER = "SELECT name, description, autoDisable, autoResolve, " +
                "autoResolveAlerts, firingMatch, autoResolveMatch, id, enabled, tenantId " +
                "FROM " + keyspace + ".triggers WHERE tenantId = ? AND id = ? ";

        SELECT_TRIGGER_DAMPENINGS = "SELECT tenantId, triggerId, triggerMode, type, " +
                "evalTrueSetting, evalTotalSetting, evalTimeSetting, dampeningId " +
                "FROM " + keyspace + ".dampenings " +
                "WHERE tenantId = ? AND triggerId = ? ";

        SELECT_TRIGGER_DAMPENINGS_MODE = "SELECT tenantId, triggerId, triggerMode, type, " +
                "evalTrueSetting, evalTotalSetting, evalTimeSetting, dampeningId " +
                "FROM " + keyspace + ".dampenings " +
                "WHERE tenantId = ? AND triggerId = ? and triggerMode = ? ";

        DELETE_DAMPENINGS = "DELETE FROM " + keyspace + ".dampenings " +
                "WHERE tenantId = ? AND triggerId = ? ";

        DELETE_CONDITIONS = "DELETE FROM " + keyspace + ".conditions " +
                "WHERE tenantId = ? AND triggerId = ? ";

        DELETE_TRIGGERS = "DELETE FROM " + keyspace + ".triggers " +
                "WHERE tenantId = ? AND id = ? ";

        UPDATE_TRIGGER = "UPDATE " + keyspace + ".triggers " +
                "SET name = ?, description = ?, autoDisable = ?, autoResolve = ?, autoResolveAlerts = ?, " +
                "firingMatch = ?, autoResolveMatch = ?, enabled = ? " +
                "WHERE tenantId = ? AND id = ? ";

        DELETE_TRIGGER_ACTIONS = "DELETE FROM " + keyspace + ".triggers_actions " +
                "WHERE tenantId = ? AND triggerId = ? ";

        DELETE_DAMPENING_ID = "DELETE FROM " + keyspace + ".dampenings " +
                "WHERE tenantId = ? AND triggerId = ? AND triggerMode = ? AND dampeningId = ? ";

        SELECT_DAMPENING_ID = "SELECT triggerId, triggerMode, type, evalTrueSetting, " +
                "evalTotalSetting, evalTimeSetting, dampeningId, tenantId FROM " + keyspace + ".dampenings " +
                "WHERE tenantId = ? AND dampeningId = ? ";

        UPDATE_DAMPENING_ID = "UPDATE " + keyspace + ".dampenings " +
                "SET type = ?, evalTrueSetting = ?, evalTotalSetting = ?, evalTimeSetting = ? " +
                "WHERE tenantId = ? AND triggerId = ? AND triggerMode = ? AND dampeningId = ? ";

        SELECT_CONDITION_ID = "SELECT triggerId, triggerMode, type, conditionSetSize, " +
                "conditionSetIndex, conditionId, dataId, operator, data2Id, data2Multiplier, pattern, " +
                "ignoreCase, threshold, operatorLow, operatorHigh, thresholdLow, thresholdHigh, inRange, " +
                "tenantId " +
                "FROM " + keyspace + ".conditions WHERE tenantId = ? AND conditionId = ? ";

        INSERT_ACTION_PLUGIN = "INSERT INTO " + keyspace + ".action_plugins (actionPlugin, " +
                "properties) VALUES (?, ?) ";

        DELETE_ACTION_PLUGIN = "DELETE FROM " + keyspace + ".action_plugins WHERE actionPlugin = ? ";

        UPDATE_ACTION_PLUGIN = "UPDATE " + keyspace + ".action_plugins " +
                "SET properties = ? WHERE actionPlugin = ? ";

        SELECT_ACTION_PLUGINS = "SELECT actionPlugin FROM " + keyspace + ".action_plugins";

        SELECT_ACTION_PLUGIN = "SELECT properties FROM " + keyspace + ".action_plugins " +
                "WHERE actionPlugin = ? ";

        DELETE_ACTION = "DELETE FROM " + keyspace + ".actions " +
                "WHERE tenantId = ? AND actionPlugin = ? AND actionId = ? ";

        UPDATE_ACTION = "UPDATE " + keyspace + ".actions " +
                "SET properties = ? " +
                "WHERE tenantId = ? AND actionPlugin = ? AND actionId = ? ";

        SELECT_ACTIONS_PLUGIN = "SELECT actionId FROM " + keyspace + ".actions " +
                "WHERE tenantId = ? AND actionPlugin = ? ";

        SELECT_ACTION = "SELECT properties FROM " + keyspace + ".actions " +
                "WHERE tenantId = ? AND actionPlugin = ? AND actionId = ? ";

        INSERT_TAGS_TRIGGERS = "INSERT INTO " + keyspace + ".tags_triggers " +
                "(tenantId, category, name, triggers) VALUES (?, ?, ?, ?) ";

        UPDATE_TAGS_TRIGGERS = "UPDATE " + keyspace + ".tags_triggers " +
                "SET triggers = ? " +
                "WHERE tenantId = ? AND name = ? ";

        DELETE_TAGS_TRIGGERS = "DELETE FROM " + keyspace + ".tags_triggers " +
                "WHERE tenantId = ? AND name = ? ";

        SELECT_TAGS = "SELECT tenantId, triggerId, category, name, visible FROM " + keyspace +
                ".tags WHERE tenantId = ? AND triggerId = ? ORDER BY triggerId, name ";

        SELECT_TAGS_BY_CATEGORY_AND_NAME = "SELECT tenantId, triggerId, category, name, visible FROM " +
                keyspace + ".tags WHERE tenantId = ? AND triggerId = ? AND category = ? AND name = ? ";

        SELECT_TAGS_BY_CATEGORY = "SELECT tenantId, triggerId, category, name, visible FROM " +
                keyspace + ".tags WHERE tenantId = ? AND triggerId = ? AND category = ? ";

        SELECT_TAGS_BY_NAME = "SELECT tenantId, triggerId, category, name, visible FROM " +
                keyspace + ".tags WHERE tenantId = ? AND triggerId = ? AND name = ? ";

        DELETE_TAGS = "DELETE FROM " + keyspace + ".tags WHERE tenantId = ? AND triggerId = ? ";

        DELETE_TAGS_BY_NAME = "DELETE FROM " + keyspace + ".tags " +
                "WHERE tenantId = ? AND triggerId = ? AND name = ?";
    }

    public static synchronized PreparedStatement get(Session session, String statement) {
        PreparedStatement result = statementMap.get(statement);
        if (null == result) {
            result = session.prepare(statement);
            statementMap.put(statement, result);
        }
        return result;
    }
}
