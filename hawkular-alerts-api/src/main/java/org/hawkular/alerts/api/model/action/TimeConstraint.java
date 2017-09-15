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
package org.hawkular.alerts.api.model.action;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.IllegalFormatException;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Define a time interval (startTime, endTime) used as a constraint for action execution.
 * Time interval can be defined in an absolute or relative expression.
 * <p>
 * An absolute time interval uses the pattern yyyy.MM.dd[,HH:mm] for startTime and endTime properties.
 * For example,these representations are valid absolute expressions for time interval:
 *</p><pre>
 *              {startTime: "2016.02.01", endTime: "2016.03.01", relative: false}
 *              {startTime: "2016.02.01,09:00", endTime: "2016.03.01,18:00", relative: false}
 *</pre>
 * Absolute time interval are marked with flag relative set to false.
 * Hour and minutes can be optional in absolute format, by default it takes 00:00 value.
 *<p>
 * A relative interval is used for repetitive expressions.
 * It can be defined an interval between months (i.e. December to March), between days of the week (i.e. Sunday to
 * Friday), between hours and minutes (i.e. 23:00 to 04:30), or a combination of month, day of the week and/or hours
 * and minutes.
 * Relative interval uses the pattern [MMM],[WWW],[HH:mm] where months and days of the week can be used in long or
 * short format.
 * Same pattern should be applied to both startTime and endTime properties.
 * For example, these representations are valid relative expressions for time interval:
 *</p><pre>
 *          {startTime: "Jul", endTime: "Dec", relative: true}
 *          {startTime: "July", endTime: "December", relative: true}
 *
 *          All dates within July and December months will be valid.
 *
 *          {startTime: "Jul,Mon", endTime: "Dec,Fri", relative: true}
 *          {startTime: "July,Monday", endTime: "December,Friday", relative: true}
 *
 *          All dates within July and December months and within Monday and Friday days are valid.
 *          So, a Sunday day of August will not be valid according previous example.
 *
 *          {startTime: "Jul,Mon,09:00", endTime: "Dec,Fri,18:00", relative: true}
 *          {startTime: "July,Monday", endTime: "December,Friday", relative: true}
 *
 *          All dates within July and December months and within Monday and Friday days and time between 09:00 and
 *          18:00 are valid.
 *          So, a Monday day of August at 18:01 will not be valid according previous example.
 *
 *          {startTime:"Monday,09:00", endTime:"Friday,18:00", relative: true}
 *          {startTime:"Mon,09:00", endTime:"Fri,18:00", relative: true}
 *
 *          All dates within Monday and Friday day and time between 09:00 and 18:00 will be valid.
 *          So, a Monday at 18:01 will not be valid according previous example.
 *
 *          {startTime:"July,09:00", endTime:"August,18:00", relative: true}
 *          {startTime:"Jul,09:00", endTime:"Aug,18:00", relative: true}
 *
 *          All dates within July and December months and time between 09:00 and 18:00 are valid.
 *          A day of August at 18:01 will not be valid according previous example.
 *
 *         {startTime:"09:00", endTime:"18:00", relative: true}
 *
 *         All times within 09:00 and 18:00 are valid.
 *</pre>
 * TimeConstraint inRange property defines whether a given time must fall inside or outside
 * the defined interval. Setting inRange == true means the constraint will be satisfied if a
 * given date is within the interval (taking the limits as inclusive). By setting
 * inRange == false the constraint is satisfied if a given date is outside of the interval.
 * By default, inRange == true.
 * For example,
 *<pre>
 *         {startTime:"09:00", endTime:"18:00", relative: true, inRange: true}
 *
 *         All times within 09:00 and 18:00 are satisfied by the interval.
 *
 *         {startTime:"09:00", endTime:"18:00", relative: true, inRange: false}
 *
 *         All times from 18:01 to 08:59 are satisfied in the interval.
 *</pre>
 * By default the defined absolute or relative intervals use the default time zone (of the server).
 * This can be unpredictable unless the server time zone is well known and acceptable.  It is
 * recommended to explicitly set the time zone for which the TimeConstraint intervals are applicable.
 * This is done by setting the timeZoneName property.
 * For example, here is a TimeConstraint for business hours:
 *<pre>
 *         {startTime:"09:00", endTime:"18:00", relative: true, inRange: true}
 *</pre>
 * If the server is running in London then this reflects business hours in London. But perhaps the
 * admins are in New York and the TimeConstraint should actually reflect their local time zone.
 * In that case use:
 *<pre>
 *         {startTime:"09:00", endTime:"18:00", timeZoneName: "America/New_York", relative: true}
 *</pre>
 * It is also possible to use a GMT-relative value:
 *<pre>
 *         {startTime:"09:00", endTime:"18:00", timeZoneName: "GMT-5:00", relative: true}
 *</pre>
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "Define a time interval (startTime, endTime) used as a constraint for action execution. + \n" +
        "Time interval can be defined in an absolute or relative expression. + \n" +
        " + \n" +
        "An absolute time interval uses the pattern yyyy.MM.dd[,HH:mm] for startTime and endTime properties. + \n" +
        "For example, these representations are valid absolute expressions for time interval: + \n" +
        " + \n" +
        "{startTime: \"2016.02.01\", endTime: \"2016.03.01\", relative: false} + \n" +
        "{startTime: \"2016.02.01,09:00\", endTime: \"2016.03.01,18:00\", relative: false} + \n" +
        " + \n" +
        "Absolute time interval are marked with flag relative set to false. + \n" +
        "Hour and minutes can be optional in absolute format, by default it takes 00:00 value. + \n" +
        " + \n" +
        "A relative interval is used for repetitive expressions. + \n" +
        "It can be defined an interval between months (i.e. December to March), between days of the week + \n " +
        "(i.e. Sunday to Friday), between hours and minutes (i.e. 23:00 to 04:30), or a combination of month, + \n" +
        "day of the week and/or hours and minutes. + \n" +
        "Relative interval uses the pattern [MMM],[WWW],[HH:mm] where months and days of the week can be used " +
        "in long or short format. + \n" +
        "Same pattern should be applied to both startTime and endTime properties. + \n" +
        "For example, these representations are valid relative expressions for time interval: + \n" +
        " + \n" +
        "{startTime: \"Jul\", endTime: \"Dec\", relative: true} + \n" +
        "{startTime: \"July\", endTime: \"December\", relative: true} + \n" +
        " + \n" +
        "All dates within July and December months will be valid. + \n" +
        " + \n" +
        "{startTime: \"Jul,Mon\", endTime: \"Dec,Fri\", relative: true} + \n" +
        "{startTime: \"July,Monday\", endTime: \"December,Friday\", relative: true} + \n" +
        " + \n" +
        "All dates within July and December months and within Monday and Friday days are valid. + \n" +
        "So, a Sunday day of August will not be valid according previous example. + \n" +
        " + \n" +
        "{startTime: \"Jul,Mon,09:00\", endTime: \"Dec,Fri,18:00\", relative: true} + \n" +
        "{startTime: \"July,Monday\", endTime: \"December,Friday\", relative: true} + \n" +
        " + \n" +
        "All dates within July and December months and within Monday and Friday days and time between 09:00 and" +
        "18:00 are valid. + \n" +
        "So, a Monday day of August at 18:01 will not be valid according previous example. + \n" +
        " + \n" +
        "{startTime:\"Monday,09:00\", endTime:\"Friday,18:00\", relative: true} + \n" +
        "{startTime:\"Mon,09:00\", endTime:\"Fri,18:00\", relative: true} + \n" +
        " + \n" +
        "All dates within Monday and Friday day and time between 09:00 and 18:00 will be valid. + \n" +
        "So, a Monday at 18:01 will not be valid according previous example. + \n" +
        " + \n" +
        "{startTime:\"July,09:00\", endTime:\"August,18:00\", relative: true} + \n" +
        "{startTime:\"Jul,09:00\", endTime:\"Aug,18:00\", relative: true} + \n" +
        " + \n" +
        "All dates within July and December months and time between 09:00 and 18:00 are valid. + \n" +
        "A day of August at 18:01 will not be valid according previous example. + \n" +
        " + \n" +
        "{startTime:\"09:00\", endTime:\"18:00\", relative: true} + \n" +
        " + \n" +
        "All times within 09:00 and 18:00 are valid. + \n" +
        " + \n" +
        "TimeConstraint inRange property defines whether a given time must fall inside or outside + \n" +
        "the defined interval. Setting inRange == true means the constraint will be satisfied if a + \n" +
        "given date is within the interval (taking the limits as inclusive). By setting + \n" +
        "inRange == false the constraint is satisfied if a given date is outside of the interval. + \n" +
        "By default, inRange == true. + \n" +
        "For example, + \n" +
        " + \n" +
        "{startTime:\"09:00\", endTime:\"18:00\", relative: true, inRange: true} + \n" +
        " + \n" +
        "All times within 09:00 and 18:00 are satisfied by the interval. + \n" +
        " + \n" +
        "{startTime:\"09:00\", endTime:\"18:00\", relative: true, inRange: false} + \n" +
        " + \n" +
        "All times from 18:01 to 08:59 are satisfied in the interval. + \n" +
        " + \n" +
        "By default the defined absolute or relative intervals use the default time zone (of the server). + \n" +
        "This can be unpredictable unless the server time zone is well known and acceptable.  It is + \n" +
        "recommended to explicitly set the time zone for which the TimeConstraint intervals are applicable. + \n" +
        "This is done by setting the timeZoneName property. + \n" +
        "For example, here is a TimeConstraint for business hours: + \n" +
        " + \n" +
        "        {startTime:\"09:00\", endTime:\"18:00\"} + \n" +
        " + \n" +
        "If the server is running in London then this reflects business hours in London. But perhaps the + \n" +
        "admins are in New York and the TimeConstraint should actually reflect their local time zone. + \n" +
        "In that case use: + \n" +
        " + \n" +
        "        {startTime:\"09:00\", endTime:\"18:00\", timeZoneName: \"America/New_York\", relative: true} + \n" +
        " + \n" +
        "It is also possible to use a GMT-relative value: + \n" +
        " + \n" +
        "        {startTime:\"09:00\", endTime:\"18:00\", timeZoneName: \"GMT-5:00\", relative: true} + \n")
