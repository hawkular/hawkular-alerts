/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.api.model.trigger;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Representation of a Trigger with Dampening and Condition objects.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class FullTrigger implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonInclude(Include.NON_NULL)
    private Trigger trigger;

    @JsonInclude(Include.NON_EMPTY)
    private List<Dampening> dampenings;

    @JsonInclude(Include.NON_EMPTY)
    private List<Condition> conditions;

    public FullTrigger() {
        this(null, null, null);
    }

    public FullTrigger(Trigger trigger, List<Dampening> dampenings,
                       List<Condition> conditions) {
        this.trigger = trigger;
        this.dampenings = dampenings;
        this.conditions = conditions;
        checkDampenings();
        checkConditions();
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
        checkDampenings();
        checkConditions();
    }

    public List<Dampening> getDampenings() {
        return dampenings;
    }

    public void setDampenings(List<Dampening> dampenings) {
        this.dampenings = dampenings;
        checkDampenings();
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
        checkConditions();
    }

    public void check() {
        checkDampenings();
        checkConditions();
    }

    private void checkDampenings() {
        if (trigger != null && !isEmpty(dampenings) && !isEmpty(trigger.getId()) && !isEmpty(trigger.getTenantId())) {
            for (Dampening d : dampenings) {
                if (isEmpty(d.getTenantId()) || !d.getTenantId().equals(trigger.getTenantId())) {
                    d.setTenantId(trigger.getTenantId());
                }
                if (isEmpty(d.getTriggerId()) || !d.getTriggerId().equals(trigger.getId())) {
                    d.setTriggerId(trigger.getId());
                }
            }
        }
    }

    private void checkConditions() {
        if (trigger != null && !isEmpty(conditions) && !isEmpty(trigger.getId()) && !isEmpty(trigger.getTenantId())) {
            for (Condition c : conditions) {
                if (isEmpty(c.getTenantId()) || !c.getTenantId().equals(trigger.getTenantId())) {
                    c.setTenantId(trigger.getTenantId());
                }
                if (isEmpty(c.getTriggerId()) || !c.getTriggerId().equals(trigger.getId())) {
                    c.setTriggerId(trigger.getId());
                }
            }
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FullTrigger that = (FullTrigger) o;

        return trigger != null ? trigger.equals(that.trigger) : that.trigger == null;

    }

    @Override
    public int hashCode() {
        return trigger != null ? trigger.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "FullTrigger" + '[' +
                "trigger=" + trigger +
                ", dampenings=" + dampenings +
                ", conditions=" + conditions +
                ']';
    }
}
