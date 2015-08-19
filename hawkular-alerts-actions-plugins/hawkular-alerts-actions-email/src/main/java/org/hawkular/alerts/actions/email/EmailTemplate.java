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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.MsgLogger;
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
import org.jboss.logging.Logger;

import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Template preprocessor based on Freemarker
 *
 * @author Lucas Ponce
 */
public class EmailTemplate {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(EmailTemplate.class);

    public static final String DEFAULT_TEMPLATE_PLAIN = "template.plain.default.ftl";
    public static final String DEFAULT_TEMPLATE_HTML = "template.html.default.ftl";
    public static final Locale DEFAULT_LOCALE = new Locale("en", "US");

    Configuration ftlCfg;
    Template ftlTemplate;
    Template ftlTemplatePlain;
    Template ftlTemplateHtml;

    public EmailTemplate() {
        ftlCfg = new Configuration();
        try {
            /*
                Check if templates are located from disk or if we are loading default ones.
             */
            String templatesDir = System.getenv(EmailPlugin.HAWKULAR_ALERTS_TEMPLATES);
            templatesDir = templatesDir == null ? System.getProperty(EmailPlugin.HAWKULAR_ALERTS_TEMPLATES_PROPERY)
                    : templatesDir;
            boolean templatesFromDir = false;
            if (templatesDir != null) {
                File fileDir = new File(templatesDir);
                if (fileDir.exists()) {
                    ftlCfg.setDirectoryForTemplateLoading(fileDir);
                    templatesFromDir = true;
                }
            }
            if (!templatesFromDir) {
                ftlCfg.setClassForTemplateLoading(this.getClass(), "/");
            }
            ftlTemplatePlain = ftlCfg.getTemplate(DEFAULT_TEMPLATE_PLAIN, DEFAULT_LOCALE);
            ftlTemplateHtml = ftlCfg.getTemplate(DEFAULT_TEMPLATE_HTML, DEFAULT_LOCALE);
        } catch (IOException e) {
            log.debug(e.getMessage(), e);
            throw new RuntimeException("Cannot initialize templates on email plugin: " + e.getMessage());
        }
    }

    public String subject(Alert alert) throws Exception {
        Map<String, Object> subjects = new HashMap<>();
        subjects(subjects, alert);
        return subjects.get("subject") != null ? (String)subjects.get("subject") : "Alert message";
    }

    public Map<String, String> body(Map<String, String> props, Alert alert) throws Exception {
        Map<String, String> processed = new HashMap<>();
        if (alert != null) {
            String plain;
            String html;
            Map<String, Object> templateData = new HashMap<>();
            if (alert != null) {
                templateData.put("alert", alert);
            }

            subjects(templateData, alert);

            String templateHawkularUrl = props != null ? props.get(EmailPlugin.PROP_TEMPLATE_HAWKULAR_URL) : null;
            templateHawkularUrl = templateHawkularUrl == null ? System.getenv(EmailPlugin.HAWKULAR_BASE_URL)
                    : templateHawkularUrl;
            templateData.put("url", templateHawkularUrl);

            if (alert != null && alert.getStatus() != null) {
                Alert.Status status = alert.getStatus();
                templateData.put("status", status.name());
            }

            String templateLocale = props != null ? props.get(EmailPlugin.PROP_TEMPLATE_LOCALE) : null;
            if (templateLocale != null) {
                plain = props.get(EmailPlugin.PROP_TEMPLATE_PLAIN + "." + templateLocale);
                html = props.get(EmailPlugin.PROP_TEMPLATE_HTML + "." + templateLocale);
            } else {
                plain = props != null ? props.get(EmailPlugin.PROP_TEMPLATE_PLAIN) : null;
                html = props != null ? props.get(EmailPlugin.PROP_TEMPLATE_HTML) : null;
            }

            int numConditions = getConditionsSize(alert);
            templateData.put("numConditions", numConditions);
            if (numConditions == 1) {
                averageAndThreshold(templateData, alert);
            } else if (numConditions > 1) {
                List<ConditionDesc> condDescs = getConditionDescription(alert);
                templateData.put("condDescs", condDescs);
            }

            StringWriter writerPlain = new StringWriter();
            StringWriter writerHtml = new StringWriter();
            if (plain != null && !plain.isEmpty()) {
                ftlTemplate = Template.getPlainTextTemplate("plainTemplate", plain, ftlCfg);
                ftlTemplate.process(templateData, writerPlain);
            }  else {
                ftlTemplatePlain.process(templateData, writerPlain);
            }
            if (html != null && !html.isEmpty()) {
                ftlTemplate = Template.getPlainTextTemplate("htmlTemplate", html, ftlCfg);
                ftlTemplate.process(templateData, writerHtml);
            } else {
                ftlTemplateHtml.process(templateData, writerHtml);
            }

            writerPlain.flush();
            writerPlain.close();

            writerHtml.flush();
            writerHtml.close();

            processed.put("plain", writerPlain.toString());
            processed.put("html", writerHtml.toString());
        } else {
            String defaultMsg = "Message received without alert data at " + System.currentTimeMillis();
            processed.put("plain", defaultMsg);
            processed.put("html", defaultMsg);
            msgLog.warnMessageReceivedWithoutPayload(EmailPlugin.PLUGIN_NAME);
            return processed;
        }
        return processed;
    }