public class TimeConstraint implements Serializable {

    public enum MONTH {

        JANUARY("jan"),
        FEBRUARY("feb"),
        MARCH("mar"),
        APRIL("apr"),
        MAY("may"),
        JUNE("jun"),
        JULY("jul"),
        AUGUST("aug"),
        SEPTEMBER("sep"),
        OCTOBER("oct"),
        NOVEMBER("nov"),
        DECEMBER("dec");

        private String month;

        MONTH(String month) {
            this.month = month;
        }

        public String getMonth() {
            return month;
        }

        public static MONTH fromString(String s) {
            if (s == null || s.isEmpty()) {
                return null;
            }
            for (MONTH m : MONTH.values()) {
                if (m.getMonth().equalsIgnoreCase(s)) {
                    return m;
                }
            }
            return null;
        }
    }

    public enum DAY {

        SUNDAY("sun"),
        MONDAY("mon"),
        TUESDAY("tue"),
        WEDNESDAY("wed"),
        THURSDAY("thu"),
        FRIDAY("fri"),
        SATURDAY("sat");

        private String day;

        DAY(String day) {
            this.day = day;
        }

        public String getDay() {
            return day;
        }

        public static DAY fromString(String s) {
            if (s == null || s.isEmpty()) {
                return null;
            }
            for (DAY d : DAY.values()) {
                if (d.getDay().equalsIgnoreCase(s)) {
                    return d;
                }
            }
            return null;
        }
    }

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy.MM.dd");
    private static final SimpleDateFormat dateTimeParser = new SimpleDateFormat("yyyy.MM.dd,HH:mm");

