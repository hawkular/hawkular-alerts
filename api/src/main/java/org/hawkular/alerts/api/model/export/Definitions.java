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
package org.hawkular.alerts.api.model.export;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.List;

import org.hawkular.alerts.api.json.GroupMemberInfo;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.trigger.FullTrigger;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Representation of a list of full triggers (trigger, dampenings and conditions) and actions definitions.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "Representation of a list of full triggers (trigger, dampenings and conditions)," +
        "group members triggers and actions definitions. + \n" +
        "Used for bulk import/export operations.")
public class Definitions {

    @ApiModelProperty(value = "List of full triggers.",
            position = 0,
            required = false)
    @JsonInclude(Include.NON_EMPTY)
    private List<FullTrigger> triggers;

    @ApiModelProperty(value = "List of group member triggers information.",
            position = 1,
            required = false)
    @JsonInclude(Include.NON_EMPTY)
    private List<GroupMemberInfo> groupMembersInfo;

    @ApiModelProperty(value = "List of action definitions.",
            position = 2,
            required = false)
    @JsonInclude(Include.NON_EMPTY)
    private List<ActionDefinition> actions;

    public Definitions() {
    }

    public Definitions(List<FullTrigger> triggers,
                       List<ActionDefinition> actions) {
        this(triggers, null, actions);
    }

    public Definitions(List<FullTrigger> triggers,
                       List<GroupMemberInfo> groupMembersInfo,
                       List<ActionDefinition> actions) {
        this.triggers = triggers;
        this.groupMembersInfo = groupMembersInfo;
        this.actions = actions;
    }

    public List<FullTrigger> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<FullTrigger> triggers) {
        this.triggers = triggers;
    }

    public List<GroupMemberInfo> getGroupMembersInfo() {
        return groupMembersInfo;
    }

    public void setGroupMembersInfo(List<GroupMemberInfo> groupMembersInfo) {
        this.groupMembersInfo = groupMembersInfo;
    }

    public List<ActionDefinition> getActions() {
        return actions;
    }

    public void setActions(List<ActionDefinition> actions) {
        this.actions = actions;
    }

    public void updateTenant(String tenantId) {
        if (triggers != null) {
            for (FullTrigger t : triggers) {
                if (t.getTrigger() != null) {
                    t.getTrigger().setTenantId(tenantId);
                    t.check();
                }
            }
        }
        if (actions != null) {
            for (ActionDefinition a : actions) {
                if (a != null) {
                    a.setTenantId(tenantId);
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Definitions that = (Definitions) o;

        if (triggers != null ? !triggers.equals(that.triggers) : that.triggers != null) return false;
        if (groupMembersInfo != null ? !groupMembersInfo.equals(that.groupMembersInfo) : that.groupMembersInfo != null) return false;
        return actions != null ? actions.equals(that.actions) : that.actions == null;
    }

    @Override
    public int hashCode() {
        int result = triggers != null ? triggers.hashCode() : 0;
        result = 31 * result + (groupMembersInfo != null ? groupMembersInfo.hashCode() : 0);
        result = 31 * result + (actions != null ? actions.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Definitions{" +
                "triggers=" + triggers +
                ", groupMembersInfo=" + groupMembersInfo +
                ", actions=" + actions +
                '}';
    }
}