    /*
        This method builds the subjects used for:
            1.- "subject" email header
            2.- Main title in HTML email
            3.- Main title in Plain email

        We may have two scenarios:
            a) Trigger with one condition:

                In this case, we personalize the subject according the type of condition
                (it supports AVAILABILITY and THRESHOLD for the moment)

            b) Trigger with more than one condition:

                With more than one condition the subject will be generic showing the name of the trigger

        Results are stores in the Map passed as first argument with the following keys:

            props.put("subject", subject);
            props.put("plainSubject", plainSubject);
            props.put("htmlSubject", htmlSubject);
     */
    private void subjects(Map<String, Object> props, Alert alert) {
        if (props == null) return;
        if (alert == null) return;
        String subject;
        String plainSubject;
        String htmlSubject;
        if (alert.getTrigger() == null) {
            subject = plainSubject = htmlSubject = getStateSubject(alert);
        } else {
            int numConditions = getConditionsSize(alert);
            if (numConditions == 0) {
                subject = plainSubject = htmlSubject = getStateSubject(alert);
            } else if (numConditions == 1) {
                Condition.Type type = getFirstConditionType(alert);
                Condition condition = getFirstCondition(alert);
                subject = plainSubject = htmlSubject = getStateSubject(alert);
                /*
                    Subject for AVAILABILITY
                 */
                if (type != null && type.equals(Condition.Type.AVAILABILITY)) {
                    plainSubject = "Server " + alert.getTrigger().getName() + " is";
                    htmlSubject = "Server is ";
                    AvailabilityCondition aCondition = (AvailabilityCondition) condition;
                    subject += ": " + alert.getTrigger().getName() + " is";
                    String description;
                    if (alert.getStatus().equals(Alert.Status.RESOLVED) && alert.getResolvedEvalSets() != null) {
                        AvailabilityCondition rCondition = (AvailabilityCondition) getFirstResolvedCondition(alert);
                        description = oneCondAvailabilityDescription(rCondition);
                    } else {
                        description = oneCondAvailabilityDescription(aCondition);
                    }
                    subject += " " + description;
                    plainSubject += " " + description;
                    htmlSubject += " " + description;
                }

                /*
                    Subject for THRESHOLD
                 */
                if (type != null && type.equals(Condition.Type.THRESHOLD)) {
                    ThresholdCondition tCondition = (ThresholdCondition) condition;
                    subject += ": " + alert.getTrigger().getName() + " has response time";
                    plainSubject = "Response time";
                    String description;
                    if (alert.getStatus().equals(Alert.Status.RESOLVED) && alert.getResolvedEvalSets() != null) {
                        ThresholdCondition rCondition = (ThresholdCondition) getFirstResolvedCondition(alert);
                        description = oneCondResponseTimeDescription(rCondition);
                    } else {
                        description = oneCondResponseTimeDescription(tCondition);
                    }
                    subject += " " + description;
                    plainSubject += " " + description;
                    htmlSubject = plainSubject;
                    plainSubject += " for " + alert.getTrigger().getName();
                }
            } else {
                /*
                    We have several conditions, so we write a generic subject
                 */
                subject = getStateSubject(alert) + " for " + alert.getTrigger().getName();
                plainSubject = "Alert for " + alert.getTrigger().getName();
                htmlSubject = "Alert for";
            }
        }
        props.put("subject", subject);
        props.put("plainSubject", plainSubject);
        props.put("htmlSubject", htmlSubject);
    }

