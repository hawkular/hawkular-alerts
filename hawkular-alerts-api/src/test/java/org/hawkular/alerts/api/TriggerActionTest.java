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
package org.hawkular.alerts.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.hawkular.alerts.api.model.trigger.TriggerAction.CalendarRelative;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class TriggerActionTest {

    @Test
    public void relativeCalendarTest() throws Exception {
        TriggerAction ta = new TriggerAction("tenant", "plugin", "action", "RM0.D0.23:59;M1.D1.22:49");

        assertTrue(ta.isRelativeCalendar());
        assertEquals("RM0.D0.23:59", ta.getStartCalendar());
        assertEquals("M1.D1.22:49", ta.getEndCalendar());

        assertEquals(0, ta.getStartCalendarRelative(CalendarRelative.MONTH));
        assertEquals(0, ta.getStartCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(23, ta.getStartCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(59, ta.getStartCalendarRelative(CalendarRelative.MINUTE));

        assertEquals(1, ta.getEndCalendarRelative(CalendarRelative.MONTH));
        assertEquals(1, ta.getEndCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(22, ta.getEndCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(49, ta.getEndCalendarRelative(CalendarRelative.MINUTE));

        ta.setCalendar("RD0.23:59;D1.22:49");

        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.MONTH));
        assertEquals(0, ta.getStartCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(23, ta.getStartCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(59, ta.getStartCalendarRelative(CalendarRelative.MINUTE));

        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.MONTH));
        assertEquals(1, ta.getEndCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(22, ta.getEndCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(49, ta.getEndCalendarRelative(CalendarRelative.MINUTE));

        ta.setCalendar("R23:59;22:49");

        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.MONTH));
        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(23, ta.getStartCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(59, ta.getStartCalendarRelative(CalendarRelative.MINUTE));

        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.MONTH));
        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(22, ta.getEndCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(49, ta.getEndCalendarRelative(CalendarRelative.MINUTE));

        boolean failed = false;
        try {
            ta = new TriggerAction("tenant", "plugin", "action", "RM0.D0.23:59");
        } catch (Exception expected) {
            failed = true;
        }
        assertTrue("Malformed calendar expression exception not thrown", failed);

        failed = false;
        try {
            ta.setCalendar("RM0.D0.23:59");
        } catch (Exception expected) {
            failed = true;
        }
        assertTrue("Malformed calendar expression exception not thrown", failed);
    }

    @Test
    public void absoluteCalendarTest() throws Exception {
        TriggerAction ta = new TriggerAction("tenant", "plugin", "action",
                "2016-01-01.23:59;2016-01-02.23:59");

        assertFalse(ta.isRelativeCalendar());

        Calendar cal = Calendar.getInstance();

        cal.setTime(ta.getStartCalendarDate());

        assertEquals(2016, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, cal.get(Calendar.MINUTE));

        cal.setTime(ta.getEndCalendarDate());

        assertEquals(2016, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(2, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, cal.get(Calendar.MINUTE));

        boolean failed = false;

        try {
            ta = new TriggerAction("tenant", "plugin", "action", "bad;bad");
            ta.getStartCalendarDate();
        } catch (Exception expected) {
            failed = true;
        }
        assertTrue("Malformed calendar expression exception not thrown", failed);

    }

    @Test
    public void noCalendar() throws Exception {
        TriggerAction ta = new TriggerAction("tenant", "plugin", "action");

        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.MONTH));
        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(-1, ta.getStartCalendarRelative(CalendarRelative.MINUTE));

        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.MONTH));
        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK));
        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY));
        assertEquals(-1, ta.getEndCalendarRelative(CalendarRelative.MINUTE));

        assertNull(ta.getStartCalendarDate());
        assertNull(ta.getEndCalendarDate());

        assertEquals(0, ta.getStates().size());

    }

}
