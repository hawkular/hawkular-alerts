--
-- Copyright 2015 Red Hat, Inc. and/or its affiliates
-- and other contributors as indicated by the @author tags.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- This CQL file is used to create the schema temporarily until we get a better schema
-- change management tool in place. The file is parsed and executed by SchemaManger.
--
-- NOTE: Statements must must be preceded by -- #
--
-- Comments that start with a double dash like this are suppressed.

-- #

CREATE KEYSPACE ${keyspace} WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}

-- #

CREATE TABLE ${keyspace}.triggers (
    tenantId text,
    id text,
    name text,
    context map<text,text>,
    autoDisable boolean,
    autoEnable boolean,
    autoResolve boolean,
    autoResolveAlerts boolean,
    autoResolveMatch text,
    memberOf text,
    description text,
    enabled boolean,
    firingMatch text,
    orphan boolean,
    group boolean,
    severity text,
    PRIMARY KEY (tenantId, id)
);

-- #

CREATE TABLE ${keyspace}.triggers_actions (
    tenantId text,
    triggerId text,
    actionPlugin text,
    actions set<text>,
    PRIMARY KEY (tenantId, triggerId, actionPlugin)
);

-- #
-- Note that the conditionId is a composed id: triggerId-triggerMode-csSize-csIndex. It provides a natural ordering.
CREATE TABLE ${keyspace}.conditions (
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
    PRIMARY KEY (tenantId, triggerId, triggerMode, conditionId)
);

-- #

CREATE INDEX conditions_id ON ${keyspace}.conditions(conditionId);

-- #

CREATE TABLE ${keyspace}.dampenings (
    tenantId text,
    triggerId text,
    triggerMode text,
    type text,
    evalTrueSetting int,
    evalTotalSetting int,
    evalTimeSetting bigint,
    dampeningId text,
    PRIMARY KEY (tenantId, triggerId, triggerMode, dampeningId)
);

-- #

CREATE INDEX dampenings_id ON ${keyspace}.dampenings(dampeningId);

-- #

CREATE TABLE ${keyspace}.action_plugins (
    actionPlugin text,
    properties set<text>,
    defaultProperties map<text, text>,
    PRIMARY KEY (actionPlugin)
);

-- #

CREATE TABLE ${keyspace}.actions (
    tenantId text,
    actionId text,
    actionPlugin text,
    properties map<text, text>,
    PRIMARY KEY (tenantId, actionPlugin, actionId)
);

-- #

CREATE TABLE ${keyspace}.tags (
    tenantId text,
    triggerId text,
    category text,
    name text,
    visible boolean,
    PRIMARY KEY (tenantId, triggerId, name)
);

-- #

CREATE INDEX tags_category ON ${keyspace}.tags(category);

-- #

CREATE TABLE ${keyspace}.tags_triggers (
    tenantId text,
    category text,
    name text,
    triggers set<text>,
    PRIMARY KEY (tenantId, name)
);

-- #

CREATE INDEX tags_triggers_category ON ${keyspace}.tags_triggers(category);

-- #

CREATE TABLE ${keyspace}.alerts (
    tenantId text,
    alertId text,
    payload text,
    PRIMARY KEY (tenantId, alertId)
);

-- #

CREATE TABLE ${keyspace}.alerts_triggers (
    tenantId text,
    alertId text,
    triggerId text,
    PRIMARY KEY (tenantId, triggerId, alertId)
);

-- #

CREATE TABLE ${keyspace}.alerts_ctimes (
    tenantId text,
    alertId text,
    ctime bigint,
    PRIMARY KEY (tenantId, ctime, alertId)
);

-- #

CREATE TABLE ${keyspace}.alerts_statuses (
    tenantId text,
    alertId text,
    status text,
    PRIMARY KEY (tenantId, status, alertId)
);

-- #

CREATE TABLE ${keyspace}.alerts_severities (
    tenantId text,
    alertId text,
    severity text,
    PRIMARY KEY (tenantId, severity, alertId)
);
