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
 * A Trigger can have zero or more Tags.  For storing a Tag the specified Trigger must exist for the specified Tenant
 * and <code>name</code> is required.
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class Tag {

    private String tenantId;
    private String triggerId;
    private String category;
    private String name;
    private boolean visible;

    public Tag() {
        // for json only
    }

    /**
     * @param name NotEmpty
     */
    public Tag(String name) {
        this(null, null, name, false);
    }

    /**
     * @param category Nullable
     * @param name NotEmpty
     */
    public Tag(String category, String name) {
        this(null, category, name, false);
    }

    /**
     * @param triggerId Nullable
     * @param category Nullable
     * @param name NotEmpty
     */
    public Tag(String triggerId, String category, String name) {
        this(triggerId, category, name, false);
    }

    /**
     * @param triggerId Nullable
     * @param category Nullable
     * @param name NotEmpty
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

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag tag = (Tag) o;

        if (visible != tag.visible) return false;
        if (tenantId != null ? !tenantId.equals(tag.tenantId) : tag.tenantId != null) return false;
        if (triggerId != null ? !triggerId.equals(tag.triggerId) : tag.triggerId != null) return false;
        if (category != null ? !category.equals(tag.category) : tag.category != null) return false;
        return !(name != null ? !name.equals(tag.name) : tag.name != null);

    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
        result = 31 * result + (category != null ? category.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (visible ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Tag{" +
                "tenantId='" + tenantId + '\'' +
                ", triggerId='" + triggerId + '\'' +
                ", category='" + category + '\'' +
                ", name='" + name + '\'' +
                ", visible=" + visible +
                '}';
    }
}
