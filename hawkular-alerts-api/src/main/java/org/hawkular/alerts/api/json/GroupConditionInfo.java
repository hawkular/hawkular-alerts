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
package org.hawkular.alerts.api.json;

import java.util.Map;

import org.hawkular.alerts.api.model.condition.Condition;

/**
 * A convenience class used in the REST API to POST a new Group Condition.
 * <p>
 * A group-level condition uses dataId tokens for the dataIds defined in the condition.  The group members
 * must then replace the tokens with actual dataIds.  For example, we may define a group ThresholdCondition like
 * ( $SystemLoad$ > 80 ).  Each member must then replace $SystemLoad$ with the actual system load dataId for that
 * member. See {@link #setDataIdMemberMap(Map)} for details on how to construct the map supplying the
 * dataId substitutions.
 * </p>
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class GroupConditionInfo {
    private Condition condition;
    private Map<String, Map<String, String>> dataIdMemberMap;

    public GroupConditionInfo() {
        // for json
    }

    public GroupConditionInfo(Condition condition, Map<String, Map<String, String>> dataIdMemberMap) {
        super();
        this.condition = condition;
        this.dataIdMemberMap = dataIdMemberMap;
    }

    public Condition getCondition() {
        return condition;
    }

    /**
     * @param condition the condition to be added to the group trigger.
     */
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Map<String, Map<String, String>> getDataIdMemberMap() {
        return dataIdMemberMap;
    }

    /**
     * The <code>dataIdMemberMap</code> is a map of the dataId tokens in the group condition to the actual dataIds to
     * be used for the current member triggers. Because most condition types have only one dataId the map will
     * typically have only 1 entry. But because a condition could have multiple dataIds (e.g CompareCondition has
     * data1Id and data2Id), it may have multiple entries. The inner map maps member triggerIds to
     * the dataId to be used for that member trigger for the given token.  It should have 1 entry for each
     * member trigger.  For example, let's define a new group ThresholdCondition ( $SystemLoad$ > 80 ).  If the
     * group has two current members, with triggerId's Member1 and Member2, the map would look like this:
     * <pre>
     * {[key   = "$SystemLoad$",
     *   value = {[key   = "Member1",
     *             value = "Member1SystemLoad"],
     *            [key   - "Member2",
     *             value = "Member1SystemLoad"]
     *           }
     * }
     * </pre><p>
     * So, in the example the actual dataIds would be <code>Member1SystemLoad</code> and <code>Member2SystemLoad</code>.
     * With this Map we can now add the group-level condition and also the two member-level conditions.
     * </p>
     * A NOTE ABOUT EXTERNAL CONDITIONS. <code>ExternalCondition.expression</code> will automatically have the
     * same token replacement performed. So, all occurrences of the dataId token found in the expression, will be
     * replaced with the mapping. This allows the expression of a group external condition to be automatically
     * customized to the member.
     * </p>
     * @param dataIdMemberMap the dataID mappings to be used for the existing member triggers. Can be empty if the
     * group has no existing members.
     */
    public void setDataIdMemberMap(Map<String, Map<String, String>> dataIdMemberMap) {
        this.dataIdMemberMap = dataIdMemberMap;
    }

    @Override
    public String toString() {
        return "GroupConditionInfo [condition=" + condition + ", dataIdMap=" + dataIdMemberMap + "]";
    }

}
