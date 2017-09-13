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
package org.hawkular.alerts.api.json;

import static com.fasterxml.jackson.annotation.JsonInclude.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.hawkular.alerts.api.doc.DocModel;
import org.hawkular.alerts.api.doc.DocModelProperty;
import org.hawkular.alerts.api.model.condition.Condition;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@DocModel(description = "A convenience class used in the REST API to POST a new Group Condition. + \n" +
        " + \n" +
        "A group-level condition uses dataId tokens for the dataIds defined in the condition.  + \n" +
        "The group members must then replace the tokens with actual dataIds. + \n" +
        " + \n" +
        "For example, we may define a group ThresholdCondition like ( $SystemLoad$ > 80 ). + \n" +
        "Each member must then replace $SystemLoad$ with the actual system load dataId for that member. + \n" +
        " + \n" +
        "The dataIdMemberMap is a map of the dataId tokens in the group conditions to the actual dataIds + \n" +
        "used for the current member triggers. + \n" +
        "Because most condition types have only one dataId the map will typically have 1 entry per condition. + \n" +
        "But because a condition could have multiple dataIds (e.g CompareCondition has dataId and data2Id), + \n" +
        "it may have more entries than conditions. + \n" +
        "The inner map maps member triggerIds to the dataId to be used for that member trigger " +
        "for the given token. + \n" +
        "It should have 1 entry for each member trigger. + \n" +
        " + \n" +
        "For example, let's define a group trigger with two conditions: + \n" +
        " + \n" +
        "ThresholdCondition( $SystemLoad$ > 80 ) + \n" +
        "ThresholdCondition( $HeapUsed$ > 70 ) + \n" +
        " + \n" +
        "If the group has two current members, with triggerId's Member1 and Member2, + \n" +
        "the map would look like this: + \n" +
        " + \n" +
        "{ + \n" +
        "\"$SystemLoad$\":{\"Member1\":\"Member1SystemLoad\", \"Member2\":\"Member2SystemLoad\"}, + \n" +
        "\"$HeapUsed$\":{\"Member1\":\"Member1HeapUsed\", \"Member2\":\"Member2HeapUsed\"} + \n" +
        "} + \n" +
        " + \n" +
        "So, in the example the actual $SystemLoad$ dataIds would be Member1SystemLoad and Member2SystemLoad. + \n" +
        "With this Map we can now add the group-level conditions and also the two member-level conditions + \n" +
        "to each member + \n" +
        " + \n" +
        "A NOTE ABOUT EXTERNAL CONDITIONS. <code>ExternalCondition.expression</code> will automatically have the + \n" +
        "same token replacement performed. So, all occurrences of the dataId token found in the expression, + \n" +
        "will be replaced with the mapping. This allows the expression of a group external condition to be + \n" +
        "automatically customized to the member.")
public class GroupConditionsInfo {

    @DocModelProperty(description = "A list of conditions for a Group Trigger.",
            position = 0,
            required = true)
    @JsonInclude(Include.NON_EMPTY)
    private Collection<Condition> conditions;

    @DocModelProperty(description = "A map of the dataId tokens in the group conditions to the actual dataIds " +
            "used for the current member triggers. Can be empty if the group has no existing members.",
            position = 1,
            required = true)
    @JsonInclude(Include.NON_EMPTY)
    private Map<String, Map<String, String>> dataIdMemberMap;

    public GroupConditionsInfo() {
        // for json
    }

    /**
     * Convenience constructor for single-condition group trigger.
     * @param condition The single condition for this group trigger.
     * @param dataIdMemberMap The dataIdMemberMap. Can be empty if no member triggers exist for the group.
     * @see {@link #setDataIdMemberMap(Map)}
     */
    public GroupConditionsInfo(Condition condition, Map<String, Map<String, String>> dataIdMemberMap) {
        this(new ArrayList<>(1), dataIdMemberMap);

        this.conditions.add(condition);
    }

    /**
     * @param condition The conditions for this group trigger.
     * @param dataIdMemberMap The dataIdMemberMap. Can be empty if no member triggers exist for the group.
     * @see {@link #setDataIdMemberMap(Map)}
     */
    public GroupConditionsInfo(Collection<Condition> conditions, Map<String, Map<String, String>> dataIdMemberMap) {
        super();
        this.conditions = conditions;
        this.dataIdMemberMap = dataIdMemberMap;
    }

    public Collection<Condition> getConditions() {
        return conditions;
    }

    /**
     * @param conditions the conditions to be added to the group trigger. To maintain ordering use a
     * <code>Collection</code> implementation that supports ordering (e.g. <code>List</code>).
     */
    public void setConditions(Collection<Condition> conditions) {
        this.conditions = conditions;
    }

    public Map<String, Map<String, String>> getDataIdMemberMap() {
        return dataIdMemberMap;
    }

    /**
     * The <code>dataIdMemberMap</code> is a map of the dataId tokens in the group conditions to the actual dataIds
     * used for the current member triggers. Because most condition types have only one dataId the map will
     * typically have 1 entry per condition. But because a condition could have multiple dataIds (e.g CompareCondition
     * has data1Id and data2Id), it may have more entries that conditions. The inner map maps member triggerIds to
     * the dataId to be used for that member trigger for the given token.  It should have 1 entry for each
     * member trigger.  For example, let's define a group trigger with two conditions:
     * <p><code>ThresholdCondition( $SystemLoad$ > 80 )</code></p>
     * <p><code>ThresholdCondition( $HeapUsed$ > 70 )</code></p>
     * If the group has two current members, with triggerId's Member1 and Member2, the map would look like this:
     * <pre>
     * {[key   = "$SystemLoad$",
     *   value = {[key   = "Member1",
     *             value = "Member1SystemLoad"],
     *            [key   - "Member2",
     *             value = "Member2SystemLoad"]
     *           }
     *  ],
     *  [key   = "$HeapUsed$",
     *   value = {[key   = "Member1",
     *             value = "Member1HeapUsed"],
     *            [key   - "Member2",
     *             value = "Member2HeapUsed"]
     *           }
     *  ]
     * }
     * </pre><p>
     * So, in the example the actual $SystemLoad$ dataIds would be <code>Member1SystemLoad</code> and
     * <code>Member2SystemLoad</code>.  With this Map we can now add the group-level conditions and also the
     * two member-level conditions to each member.
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
        return "GroupConditionsInfo [conditions=" + conditions + ", dataIdMemberMap=" + dataIdMemberMap + "]";
    }

}
