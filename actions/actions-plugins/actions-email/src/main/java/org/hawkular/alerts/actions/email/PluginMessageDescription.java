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
package org.hawkular.alerts.actions.email;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.CompareConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.EventCondition;
import org.hawkular.alerts.api.model.condition.EventConditionEval;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.ExternalConditionEval;
import org.hawkular.alerts.api.model.condition.MissingCondition;
import org.hawkular.alerts.api.model.condition.MissingConditionEval;
import org.hawkular.alerts.api.model.condition.NelsonCondition;
import org.hawkular.alerts.api.model.condition.NelsonConditionEval;
import org.hawkular.alerts.api.model.condition.RateCondition;
import org.hawkular.alerts.api.model.condition.RateConditionEval;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.StringConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeConditionEval;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
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

    /** Context property "resourceType". Supported at Trigger.getContext() level */
    public static final String CONTEXT_PROPERTY_RESOURCE_TYPE = "resourceType";

    /** Context property "resourceName". Supported at Trigger.getContext() level */
    public static final String CONTEXT_PROPERTY_RESOURCE_NAME = "resourceName";

    /** Context property "unit". Supported at Condition.getContext() level */
    public static final String CONTEXT_PROPERTY_UNIT = "unit";

    /** Context property "description". Supported at Condition.getContext() level */
    public static final String CONTEXT_PROPERTY_DESCRIPTION = "description";

    /** Context property "description". Supported at Condition.getContext() level with CompareCondition classes */
    public static final String CONTEXT_PROPERTY_DESCRIPTION2 = "description2";

    /** Shortcut for PluginMessage.getAction().event */
    private Event event;

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

    private DecimalFormat decimalFormat = new DecimalFormat("####0.0", new DecimalFormatSymbols(Locale.ENGLISH));

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
    public PluginMessageDescription(ActionMessage pm) {
        if (pm == null) {
            throw new IllegalArgumentException("PluginMessage cannot be null");
        }
        if (pm.getAction() == null) {
            throw new IllegalArgumentException("Action cannot be null on PluginMessage");
        }
        if (pm.getAction().getProperties() == null) {
            throw new IllegalArgumentException("Properties cannot be null on PluginMessage");
        }
        event = pm.getAction().getEvent();
        if (event instanceof Alert) {
            alert = (Alert)event;
        }
        props = pm.getAction().getProperties();
        if (event != null && event instanceof Alert) {
            Alert alert = (Alert) event;
            if (alert.getStatus() != null) {
                status = alert.getStatus().name().toLowerCase();
                emailSubject = "Alert [" + status + "] message";
            } else {
                emailSubject = "Alert message";
            }
        } else {
            emailSubject = "Event message";
        }
        if (event != null && event.getTrigger() != null) {
            trigger = event.getTrigger();
            if (!isEmpty(trigger.getContext()) &&
                    trigger.getContext().containsKey(CONTEXT_PROPERTY_RESOURCE_TYPE) &&
                    trigger.getContext().containsKey(CONTEXT_PROPERTY_RESOURCE_NAME)) {
                triggerDescription = trigger.getContext().get(CONTEXT_PROPERTY_RESOURCE_TYPE) + " " +
                        trigger.getContext().get(CONTEXT_PROPERTY_RESOURCE_NAME);
            } else {
                triggerDescription = trigger.getName();
            }
        }
        if (event != null && event.getDampening() != null) {
            dampening = event.getDampening();
            dampeningDescription = dampeningDescription(dampening);
        }
        initConditions(event);

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
        if (d == null)
            return null;
        String description = "Alert triggered ";
        switch (d.getType()) {
            case STRICT:
                description += "after " + d.getEvalTrueSetting() + " consecutive evaluations";
                break;
            case RELAXED_COUNT:
                description += "after " + d.getEvalTrueSetting() + " of " + d.getEvalTotalSetting() + " evaluations";
                break;
            case RELAXED_TIME:
                description += "after" + d.getEvalTrueSetting() + " evaluations in " + (d.getEvalTimeSetting() / 1000)
                        + " s";
                break;
            case STRICT_TIME:
            case STRICT_TIMEOUT:
                description += "after " + (d.getEvalTimeSetting() / 1000) + " s";
                break;
            default:
                throw new IllegalArgumentException(d.getType().name());
        }
        return description;
    }

    private void initConditions(Event event) {
        numConditions = 0;
        if (event == null)
            return;
        if (event.getEvalSets() == null || event.getEvalSets().isEmpty())
            return;

        Map<String, ConditionDescription> mapConditions = new HashMap<>();

        int listEvals = event.getEvalSets().size();
        for (int i = 0; i < listEvals; i++) {
            Set<ConditionEval> iEvalSet = event.getEvalSets().get(i);
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

        for (int i = 0; i < numConditions; i++) {
            ConditionDescription condDesc = conditions[i];
            condDesc.average = (condDesc.data.stream().reduce(0.0, (j, k) -> j + k)) / condDesc.data.size();
            if (condDesc.condition.getContext().containsKey(CONTEXT_PROPERTY_UNIT)) {
                condDesc.averageDescription = decimalFormat.format(condDesc.average) + " " +
                        condDesc.condition.getContext().get(CONTEXT_PROPERTY_UNIT);
            }
        }
    }

    private Condition extractCondition(ConditionEval conditionEval) {
        if (conditionEval == null)
            return null;
        switch (conditionEval.getType()) {
            case AVAILABILITY:
                return ((AvailabilityConditionEval) conditionEval).getCondition();
            case COMPARE:
                return ((CompareConditionEval) conditionEval).getCondition();
            case EXTERNAL:
                return ((ExternalConditionEval) conditionEval).getCondition();
            case EVENT:
                return ((EventConditionEval) conditionEval).getCondition();
            case MISSING:
                return ((MissingConditionEval) conditionEval).getCondition();
            case NELSON:
                return ((NelsonConditionEval) conditionEval).getCondition();
            case RANGE:
                return ((ThresholdRangeConditionEval) conditionEval).getCondition();
            case RATE:
                return ((RateConditionEval) conditionEval).getCondition();
            case STRING:
                return ((StringConditionEval) conditionEval).getCondition();
            case THRESHOLD:
                return ((ThresholdConditionEval) conditionEval).getCondition();
            default:
                return null;
        }
    }

    private Double extractValue(ConditionEval conditionEval) {
        if (conditionEval == null)
            return 0d;
        switch (conditionEval.getType()) {
            case THRESHOLD:
                return ((ThresholdConditionEval) conditionEval).getValue();
            case RANGE:
                return ((ThresholdRangeConditionEval) conditionEval).getValue();
            default:
                return 0d;
        }
    }

    private String description(Condition condition) {
        if (condition == null)
            return null;
        switch (condition.getType()) {
            case AVAILABILITY:
                return availability((AvailabilityCondition) condition);
            case COMPARE:
                return compare((CompareCondition) condition);
            case EXTERNAL:
                return external((ExternalCondition) condition);
            case EVENT:
                return events((EventCondition) condition);
            case MISSING:
                return missing((MissingCondition) condition);
            case NELSON:
                return nelson((NelsonCondition) condition);
            case RANGE:
                return range((ThresholdRangeCondition) condition);
            case RATE:
                return rate((RateCondition) condition);
            case STRING:
                return string((StringCondition) condition);
            case THRESHOLD:
                return threshold((ThresholdCondition) condition);
            default:
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
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
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
            default:
                throw new IllegalArgumentException(operator.name());
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
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
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
            default:
                throw new IllegalArgumentException(operator.name());
        }
        if (condition.getData2Multiplier() != 1.0) {
            description += "( " + decimalFormat.format(condition.getData2Multiplier()) + " ";
        }
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION2) != null) {
            description += condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION2);
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
        String description = "AlerterId: " + condition.getAlerterId();
        description += " DataId: " + condition.getDataId();
        description += " Expression: " + condition.getExpression();
        return description;
    }

    /**
     * Create a description for an EventCondition object.
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String events(EventCondition condition) {
        String description = "event on: " + condition.getDataId();
        if (condition.getExpression() != null) {
            description += " [" + condition.getExpression() + "]";
        }
        return description;
    }

    /**
     * Create a description for a MissingCondition object.
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String missing(MissingCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
        } else {
            description = condition.getDataId();
        }
        description += " not reported for " + condition.getInterval() + "ms";
        return description;
    }

    /**
     * Create a description for a NelsonCondition object.
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String nelson(NelsonCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
        } else {
            description = condition.getDataId();
        }
        description += " violates one or the following Nelson rules: " + condition.getActiveRules();
        return description;
    }

    /**
     * Create a description for a RateCondition object.
     *
     * @param condition the condition
     * @return a description to be used on email templates
     */
    public String rate(RateCondition condition) {
        String description;
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
        } else {
            description = condition.getDataId();
        }
        switch (condition.getDirection()) {
            case DECREASING:
                description += " decreasing ";
                break;
            case INCREASING:
                description += " increasing ";
                break;
            case NA:
                break;
            default:
                throw new IllegalArgumentException(condition.getDirection().name());
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
            default:
                throw new IllegalArgumentException(condition.getOperator().name());
        }
        description += decimalFormat.format(condition.getThreshold());
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_UNIT) != null) {
            description += " " + condition.getContext().get(CONTEXT_PROPERTY_UNIT);
        }
        switch (condition.getPeriod()) {
            case DAY:
                description = " per day ";
                break;
            case HOUR:
                description = " per hour ";
                break;
            case MINUTE:
                description = " per minute ";
                break;
            case SECOND:
                description = " per second ";
                break;
            case WEEK:
                description = " per week ";
                break;
            default:
                throw new IllegalArgumentException(condition.getOperator().name());
        }
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
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
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
            default:
                throw new IllegalArgumentException(operator.name());
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
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
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
            default:
                throw new IllegalArgumentException(condition.getOperator().name());
        }
        description += decimalFormat.format(condition.getThreshold());
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_UNIT) != null) {
            description += " " + condition.getContext().get(CONTEXT_PROPERTY_UNIT);
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
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION) != null) {
            description = condition.getContext().get(CONTEXT_PROPERTY_DESCRIPTION);
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
        description += decimalFormat.format(condition.getThresholdLow());
        description += ", ";
        description += decimalFormat.format(condition.getThresholdHigh());
        if (operatorHigh.equals(ThresholdRangeCondition.Operator.INCLUSIVE)) {
            description += "]";
        } else {
            description += ")";
        }
        if (condition.getContext() != null && condition.getContext().get(CONTEXT_PROPERTY_UNIT) != null) {
            description += " " + condition.getContext().get(CONTEXT_PROPERTY_UNIT);
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

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
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
