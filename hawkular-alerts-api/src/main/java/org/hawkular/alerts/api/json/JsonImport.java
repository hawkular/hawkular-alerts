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
package org.hawkular.alerts.api.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class to read json representations and convert them on model classes.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class JsonImport {

    private static ObjectMapper om = new ObjectMapper();

    /**
     * Read a full trigger json representation on a string in the form:
     * {
     *  "trigger":{...},
     *  "dampenings":[...],
     *  "conditions":[...]
     *  }
     *
     * @param strFullTrigger a json string representing a a full trigger
     * @return a FullTrigger object with the parsed json
     * @throws Exception on any issue
     */
    public static FullTrigger readFullTrigger(String strFullTrigger) throws Exception {
        if (strFullTrigger == null || strFullTrigger.isEmpty()) {
            throw new IllegalArgumentException("strFullTrigger must be not null");
        }
        Map<String, Object> rawTrigger = om.readValue(strFullTrigger, Map.class);
        return readFullTrigger(rawTrigger);
    }

    /**
     * Read a full trigger json representation on a map in the form:
     * {
     *  "trigger":{...},
     *  "dampenings":[...],
     *  "conditions":[...]
     *  }
     *
     * @param mapFullTrigger a json map representing a a full trigger
     * @return a FullTrigger object with the parsed json
     * @throws Exception on any issue
     */
    public static FullTrigger readFullTrigger(Map<String, Object> mapFullTrigger) throws Exception {
        if (mapFullTrigger == null || mapFullTrigger.isEmpty()) {
            throw new IllegalArgumentException("strFullTrigger must be not null");
        }
        String strTrigger = om.writeValueAsString(mapFullTrigger.get("trigger"));

        Trigger trigger = om.readValue(strTrigger, Trigger.class);
        List<Dampening> dampenings = new ArrayList<>();
        List<Map<String, Object>> rawDampenings = (List<Map<String, Object>>) mapFullTrigger.get("dampenings");
        for (Map<String, Object> rawDampening : rawDampenings) {
            String strDampening = om.writeValueAsString(rawDampening);
            Dampening dampening = om.readValue(strDampening, Dampening.class);
            dampening.setTenantId(trigger.getTenantId());
            dampening.setTriggerId(trigger.getId());
            dampenings.add(dampening);
        }
        List<Condition> conditions = new ArrayList<>();
        List<Map<String, Object>> rawConditions = (List<Map<String, Object>>) mapFullTrigger.get("conditions");
        for (Map<String, Object> rawCondition : rawConditions) {
            String strCondition = om.writeValueAsString(rawCondition);
            Condition condition = JacksonDeserializer.deserializeCondition(om.readTree(strCondition));
            condition.setTenantId(trigger.getTenantId());
            condition.setTriggerId(trigger.getId());
            conditions.add(condition);
        }
        return new FullTrigger(trigger, dampenings, conditions);
    }

    /**
     * Read a full action definition json representation on a string in the form:
     * {
     *     "tenantId":"...",
     *     "actionPlugin":"...",
     *     "actionId":"...",
     *     "properties":{...}
     * }
     * @param strFullAction a json string representing a a full action definition
     * @return a FullAction object with the parsed jon
     * @throws Exception on any issue
     */
    public static FullAction readFullAction(String strFullAction) throws Exception {
        if (strFullAction == null || strFullAction.isEmpty()) {
            throw new IllegalArgumentException("strFullAction must be not null");
        }
        Map<String, Object> rawAction = om.readValue(strFullAction, Map.class);
        return readFullAction(rawAction);
    }

    public static FullAction readFullAction(Map<String, Object> mapFullAction) throws Exception {
        if (mapFullAction == null || mapFullAction.isEmpty()) {
            throw new IllegalArgumentException("mapFullAction must be not null");
        }
        return new FullAction((String)mapFullAction.get("tenantId"),
                (String)mapFullAction.get("actionPlugin"),
                (String)mapFullAction.get("actionId"),
                (Map<String, String>)mapFullAction.get("properties"));
    }

    /**
     * Representation of a Trigger plus its Dampening and Condition objects.
     * This class is mainly used for json import scenarios based.
     */
    public static class FullTrigger {
        private Trigger trigger;
        private List<Dampening> dampenings;
        private List<Condition> conditions;

        public FullTrigger(Trigger trigger, List<Dampening> dampenings, List<Condition> conditions) {
            this.trigger = trigger;
            this.dampenings = dampenings;
            this.conditions = conditions;
        }

        public Trigger getTrigger() {
            return trigger;
        }

        public List<Dampening> getDampenings() {
            return dampenings;
        }

        public List<Condition> getConditions() {
            return conditions;
        }

        @Override
        public String toString() {
            return "FullTrigger" + '[' +
                    "trigger=" + trigger +
                    ", dampenings=" + dampenings +
                    ", conditions=" + conditions +
                    ']';
        }
    }

    /**
     * Representation of an Action definition.
     * This class is mainly used for json import scenarios.
     */
    public static class FullAction {
        private String tenantId;
        private String actionPlugin;
        private String actionId;
        private Map<String, String> properties;

        public FullAction(String tenantId, String actionPlugin, String actionId, Map<String, String> properties) {
            this.tenantId = tenantId;
            this.actionPlugin = actionPlugin;
            this.actionId = actionId;
            this.properties = properties;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getActionPlugin() {
            return actionPlugin;
        }

        public String getActionId() {
            return actionId;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            return "FullAction" + '[' +
                    "tenantId='" + tenantId + '\'' +
                    ", actionPlugin='" + actionPlugin + '\'' +
                    ", actionId='" + actionId + '\'' +
                    ", properties=" + properties +
                    ']';
        }
    }
}
