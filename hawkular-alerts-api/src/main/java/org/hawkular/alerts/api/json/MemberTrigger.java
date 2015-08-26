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

/**
 * A convenience class used in the REST API to POST a Member Trigger.
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class MemberTrigger {

    private String groupId;
    private String memberId;
    private String memberName;
    private Map<String, String> memberContext;
    private Map<String, String> dataIdMap;

    public MemberTrigger() {
        // for json construction
    }

    public MemberTrigger(String groupId, String memberId, String memberName, Map<String, String> memberContext,
            Map<String, String> dataIdMap) {
        super();
        this.groupId = groupId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.memberContext = memberContext;
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

    /** The member triggerName. Required. */
    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public Map<String, String> getMemberContext() {
        return memberContext;
    }

    /** The member context. If null will be inherited from the group. */
    public void setMemberContext(Map<String, String> memberContext) {
        this.memberContext = memberContext;
    }

    public Map<String, String> getDataIdMap() {
        return dataIdMap;
    }

    /** Map of dataId "tokens" in the group conditions to dataIds to be set in the member conditions. Can be Null
     * only if the group trigger does yet have any conditions defined. */
    public void setDataIdMap(Map<String, String> dataIdMap) {
        this.dataIdMap = dataIdMap;
    }

    @Override
    public String toString() {
        return "MemberTrigger [groupId=" + groupId + ", memberId=" + memberId + ", memberName=" + memberName
                + ", memberContext=" + memberContext + ", dataIdMap=" + dataIdMap + "]";
    }


}
