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
    name text,
    description text,
    autoDisable boolean,
    autoResolve boolean,
    autoResolveAlerts boolean,
    actions set<text>,
    firingMatch text,
    autoResolveMatch text,
    id text,
    enabled boolean,
    PRIMARY KEY (id)
);

-- #

CREATE TABLE ${keyspace}.conditions (
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
    PRIMARY KEY (triggerId, triggerMode, type, conditionId)
);

-- #

CREATE INDEX conditions_id ON ${keyspace}.conditions(conditionId);

-- #

CREATE TABLE ${keyspace}.dampenings (
    triggerId text,
    triggerMode text,
    type text,
    evalTrueSetting int,
    evalTotalSetting int,
    evalTimeSetting bigint,
    dampeningId text,
    PRIMARY KEY (triggerId, triggerMode, dampeningId)
);

-- #

CREATE INDEX dampenings_id ON ${keyspace}.dampenings(dampeningId);

-- #

CREATE TABLE ${keyspace}.action_plugins (
    actionPlugin text,
    properties set<text>,
    PRIMARY KEY (actionPlugin)
);

-- #

CREATE TABLE ${keyspace}.actions (
    actionId text,
    actionPlugin text,
    properties map<text, text>,
    PRIMARY KEY (actionId)
);

-- #

CREATE INDEX action_plugin ON ${keyspace}.actions(actionPlugin);

-- #

CREATE TABLE ${keyspace}.tags (
    triggerId text,
    category text,
    name text,
    visible boolean,
    PRIMARY KEY (triggerId, category, name)
);

-- #

CREATE TABLE ${keyspace}.tags_triggers (
    category text,
    name text,
    triggers set<text>,
    PRIMARY KEY (category, name)
);

-- #

CREATE TABLE ${keyspace}.alerts (
    alertId text,
    payload text,
    PRIMARY KEY (alertId)
);

-- #

CREATE TABLE ${keyspace}.alerts_triggers (
    alertId text,
    triggerId text,
    PRIMARY KEY (triggerId, alertId)
);

-- #

CREATE TABLE ${keyspace}.alerts_ctimes (
    alertId text,
    ctime bigint,
    PRIMARY KEY (alertId, ctime)
);

-- #

CREATE TABLE ${keyspace}.alerts_statuses (
    alertId text,
    status text,
    PRIMARY KEY (status, alertId)
);