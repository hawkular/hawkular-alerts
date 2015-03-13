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
 * An immutable class. Each instance is a tag
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class Tag {

    private String triggerId;
    private String category;
    private String name;
    private boolean visible;

    /**
     * Create an invisible Tag.
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

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public String toString() {
        return "Tag [triggerId=" + triggerId + ", category=" + category + ", name=" + name + ", visible=" + visible
                + "]";
    }

}
