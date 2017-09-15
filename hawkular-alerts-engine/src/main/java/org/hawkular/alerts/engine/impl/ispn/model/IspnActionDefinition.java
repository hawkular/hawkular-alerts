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
package org.hawkular.alerts.engine.impl.ispn.model;

import java.io.Serializable;

import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Indexed(index = "actionDefinition")
public class IspnActionDefinition implements Serializable {
    @Field(store = Store.YES, analyze = Analyze.NO)
    private String tenantId;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String actionPlugin;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private String actionId;

    @Field(store = Store.YES, analyze = Analyze.NO)
    private boolean global;

    private ActionDefinition actionDefinition;

    public IspnActionDefinition() {
    }

    public IspnActionDefinition(ActionDefinition actionDefinition) {
        updateActionDefinition(actionDefinition);
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public ActionDefinition getActionDefinition() {
        return actionDefinition;
    }

    public void setActionDefinition(ActionDefinition actionDefinition) {
        updateActionDefinition(actionDefinition);
    }

    private void updateActionDefinition(ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            throw new IllegalArgumentException("actionDefinitions must be not null");
        }
        this.tenantId = actionDefinition.getTenantId();
        this.actionPlugin = actionDefinition.getActionPlugin();
        this.actionId = actionDefinition.getActionId();
        this.global = actionDefinition.isGlobal();
        this.actionDefinition = new ActionDefinition(actionDefinition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IspnActionDefinition that = (IspnActionDefinition) o;

        if (global != that.global) return false;
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null) return false;
        if (actionId != null ? !actionId.equals(that.actionId) : that.actionId != null) return false;
        return actionDefinition != null ? actionDefinition.equals(that.actionDefinition) : that.actionDefinition == null;
    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (actionPlugin != null ? actionPlugin.hashCode() : 0);
        result = 31 * result + (actionId != null ? actionId.hashCode() : 0);
        result = 31 * result + (global ? 1 : 0);
        result = 31 * result + (actionDefinition != null ? actionDefinition.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "IspnActionDefinition{" +
                "tenantId='" + tenantId + '\'' +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionId='" + actionId + '\'' +
                ", global=" + global +
                ", actionDefinition=" + actionDefinition +
                '}';
    }


}
