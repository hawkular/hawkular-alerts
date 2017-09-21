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

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hawkular.alerts.api.model.Severity;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.event.Alert.Status;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerAction;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.EventsCriteria;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Utils {

    public static void checkTenantId(String tenantId, Object obj) {
        if (isEmpty(tenantId)) {
            return;
        }
        if (obj == null) {
            return;
        }
        if (obj instanceof Trigger) {
            Trigger trigger = (Trigger) obj;
            if (trigger.getTenantId() == null || !trigger.getTenantId().equals(tenantId)) {
                trigger.setTenantId(tenantId);
                if (trigger.getActions() != null) {
                    for (TriggerAction triggerAction : trigger.getActions()) {
                        triggerAction.setTenantId(tenantId);
                    }
                }
            }
        } else if (obj instanceof Dampening) {
            Dampening dampening = (Dampening) obj;
            if (dampening.getTenantId() == null || !dampening.getTenantId().equals(tenantId)) {
                dampening.setTenantId(tenantId);
            }
        }
    }

    public static Set<String> extractTriggerIds(AlertsCriteria criteria) {

        boolean hasTriggerId = !isEmpty(criteria.getTriggerId());
        boolean hasTriggerIds = !isEmpty(criteria.getTriggerIds());

        Set<String> triggerIds = hasTriggerId || hasTriggerIds ? new HashSet<>() : Collections.emptySet();

        if (!hasTriggerIds) {
            if (hasTriggerId) {
                triggerIds.add(criteria.getTriggerId());
            }
        } else {
            for (String triggerId : criteria.getTriggerIds()) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                triggerIds.add(triggerId);
            }
        }

        return triggerIds;
    }

    public static Set<String> extractTriggerIds(EventsCriteria criteria) {

        boolean hasTriggerId = !isEmpty(criteria.getTriggerId());
        boolean hasTriggerIds = !isEmpty(criteria.getTriggerIds());

        Set<String> triggerIds = hasTriggerId || hasTriggerIds ? new HashSet<>() : Collections.emptySet();

        if (!hasTriggerIds) {
            if (hasTriggerId) {
                triggerIds.add(criteria.getTriggerId());
            }
        } else {
            for (String triggerId : criteria.getTriggerIds()) {
                if (isEmpty(triggerId)) {
                    continue;
                }
                triggerIds.add(triggerId);
            }
        }

        return triggerIds;
    }

    public static Set<Severity> extractSeverity(AlertsCriteria criteria) {
        Set<Severity> severities = new HashSet<>();
        if (criteria.getSeverity() != null) {
            severities.add(criteria.getSeverity());
        }
        if (!isEmpty(criteria.getSeverities())) {
            severities.addAll(criteria.getSeverities());
        }
        return severities;
    }

    public static Set<Status> extractStatus(AlertsCriteria criteria) {
        Set<Status> statuses = new HashSet<>();
        if (criteria.getStatus() != null) {
            statuses.add(criteria.getStatus());
        }
        if (!isEmpty(criteria.getStatusSet())) {
            statuses.addAll(criteria.getStatusSet());
        }
        return statuses;
    }

    public static Set<String> extractCategories(EventsCriteria criteria) {
        Set<String> categories = new HashSet<>();
        if (!isEmpty(criteria.getCategory())) {
            categories.add(criteria.getCategory());
        }
        if (!isEmpty(criteria.getCategories())) {
            categories.addAll(criteria.getCategories());
        }
        return categories;
    }

    public static Set<String> extractEventIds(EventsCriteria criteria) {
        Set<String> eventIds = new HashSet<>();
        if (!isEmpty(criteria.getEventId())) {
            eventIds.add(criteria.getEventId());
        }
        if (!isEmpty(criteria.getEventIds())) {
            eventIds.addAll(criteria.getEventIds());
        }
        return eventIds;
    }

    public static Set<String> extractAlertIds(AlertsCriteria criteria) {
        Set<String> alertIds = new HashSet<>();
        if (!isEmpty(criteria.getAlertId())) {
            alertIds.add(criteria.getAlertId());
        }
        if (!isEmpty(criteria.getAlertIds())) {
            alertIds.addAll(criteria.getAlertIds());
        }
        return alertIds;
    }

}
