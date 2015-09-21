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
package org.hawkular.alerts.api.model.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * An Event, a recording of potentially valuable information in the monitored world. 
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Event extends AbstractEvent {

    @JsonInclude(Include.NON_EMPTY)
    private Map<String, String> context;

    public Event() {
        // for json assembly
    }

    public Event(String tenantId, String id, Severity severity, Map<String, String> context) {
        super(tenantId, id, severity);

        this.context = context;
    }

    public Event(String tenantId, Trigger trigger, Dampening dampening, List<Set<ConditionEval>> evalSets) {
        super(tenantId, trigger, dampening, evalSets);

        this.id = trigger.getId() + "-" + this.ctime;
        this.context = trigger.getContext();
    }

    public Map<String, String> getContext() {
        if ( null == context ) {
            context = new HashMap<>();
        }
        return context;
    }

    public void setContext(Map<String, String> context) {
        this.context = context;
    }

    /**
     * Add context information.
     * @param name context key.
     * @param value context value.
     */
    public void addProperty(String name, String value) {
        if (null == name || null == value) {
            throw new IllegalArgumentException("Propety must have non-null name and value");
        }
        if (null == context) {
            context = new HashMap<>();
        }
        context.put(name, value);
    }

    @Override
    public String toString() {
        return "Event [context=" + context + ", tenantId=" + tenantId + ", id=" + id + ", ctime=" + ctime
                + ", getSeverity()=" + getSeverity() + ", getTrigger()=" + getTrigger() + "]";
    }


}
