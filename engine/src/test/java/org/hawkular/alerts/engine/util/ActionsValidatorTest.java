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
package org.hawkular.alerts.engine.util;

import static org.hawkular.alerts.api.model.event.Alert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.alerts.api.model.action.TimeConstraint;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionsValidatorTest {

    @Test
    public void noConstraintsTest() {
        TriggerAction ta = new TriggerAction("tenant", "plugin", "action");
        Event event = new Event();

        assertTrue(ActionsValidator.validate(ta, event));
    }

    @Test
    public void statusTest() {
        TriggerAction ta = new TriggerAction("tenant", "plugin", "action",
                Collections.singleton(Status.RESOLVED.name()));
        Event event = new Event();
        assertTrue(ActionsValidator.validate(ta, event));

        Alert alert = new Alert();
        assertFalse(ActionsValidator.validate(ta, alert));

        alert.setStatus(Status.ACKNOWLEDGED);
        assertFalse(ActionsValidator.validate(ta, alert));

        alert.setStatus(Status.RESOLVED);
        assertTrue(ActionsValidator.validate(ta, alert));

        Set<String> statuses = new HashSet<>();
        statuses.add(Status.OPEN.name());
        statuses.add(Status.ACKNOWLEDGED.name());
        ta = new TriggerAction("tenant", "plugin", "action", statuses);
        alert = new Alert();

        assertTrue(ActionsValidator.validate(ta, alert));

        alert.setStatus(Status.ACKNOWLEDGED);
        assertTrue(ActionsValidator.validate(ta, alert));

        alert.setStatus(Status.RESOLVED);
        assertFalse(ActionsValidator.validate(ta, alert));
    }

    @Test
    public void calendarAbsoluteTest() {
        TriggerAction ta = new TriggerAction("tenant", "plugin", "action",
                new TimeConstraint("2016.01.26,20:30","2016.01.26,20:45", false));

        Calendar eventCal = Calendar.getInstance();
        eventCal.set(2016, Calendar.JANUARY, 26, 20, 31);

        Alert alert = new Alert();
        alert.setCtime(eventCal.getTimeInMillis());

        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 26, 20, 46);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 26, 20, 29);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));

        eventCal.set(2015, Calendar.JANUARY, 26, 20, 29);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));
    }

    @Test
    public void calendarRelativeTest() {

        TriggerAction ta = new TriggerAction("tenant", "plugin", "action", new TimeConstraint("20:30", "20:45"));

        Calendar eventCal = Calendar.getInstance();
        eventCal.set(2016, Calendar.JANUARY, 26, 20, 31);

        Alert alert = new Alert();
        alert.setCtime(eventCal.getTimeInMillis());

        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 23, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2015, Calendar.FEBRUARY, 23, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2015, Calendar.FEBRUARY, 23, 19, 25);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));

        eventCal.set(2015, Calendar.MARCH, 23, 19, 25);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));

        ta = new TriggerAction("tenant", "plugin", "action", new TimeConstraint("Thursday,20:30", "Sunday,20:45"));

        eventCal.set(2016, Calendar.JANUARY, 28, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 29, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 30, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 18, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));

        eventCal.set(2015, Calendar.JANUARY, 19, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 31, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 7, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.JANUARY, 8, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.FEBRUARY, 5, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertTrue(ActionsValidator.validate(ta, alert));

        eventCal.set(2016, Calendar.FEBRUARY, 15, 20, 31);
        alert.setCtime(eventCal.getTimeInMillis());
        assertFalse(ActionsValidator.validate(ta, alert));
    }

}
