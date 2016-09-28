/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

/**
 * Because we have releases of Hawkular Metrics prior to using cassalog, we need some special logic to initially
 * get things set up. That is the purpose of this script. If the target keyspace does not exist, then we have a new
 * installation and the script installs the schema as it exists prior to cassalog integration. If the target keyspace
 * exists, we check to see if it is already versions, i.e., managed by cassalog. If so, then we assume that there
 * is nothing left for this script to do. If the keyspace is not versioned, then we check that the schema matches
 * what we expect it to be to ensure we are in a known, consistent state before we start managing it with cassalog.
 */

def executeCQL(String cql, Integer readTimeoutMillis = null) {
  def statement = new SimpleStatement(cql)
  statement.consistencyLevel = ConsistencyLevel.LOCAL_QUORUM

  if (readTimeoutMillis) {
    statement.readTimeoutMillis = readTimeoutMillis
  }

  return session.execute(statement)
}

// We check the reset flag here because if we are resetting the database as would be the case in a dev/test environment,
// we don't need to worry about the state of the schema since we are dropping it.
if (!reset && keyspaceExists(keyspace)) {
  if (isSchemaVersioned(keyspace)) {
    // nothing to do
  } else {
    // If the schema exists and is not versioned, then we want to check that it matches what we expect it to be prior
    // to being managed with cassalog. We perform this check simply by checking the tables and UDTs in the keyspace.

    def expectedTables = [
        'triggers', 'triggers_actions', 'conditions', 'dampenings', 'action_plugins', 'actions_definitions',
        'actions_history', 'actions_history_actions', 'actions_history_alerts', 'actions_history_ctimes',
        'actions_history_results', 'tags', 'alerts', 'alerts_triggers', 'alerts_ctimes', 'alerts_statuses',
        'alerts_severities', 'alerts_lifecycle', 'events', 'events_triggers', 'events_ctimes', 'events_categories'
    ] as Set

    def actualTables = getTables(keyspace) as Set

    if (actualTables != expectedTables) {
      throw new RuntimeException("The schema is in an unknown state and cannot be versioned. Expected tables to be " +
        "$expectedTables but found $actualTables.")
    }
  }
} else {
  // If the schema does not already exist, we bypass cassalog's API and create it without
  // being versioned. This allows to use the same logic for subsequent schema changes
  // regardless of whether we are dealing with a new install or an upgrade.

  if (reset) {
    executeCQL("DROP KEYSPACE IF EXISTS $keyspace", 20000)
  }

  executeCQL("CREATE KEYSPACE $keyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}")
  executeCQL("USE $keyspace")

  executeCQL("""
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

  executeCQL("""
CREATE TABLE triggers_actions (
    tenantId text,
    triggerId text,
    actionPlugin text,
    actionId text,
    payload text,
    PRIMARY KEY (tenantId, triggerId, actionPlugin, actionId)
)
""")

  executeCQL("""
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
CREATE INDEX conditions_id ON conditions(conditionId)
""")

  executeCQL("""
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
CREATE INDEX dampenings_id ON dampenings(dampeningId)
""")

  executeCQL("""
CREATE TABLE action_plugins (
    actionPlugin text,
    properties set<text>,
    defaultProperties map<text, text>,
    PRIMARY KEY (actionPlugin)
)
""")

  executeCQL("""
CREATE TABLE actions_definitions (
    tenantId text,
    actionId text,
    actionPlugin text,
    payload text,
    PRIMARY KEY (tenantId, actionPlugin, actionId)
)
""")

  executeCQL("""
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

  executeCQL("""
CREATE TABLE actions_history_actions (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, actionId, actionPlugin, alertId, ctime)
)
""")

  executeCQL("""
CREATE TABLE actions_history_alerts (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, alertId, actionPlugin, actionId, ctime)
)
""")

  executeCQL("""
CREATE TABLE actions_history_ctimes (
    tenantId text,
    actionPlugin text,
    actionId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, actionPlugin, actionId, alertId)
)
""")

  executeCQL("""
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

  executeCQL("""
CREATE TABLE tags (
    tenantId text,
    type text,
    name text,
    value text,
    id text,
    PRIMARY KEY (( tenantId, type, name ), value, id)
)
""")

  executeCQL("""
CREATE TABLE alerts (
    tenantId text,
    alertId text,
    payload text,
    PRIMARY KEY (tenantId, alertId)
)
""")

  executeCQL("""
CREATE TABLE alerts_triggers (
    tenantId text,
    alertId text,
    triggerId text,
    PRIMARY KEY (tenantId, triggerId, alertId)
)
""")

  executeCQL("""
CREATE TABLE alerts_ctimes (
    tenantId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, alertId)
)
""")

  executeCQL("""
CREATE TABLE alerts_statuses (
    tenantId text,
    alertId text,
    status text,
    PRIMARY KEY (tenantId, status, alertId)
)
""")

  executeCQL("""
CREATE TABLE alerts_severities (
    tenantId text,
    alertId text,
    severity text,
    PRIMARY KEY (tenantId, severity, alertId)
)
""")

  executeCQL("""
CREATE TABLE alerts_lifecycle (
    tenantId text,
    alertId text,
    status text,
    stime bigint,
    PRIMARY KEY (tenantId, status, stime, alertId)
)
""")

  executeCQL("""
CREATE TABLE events (
    tenantId text,
    id text,
    payload text,
    PRIMARY KEY (tenantId, id)
)
""")

  executeCQL("""
CREATE TABLE events_triggers (
    tenantId text,
    id text,
    triggerId text,
    PRIMARY KEY (tenantId, triggerId, id)
)
""")

  executeCQL("""
CREATE TABLE events_ctimes (
    tenantId text,
    id text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, id)
)
""")

  executeCQL("""
CREATE TABLE events_categories (
    tenantId text,
    id text,
    category text,
    PRIMARY KEY ((tenantId, category), id)
)
""")

}