    private String oneCondAvailabilityDescription(AvailabilityCondition cond) {
        switch (cond.getOperator()) {
            case UP:
                return "up";
            case NOT_UP:
                return "not up";
            case DOWN:
                return "down";
        }
        return "";
    }

    private String oneCondResponseTimeDescription(ThresholdCondition cond) {
        switch(cond.getOperator()) {
            case GT:
                return "greater than threshold";
            case GTE:
                return "greater or equal than threshold";
            case LT:
                return "less than threshold";
            case LTE:
                return "less or equal than threshold";
        }
        return "";
    }

    private String mixDescription(Condition cond) {
        String desc = "";
        if (cond instanceof ThresholdCondition) {
            desc += cond.getDataId() + " is ";
            ThresholdCondition.Operator operator = ((ThresholdCondition) cond).getOperator();
            switch (operator) {
                case LT:
                    desc += "less than ";
                    break;
                case LTE:
                    desc += "less or equals than ";
                    break;
                case GT:
                    desc += "greater than ";
                    break;
                case GTE:
                    desc += "greater or equals than ";
                    break;
            }
            desc += ((ThresholdCondition) cond).getThreshold();
        } else if (cond instanceof AvailabilityCondition) {
            desc += cond.getDataId() + " is ";
            AvailabilityCondition.Operator operator = ((AvailabilityCondition) cond).getOperator();
            switch (operator) {
                case DOWN:
                    desc += "down ";
                    break;
                case NOT_UP:
                    desc += "not up";
                    break;
                case UP:
                    desc += "up";
                    break;
            }
        } else if (cond instanceof ThresholdRangeCondition) {
            desc += cond.getDataId() + " ";
            if (((ThresholdRangeCondition) cond).isInRange()) {
                desc += "in range ";
            } else {
                desc += "out of range ";
            }
            ThresholdRangeCondition.Operator operatorLow = ((ThresholdRangeCondition) cond).getOperatorLow();
            ThresholdRangeCondition.Operator operatorHigh = ((ThresholdRangeCondition) cond).getOperatorHigh();
            if (operatorLow.equals(ThresholdRangeCondition.Operator.INCLUSIVE)) {
                desc += "[ ";
            } else {
                desc += "( ";
            }
            desc += ((ThresholdRangeCondition) cond).getThresholdLow();
            desc += ", ";
            desc += ((ThresholdRangeCondition) cond).getThresholdHigh();
            if (operatorHigh.equals(ThresholdRangeCondition.Operator.INCLUSIVE)) {
                desc += " ]";
            } else {
                desc += " )";
            }
        } else if (cond instanceof StringCondition) {
            desc += cond.getDataId() + " ";
            StringCondition.Operator operator = ((StringCondition) cond).getOperator();
            switch (operator) {
                case STARTS_WITH:
                    desc += "starts with ";
                    break;
                case CONTAINS:
                    desc += "contains ";
                    break;
                case ENDS_WITH:
                    desc += "ends with ";
                    break;
                case EQUAL:
                    desc += "is equal to ";
                    break;
                case NOT_EQUAL:
                    desc += "is not equal to ";
                    break;
                case MATCH:
                    desc += "matches to ";
                    break;
            }
            desc += ((StringCondition) cond).getPattern();
            if (((StringCondition) cond).isIgnoreCase()) {
                desc += " (ignore case) ";
            }
        } else if (cond instanceof CompareCondition) {
            desc += cond.getDataId() + " ";
            CompareCondition.Operator operator = ((CompareCondition) cond).getOperator();
            switch (operator) {
                case LT:
                    desc += "less than ";
                    break;
                case LTE:
                    desc += "less or equals than ";
                    break;
                case GT:
                    desc += "greater than ";
                    break;
                case GTE:
                    desc += "greater or equals than ";
                    break;
            }
            if (((CompareCondition) cond).getData2Multiplier() != 1.0) {
                desc += "( " + ((CompareCondition) cond).getData2Id() + " * "
                        + ((CompareCondition) cond).getData2Multiplier() + " )";
            } else {
                desc += ((CompareCondition) cond).getData2Id();
            }
        } else if (cond instanceof ExternalCondition) {
            desc += cond.getDataId() + " with external expression " + ((ExternalCondition) cond).getExpression();
        }
        return desc;
    }

    private String getStateSubject(Alert alert) {
        if (alert != null && alert.getStatus() != null) {
            return "Alert [" + alert.getStatus().name().toLowerCase() + "] message";
        }
        return "Alert message";
    }