    /**
     * Define the start of the time interval.
     * It can be in absolute or relative format.
     */
    @ApiModelProperty(value = "Define the start of the time interval. It can be in absolute or relative format.",
            position = 0,
            required = true)
    @JsonInclude
    private String startTime;

    /**
     * Define the end of the time interval.
     * It can be in absolute or relative format.
     */
    @ApiModelProperty(value = "Define the end of the time interval. It can be in absolute or relative format.",
            position = 1,
            required = true)
    @JsonInclude
    private String endTime;

    /**
     * Define if startTime and endTime properties are defined in absolute or relative format.
     */
    @ApiModelProperty(value = "Define if startTime and endTime properties are defined in absolute or relative format.",
            position = 2,
            example = "true")
    @JsonInclude(Include.NON_NULL)
    private boolean relative;

    /**
     * Indicate if time constraint is satisfied when a given timestamp is inside or outside the interval.
     */
    @ApiModelProperty(value = "Indicate if time constraint is satisfied when a given timestamp is inside or outside " +
            "the interval.",
            position = 3,
            example = "true")
    @JsonInclude(Include.NON_NULL)
    private boolean inRange;

    /**
     * Indicate the time zone in which the times are expressed. If not specified the server's default time zone is
     * applied. Time zone is expressed in standard <i>Area/Location</i> format. It is recommended to specify the time
     * zone unless you are sure of the server environment.
     */
    @ApiModelProperty(value = "Indicate the time zone in which the times are expressed. If not specified the " +
            "server's default time zone is applied. Time zone is expressed in standard Area/Location format. " +
            "It is recommended to specify the time zone unless you are sure of the server environment.",
            position = 4,
            required = false)
    @JsonInclude(Include.NON_NULL)
    private String timeZoneName;

