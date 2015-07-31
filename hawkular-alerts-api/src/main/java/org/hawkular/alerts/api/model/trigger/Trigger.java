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
package org.hawkular.alerts.api.model.trigger;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A trigger definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Trigger extends TriggerTemplate {

    public enum Mode {
        FIRING, AUTORESOLVE
    };

    @JsonInclude
    private String id;

    @JsonInclude
    private boolean enabled;

    @JsonIgnore
    private Mode mode;

    @JsonIgnore
    private transient Match match;

    @JsonInclude
    private String tenantId;

    public Trigger() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("defaultName");
    }

    public Trigger(String name) {
        this(generateId(), name, null);
    }

    public Trigger(String name, Map<String, String> context) {
        this(generateId(), name, context);
    }

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    public Trigger(String id, String name) {
        this(id, name, null);
    }

    public Trigger(String id, String name, Map<String, String> context) {
        super(name, context);

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Trigger id must be non-empty");
        }
        this.id = id;

        this.enabled = false;
        this.mode = Mode.FIRING;
        this.match = Match.ALL;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonIgnore
    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    @JsonIgnore
    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    @Override
    public void setFiringMatch(Match firingMatch) {
        super.setFiringMatch(firingMatch);
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    @Override
    public void setAutoResolveMatch(Match autoResolveMatch) {
        super.setAutoResolveMatch(autoResolveMatch);
        setMatch(this.mode == Mode.FIRING ? getFiringMatch() : getAutoResolveMatch());
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Trigger trigger = (Trigger)o;

        if (id != null ? !id.equals(trigger.id) : trigger.id != null)
            return false;
        return !(tenantId != null ? !tenantId.equals(trigger.tenantId) : trigger.tenantId != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Trigger [tenantId=" + tenantId + " id=" + id + ", enabled=" + enabled + ", mode=" + mode +
                ", getName()=" + getName() + ", isAutoDisable()=" + isAutoDisable() + ", isAutoEnable()="
                + isAutoEnable() + ", isAutoResolve()=" + isAutoResolve() + ", context=" + context + "]";
    }

}
