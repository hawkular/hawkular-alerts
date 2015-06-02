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
