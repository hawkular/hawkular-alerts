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
package org.hawkular.notifiers.api.model;

import com.google.gson.annotations.Expose;
import org.hawkular.bus.common.BasicMessage;

import java.util.Set;

/**
 * A notifier registration message.
 * i.e. email to admin, send trap #1 to OID B, send SMS to 555-12345
 * It helps to centralize into the alerts engine how many notifiers are available.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class NotifierRegistrationMessage extends BasicMessage {

    @Expose
    String op;

    @Expose
    String notifierId;

    @Expose
    Set<String> properties;

    public NotifierRegistrationMessage() { }

    public String getNotifierId() {
        return notifierId;
    }

    public void setNotifierId(String notifierId) {
        this.notifierId = notifierId;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Set<String> getProperties() {
        return properties;
    }

    public void setProperties(Set<String> properties) {
        this.properties = properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotifierRegistrationMessage that = (NotifierRegistrationMessage) o;

        if (notifierId != null ? !notifierId.equals(that.notifierId) : that.notifierId != null) return false;
        if (op != null ? !op.equals(that.op) : that.op != null) return false;
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = op != null ? op.hashCode() : 0;
        result = 31 * result + (notifierId != null ? notifierId.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NotifierRegistrationMessage{" +
                "notifierId='" + notifierId + '\'' +
                ", op='" + op + '\'' +
                ", properties=" + properties +
                '}';
    }
}
