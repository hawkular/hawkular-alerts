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
package org.hawkular.alerts.api.model.notification;

/**
 * A base class for notification representation from the perspective of the alerts engine.
 * Alert engine only needs to know a destination id and message to send.
 *
 * Notifiers plugin will be responsible to process these notifications according with specific configuration.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Notification {

    private String notifierId;
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNotifierId() {
        return notifierId;
    }

    public void setNotifierId(String notifierId) {
        this.notifierId = notifierId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Notification that = (Notification) o;

        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        if (notifierId != null ? !notifierId.equals(that.notifierId) : that.notifierId != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = notifierId != null ? notifierId.hashCode() : 0;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "message='" + message + '\'' +
                ", notifierId='" + notifierId + '\'' +
                '}';
    }
}
