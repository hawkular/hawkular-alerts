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

import java.util.Map;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * A convenience class used in the REST API to POST a new group Member Trigger.
 * <p>
 * A group-level condition uses dataId tokens for the dataIds defined in the condition.  The group members
 * must then replace the tokens with actual dataIds.  For example, we may define a group ThresholdCondition like
 * ( $SystemLoad$ > 80 ).  Each member must then replace $SystemLoad$ with the actual system load dataId for that
 * member. See {@link #setDataIdMap(Map)} for details on how to construct the map supplying the
 * dataId substitutions.
 * </p>
 * @author jay shaughnessy
 * @author lucas ponce
 */
@ApiModel(description = "A convenience class used in the REST API to POST a new group Member Trigger. + \n" +
        " + \n" +
        "A group-level condition uses dataId tokens for the dataIds defined in the condition. + \n" +
        "The group members must then replace the tokens with actual dataIds. + \n" +
        " + \n" +
        "For example, we may define a group ThresholdCondition like ( $SystemLoad$ > 80 ). + \n" +
        "Each member must then replace $SystemLoad$ with the actual system load dataId for that member." +
        " + \n" +
        "The dataIdMap is a map of the dataId tokens in the group conditions to the actual dataIds to + \n" +
        "be used for the member being added. For example, assume the group trigger has two conditions defined: + \n" +
        " + \n" +
        "ThresholdCondition( $SystemLoad$ > 80 ) and ThresholdCondition( $HeapUsed$ > 70 ) + \n" +
        " + \n" +
        "And now let's assume we are adding a new member, Member1.  The map would look like this: + \n" +
        " + \n" +
        "{ \"$SystemLoad$\":\"Member1SystemLoad\", \"$HeapUsed$\":\"Member1HeapUsed\" } + \n" +
        " + \n" +
        "So, in the example the actual dataIds would be Member1SystemLoad and Member1HeapUsed. + \n" +
        "With this Map we can now add the new member trigger. + \n" +
        " + \n" +
        "A NOTE ABOUT EXTERNAL CONDITIONS. ExternalCondition.expression will automatically have the + \n" +
        "same token replacement performed. So, all occurrences of the dataId token found in the expression, + \n" +
        "will be replaced with the mapping. + \n" +
        "This allows the expression of a group external condition to be automatically customized to the member.")
public class GroupMemberInfo {

    @ApiModelProperty(value = "Trigger id for parent Group Trigger.",
            position = 0,
            required = true)
    private String groupId;

    @ApiModelProperty(value = "Trigger id for member Trigger.",
            position = 1,
            required = true)
    private String memberId;

    @ApiModelProperty(value = "Trigger name for member Trigger.",
            position = 2,
            required = true)
    private String memberName;

    @ApiModelProperty(value = "Trigger description for member Trigger.",
            position = 3,
            required = true)
    private String memberDescription;

    @ApiModelProperty(value = "Trigger context for member Trigger.",
            position = 4,
            required = true)
    private Map<String, String> memberContext;

    @ApiModelProperty(value = "Trigger tags for member Trigger.",
            position = 5,
            required = true)
    private Map<String, String> memberTags;

    @ApiModelProperty(value = "A map of the dataId tokens in the group conditions to the actual dataIds to " +
            "be used for the member being added. + \n" +
            "Can be empty if the group has no current conditions.",
            position = 6,
            required = true)
    private Map<String, String> dataIdMap;

    public GroupMemberInfo() {
        // for json construction
    }

    public GroupMemberInfo(String groupId, String memberId, String memberName, String memberDescription,
            Map<String, String> memberContext, Map<String, String> memberTags, Map<String, String> dataIdMap) {
        super();
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.memberDescription = memberDescription;
        this.memberContext = memberContext;
        this.memberTags = memberTags;
        this.dataIdMap = dataIdMap;
    }

    public String getGroupId() {
        return groupId;
    }

    /** The group triggerId for which this will be a member trigger. Required.*/
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getMemberId() {
        return memberId;
    }

    /** The member triggerId. If null will be generated. */
    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    /** The member triggerName. If null defaults to group trigger's name */
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMemberDescription() {
        return memberDescription;
    }

    /**
     * @param memberDescription If null defaults to group trigger's description
     */
    public void setMemberDescription(String memberDescription) {
        this.memberDescription = memberDescription;
    }

    public Map<String, String> getMemberContext() {
        return memberContext;
    }

    /**
     * @param memberContext Members inherit the group trigger context. If not null this adds additional, or
     *                      overrides existing, context entries.
     */
    public void setMemberContext(Map<String, String> memberContext) {
        this.memberContext = memberContext;
    }

    public Map<String, String> getMemberTags() {
        return memberTags;
    }

    /**
     * @param memberTags Members inherit the group trigger tags. If not null this adds additional, or
     *                      overrides existing, tags.
     */
    public void setMemberTags(Map<String, String> memberTags) {
        this.memberTags = memberTags;
    }

    public Map<String, String> getDataIdMap() {
        return dataIdMap;
    }

    /**
     * The <code>dataIdMap</code> is a map of the dataId tokens in the group conditions to the actual dataIds to
     * be used for the member being added. For example, assume the group trigger has two conditions defined:
     * ThresholdCondition( $SystemLoad$ > 80 ) and ThresholdCondition( $HeapUsed$ > 70 ).  And now let's assume we
     * are adding a new member, Member1.  The map would look like this:
     * <pre>
     * {[key   = "$SystemLoad$",
     *   value = "Member1SystemLoad"],
     *  [key   = "$HeapUsed$",
     *   value = "Member1HeapUsed"]
     * }
     * </pre>
     * <p>
     *  So, in the example the actual dataIds would be <code>Member1SystemLoad</code> and <code>Member1HeapUsed</code>.
     *  With this Map we can now add the new member trigger.
     * </p>
     * <p>
     * A NOTE ABOUT EXTERNAL CONDITIONS. <code>ExternalCondition.expression</code> will automatically have the
     * same token replacement performed. So, all occurrences of the dataId token found in the expression, will be
     * replaced with the mapping. This allows the expression of a group external condition to be automatically
     * customized to the member.
     *</p
     * @param dataIdMap the dataId mappings to be used for the new member trigger.existing member triggers. Can be
     * empty if the group has no current conditions.
     */
    public void setDataIdMap(Map<String, String> dataIdMap) {
        this.dataIdMap = dataIdMap;
    }

    @Override
    public String toString() {
        return "GroupMemberInfo [groupId=" + groupId + ", memberId=" + memberId + ", memberName=" + memberName
                + ", memberContext=" + memberContext + ", dataIdMap=" + dataIdMap + "]";
    }

}