    @JsonIgnore
    private transient int startMonth = -1;

    @JsonIgnore
    private transient int startDay = -1;

    @JsonIgnore
    private transient int startMinute = -1;

    @JsonIgnore
    private transient int endMonth = -1;

    @JsonIgnore
    private transient int endDay = -1;

    @JsonIgnore
    private transient int endMinute = -1;

    @JsonIgnore
    private transient Date startDate = null;

    @JsonIgnore
    private transient Date endDate = null;

    @JsonIgnore
    private transient TimeZone timeZone;

    public TimeConstraint() {
        this("Jan","Dec", true, true);
    }

    public TimeConstraint(String startTime, String endTime) {
        this(startTime, endTime, null, true, true);
    }

    public TimeConstraint(String startTime, String endTime, String timeZoneName) {
        this(startTime, endTime, timeZoneName, true, true);
    }

    public TimeConstraint(String startTime, String endTime, boolean relative) {
        this(startTime, endTime, null, relative, true);
    }

    public TimeConstraint(String startTime, String endTime, String timeZoneName, boolean relative) {
        this(startTime, endTime, timeZoneName, relative, true);
    }

    public TimeConstraint(String startTime, String endTime, boolean relative, boolean inRange) {
        this(startTime, endTime, null, relative, inRange);
    }

    public TimeConstraint(TimeConstraint timeConstraint) {
        if (timeConstraint == null) {
            throw new IllegalArgumentException("timeConstraint must be not null");
        }
        this.startTime = timeConstraint.getStartTime();
        this.endTime = timeConstraint.getEndTime();
        this.relative = timeConstraint.isRelative();
        this.inRange = timeConstraint.isInRange();
        setTimeZoneName(timeConstraint.getTimeZoneName());
    }

