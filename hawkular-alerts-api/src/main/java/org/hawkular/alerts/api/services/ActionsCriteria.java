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

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.Collection;

/**
 * Query criteria for fetching Actions from history backend.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionsCriteria {
    Long startTime = null;
    Long endTime = null;
    String actionPlugin = null;
    Collection<String> actionPlugins = null;
    String actionId = null;
    Collection<String> actionIds = null;
    String eventId = null;
    Collection<String> eventIds = null;
    String result = null;
    Collection<String> results = null;
    boolean thin = false;

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public Collection<String> getActionPlugins() {
        return actionPlugins;
    }

    public void setActionPlugins(Collection<String> actionPlugins) {
        this.actionPlugins = actionPlugins;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public Collection<String> getActionIds() {
        return actionIds;
    }

    public void setActionIds(Collection<String> actionIds) {
        this.actionIds = actionIds;
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

    /**
     * This is an alias for <code>getEventId()</code>
     * @deprecated
     */
    @Deprecated
    public String getAlertId() {
        return eventId;
    }

    /**
     * This is an alias for <code>setEventId()</code>
     * @param alertId the id of the event or alert
     * @deprecated
     */
    @Deprecated
    public void setAlertId(String alertId) {
        this.eventId = alertId;
    }

    /**
     * This is an alias for <code>getAlertIds()</code>
     * @deprecated
     */
    @Deprecated
    public Collection<String> getAlertIds() {
        return eventIds;
    }

    /**
     * This is an alias for <code>setEventIds()</code>
     * @param alertIds the ids of the events or alerts
     * @deprecated
     */
    @Deprecated
    public void setAlertIds(Collection<String> alertIds) {
        this.eventIds = alertIds;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Collection<String> getResults() {
        return results;
    }

    public void setResults(Collection<String> results) {
        this.results = results;
    }

    public boolean isThin() {
        return thin;
    }

    public void setThin(boolean thin) {
        this.thin = thin;
    }

    public boolean hasActionIdCriteria() {
        return !isEmpty(actionId) || !isEmpty(actionIds);
    }

    public boolean hasActionPluginCriteria() {
        return !isEmpty(actionPlugin) || !isEmpty(actionPlugins);
    }

    public boolean hasEventIdCriteria() {
        return !isEmpty(eventId) || !isEmpty(eventIds);
    }

    public boolean hasResultCriteria() {
        return !isEmpty(result) || !isEmpty(results);
    }

    public boolean hasStartCriteria() {
        return null != startTime;
    }

    public boolean hasEndCriteria() {
        return null != endTime;
    }

    public boolean hasRangeCriteria() {
        return hasStartCriteria() && hasEndCriteria();
    }

    public boolean hasCTimeCriteria() {
        return hasStartCriteria() || hasEndCriteria();
    }

    public boolean hasCriteria() {
        return hasCTimeCriteria()
                || hasActionPluginCriteria()
                || hasActionIdCriteria()
                || hasEventIdCriteria()
                || hasResultCriteria();
    }

    @Override
    public String toString() {
        return "ActionsCriteria{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionPlugins=" + actionPlugins +
                ", actionId='" + actionId + '\'' +
                ", actionIds=" + actionIds +
                ", alertId='" + eventId + '\'' +
                ", alertIds=" + eventIds +
                ", result='" + result + '\'' +
                ", results=" + results +
                '}';
    }
}
