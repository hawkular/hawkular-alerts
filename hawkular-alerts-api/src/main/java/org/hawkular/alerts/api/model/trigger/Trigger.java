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

/**
 * A trigger definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Trigger extends TriggerTemplate {

    public enum Mode {
        FIRE, SAFETY
    };

    private String id;
    private boolean enabled;
    private boolean safetyEnabled;
    private Mode mode;

    private transient Match match;

    public Trigger() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("DefaultId", null);
    }

    public Trigger(String id, String name) {
        super(name);

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Trigger id must be non-empty");
        }
        this.id = id;

        this.enabled = false;
        this.safetyEnabled = false;
        this.mode = Mode.FIRE;
        this.match = getFiringMatch();
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

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        setMatch(this.mode == Mode.FIRE ? getFiringMatch() : getSafetyMatch());
    }

    /**
     * This tells you whether the Trigger defines safety conditions and whether safety mode is enabled.
     * This does NOT return the current <code>mode</code> of the Trigger.
     * @return true if this Trigger supports safety mode and is it enabled.
     * @see {@link #getMode()} to see the current <code>mode</code>.
     */
    public boolean isSafetyEnabled() {
        return safetyEnabled;
    }

    /**
     * Set true if safety conditions and dampening are fully defined and should be activated on a Trigger firing. Set
     * false otherwise.
     * @param safetyEnabled
     */
    public void setSafetyEnabled(boolean safetyEnabled) {
        this.safetyEnabled = safetyEnabled;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (enabled ? 1231 : 1237);
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        Trigger other = (Trigger) obj;
        if (enabled != other.enabled)
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Trigger [id=" + id + ", enabled=" + enabled + ", mode=" + mode + ", match=" + getMatch() + "]";
    }

}
