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
package org.hawkular.alerts.api.model.trigger;

/**
 * The type of a Group Trigger.
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public enum TriggerType {
    STANDARD, // Deployed, individually managed trigger
    GROUP, // Undeployed, template-level definition for managing a group of member triggers
    DATA_DRIVEN_GROUP, // Like group, but members are generated automatically based on incoming data
    MEMBER, // Deployed, member trigger of a group
    ORPHAN // Member trigger not being managed by the group. It maintains it's group reference and can be un-orphaned.
}
