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
package org.hawkular.alerts.api.model.action;

import java.util.Map;

import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.Alert.Thin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base class for action representation from the perspective of the alerts engine.
 * An action is the abstract concept of a consequence of an alert.
 * A Trigger definition can be linked with a list of actions.
 *
 * Alert engine only needs to know an action id and message/payload.
 * Action payload must have an alert as payload.
 *
 * Action plugins will be responsible to process the action according its own plugin configuration.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Action {

    @JsonInclude
    private String tenantId;

    @JsonInclude
    private String actionPlugin;

    @JsonInclude
    private String actionId;

    @JsonInclude
    private String alertId;

    @JsonInclude
    private long ctime;

    @JsonInclude(Include.NON_NULL)
    @Thin
    private Alert alert;

    @JsonInclude(Include.NON_EMPTY)
    private Map<String, String> properties;

    @JsonInclude(Include.NON_NULL)
    private String result;

    public Action() { }

    public Action(String tenantId, String actionPlugin, String actionId, Alert alert) {
        this.tenantId = tenantId;
        this.actionPlugin = actionPlugin;
        this.actionId = actionId;
        this.alert = alert;
        if (alert != null) {
            this.alertId = alert.getAlertId();
        }
        this.ctime = System.currentTimeMillis();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = alert;
    }

    public long getCtime() {
        return ctime;
    }

    public void setCtime(long ctime) {
        this.ctime = ctime;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Action action = (Action) o;

        if (ctime != action.ctime) return false;
        if (tenantId != null ? !tenantId.equals(action.tenantId) : action.tenantId != null) return false;
        if (actionPlugin != null ? !actionPlugin.equals(action.actionPlugin) : action.actionPlugin != null)
            return false;
        if (actionId != null ? !actionId.equals(action.actionId) : action.actionId != null) return false;
        if (alertId != null ? !alertId.equals(action.alertId) : action.alertId != null) return false;
        if (properties != null ? !properties.equals(action.properties) : action.properties != null) return false;
        return !(result != null ? !result.equals(action.result) : action.result != null);

    }

    @Override
    public int hashCode() {
        int result1 = tenantId != null ? tenantId.hashCode() : 0;
        result1 = 31 * result1 + (actionPlugin != null ? actionPlugin.hashCode() : 0);
        result1 = 31 * result1 + (actionId != null ? actionId.hashCode() : 0);
        result1 = 31 * result1 + (alertId != null ? alertId.hashCode() : 0);
        result1 = 31 * result1 + (int) (ctime ^ (ctime >>> 32));
        result1 = 31 * result1 + (properties != null ? properties.hashCode() : 0);
        result1 = 31 * result1 + (result != null ? result.hashCode() : 0);
        return result1;
    }

    @Override
    public String toString() {
        return "Action{" +
                "tenantId='" + tenantId + '\'' +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionId='" + actionId + '\'' +
                ", alertId='" + alertId + '\'' +
                ", ctime=" + ctime +
                ", alert=" + alert +
                ", properties=" + properties +
                ", result='" + result + '\'' +
                '}';
    }
}
