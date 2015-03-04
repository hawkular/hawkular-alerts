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
package org.hawkular.actions.api.model;

import java.util.Map;

import com.google.gson.annotations.Expose;
import org.hawkular.bus.common.BasicMessage;

/**
 * A action message generated from the alerts engine through alert-bus subsystem.
 * Action plugins must listen per actionPlugin of message in the filter.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionMessage extends BasicMessage {

    @Expose
    String actionId;

    @Expose
    String message;

    @Expose
    Map<String, String> properties;

    public ActionMessage() { }

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

        ActionMessage that = (ActionMessage) o;

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
        return "ActionMessage{" +
                "message='" + message + '\'' +
                ", actionId='" + actionId + '\'' +
                ", properties=" + properties + '}';
    }
}
