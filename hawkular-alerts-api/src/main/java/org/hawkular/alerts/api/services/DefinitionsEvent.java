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
package org.hawkular.alerts.api.services;

import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Immutable definitions change event.  The target object depends on the change type.
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class DefinitionsEvent {

    public enum Type {
        DAMPENING_CHANGE,
        TRIGGER_CONDITION_CHANGE,
        TRIGGER_CREATE,
        TRIGGER_REMOVE,
        TRIGGER_UPDATE
    };

    private Type type;
    private String targetTenantId;
    private String targetId;

    public DefinitionsEvent(Type type, Dampening dampening) {
        this(type, dampening.getTenantId(), dampening.getDampeningId());
    }

    public DefinitionsEvent(Type type, Trigger trigger) {
        this(type, trigger.getTenantId(), trigger.getId());
    }

    public DefinitionsEvent(Type type, String targetTenantId, String targetId) {
        super();
        this.type = type;
        this.targetTenantId = targetTenantId;
        this.targetId = targetId;
    }

    public Type getType() {
        return type;
    }

    public String getTargetTenantId() {
        return targetTenantId;
    }

    public String getTargetId() {
        return targetId;
    }

    @Override
    public String toString() {
        return "DefinitionsEvent [type=" + type + ", targetTenantId=" + targetTenantId + ", targetId=" + targetId
                + "]";
    }

}
