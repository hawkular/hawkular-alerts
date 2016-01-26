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
package org.hawkular.alerts.engine.util;

import static org.hawkular.alerts.api.model.trigger.TriggerAction.*;

import java.util.Calendar;
import java.util.Date;

import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.jboss.logging.Logger;

/**
 * A Trigger can define a list of Actions that will be executed on Event/Alert generation.
 * A Trigger can optionally define constraints based on Alert's state and/or time intervals that indicates when an
 * action should be generated.
 *
 * This class is a helper to validate if an Event/Alert should be sent to the ActionsService based on the constraints
 * defined of a specific TriggerAction object.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ActionsValidator {
    private static final MsgLogger msgLog = MsgLogger.LOGGER;
    private static final Logger log = Logger.getLogger(ActionsValidator.class);
    /**
     * Validate if an Event should generate an Action based on the constraints defined on a TriggerAction.
     *
     * @param triggerAction a TriggerAction where status and time constraints are defined.
     * @param event a given Event to validate against a TriggerAction
     * @return true if the Event is validated and it should generated an action
     *         false on the contrary
     */
    public static boolean validate(TriggerAction triggerAction, Event event) {
        if (triggerAction == null || event == null) {
            return true;
        }
        if ((triggerAction.getStates() == null || triggerAction.getStates().isEmpty())
                && triggerAction.getCalendar() == null) {
            return true;
        }
        if (event instanceof Alert
                && triggerAction.getStates() != null
                && !triggerAction.getStates().isEmpty()
                && !triggerAction.getStates().contains( ((Alert)event).getStatus().name()) ) {
            return false;
        }
        if (triggerAction.getCalendar() != null) {
            Calendar eventCal = Calendar.getInstance();
            eventCal.setTimeInMillis(event.getCtime());
            try {
                if (triggerAction.isRelativeCalendar()) {
                    // Validate MONTH
                    if (triggerAction.getStartCalendarRelative(CalendarRelative.MONTH) > -1
                            && triggerAction.getEndCalendarRelative(CalendarRelative.MONTH) > -1) {
                        int startMonth = triggerAction.getStartCalendarRelative(CalendarRelative.MONTH);
                        int endMonth = triggerAction.getEndCalendarRelative(CalendarRelative.MONTH);
                        int eventMonth = eventCal.get(Calendar.MONTH);
                        if (startMonth <= endMonth) {
                            if (eventMonth < startMonth || eventMonth > endMonth) {
                                return false;
                            }
                        } else {
                            if (!(eventMonth >= startMonth || eventMonth <= endMonth)) {
                                return false;
                            }
                        }
                    }
                    // Validate DAY
                    if (triggerAction.getStartCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK) > -1
                            && triggerAction.getEndCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK) > -1) {
                        int startDay = triggerAction.getStartCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK);
                        int endDay = triggerAction.getEndCalendarRelative(CalendarRelative.DAY_OF_THE_WEEK);
                        int eventDay = eventCal.get(Calendar.DAY_OF_WEEK);
                        if (startDay <= endDay) {
                            if (eventDay < startDay || eventDay > endDay) {
                                return false;
                            }
                        } else {
                            if (!(eventDay >= startDay || eventDay <= endDay)) {
                                return false;
                            }
                        }
                    }
                    // Validate HOUR
                    if (triggerAction.getStartCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY) > -1
                            && triggerAction.getEndCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY) > -1) {
                        int startDay = triggerAction.getStartCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY);
                        int endDay = triggerAction.getEndCalendarRelative(CalendarRelative.HOUR_OF_THE_DAY);
                        int eventDay = eventCal.get(Calendar.HOUR_OF_DAY);
                        if (eventDay < startDay || eventDay > endDay) {
                            return false;
                        }
                    }
                    // Validate MINUTE
                    if (triggerAction.getStartCalendarRelative(CalendarRelative.MINUTE) > -1
                            && triggerAction.getEndCalendarRelative(CalendarRelative.MINUTE) > -1) {
                        int startDay = triggerAction.getStartCalendarRelative(CalendarRelative.MINUTE);
                        int endDay = triggerAction.getEndCalendarRelative(CalendarRelative.MINUTE);
                        int eventDay = eventCal.get(Calendar.MINUTE);
                        if (eventDay < startDay || eventDay > endDay) {
                            return false;
                        }
                    }
                } else {
                    Date eventDate = eventCal.getTime();

                        Date startInterval = triggerAction.getStartCalendarDate();
                        Date endInterval = triggerAction.getEndCalendarDate();
                        if (eventDate != null
                                && startInterval != null
                                && endInterval != null
                                && (eventDate.before(startInterval) || eventDate.after(endInterval)))  {
                            return false;
                        }
                }
            } catch (Exception e) {
                log.debug(e.getMessage(), e);
                msgLog.errorCannotValidateAction(e.getMessage());
            }
        }
        return true;
    }
}
