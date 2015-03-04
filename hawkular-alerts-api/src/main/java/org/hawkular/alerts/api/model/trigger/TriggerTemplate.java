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

import java.util.HashSet;
import java.util.Set;

/**
 * A base template for trigger definition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class TriggerTemplate {

    public enum Match {
        ALL, ANY
    };

    private String name;
    private String description;

    /** A group of actions's ids. */
    private Set<String> actions;

    private transient Match firingMatch;
    private transient Match safetyMatch;

    public TriggerTemplate(String name) {
        this.name = name;

        this.firingMatch = Match.ALL;
        this.safetyMatch = Match.ALL;
        this.actions = new HashSet();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Trigger name must be non-empty.");
        }
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Match getFiringMatch() {
        return firingMatch;
    }

    public void setFiringMatch(Match firingMatch) {
        this.firingMatch = firingMatch;
    }

    public Match getSafetyMatch() {
        return safetyMatch;
    }

    public void setSafetyMatch(Match safetyMatch) {
        this.safetyMatch = safetyMatch;
    }

    public Set<String> getActions() {
        return actions;
    }

    public void setActions(Set<String> actions) {
        this.actions = actions;
    }

    public void addAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("ActionId must be non-empty.");
        }
        actions.add(actionId);
    }

    public void addActions(Set<String> actionIds) {
        if (actionIds == null) {
            return;
        }
        actions.addAll(actionIds);
    }

    public void removeAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) {
            throw new IllegalArgumentException("ActionId must be non-empty.");
        }
        actions.remove(actionId);
    }

    @Override
    public String toString() {
        return "TriggerTemplate [name=" + name + ", description=" + description + ", firingMatch=" + firingMatch
                + ", safetyMatch=" + safetyMatch + "]";
    }

}
