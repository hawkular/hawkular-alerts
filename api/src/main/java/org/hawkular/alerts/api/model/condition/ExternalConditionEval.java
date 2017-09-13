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
package org.hawkular.alerts.api.model.condition;

import org.hawkular.alerts.api.doc.DocModel;
import org.hawkular.alerts.api.doc.DocModelProperty;
import org.hawkular.alerts.api.model.condition.Condition.Type;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An evaluation state for an external condition.  Note that external conditions may report a <code>Data</code> value
 * or an <code>Event</code>.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@DocModel(description = "An evaluation state for an external condition. + \n" +
        "Note that external conditions may report a Data value or an Event.")
public class ExternalConditionEval extends ConditionEval {

    private static final long serialVersionUID = 1L;

    @DocModelProperty(description = "External condition linked with this state.",
            position = 0)
    @JsonInclude(Include.NON_NULL)
    private ExternalCondition condition;

    @DocModelProperty(description = "String value used for dataId.",
            position = 1)
    @JsonInclude(Include.NON_NULL)
    private String value;

    @DocModelProperty(description = "Event value used for dataId.",
            position = 2)
    @JsonInclude(Include.NON_NULL)
    private Event event;

    public ExternalConditionEval() {
        super(Type.EXTERNAL, false, 0, null);
        this.condition = null;
        this.value = null;
        this.event = null;
    }

    public ExternalConditionEval(ExternalCondition condition, Event event) {
        super(Type.EXTERNAL, condition.match(event.getText()), event.getCtime(), event.getContext());
        this.condition = condition;
        this.event = event;
    }

    public ExternalConditionEval(ExternalCondition condition, Data data) {
        super(Type.EXTERNAL, condition.match(data.getValue()), data.getTimestamp(), data.getContext());
        this.condition = condition;
        this.value = data.getValue();
    }

    public ExternalCondition getCondition() {
        return condition;
    }

    public void setCondition(ExternalCondition condition) {
        this.condition = condition;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    @Override
    public String getTenantId() {
        return condition.getTenantId();
    }

    @Override
    public String getTriggerId() {
        return condition.getTriggerId();
    }

    @Override
    public int getConditionSetSize() {
        return condition.getConditionSetSize();
    }

    @Override
    public int getConditionSetIndex() {
        return condition.getConditionSetIndex();
    }

    @Override
    public void updateDisplayString() {
        String s = String.format("External[%s]: %s[%s] matches [%s]", condition.getAlerterId(),
                condition.getDataId(), (value != null ? value : event.toString()), condition.getExpression());
        setDisplayString(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExternalConditionEval that = (ExternalConditionEval) o;

        if (condition != null ? !condition.equals(that.condition) : that.condition != null) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        return event != null ? event.equals(that.event) : that.event == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (event != null ? event.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ExternalConditionEval{" +
                "condition=" + condition +
                ", value='" + value + '\'' +
                ", event=" + event +
                '}';
    }
}
