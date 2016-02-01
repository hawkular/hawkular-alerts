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
package org.hawkular.alerts.api.model.action;

import static com.fasterxml.jackson.annotation.JsonInclude.*;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.IllegalFormatException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Define a time interval used as a constraint for action execution.
 * Time interval can be defined in a absolute or relative format.
 * An absolute time format uses the pattern:
 *  - yyyy.MM.dd[,HH:mm]
 *    i.e. of valid formats:
 *                              2016.02.01
 *                              2016.02.01,18:00
 *
 *    Hour and minutes can be optional in absolute format, by default it takes 00:00 value.
 *
 * A relative format can define month (long or short), day of the week (long or short) and hour and minutes using
 * the following format:
 *  - [MMM],[WWW],[HH:mm]
 *    i.e. of valid formats:
 *                              July
 *                              Jul
 *                              July,Mon
 *                              July
 *                              Jul,Monday
 *                              Jul,Mon,00:00
 *                              Mon,01:00
 *                              Jul,01:00
 *                              01:00
 *
 *    If not hour and minutes defined, by default it takes 00:00 value.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
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
    @JsonInclude
    private String startTime;

    /**
     * Define the end of the time interval.
     * It can be in absolute or relative format.
     */
    @JsonInclude
    private String endTime;

    /**
     * Define if startTime and endTime properties are defined in absolute or relative format.
     */
    @JsonInclude(Include.NON_NULL)
    private boolean relative;

    /**
     * Indicate if time constraint is satisfied when a given timestamp is inside or outside the interval.
     */
    @JsonInclude(Include.NON_NULL)
    private boolean inRange;

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

    public TimeConstraint() {
        this("Jan","Dec", true, true);
    }

    public TimeConstraint(String startTime, String endTime) {
        this(startTime, endTime, true, true);
    }

    public TimeConstraint(String startTime, String endTime, boolean relative) {
        this(startTime, endTime, relative, true);
    }

    public TimeConstraint(String startTime, String endTime, boolean relative, boolean inRange) {
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
        if (relative) {
            updateRelative();
        } else {
            updateAbsolute();
        }
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
        Calendar cal = Calendar.getInstance();
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
        return (Integer.valueOf(sTime.substring(0, separator)) * 60) +  Integer.valueOf(sTime.substring(separator +1));
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