    private Condition.Type getFirstConditionType(Alert alert) {
        if (alert != null && alert.getEvalSets() != null && alert.getEvalSets().size() > 0) {
            if (alert.getEvalSets().get(0) != null && alert.getEvalSets().get(0).size() > 0) {
                ConditionEval conditionEval = alert.getEvalSets().get(0).iterator().next();
                return conditionEval.getType();
            }
        }
        return null;
    }

    private int getConditionsSize(Alert alert) {
        if (alert != null && alert.getEvalSets() != null && alert.getEvalSets().size() > 0) {
            return alert.getEvalSets().get(0).size();
        }
        return 0;
    }

    private Condition getFirstCondition(Alert alert) {
        if (alert == null
                || alert.getEvalSets() == null
                || alert.getEvalSets().size() == 0
                || alert.getStatus() == null) {
            return null;
        }
        if (alert.getEvalSets().get(0) != null && alert.getEvalSets().get(0).size() > 0) {
            ConditionEval conditionEval = alert.getEvalSets().get(0).iterator().next();
            if (conditionEval instanceof AvailabilityConditionEval) {
                return ((AvailabilityConditionEval) conditionEval).getCondition();
            } else if (conditionEval instanceof ThresholdConditionEval) {
                return ((ThresholdConditionEval) conditionEval).getCondition();
            }
        }
        return null;
    }

    private Condition getFirstResolvedCondition(Alert alert) {
        if (alert == null
                || alert.getResolvedEvalSets() == null
                || alert.getResolvedEvalSets().size() == 0
                || alert.getStatus() == null) {
            return null;
        }
        if (alert.getResolvedEvalSets().get(0) != null && alert.getResolvedEvalSets().get(0).size() > 0) {
            ConditionEval conditionEval = alert.getResolvedEvalSets().get(0).iterator().next();
            if (conditionEval instanceof AvailabilityConditionEval) {
                return ((AvailabilityConditionEval) conditionEval).getCondition();
            } else if (conditionEval instanceof ThresholdConditionEval) {
                return ((ThresholdConditionEval) conditionEval).getCondition();
            }
        }
        return null;
    }

    private void averageAndThreshold(Map<String, Object> props, Alert alert) {
        if (props != null && alert != null) {
            double totalTime = 0;
            double average;
            int countTime = 0;
            for (Set<ConditionEval> setEval : alert.getEvalSets()) {
                for (ConditionEval cEval : setEval) {
                    if (cEval instanceof ThresholdConditionEval) {
                        totalTime += ((ThresholdConditionEval) cEval).getValue();
                        countTime++;
                    }
                }
            }
            average = totalTime / countTime;
            props.put("average", Double.toString(average));
            props.put("condition", getFirstCondition(alert));
        }
    }

    /*
        This class is used to represent the description of a List<Set<ConditionEval>>.
        It is listed by conditions, and each condition will have a description plus a description of an average values.
     */
    public static class ConditionDesc {
        public String type;
        public String description;
        public String values;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getValues() {
            return values;
        }

        public void setValues(String values) {
            this.values = values;
        }
    }

    private List<ConditionDesc> getConditionDescription(Alert alert) {
        List<ConditionDesc> descs = new ArrayList<>();
        if (alert != null && alert.getEvalSets() != null) {
            List<Set<ConditionEval>> evalsSets = alert.getEvalSets();
            if (evalsSets.size() > 0) {
                Set<ConditionEval> setEval = evalsSets.get(0);
                for (ConditionEval eval : setEval) {
                    /*
                        First item is to build the description
                     */
                    ConditionDesc desc = new ConditionDesc();
                    desc.type = eval.getType().name();
                    switch (eval.getType()) {
                        case THRESHOLD:
                            desc.description = mixDescription(((ThresholdConditionEval)eval).getCondition());
                            break;
                        case AVAILABILITY:
                            desc.description = mixDescription(((AvailabilityConditionEval)eval).getCondition());
                            break;
                        case RANGE:
                            desc.description = mixDescription(((ThresholdRangeConditionEval)eval).getCondition());
                            break;
                        case COMPARE:
                            desc.description = mixDescription(((CompareConditionEval)eval).getCondition());
                            break;
                        case STRING:
                            desc.description = mixDescription(((StringConditionEval)eval).getCondition());
                            break;
                        case EXTERNAL:
                            desc.description = mixDescription(((ExternalConditionEval)eval).getCondition());
                            break;
                    }
                    descs.add(desc);
                }
            }
        }
        return descs;
    }
}
