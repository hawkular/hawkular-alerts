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

import org.hawkular.alerts.api.model.event.Alert;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base class for action representation from the perspective of the alerts engine.
 * An action is the abstract concept of a consequence of an alert.
 * A Trigger definition can be linked with a list of actions.
 *
 * Alert engine only needs to know an action id and message/payload.
 * Action payload can optionally have an alert as payload.
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

    @JsonInclude(Include.NON_NULL)
    private String message;

    @JsonInclude(Include.NON_NULL)
    private Alert alert;

    public Action() { }

    public Action(String tenantId, String actionPlugin, String actionId, String message) {
        this.tenantId = tenantId;
        this.actionPlugin = actionPlugin;
        this.actionId = actionId;
        this.message = message;
    }

    public Action(String tenantId, String actionPlugin, String actionId, Alert alert) {
        this.tenantId = tenantId;
        this.actionPlugin = actionPlugin;
        this.actionId = actionId;
        this.alert = alert;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = alert;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Action action = (Action) o;

        if (!tenantId.equals(action.tenantId)) return false;
        if (!actionPlugin.equals(action.actionPlugin)) return false;
        if (!actionId.equals(action.actionId)) return false;
        return alert.equals(action.alert);

    }

    @Override
    public int hashCode() {
        int result = tenantId.hashCode();
        result = 31 * result + actionPlugin.hashCode();
        result = 31 * result + actionId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Action{" +
                "tenantId='" + tenantId + '\'' +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionId='" + actionId + '\'' +
                ", message='" + message + '\'' +
                ", alert=" + alert +
                '}';
    }
}
