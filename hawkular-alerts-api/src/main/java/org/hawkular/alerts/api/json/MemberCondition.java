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
 * A convenience class used in the REST API to POST a Member Condition.
 *
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class MemberCondition {
    private Condition condition;
    private Map<String, Map<String, String>> dataIdMemberMap;

    public MemberCondition() {
        // for json
    }

    public MemberCondition(Condition condition, Map<String, Map<String, String>> dataIdMemberMap) {
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
     * The dataIdMap is a map of the dataId tokens in the group condition to the actual dataIds to be used for the
     * current member triggers. This map will usually have 1 entry but because a condition could have multiple
     * dataIds (e.g CompareCondition), it may have multiple entries.  The inner map maps member triggerIds to
     * the dataId to be used for that member trigger for the given token.  It should have 1 entry for each
     * member trigger.
     * @param dataIdMap the dataID mappings to be used for the existing member triggers.
     */
    public void setDataIdMemberMap(Map<String, Map<String, String>> dataIdMemberMap) {
        this.dataIdMemberMap = dataIdMemberMap;
    }

    @Override
    public String toString() {
        return "MemberCondition [condition=" + condition + ", dataIdMap=" + dataIdMemberMap + "]";
    }

}
