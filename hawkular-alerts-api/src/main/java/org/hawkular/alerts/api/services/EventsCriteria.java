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
package org.hawkular.alerts.api.services;

import java.util.Arrays;
import java.util.Collection;

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
    String tagQuery = null;
    boolean thin = false;
    Integer criteriaNoQuerySize = null;
    String eventType = null;

    public EventsCriteria() {
        super();
    }

    public EventsCriteria(Long startTime, Long endTime, String eventIds, String triggerIds, String categories,
                          String tagQuery, Boolean thin) {
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
        setTagQuery(tagQuery);
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

    public String getTagQuery() {
        return tagQuery;
    }

    /**
     * @param tagQuery return events with *any* of the tags specified by the tag expression language:
     *
     * <pre>
     *        <tag_query> ::= ( <expression> | "(" <object> ")" | <object> <logical_operator> <object> )
     *        <expression> ::= ( <tag_name> | <not> <tag_name> | <tag_name> <boolean_operator> <tag_value> |
     *               <tag_key> <array_operator> <array> )
     *        <not> ::= [ "NOT" | "not" ]
     *        <logical_operator> ::= [ "AND" | "OR" | "and" | "or" ]
     *        <boolean_operator> ::= [ "==" | "!=" ]
     *        <array_operator> ::= [ "IN" | "NOT IN" | "in" | "not in" ]
     *        <array> ::= ( "[" "]" | "[" ( "," <tag_value> )* )
     *        <tag_name> ::= <identifier>                                // Tag identifier
     *        <tag_value> ::= ( "'" <regexp> "'" | <simple_value> )      // Regular expression used with quotes
     * </pre>
     */
    public void setTagQuery(String tagQuery) {
        this.tagQuery = tagQuery;
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

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public boolean hasCategoryCriteria() {
        return null != category
                || (null != categories && !categories.isEmpty());
    }

    public boolean hasTagQueryCriteria() {
        return !isEmpty(tagQuery);
    }

    public boolean hasCTimeCriteria() {
        return (null != startTime || null != endTime);
    }

    public boolean hasTriggerIdCriteria() {
        return null != triggerId
                || (null != triggerIds && !triggerIds.isEmpty());
    }

    public boolean hasEventTypeCriteria() {
        return !isEmpty(eventType);
    }

    public boolean hasCriteria() {
        return hasEventIdCriteria()
                || hasCategoryCriteria()
                || hasTagQueryCriteria()
                || hasCTimeCriteria()
                || hasTriggerIdCriteria()
                || hasEventTypeCriteria();
    }

    @Override
    public String toString() {
        return "EventsCriteria{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", eventId='" + eventId + '\'' +
                ", eventIds=" + eventIds +
                ", category='" + category + '\'' +
                ", categories=" + categories +
                ", triggerId='" + triggerId + '\'' +
                ", triggerIds=" + triggerIds +
                ", tagQuery='" + tagQuery + '\'' +
                ", thin=" + thin +
                ", criteriaNoQuerySize=" + criteriaNoQuerySize +
                ", eventType='" + eventType + '\'' +
                '}';
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

}
