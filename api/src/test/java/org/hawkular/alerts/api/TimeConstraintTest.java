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
package org.hawkular.alerts.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.hawkular.alerts.api.model.action.TimeConstraint;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TimeConstraintTest {

    Calendar cal = Calendar.getInstance();
    long timestamp;

    @Test
    public void absoluteTest() throws Exception {
        TimeConstraint tc = new TimeConstraint("2016.02.01", "2016.02.03", false);

        cal.set(2016, Calendar.FEBRUARY, 1, 0, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 2, 23, 59);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JANUARY, 31, 23, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 3, 0, 1);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("2016.02.01,03:00");
        tc.setEndTime("2016.02.03,04:34");

        cal.set(2016, Calendar.FEBRUARY, 1, 0, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 3, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 3, 4, 33);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 3, 4, 35);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        // range == false

        tc.setStartTime("2016.02.01,03:00");
        tc.setEndTime("2016.02.03,04:34");
        tc.setInRange(false);

        cal.set(2016, Calendar.FEBRUARY, 1, 0, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 3, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 3, 4, 33);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 3, 4, 35);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 3, 4, 35);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        // tz
        tc.setStartTime("2016.02.03,10:00");
        tc.setEndTime("2016.02.03,18:00");
        tc.setInRange(true);

        Calendar gmtCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        gmtCal.set(2016, Calendar.FEBRUARY, 3, 16, 00);
        timestamp = gmtCal.getTimeInMillis();

        tc.setTimeZoneName("GMT");
        assertTrue(tc.isSatisfiedBy(timestamp));

        tc.setTimeZoneName("GMT-4:00"); // GMT 14:00-22:00
        assertTrue(tc.isSatisfiedBy(timestamp));

        tc.setTimeZoneName("GMT+4:00"); // GMT 06:00-14:00
        assertFalse(tc.isSatisfiedBy(timestamp));
    }

    @Test
    public void relativeTest() throws Exception {
        TimeConstraint tc = new TimeConstraint("10:00", "13:00");

        cal.set(2016, Calendar.FEBRUARY, 1, 10, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 14, 13, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 9, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 13, 1);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Feb,10:00");
        tc.setEndTime("Mar,13:00");

        cal.set(2016, Calendar.FEBRUARY, 1, 10, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 14, 12, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 13, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 9, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 13, 1);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JULY, 14, 12, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Mon,09:00");
        tc.setEndTime("Fri,18:00");

        cal.set(2016, Calendar.FEBRUARY, 1, 9, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 5, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 8, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 5, 18, 1);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 8, 9, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 12, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Jul,Mon,09:00");
        tc.setEndTime("Aug,Fri,18:00");

        cal.set(2016, Calendar.JULY, 18, 9, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.AUGUST, 26, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JULY, 18, 8, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.AUGUST, 26, 18, 1);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JULY, 17, 9, 01);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.AUGUST, 27, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        // range == false

        tc.setStartTime("10:00");
        tc.setEndTime("13:00");
        tc.setInRange(false);

        cal.set(2016, Calendar.FEBRUARY, 1, 10, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 14, 13, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 9, 59);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 13, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Feb,10:00");
        tc.setEndTime("Mar,13:00");

        cal.set(2016, Calendar.FEBRUARY, 1, 10, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 14, 12, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 13, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 9, 59);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 13, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JULY, 14, 12, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Mon,09:00");
        tc.setEndTime("Fri,18:00");

        cal.set(2016, Calendar.FEBRUARY, 1, 9, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 5, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 8, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 5, 18, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 8, 9, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 12, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Jul,Mon,09:00");
        tc.setEndTime("Aug,Fri,18:00");

        cal.set(2016, Calendar.JULY, 18, 9, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.AUGUST, 26, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JULY, 18, 8, 59);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.AUGUST, 26, 18, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JULY, 17, 9, 01);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.AUGUST, 27, 18, 0);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        // tz
        tc.setStartTime("Jul,Mon,02:00");
        tc.setEndTime("Jul,Fri,18:00");
        tc.setInRange(true);

        Calendar gmtCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        gmtCal.set(2016, Calendar.JULY, 18, 16, 00); // Monday 16:00
        timestamp = gmtCal.getTimeInMillis();

        tc.setTimeZoneName("GMT");
        assertTrue(tc.isSatisfiedBy(timestamp));  // Monday 16:00

        tc.setTimeZoneName("GMT-4:00");
        assertTrue(tc.isSatisfiedBy(timestamp));  // Monday 12:00

        tc.setTimeZoneName("GMT+4:00");
        assertFalse(tc.isSatisfiedBy(timestamp)); // Monday 20:00

        gmtCal.set(2016, Calendar.JULY, 17, 16, 00); // Sunday 16:00
        timestamp = gmtCal.getTimeInMillis();

        tc.setTimeZoneName("GMT");
        assertFalse(tc.isSatisfiedBy(timestamp)); // Sunday 16:00

        tc.setTimeZoneName("GMT-4:00");
        assertFalse(tc.isSatisfiedBy(timestamp)); // Sunday 12:00

        tc.setTimeZoneName("GMT+4:00");
        assertFalse(tc.isSatisfiedBy(timestamp)); // Sunday 20:00

        tc.setTimeZoneName("GMT+10:00");
        assertTrue(tc.isSatisfiedBy(timestamp));  // Monday 02:00
    }

    @Test
    public void inverseIntervals() throws Exception {

        TimeConstraint tc = new TimeConstraint("23:00", "01:00");

        cal.set(2016, Calendar.FEBRUARY, 1, 23, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 0, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 0, 59);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.FEBRUARY, 1, 22, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        tc.setStartTime("Nov");
        tc.setEndTime("Feb");

        cal.set(2016, Calendar.NOVEMBER, 1, 23, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.JANUARY, 1, 23, 1);
        timestamp = cal.getTimeInMillis();
        assertTrue(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.OCTOBER, 1, 22, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));

        cal.set(2016, Calendar.MARCH, 1, 22, 59);
        timestamp = cal.getTimeInMillis();
        assertFalse(tc.isSatisfiedBy(timestamp));
    }

    @Test
    public void handleErrors() throws Exception {

        // Null / empty arguments
        try {
            new TimeConstraint(null, null);
            throw new Exception("It should fail with null arguments");
        } catch (IllegalArgumentException expected) { }

        try {
            new TimeConstraint("", "");
            throw new Exception("It should fail with empty arguments");
        } catch (IllegalArgumentException expected) { }

        try {
            TimeConstraint tc = new TimeConstraint();
            tc.setStartTime(null);
            throw new Exception("It should fail with starTime null arguments");
        } catch (IllegalArgumentException expected) { }

        try {
            TimeConstraint tc = new TimeConstraint();
            tc.setStartTime("");
            throw new Exception("It should fail with starTime empty arguments");
        } catch (IllegalArgumentException expected) { }

        try {
            TimeConstraint tc = new TimeConstraint();
            tc.setEndTime(null);
            throw new Exception("It should fail with starTime null arguments");
        } catch (IllegalArgumentException expected) { }

        try {
            TimeConstraint tc = new TimeConstraint();
            tc.setEndTime("");
            throw new Exception("It should fail with starTime empty arguments");
        } catch (IllegalArgumentException expected) { }

        // Bad formats
        try {
            new TimeConstraint("badformat", "badformat");
            throw new Exception("It should fail with bad formats");
        } catch (IllegalArgumentException expected) { }

        try {
            new TimeConstraint("badformat", "badformat", false);
            throw new Exception("It should fail with bad formats");
        } catch (IllegalArgumentException expected) { }
    }

}
