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
public class TriggerTemplate {

    public enum Match {
        ALL, ANY
    };

    private String name;
    private String description;
    private Match firingMatch;
    private Match safetyMatch;
    /** A group of notifier's ids. */
    private Set<String> notifiers;

    public TriggerTemplate(String name) {
        this.name = name;
        this.firingMatch = Match.ALL;
        this.safetyMatch = Match.ALL;
        this.notifiers = new HashSet();
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

    public Set<String> getNotifiers() {
        return notifiers;
    }

    public void setNotifiers(Set<String> notifiers) {
        this.notifiers = notifiers;
    }

    public void addNotifier(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Notifier id must be non-empty.");
        }
        notifiers.add(id);
    }

    public void addNotifiers(Set<String> ids) {
        if (ids == null) {
            return;
        }
        notifiers.addAll(ids);
    }

    public void removeNotifier(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Notifier id must be non-empty.");
        }
        notifiers.remove(id);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((firingMatch == null) ? 0 : firingMatch.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((safetyMatch == null) ? 0 : safetyMatch.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TriggerTemplate other = (TriggerTemplate) obj;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (firingMatch != other.firingMatch)
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (safetyMatch != other.safetyMatch)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "TriggerTemplate [name=" + name + ", description=" + description + ", firingMatch=" + firingMatch
                + ", safetyMatch=" + safetyMatch + "]";
    }

}
