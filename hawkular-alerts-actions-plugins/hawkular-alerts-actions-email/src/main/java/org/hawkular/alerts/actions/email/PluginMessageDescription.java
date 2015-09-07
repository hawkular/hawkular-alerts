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
package org.hawkular.alerts.actions.email;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.ExternalConditionEval;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.StringConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Helper class to store Alert data and related descriptions and average data to be used into email freemarker
 * templates.
 *
 * This class unwraps PluginMessage fields to be located at root level for freemarker template.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class PluginMessageDescription {

    /** Shortcut for PluginMessage.getAction().message */
    private String message;

    /** Shortcut for PluginMessage.getAction().alert */
    private Alert alert;

    /** Shortcut for PluginMessage.getAction().alert.getStatus().name().toLowercase() */
    private String status;

    /** Shortcut for PluginMessage.getAction().alert.trigger */
    private Trigger trigger;

    /**
     * Long description for Trigger.
     * Description is based on Trigger.getContext() properties:
     * - "resourceType": Type of the resource which this trigger is linked
     * i.e. "resourceType": "App Server"
     *
     * - "resourceName": Name of the resource
     * i.e. "resourceName": "Localhost"
     *
     * if Trigger.getContext is not present Trigger.getName() is used instead.
     *
     * */
    private String triggerDescription;

    /** Shortcut for PluginMessage.getAction().alert.dampening */
    private Dampening dampening;

    /** Long description for Dampening */
    private String dampeningDescription;

    /** Shortcut for PluginMessage.getProperties() */
    private Map<String, String> props;

    /** Email subject based on PluginMEssage.getAction().alert content */
    private String emailSubject;

    /** Number of firing conditions for PluginMessage.getAction().alert */
    private int numConditions;

    /** Base URL to be used on templates to build links into main UI */
    private String baseUrl;

    /**
     * Helper inner class to store a Condition with a calculated description.
     * It stores a list of data of Firing ConditionEval for Threshold and ThresholdRange conditions types.
     * It calculates the average of Firing ConditionEval data for Threshold and ThresholdRange condition types.
     */
    public static class ConditionDescription {

        /** The Condition being stored */
        protected Condition condition;

        /** Description based on the Condition being stored */
        protected String description;

        /** List of numeric data if Condition is ThresholdCondition or ThresholdRangeCondition types, 0 otherwise */
        protected List<Double> data = new ArrayList<>();

        /** Average of numeric data. Only used for ThresholdCondition or ThresholdRangeCondition types, 0 otherwise */
        protected Double average;

        /** Average formatted with units */
        protected String averageDescription;

        public Condition getCondition() {
            return condition;
        }

        public void setCondition(Condition condition) {
            this.condition = condition;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<Double> getData() {
            return data;
        }

        public void setData(List<Double> data) {
            this.data = data;
        }

        public Double getAverage() {
            return average;
        }

        public void setAverage(Double average) {
            this.average = average;
        }

        public String getAverageDescription() {
            return averageDescription;
        }

        public void setAverageDescription(String averageDescription) {
            this.averageDescription = averageDescription;
        }
    }

    /** A list of conditions stored with ConditionDescription wrapper class */
    public ConditionDescription[] conditions;

    /**
     * Unwraps a PluginMessage object as a helper for freemaker templates.
     * It creates description and calculated data for all conditions attached into ConditionEvals inside an alert.
     * It uses Trigger data to generate an email subject description.
     * It support Trigger.context properties:
     * - "resourceType": Description of the type or resource where this trigger is defined
     * i.e. "resourceType": "App Server"
     *
     * - "resourceName": Name of the resource where this trigger is defined
     * i.e. "resourceName": "Localhost"
     *
     * If not Trigger.context properties found, Trigger.name will be used in the email subject description.
     *
     * @param pm the PluginMessage
     */
    public PluginMessageDescription(PluginMessage pm) {
        if (pm == null) {
            throw new IllegalArgumentException("PluginMessage cannot be null");
        }
        if (pm.getAction() == null) {
            throw new IllegalArgumentException("Action cannot be null on PluginMessage");
        }
        if (pm.getProperties() == null) {
            throw new IllegalArgumentException("Properties cannot be null on PluginMessage");
        }
        message = pm.getAction().getMessage();
        alert = pm.getAction().getAlert();
        props = pm.getProperties();
        if (alert != null && alert.getStatus() != null) {
            status = alert.getStatus().name().toLowerCase();
            emailSubject = "Alert [" + status + "] message";
        } else {
            emailSubject = "Alert message";
        }
        if (alert != null && alert.getTrigger() != null) {
            trigger = alert.getTrigger();
            if (trigger.getContext() != null &&
                    !trigger.getContext().isEmpty() &&
                    trigger.getContext().containsKey("resourceType") &&
                    trigger.getContext().containsKey("resourceName")) {
                triggerDescription = trigger.getContext().get("resourceType") + " " +
                        trigger.getContext().get("resourceName");
            } else {
                triggerDescription = trigger.getName();
            }
        }
        if (alert != null && alert.getDampening() != null) {
            dampening = alert.getDampening();
            dampeningDescription = dampeningDescription(dampening);
        }
        initConditions(alert);

        if (numConditions == 1) {
            emailSubject += ": " + conditions[0].description + " ";
            if (triggerDescription != null) {
                emailSubject += "for " + triggerDescription;
            }
        } else {
            if (triggerDescription != null) {
                emailSubject += "for " + triggerDescription;
            }
        }

        baseUrl = props != null ? props.get(EmailPlugin.PROP_TEMPLATE_HAWKULAR_URL) : null;
        baseUrl = baseUrl == null ? System.getenv(EmailPlugin.HAWKULAR_BASE_URL) : baseUrl;
    }

    private String dampeningDescription(Dampening d) {
        if (d == null) return null;
        String description = "Alert triggered ";
        switch (d.getType()) {
            case STRICT:
                description += "after " + d.getEvalTrueSetting() + " consecutive evaluations";
                break;
            case RELAXED_COUNT:
                description += "after " + d.getEvalTrueSetting() + " of " + d.getEvalTotalSetting() + " evaluations";
                break;
            case RELAXED_TIME:
                description += "after" + d.getEvalTrueSetting() + " evaluations in " + (d.getEvalTimeSetting()/1000) +
                        " s";
                break;
            case STRICT_TIME:
            case STRICT_TIMEOUT:
                description += "after " + (d.getEvalTimeSetting()/1000) + " s";
                break;
        }
        return description;
    }

    private void initConditions(Alert alert) {
        numConditions = 0;
        if (alert == null) return;
        if (alert.getEvalSets() == null || alert.getEvalSets().isEmpty()) return;

        Map<String, ConditionDescription> mapConditions = new HashMap<>();

        int listEvals = alert.getEvalSets().size();
        for (int i = 0; i < listEvals; i++) {
            Set<ConditionEval> iEvalSet = alert.getEvalSets().get(i);
            for (ConditionEval condEval : iEvalSet) {
                Condition condition = extractCondition(condEval);
                if (!mapConditions.containsKey(condition.getConditionId())) {
                    ConditionDescription condDesc = new ConditionDescription();
                    condDesc.condition = condition;
                    condDesc.description = description(condition);
                    mapConditions.put(condition.getConditionId(), condDesc);
                }
                ConditionDescription condDesc = mapConditions.get(condition.getConditionId());
                condDesc.data.add(extractValue(condEval));
            }
        }

        Collection<ConditionDescription> values = mapConditions.values();
        conditions = values.toArray(new ConditionDescription[values.size()]);
        numConditions = conditions.length;

        DecimalFormat decimalFormat = new DecimalFormat("####0.00");
        for (int i = 0; i < numConditions; i++) {
            ConditionDescription condDesc = conditions[i];
            condDesc.average = ( condDesc.data.stream().reduce(0.0, (j,k) -> j+k ) ) / condDesc.data.size();
            if (condDesc.condition.getContext().containsKey("unit")) {
                condDesc.averageDescription = decimalFormat.format(condDesc.average) + " " +
                        condDesc.condition.getContext().get("unit");
            }
        }
    }

    private Condition extractCondition(ConditionEval conditionEval) {
        if (conditionEval == null) return null;
        if (conditionEval instanceof AvailabilityConditionEval) {
            return ((AvailabilityConditionEval) conditionEval).getCondition();
        } else if (conditionEval instanceof CompareConditionEval) {
            return ((CompareConditionEval) conditionEval).getCondition();
        } else if (conditionEval instanceof ExternalConditionEval) {
            return ((ExternalConditionEval) conditionEval).getCondition();
        } else if (conditionEval instanceof StringConditionEval) {
            return ((StringConditionEval) conditionEval).getCondition();
        } else if (conditionEval instanceof ThresholdConditionEval) {
            return ((ThresholdConditionEval) conditionEval).getCondition();
        } else if (conditionEval instanceof ThresholdRangeConditionEval) {
            return ((ThresholdRangeConditionEval) conditionEval).getCondition();
        } else {
            return null;
        }
    }

    private Double extractValue(ConditionEval conditionEval) {
        if (conditionEval == null) return 0d;
        if (conditionEval instanceof ThresholdConditionEval) {
            return ((ThresholdConditionEval) conditionEval).getValue();
        } else if (conditionEval instanceof ThresholdRangeConditionEval) {
            return ((ThresholdRangeConditionEval) conditionEval).getValue();
        } else {
            return 0d;
        }
    }

    private String description(Condition condition) {
        if (condition == null) return null;
        if (condition instanceof AvailabilityCondition) {
            return availability((AvailabilityCondition) condition);
        } else if (condition instanceof CompareCondition) {
            return compare((CompareCondition) condition);
        } else if (condition instanceof ExternalCondition) {
            return external((ExternalCondition) condition);
        } else if (condition instanceof StringCondition) {
            return string((StringCondition) condition);
        } else if (condition instanceof ThresholdCondition) {
            return threshold((ThresholdCondition) condition);
        } else if (condition instanceof ThresholdRangeCondition) {
            return range((ThresholdRangeCondition) condition);
        } else {
            return null;
        }
    }

    /**
     * Create a description for an AvailabilityCondition object.
     * It supports Condition.context properties:
     * - "description": Description of the dataId used for this condition, if not present, description will use
     * dataId literal
     * i.e. "description": "Response Time"
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String availability(AvailabilityCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get("description") != null) {
            description = condition.getContext().get("description");
        } else {
            description = condition.getDataId();
        }
        AvailabilityCondition.Operator operator = condition.getOperator();
        switch (operator) {
            case DOWN:
                description += " is down";
                break;
            case NOT_UP:
                description += " is not up";
                break;
            case UP:
                description += " is up";
                break;
        }
        return description;
    }

    /**
     * Create a description for an CompareCondition object.
     * It supports Condition.context properties:
     * - "description": Description of the dataId used for this condition, if not present, description will use
     * dataId literal
     * i.e. "description": "Response Time"
     *
     * - "description2": Description of the data2Id used for this comparition, if not present, description will use
     * data2Id literal
     * i.e. "description2": "Response Time 2"
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String compare(CompareCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get("description") != null) {
            description = condition.getContext().get("description");
        } else {
            description = condition.getDataId();
        }
        CompareCondition.Operator operator = condition.getOperator();
        switch (operator) {
            case LT:
                description += " less than ";
                break;
            case LTE:
                description += " less or equals than ";
                break;
            case GT:
                description += " greater than ";
                break;
            case GTE:
                description += " greater or equals than ";
                break;
        }
        if (condition.getData2Multiplier() != 1.0) {
            description += "( " + condition.getData2Multiplier() + " ";
        }
        if (condition.getContext() != null && condition.getContext().get("description2") != null) {
            description += condition.getContext().get("description2");
        } else {
            description += condition.getData2Id();
        }
        if (condition.getData2Multiplier() != 1.0) {
            description += " )";
        }
        return description;
    }

    /**
     * Create a description for an ExternalCondition object.
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String external(ExternalCondition condition) {
        String description = "SystemId: " + condition.getSystemId();
        description += " DataId: " + condition.getDataId();
        description += " Expression: " + condition.getExpression();
        return description;
    }

    /**
     * Create a description for an StringCondition object.
     * It supports Condition.context properties:
     * - "description": Description of the dataId used for this condition, if not present, description will use
     * dataId literal
     * i.e. "description": "Response Time"
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String string(StringCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get("description") != null) {
            description = condition.getContext().get("description");
        } else {
            description = condition.getDataId();
        }
        StringCondition.Operator operator = condition.getOperator();
        switch (operator) {
            case STARTS_WITH:
                description += "starts with ";
                break;
            case CONTAINS:
                description += "contains ";
                break;
            case ENDS_WITH:
                description += "ends with ";
                break;
            case EQUAL:
                description += "is equal to ";
                break;
            case NOT_EQUAL:
                description += "is not equal to ";
                break;
            case MATCH:
                description += "matches to ";
                break;
        }
        description += condition.getPattern();
        if (condition.isIgnoreCase()) {
            description += " (ignore case)";
        }
        return description;
    }

    /**
     * Create a description for an ThresholdCondition object.
     * It supports Condition.context properties:
     * - "description": Description of the dataId used for this condition, if not present, description will use
     * dataId literal
     * i.e. "description": "Response Time"
     *
     * - "unit": Description of the unit used for the threshold, if not present, description will be "threshold"
     * i.e. "unit": "milliseconds"
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String threshold(ThresholdCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get("description") != null) {
            description = condition.getContext().get("description");
        } else {
            description = condition.getDataId();
        }
        switch (condition.getOperator()) {
            case GT:
                description += " greater than ";
                break;
            case GTE:
                description += " greater or equal than ";
                break;
            case LT:
                description += " less than ";
                break;
            case LTE:
                description += " less or equal than ";
                break;
        }
        description += condition.getThreshold();
        if (condition.getContext() != null && condition.getContext().get("unit") != null) {
            description += " " + condition.getContext().get("unit");
        } else {
            description += " (threshold)";
        }
        return description;
    }

    /**
     * Create a description for an ThresholdRangeCondition object.
     * It supports Condition.context properties:
     * - "description": Description of the dataId used for this condition, if not present, description will use
     * dataId literal
     * i.e. "description": "Response Time"
     *
     * - "unit": Description of the unit used for the range, if not present, description will be "range"
     * i.e. "unit": "milliseconds"
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String range(ThresholdRangeCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get("description") != null) {
            description = condition.getContext().get("description");
        } else {
            description = condition.getDataId();
        }
        if (condition.isInRange()) {
            description += " in ";
        } else {
            description += " out of ";
        }
        ThresholdRangeCondition.Operator operatorLow = condition.getOperatorLow();
        ThresholdRangeCondition.Operator operatorHigh = condition.getOperatorHigh();
        if (operatorLow.equals(ThresholdRangeCondition.Operator.INCLUSIVE)) {
            description += "[";
        } else {
            description += "(";
        }
        description += condition.getThresholdLow();
        description += ", ";
        description += condition.getThresholdHigh();
        if (operatorHigh.equals(ThresholdRangeCondition.Operator.INCLUSIVE)) {
            description += "]";
        } else {
            description += ")";
        }
        if (condition.getContext() != null && condition.getContext().get("unit") != null) {
            description += " " + condition.getContext().get("unit");
        } else {
            description += " (range)";
        }
        return description;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Alert getAlert() {
        return alert;
    }

    public void setAlert(Alert alert) {
        this.alert = alert;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public Dampening getDampening() {
        return dampening;
    }

    public void setDampening(Dampening dampening) {
        this.dampening = dampening;
    }

    public String getDampeningDescription() {
        return dampeningDescription;
    }

    public void setDampeningDescription(String dampeningDescription) {
        this.dampeningDescription = dampeningDescription;
    }

    public Map<String, String> getProps() {
        return props;
    }

    public void setProps(Map<String, String> props) {
        this.props = props;
    }

    public int getNumConditions() {
        return numConditions;
    }

    public void setNumConditions(int numConditions) {
        this.numConditions = numConditions;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public ConditionDescription[] getConditions() {
        return conditions;
    }

    public void setConditions(
            ConditionDescription[] conditions) {
        this.conditions = conditions;
    }

    public String getTriggerDescription() {
        return triggerDescription;
    }

    public void setTriggerDescription(String triggerDescription) {
        this.triggerDescription = triggerDescription;
    }
}
