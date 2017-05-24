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
    String alertId = null;
    Collection<String> alertIds = null;
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

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public Collection<String> getAlertIds() {
        return alertIds;
    }

    public void setAlertIds(Collection<String> alertIds) {
        this.alertIds = alertIds;
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

    public boolean hasCriteria() {
        return null != startTime
                || null != endTime
                || null != actionPlugin
                || null != actionId
                || null != alertId
                || null != result
                || (null != actionPlugins && !actionPlugins.isEmpty())
                || (null != actionIds && !actionIds.isEmpty())
                || (null != alertIds && !alertIds.isEmpty())
                || (null != results && !results.isEmpty());
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
                ", alertId='" + alertId + '\'' +
                ", alertIds=" + alertIds +
                ", result='" + result + '\'' +
                ", results=" + results +
                '}';
    }
}
