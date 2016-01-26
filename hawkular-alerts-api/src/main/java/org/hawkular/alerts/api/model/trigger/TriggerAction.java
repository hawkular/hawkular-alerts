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
package org.hawkular.alerts.api.model.trigger;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Link an ActionDefinition with a Trigger.
 *
 * It can add optional constraints that determine when an action will be executed:
 *
 * - A set of Alert.Status (represented by its string value).
 *   The action will be executed if the Alert which is linked is on one of the states defined.
 *   This is not applicable if the action is linked with an Event.
 *
 * - A calendar expression that defines an interval when the action will be executed.
 *   The format of the calendar can be:
 *   - Absolute: <startAbsoluteDate>;<endAbsoluteDate>
 *       With <startAbsoluteDate> and <endAbsoluteDate> format as yyyy-MM-dd.HH:mm
 *   - Relative: <startRelativeDate>;<endRelativeDate>
 *       With <startRelativeDate> and <endRelativeDate> format as
 *       R[[M<Month_of_the_year>.]D<Day_of_the_week>.]HH:mm
 *
 *       <Month_of_the_year> uses Calendar.MONTH numeration
 *       <Day_of_the_week> uses Calendar.DAY_OF_WEEK numeration
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TriggerAction implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd.HH:mm");

    public enum CalendarRelative {
        MONTH, DAY_OF_THE_WEEK, HOUR_OF_THE_DAY, MINUTE
    }

    @JsonInclude(Include.NON_NULL)
    private String tenantId;

    @JsonInclude
    private String actionPlugin;

    @JsonInclude
    private String actionId;

    @JsonInclude(Include.NON_EMPTY)
    Set<String> states;

    @JsonInclude(Include.NON_NULL)
    private String calendar;

    @JsonIgnore
    private transient String[] calendarInterval = null;

    public TriggerAction() {
        this(null, null, null);
    }

    public TriggerAction(String actionPlugin, String actionId) {
        this(null, actionPlugin, actionId);
    }

    public TriggerAction(String tenantId, String actionPlugin, String actionId) {
        this(tenantId, actionPlugin, actionId, new HashSet<>(), null);
    }

    public TriggerAction(String tenantId, String actionPlugin, String actionId, Set<String> states) {
        this(tenantId, actionPlugin, actionId, new HashSet<>(states), null);
    }

    public TriggerAction(String tenantId, String actionPlugin, String actionId, String calendar) {
        this(tenantId, actionPlugin, actionId, new HashSet<>(), calendar);
    }

    public TriggerAction(String tenantId, String actionPlugin, String actionId, Set<String> states,
                         String calendar) {
        this.tenantId = tenantId;
        this.actionPlugin = actionPlugin;
        this.actionId = actionId;
        this.states = states;
        this.calendar = calendar;
        if (!isEmpty(this.calendar)) {
            calendarInterval = calendar.split(";");
            if (calendarInterval.length != 2) {
                throw new IllegalArgumentException("calendar must follow pattern <startCalendarExpression>;" +
                        "<endCalendarExpression>");
            }
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public Set<String> getStates() {
        if (states == null) {
            states = new HashSet<>();
        }
        return states;
    }

    public void setStates(Set<String> states) {
        this.states = states;
    }

    public void addState(String state) {
        getStates().add(state);
    }

    public String getCalendar() {
        return calendar;
    }

    public void setCalendar(String calendar) {
        this.calendar = calendar;
        if (!isEmpty(this.calendar)) {
            calendarInterval = this.calendar.split(";");
            if (calendarInterval.length != 2) {
                throw new IllegalArgumentException("calendar must follow pattern <startCalendarExpression>;" +
                        "<endCalendarExpression>");
            }
        }
    }

    /**
     * @return true if TriggerAction.calendar is a relative expression
     *         false if no calendar or absolute expression defined
     */
    @JsonIgnore
    public boolean isRelativeCalendar() {
        if (isEmpty(calendarInterval)) {
            return false;
        }
        return calendarInterval[0].charAt(0) == 'R' && calendarInterval[1].charAt(0) == 'R';
    }

    /**
     * @return a string with the start date expression defined on TriggerAction.calendar interval
     *         null if no calendar expression defined
     */
    @JsonIgnore
    public String getStartCalendar() {
        if (isEmpty(calendarInterval)) {
            return null;
        }
        return calendarInterval[0];
    }

    /**
     * @return a string with the end date expression defined on TriggerAction.calendar interval
     *         null if no calendar expression defined
     */
    @JsonIgnore
    public String getEndCalendar() {
        if (isEmpty(calendarInterval)) {
            return null;
        }
        return calendarInterval[1];
    }

    /**
     * @return a java.util.Date with the absolute start date expression defined on TriggerAction.calendar interval
     *         null if no calendar defined or no absolute expression defined
     * @throws Exception if absolute date expression cannot be parsed
     */
    @JsonIgnore
    public Date getStartCalendarDate() throws Exception {
        if (isEmpty(calendarInterval)) {
            return null;
        }
        if (isRelativeCalendar()) {
            return null;
        }
        return parser.parse(calendarInterval[0]);
    }

    /**
     * @return a java.util.Date with the absolute end date expression defined on TriggerAction.calendar interval
     *         null if no calendar defined or no absolute expression defined
     * @throws Exception if absolute date expression cannot be parsed
     */
    @JsonIgnore
    public Date getEndCalendarDate() throws Exception {
        if (isEmpty(calendarInterval)) {
            return null;
        }
        if (isRelativeCalendar()) {
            return null;
        }
        return parser.parse(calendarInterval[1]);
    }

    /**
     * Parse the start relative expression defined on TriggerAction.calendar interval.
     *
     * @param field CalendarRelative field of the expression to parse
     * @return the value of the field defined on the relative param
     *         -1 if value not present or expression is not relative
     * @throws Exception if specific field cannot be extracted
     */
    @JsonIgnore
    public int getStartCalendarRelative(CalendarRelative field) throws Exception {
        if (isEmpty(calendarInterval)) {
            return -1;
        }
        if (!isRelativeCalendar()) {
            return -1;
        }
        if (isEmpty(calendarInterval[0])) {
            return -1;
        }
        return extractField(field, calendarInterval[0]);
    }

    /**
     * Parse the end relative expression defined on TriggerAction.calendar interval.
     *
     * @param field CalendarRelative field of the expression to parse
     * @return the value of the field defined on the relative param
     *         -1 if value not present or expression is not relative
     * @throws Exception if specific field cannot be extracted
     */
    @JsonIgnore
    public int getEndCalendarRelative(CalendarRelative field) throws Exception {
        if (isEmpty(calendarInterval)) {
            return -1;
        }
        if (!isRelativeCalendar()) {
            return -1;
        }
        if (isEmpty(calendarInterval[1])) {
            return -1;
        }
        return extractField(field, calendarInterval[1]);
    }

    private int extractField(CalendarRelative field, String interval) throws Exception {
        int iMonth = -1, endMonth = -1, iDay = -1, endDay = -1, hourSeparator = -1, startHour = -1;
        for (int i = 0; i < interval.length(); i++) {
            if (interval.charAt(i) == 'M') {
                iMonth = i;
            }
            if (interval.charAt(i) == 'D') {
                iDay = i;
            }
            if (interval.charAt(i) == '.') {
                if (iMonth > 0 && iDay < 0) {
                    endMonth = i;
                }
                if (iDay > 0) {
                    endDay = i;
                }
            }
            if (interval.charAt(i) == ':') {
                hourSeparator = i;
            }
        }
        if (endDay > 0) {
            startHour = endDay + 1;
        }
        if (endMonth > 0 && endDay < 0) {
            startHour = endMonth + 1;
        }
        if (endMonth < 0 && endDay < 0) {
            startHour = 1;
        }
        switch (field) {
            case MONTH:
                if (iMonth < 0) {
                    return -1;
                }
                if (endMonth < -1) {
                    return -1;
                }
                return Integer.valueOf(interval.substring(iMonth + 1, endMonth));
            case DAY_OF_THE_WEEK:
                if (iDay < 0) {
                    return -1;
                }
                if (endDay < -1) {
                    return -1;
                }
                return Integer.valueOf(interval.substring(iDay + 1, endDay));
            case HOUR_OF_THE_DAY:
                if (hourSeparator < 0) {
                    return -1;
                }
                return Integer.valueOf(interval.substring(startHour, hourSeparator));
            case MINUTE:
                if (hourSeparator < 0) {
                    return -1;
                }
                return Integer.valueOf(interval.substring(hourSeparator + 1));
            default:
                return -1;
        }
    }

    private boolean isEmpty(String s) {
        return (null == s || s.isEmpty());
    }

    private boolean isEmpty(String[] a) {
        return (null == a || a.length == 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TriggerAction that = (TriggerAction) o;

        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
        if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null) return false;
        if (actionId != null ? !actionId.equals(that.actionId) : that.actionId != null) return false;
        if (states != null ? !states.equals(that.states) : that.states != null) return false;
        return calendar != null ? calendar.equals(that.calendar) : that.calendar == null;

    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (actionPlugin != null ? actionPlugin.hashCode() : 0);
        result = 31 * result + (actionId != null ? actionId.hashCode() : 0);
        result = 31 * result + (states != null ? states.hashCode() : 0);
        result = 31 * result + (calendar != null ? calendar.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TriggerAction" + '[' +
                "tenantId='" + tenantId + '\'' +
                ", actionPlugin='" + actionPlugin + '\'' +
                ", actionId='" + actionId + '\'' +
                ", states=" + states +
                ", calendar='" + calendar + '\'' +
                ']';
    }
}
