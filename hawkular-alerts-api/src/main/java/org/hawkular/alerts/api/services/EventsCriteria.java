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
package org.hawkular.alerts.api.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Query criteria for fetching Alerts.
 * @author jay shaughnessy
 * @author lucas ponce
 */
public class EventsCriteria {
    Long startTime = null;
    Long endTime = null;
    String eventId = null;
    Collection<String> eventIds = null;
    String category = null;
    Collection<String> categories = null;
    String triggerId = null;
    Collection<String> triggerIds = null;
    Map<String, String> tags = null;
    boolean thin = false;
    Integer criteriaNoQuerySize = null;

    public EventsCriteria() {
        super();
    }

    public EventsCriteria(Long startTime, Long endTime, String eventIds, String triggerIds, String categories,
           String tags, Boolean thin) {
        setStartTime(startTime);
        setEndTime(endTime);
        if (!isEmpty(eventIds)) {
            setEventIds(Arrays.asList(eventIds.split(",")));
        }
        if (!isEmpty(triggerIds)) {
            setTriggerIds(Arrays.asList(triggerIds.split(",")));
        }
        if (!isEmpty(categories)) {
            setCategories(Arrays.asList(categories.split(",")));
        }
        if (!isEmpty(tags)) {
            String[] tagTokens = tags.split(",");
            Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
            for (String tagToken : tagTokens) {
                String[] fields = tagToken.split("\\|");
                if (fields.length == 2) {
                    tagsMap.put(fields[0], fields[1]);
                } else {
                    throw new IllegalArgumentException("Invalid Tag Criteria " + Arrays.toString(fields));
                }
            }
            setTags(tagsMap);
        }
        if (null != thin) {
            setThin(thin.booleanValue());
        }
    }

    public Long getStartTime() {
        return startTime;
    }

    /**
     * @param startTime fetched Alerts must have cTime greater than or equal to startTime
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    /**
     * @param endTime fetched Alerts must have cTime less than or equal to endTime
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Collection<String> getEventIds() {
        return eventIds;
    }

    public void setEventIds(Collection<String> eventIds) {
        this.eventIds = eventIds;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Collection<String> getCategories() {
        return categories;
    }

    public void setCategories(Collection<String> categories) {
        this.categories = categories;
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

    public Collection<String> getTriggerIds() {
        return triggerIds;
    }

    /**
     * @param triggerIds fetched alerts must be for one of the specified triggers.
     */
    public void setTriggerIds(Collection<String> triggerIds) {
        this.triggerIds = triggerIds;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * @param tags return alerts with *any* of these tags, it does not have to have all of the tags). Specify '*' for
     * the value if you only want to match the name portion of the tag.
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public void addTag(String name, String value) {
        if (null == tags) {
            tags = new HashMap<>();
        }
        tags.put(name, value);
    }

    public boolean isThin() {
        return thin;
    }

    public void setThin(boolean thin) {
        this.thin = thin;
    }

    public Integer getCriteriaNoQuerySize() {
        return criteriaNoQuerySize;
    }

    public void setCriteriaNoQuerySize(Integer criteriaNoQuerySize) {
        this.criteriaNoQuerySize = criteriaNoQuerySize;
    }

    public boolean hasEventIdCriteria() {
        return null != eventId
                || (null != eventIds && !eventIds.isEmpty());
    }

    public boolean hasCategoryCriteria() {
        return null != category
                || (null != categories && !categories.isEmpty());
    }

    public boolean hasTagCriteria() {
        return (null != tags && !tags.isEmpty());
    }

    public boolean hasCTimeCriteria() {
        return (null != startTime || null != endTime);
    }

    public boolean hasTriggerIdCriteria() {
        return null != triggerId
                || (null != triggerIds && !triggerIds.isEmpty());
    }

    public boolean hasCriteria() {
        return hasEventIdCriteria()
                || hasCategoryCriteria()
                || hasTagCriteria()
                || hasCTimeCriteria()
                || hasTriggerIdCriteria();
    }

    @Override
    public String toString() {
        return "EventsCriteria [startTime=" + startTime + ", endTime=" + endTime + ", eventId=" + eventId
                + ", eventIds=" + eventIds + ", category=" + category + ", categories=" + categories + ", triggerId="
                + triggerId + ", triggerIds=" + triggerIds + ", tags=" + tags + ", thin=" + thin
                + ", criteriaNoQuerySize=" + criteriaNoQuerySize + "]";
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

}
