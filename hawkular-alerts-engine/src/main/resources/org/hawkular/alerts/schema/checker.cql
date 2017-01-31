--
-- Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
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
-- NOTE: Statements must be preceded by -- #
--
-- Comments that start with a double dash like this are suppressed.

-- Pattern for checker queries:
--
-- SELECT table_name FROM system_schema.tables
-- WHERE keyspace_name='<keyspace_to_validate>' AND table_name = '<table_to_validate>';
--
-- An empty query will means that a table (or object) was not correctly created.

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'triggers';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'triggers_actions';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'conditions';

-- #

SELECT column_name FROM system_schema.columns
WHERE keyspace_name='${keyspace}' AND table_name = 'conditions' AND column_name = 'interval';

-- #

SELECT column_name FROM system_schema.columns
WHERE keyspace_name='${keyspace}' AND table_name = 'conditions' AND column_name = 'activerules';

-- #

SELECT column_name FROM system_schema.columns
WHERE keyspace_name='${keyspace}' AND table_name = 'conditions' AND column_name = 'samplesize';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'dampenings';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'action_plugins';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'actions_definitions';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'actions_history';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'actions_history_actions';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'actions_history_alerts';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'actions_history_ctimes';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'actions_history_results';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'tags';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'alerts';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'alerts_triggers';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'alerts_ctimes';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'alerts_lifecycle';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'events';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'events_triggers';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'events_ctimes';

-- #

SELECT table_name FROM system_schema.tables
WHERE keyspace_name='${keyspace}' AND table_name = 'events_categories';