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
package org.hawkular.alerts.api.services;

import java.util.Collection;

import org.hawkular.alerts.api.model.trigger.Tag;

/**
 * Query criteria for fetching Alerts.
 * TODO: paging, sorting
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class AlertsCriteria {
    Long startTime = null;
    Long endTime = null;
    String triggerId = null;
    Collection<String> triggerIds = null;
    Tag tag = null;
    Collection<Tag> tags = null;

    public AlertsCriteria() {
        super();
    }

    public Long getStartTime() {
        return startTime;
    }

    /**
     * @param startTime fetched Alerts must have cTime >= startTime
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    /**
     * @param endTime fetched Alerts must have cTime <= endTime
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getTriggerId() {
        return triggerId;
    }

    /**
     * @param triggerId fetched Alerts must be for the specified trigger. Ignored if triggerIds is not empty.
     */
    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    /**
     * @param triggerId fetched Alerts must be for any of the specified triggers.
     */
    public Collection<String> getTriggerIds() {
        return triggerIds;
    }

    public void setTriggerIds(Collection<String> triggerIds) {
        this.triggerIds = triggerIds;
    }

    public Tag getTag() {
        return tag;
    }

    /**
     * @param triggerId fetched Alerts must be for triggers with the specified Tag. Ignored if Tags is not empty.
     */
    public void setTag(Tag tag) {
        this.tag = tag;
    }

    public Collection<Tag> getTags() {
        return tags;
    }

    /**
     * @param triggerId fetched Alerts must be for trigger with any of the specified Tags.
     */
    public void setTags(Collection<Tag> tags) {
        this.tags = tags;
    }

    public boolean hasCriteria() {
        return null != startTime || //
                null != endTime || //
                null != triggerId || //
                (null != triggerIds && !triggerIds.isEmpty()) || //
                null != tag || //
                (null != tags && !tags.isEmpty());
    }

    @Override
    public String toString() {
        return "AlertsCriteria [startTime=" + startTime + ", endTime=" + endTime + ", triggerId=" + triggerId
                + ", triggerIds=" + triggerIds + ", tag=" + tag + ", tags=" + tags + "]";
    }

}
