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
package org.hawkular.alerts.bus.messages;


import java.util.ArrayList;
import java.util.List;

import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.bus.common.BasicMessage;

import com.fasterxml.jackson.annotation.JsonInclude;
/**
 * A bus message used to receive data for the alerts subsystem.
 * One message can store a collection of {@link org.hawkular.alerts.bus.messages.AlertData}.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertDataMessage extends BasicMessage {

    @JsonInclude
    List<AlertData> data;

    protected AlertDataMessage() { }

    public AlertDataMessage(List<AlertData> data) {
        this.data = new ArrayList<AlertData>(data);
    }

    public AlertDataMessage(AlertData... data) {
        this.data = new ArrayList<AlertData>();
        if (data != null) {
            for (AlertData item : data) {
                this.data.add(item);
            }
        }
    }

    public List<Data> getData() {
        if (data == null) {
            return null;
        }
        List<Data> converted = new ArrayList<Data>();
        for (AlertData item : data) {
            converted.add(item.convert());
        }
        return converted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlertDataMessage that = (AlertDataMessage) o;

        if (data != null ? !data.equals(that.data) : that.data != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return data != null ? data.hashCode() : 0;
    }
}
