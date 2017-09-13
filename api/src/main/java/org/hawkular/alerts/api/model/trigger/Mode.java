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
 * The mode of the Trigger.  Triggers always start in <code>FIRING<code> mode.  If the auto-resolve feature is enabled
 * for the Trigger, then it will switch to <code>AUTORESOLVE<code> mode after firing.  When the auto-resolve condition
 * set is satisfied, or if the Trigger is reloaded (manually, via edit, or at startup), the trigger returns to
 * <code>FIRING<code> mode.  The mode is also needed when defining a trigger, to indicate the relevant mode for a
 * conditions or dampening definition.
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public enum Mode {
    FIRING, AUTORESOLVE
};
