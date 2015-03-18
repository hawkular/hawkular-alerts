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
 * Each instance is a tag
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class Tag {

    private String triggerId;
    private String category;
    private String name;
    private boolean visible;

    public Tag() {
        // for json only
    }

    /**
     * Create a searchable Tag on name only
     *
     * @param tag @NotEmpty
     */
    public Tag(String name) {
        this(null, null, name, false);
    }

    /**
     * Create a searchable Tag on category + name
     *
     * @param category @Nullable
     * @param tag @NotEmpty
     */
    public Tag(String category, String tag) {
        this(null, category, tag, false);
    }

    /**
     * Create an invisible Tag for persisting.
     *
     * @param triggerId @Nullable Note, required for storage but not search.
     * @param category @Nullable
     * @param tag @NotEmpty
     */
    public Tag(String triggerId, String category, String tag) {
        this(triggerId, category, tag, false);
    }

    /**
     * @param triggerId @Nullable Note, required for storage but not search.
     * @param category @Nullable
     * @param name @NotEmpty
     * @param visible flag indicating whether this tag is available for display
     */
    public Tag(String triggerId, String category, String name, boolean visible) {
        super();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Tag can not be null or empty.");
        }

        this.triggerId = triggerId;
        this.category = category;
        this.name = name;
        this.visible = visible;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public String toString() {
        return "Tag [triggerId=" + triggerId + ", category=" + category + ", name=" + name + ", visible=" + visible
                + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((category == null) ? 0 : category.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((triggerId == null) ? 0 : triggerId.hashCode());
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
        Tag other = (Tag) obj;
        if (category == null) {
            if (other.category != null)
                return false;
        } else if (!category.equals(other.category))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (triggerId == null) {
            if (other.triggerId != null)
                return false;
        } else if (!triggerId.equals(other.triggerId))
            return false;
        return true;
    }

}
