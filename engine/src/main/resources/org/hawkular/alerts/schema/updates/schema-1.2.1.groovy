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

setKeyspace keyspace

schemaChange {
  version '1.0'
  author 'lponce'
  tags '1.2.x'
  cql """
CREATE TABLE sys_config (
        config_id text,
        name text,
        value text,
        PRIMARY KEY (config_id, name)
)
"""
  verify { tableExists(keyspace, "sys_config") }
}