    public TimeConstraint(String startTime, String endTime, String timeZoneName, boolean relative, boolean inRange) {
        if (isEmpty(startTime)) {
            throw new IllegalArgumentException("startTime must be not null");
        }
        if (isEmpty(endTime)) {
            throw new IllegalArgumentException("endTime must be not null");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.relative = relative;
        this.inRange = inRange;
        setTimeZoneName(timeZoneName);
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        if (isEmpty(startTime)) {
            throw new IllegalArgumentException("startTime must be not null");
        }
        this.startTime = startTime;
        if (relative) {
            updateRelative();
        } else {
            updateAbsolute();
        }
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        if (isEmpty(endTime)) {
            throw new IllegalArgumentException("endTime must be not null");
        }
        this.endTime = endTime;
        if (relative) {
            updateRelative();
        } else {
            updateAbsolute();
        }
    }

    public boolean isRelative() {
        return relative;
    }

    public void setRelative(boolean relative) {
        this.relative = relative;
        if (relative) {
            updateRelative();
        } else {
            updateAbsolute();
        }
    }

    public boolean isInRange() {
        return inRange;
    }

    public void setInRange(boolean inRange) {
        this.inRange = inRange;
        if (relative) {
            updateRelative();
        } else {
            updateAbsolute();
        }
    }

    public String getTimeZoneName() {
        return timeZoneName;
    }

    public void setTimeZoneName(String timeZoneName) {
        this.timeZoneName = timeZoneName;
        this.timeZone = (null == timeZoneName) ? TimeZone.getDefault() : TimeZone.getTimeZone(timeZoneName);
        // Parse absolute date/time in the target tz
        dateParser.setTimeZone(this.timeZone);
        dateTimeParser.setTimeZone(this.timeZone);
        if (relative) {
            updateRelative();
        } else {
            updateAbsolute();
        }
    }

    /**
     * Validate whether a timestamp satisfies the time interval defined in the constraint.
     *
     * @param timestamp A specific timestamp to validate
     * @return true if timestamp satisfies the time constraint
     *         false if timestamp does not satisfy the time constraint
     * @throws IllegalFormatException if startTime or endTime have an illegal format
     */
    @JsonIgnore
    public boolean isSatisfiedBy(long timestamp) throws IllegalArgumentException {
        if (relative) {
            return checkRelative(timestamp);
        } else {
            return checkAbsolute(timestamp);
        }
    }

    private void updateRelative() {
        startMonth = -1;
        endMonth = -1;
        startDay = -1;
        endDay = -1;
        startMinute = -1;
        endMinute = -1;

        String[] start = startTime.split(",");
        String[] end = endTime.split(",");
        int startFields = start.length;
        if (startFields > 3) {
            throw new IllegalArgumentException("startTime has more than 3 fields");
        }
        int endFields = end.length;
        if (endFields > 3) {
            throw new IllegalArgumentException("endTime has more than 3 fields");
        }
        switch (startFields) {
            case 3:
                startMonth = month(start[0]);
                startDay = day(start[1]);
                startMinute = minute(start[2]);
                break;
            case 2:
                startMonth = month(start[0]);
                startDay = day(start[0]);
                startMinute = minute(start[1]);
                break;
            case 1:
                startMonth = month(start[0]);
                startDay = day(start[0]);
                startMinute = minute(start[0]);
                break;
            default:
        }
        switch (endFields) {
            case 3:
                endMonth = month(end[0]);
                endDay = day(end[1]);
                endMinute = minute(end[2]);
                break;
            case 2:
                endMonth = month(end[0]);
                endDay = day(end[0]);
                endMinute = minute(end[1]);
                break;
            case 1:
                endMonth = month(end[0]);
                endDay = day(end[0]);
                endMinute = minute(end[0]);
                break;
            default:
        }
        if (startMonth == -1 && startDay == -1 && startMinute == -1) {
            throw new IllegalArgumentException("Bad format on startTime: " + startTime);
        }
        if (endMonth == -1 && endDay == -1 && endMinute == -1) {
            throw new IllegalArgumentException("Bad format on endTime: " + endTime);
        }
    }

    private boolean isInInterval(int start, int end, int value) {
        if (start <= end) {
            return (start <= value && value <= end);
        } else {
            return (start <= value || value <= end);
        }
    }

    private boolean checkRelative(long timestamp) throws IllegalArgumentException {
        // make sure the calendar reflects Month/Day/Minute in the TC's target time zone
        Calendar cal = Calendar.getInstance(this.timeZone);
        cal.setTimeInMillis(timestamp);
        if (inRange) {
            // Check month
            int month = cal.get(Calendar.MONTH);
            if (startMonth != -1 && endMonth != -1 && !isInInterval(startMonth, endMonth, month)) {
                return false;
            }
            // Check day
            int day = cal.get(Calendar.DAY_OF_WEEK);
            if (startDay != -1 && endDay != -1 && !isInInterval(startDay, endDay, day)) {
                return false;
            }
            // Check minute
            int minute = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);
            if (startMinute != -1 && endMinute != -1 && !isInInterval(startMinute, endMinute, minute)) {
                return false;
            }
            return true;
        } else {
            // Check month
            int month = cal.get(Calendar.MONTH);
            if (startMonth != -1 && endMonth != -1 && !isInInterval(startMonth, endMonth, month)) {
                return true;
            }
            // Check day
            int day = cal.get(Calendar.DAY_OF_WEEK);
            if (startDay != -1 && endDay != -1 && !isInInterval(startDay, endDay, day)) {
                return true;
            }
            // Check minute
            int minute = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);
            if (startMinute != -1 && endMinute != -1 && !isInInterval(startMinute, endMinute, minute)) {
                return true;
            }
            return false;
        }
    }

    private int month(String sMonth) {
        if (isEmpty(sMonth)) {
            return -1;
        }
        if (sMonth.length() < 3) {
            return -1;
        }
        String prefix = sMonth.substring(0, 3).toLowerCase();
        MONTH m = MONTH.fromString(prefix);
        if (m == null) {
            return -1;
        }
        switch (m) {
            case JANUARY:
                return Calendar.JANUARY;
            case FEBRUARY:
                return Calendar.FEBRUARY;
            case MARCH:
                return Calendar.MARCH;
            case APRIL:
                return Calendar.APRIL;
            case MAY:
                return Calendar.MAY;
            case JUNE:
                return Calendar.JUNE;
            case JULY:
                return Calendar.JULY;
            case AUGUST:
                return Calendar.AUGUST;
            case SEPTEMBER:
                return Calendar.SEPTEMBER;
            case OCTOBER:
                return Calendar.OCTOBER;
            case NOVEMBER:
                return Calendar.NOVEMBER;
            case DECEMBER:
                return Calendar.DECEMBER;
            default:
                return -1;
        }
    }

    private int day(String sDay) {
        if (isEmpty(sDay)) {
            return -1;
        }
        if (sDay.length() < 3) {
            return -1;
        }
        String prefix = sDay.substring(0, 3).toLowerCase();
        DAY d = DAY.fromString(prefix);
        if (d == null) {
            return -1;
        }
        switch (d) {
            case SUNDAY:
                return Calendar.SUNDAY;
            case MONDAY:
                return Calendar.MONDAY;
            case TUESDAY:
                return Calendar.TUESDAY;
            case WEDNESDAY:
                return Calendar.WEDNESDAY;
            case THURSDAY:
                return Calendar.THURSDAY;
            case FRIDAY:
                return Calendar.FRIDAY;
            case SATURDAY:
                return Calendar.SATURDAY;
            default:
                return -1;
        }
    }

    private int minute(String sTime) {
        if (isEmpty(sTime)) {
            return -1;
        }
        int separator = sTime.indexOf(":");
        if (separator < 0) {
            return -1;
        }
        try {
            return (Integer.valueOf(sTime.substring(0, separator)) * 60) +
                    Integer.valueOf(sTime.substring(separator + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean checkAbsolute(long timestamp) throws IllegalArgumentException {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        Date timeDate = cal.getTime();

        if (inRange) {
            return (startDate.compareTo(timeDate) <= 0 && endDate.compareTo(timeDate) >= 0);
        } else {
            return (startDate.compareTo(timeDate) > 0 || endDate.compareTo(timeDate) < 0);
        }
    }

    private void updateAbsolute() {
        startDate = null;
        endDate = null;
        try {
            if (startTime.indexOf(",") == -1) {
                startDate = dateParser.parse(startTime);
            } else {
                startDate = dateTimeParser.parse(startTime);
            }
            if (endTime.indexOf(",") == -1) {
                endDate = dateParser.parse(endTime);
            } else {
                endDate = dateTimeParser.parse(endTime);
            }
        } catch (ParseException e) {
            throw new IllegalArgumentException("Bad format on startTime and/or endTime: " + e.getMessage());
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public String toString() {
        return "TimeConstraint" + '[' +
                "startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", relative=" + relative +
                ", inRange=" + inRange +
                ", timeZoneName=" + timeZoneName +
                ", timeZone=" + timeZone.toString() +
                ']';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimeConstraint that = (TimeConstraint) o;

        if (relative != that.relative) return false;
        if (inRange != that.inRange) return false;
        if (startTime != null ? !startTime.equals(that.startTime) : that.startTime != null) return false;
        return endTime != null ? endTime.equals(that.endTime) : that.endTime == null;

    }

    @Override
    public int hashCode() {
        int result = startTime != null ? startTime.hashCode() : 0;
        result = 31 * result + (endTime != null ? endTime.hashCode() : 0);
        result = 31 * result + (relative ? 1 : 0);
        result = 31 * result + (inRange ? 1 : 0);
        return result;
    }
}
