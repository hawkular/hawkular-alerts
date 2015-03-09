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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A base class for action representation from the perspective of the alerts engine.
 * An action is the abstract concept of a consequence of an alert.
 * A Trigger definition can be linked with a list of actions.
 *
 * Alert engine only needs to know an action id and message/payload.
 *
 * Action plugins will be responsible to process the action according its own plugin configuration.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Action {

    @JsonInclude
    private String actionId;

    @JsonInclude(Include.NON_NULL)
    private String message;

    public Action() { }

    public Action(String actionId, String message) {
        this.actionId = actionId;
        this.message = message;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Action that = (Action) o;

        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (actionId != null ? !actionId.equals(that.actionId) : that.actionId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = actionId != null ? actionId.hashCode() : 0;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Action{" +
                "message=" + (message == null ? null : '\'' + message + '\'') +
                ", actionId='" + actionId + '\'' +
                '}';
    }
}
