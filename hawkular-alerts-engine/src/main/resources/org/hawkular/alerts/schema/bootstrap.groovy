/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.schema

import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.SimpleStatement

def executeCQL(String cql, Integer readTimeoutMillis = null) {
  def statement = new SimpleStatement(cql)
  statement.consistencyLevel = ConsistencyLevel.LOCAL_QUORUM

  if (readTimeoutMillis) {
    statement.readTimeoutMillis = readTimeoutMillis
  }

  return session.execute(statement)
}

def createTable(String table, String cql) {
  if (tableDoesNotExist(keyspace, table)) {
    logger.info("Creating table $table")
    executeCQL(cql)
  }
}

if (reset) {
  executeCQL("DROP KEYSPACE IF EXISTS $keyspace", 20000)
}

executeCQL("CREATE KEYSPACE IF NOT EXISTS $keyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
executeCQL("USE $keyspace")

createTable('triggers', """
CREATE TABLE triggers (
    tenantId text,
    id text,
    autoDisable boolean,
    autoEnable boolean,
    autoResolve boolean,
    autoResolveAlerts boolean,
    autoResolveMatch text,
    context map<text,text>,
    dataIdMap map<text,text>,
    description text,
    enabled boolean,
    eventCategory text,
    eventText text,
    eventType text,
    firingMatch text,
    source text,
    memberOf text,
    name text,
    severity text,
    type text,
    tags map<text,text>,
    PRIMARY KEY (tenantId, id)
)
""")

createTable('triggers_actions', """
CREATE TABLE triggers_actions (
    tenantId text,
    triggerId text,
    actionPlugin text,
    actionId text,
    payload text,
    PRIMARY KEY (tenantId, triggerId, actionPlugin, actionId)
)
""")

createTable('conditions', """
CREATE TABLE conditions (
    tenantId text,
    triggerId text,
    triggerMode text,
    type text,
    conditionSetSize int,
    conditionSetIndex int,
    conditionId text,
    dataId text,
    operator text,
    data2Id text,
    data2Multiplier double,
    pattern text,
    ignoreCase boolean,
    threshold double,
    operatorLow text,
    operatorHigh text,
    thresholdLow double,
    thresholdHigh double,
    inRange boolean,
    direction text,
    period text,
    context map<text,text>,
    PRIMARY KEY (tenantId, triggerId, triggerMode, conditionId)
)
""")

executeCQL("""
CREATE INDEX IF NOT EXISTS conditions_id ON conditions(conditionId)
""")

createTable('dampenings', """
CREATE TABLE dampenings (
    tenantId text,
    triggerId text,
    triggerMode text,
    type text,
    evalTrueSetting int,
    evalTotalSetting int,
    evalTimeSetting bigint,
    dampeningId text,
    PRIMARY KEY (tenantId, triggerId, triggerMode, dampeningId)
)
""")

executeCQL("""
CREATE INDEX IF NOT EXISTS dampenings_id ON dampenings(dampeningId)
""")

createTable('action_plugins', """
CREATE TABLE action_plugins (
    actionPlugin text,
    properties set<text>,
    defaultProperties map<text, text>,
    PRIMARY KEY (actionPlugin)
)
""")

createTable('actions_definitions', """
CREATE TABLE actions_definitions (
    tenantId text,
    actionId text,
    actionPlugin text,
    payload text,
    PRIMARY KEY (tenantId, actionPlugin, actionId)
)
""")

createTable('actions_history', """
CREATE TABLE actions_history (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    payload text,
    PRIMARY KEY (tenantId, actionPlugin, actionId, alertId, ctime)
)
""")

createTable('actions_history_actions', """
CREATE TABLE actions_history_actions (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, actionId, actionPlugin, alertId, ctime)
)
""")

createTable('actions_history_alerts', """
CREATE TABLE actions_history_alerts (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, alertId, actionPlugin, actionId, ctime)
)
""")

createTable('actions_history_ctimes', """
CREATE TABLE actions_history_ctimes (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, actionPlugin, actionId, alertId)
)
""")

createTable('actions_history_results', """
CREATE TABLE actions_history_results (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    result text,
    PRIMARY KEY (tenantId, result, actionPlugin, actionId, alertId, ctime)
)
""")

createTable('tags', """
CREATE TABLE tags (
    tenantId text,
    type text,
    name text,
    value text,
    id text,
    PRIMARY KEY (( tenantId, type, name ), value, id)
)
""")

createTable('alerts', """
CREATE TABLE alerts (
    tenantId text,
    alertId text,
    payload text,
    PRIMARY KEY (tenantId, alertId)
)
""")

createTable('alerts_triggers', """
CREATE TABLE alerts_triggers (
    tenantId text,
    alertId text,
    triggerId text,
    PRIMARY KEY (tenantId, triggerId, alertId)
)
""")

createTable('alerts_ctimes', """
CREATE TABLE alerts_ctimes (
    tenantId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, alertId)
)
""")

createTable('alerts_statuses', """
CREATE TABLE alerts_statuses (
    tenantId text,
    alertId text,
    status text,
    PRIMARY KEY (tenantId, status, alertId)
)
""")

createTable('alerts_severities', """
CREATE TABLE alerts_severities (
    tenantId text,
    alertId text,
    severity text,
    PRIMARY KEY (tenantId, severity, alertId)
)
""")

createTable('alerts_lifecycle', """
CREATE TABLE alerts_lifecycle (
    tenantId text,
    alertId text,
    status text,
    stime bigint,
    PRIMARY KEY (tenantId, status, stime, alertId)
)
""")

createTable('events', """
CREATE TABLE events (
    tenantId text,
    id text,
    payload text,
    PRIMARY KEY (tenantId, id)
)
""")

createTable('events_triggers', """
CREATE TABLE events_triggers (
    tenantId text,
    id text,
    triggerId text,
    PRIMARY KEY (tenantId, triggerId, id)
)
""")

createTable('events_ctimes', """
CREATE TABLE events_ctimes (
    tenantId text,
    id text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, id)
)
""")

createTable('events_categories', """
CREATE TABLE events_categories (
    tenantId text,
    id text,
    category text,
    PRIMARY KEY ((tenantId, category), id)
)
""")