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
package org.hawkular.actions.email.template;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.email.EmailPlugin;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.AvailabilityConditionEval;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdConditionEval;
import org.jboss.logging.Logger;

/**
 * Template preprocessor based on Mustache
 *
 * @author Lucas Ponce
 */
public class EmailTemplate {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(EmailTemplate.class);

    private static final String DEFAULT_TEMPLATE_PLAIN = "template.plain.default.ftl";
    private static final String DEFAULT_TEMPLATE_HTML = "template.html.default.ftl";
    private static final Locale DEFAULT_LOCALE = new Locale("en", "US");

    private static final String SUBJECT = "Alert message";

    Configuration ftlCfg;
    Template ftlTemplate;
    Template ftlTemplatePlain;
    Template ftlTemplateHtml;

    public EmailTemplate() {
        ftlCfg = new Configuration();
        ftlCfg.setClassForTemplateLoading(this.getClass(), "/");
        try {
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

    public Map<String, String> body(Map<String, String> props, Map<String, String> defaultProps, String message,
            Alert alert) throws Exception {
        Map<String, String> processed = new HashMap<>();
        if (message != null || alert != null) {
            String plain;
            String html;
            if (alert == null && message == null) {
                html = plain = "Message received without data at " + System.currentTimeMillis();
                processed.put("plain", plain);
                processed.put("html", html);
                msgLog.warnMessageReceivedWithoutPayload(EmailPlugin.PLUGIN_NAME);
                return processed;
            }
            Map<String, Object> templateData = new HashMap<>();
            if (alert != null) {
                templateData.put("alert", alert);
            } else if (message != null) {
                templateData.put("message", message);
            }

            subjects(templateData, alert);

            String templateHawkularUrl = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_HAWKULAR_URL);
            if (templateHawkularUrl == null) {
                templateHawkularUrl = System.getenv(EmailPlugin.HAWKULAR_BASE_URL);
            }
            templateData.put("url", templateHawkularUrl);

            String templateLocale = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_LOCALE);
            Condition.Type type = getFirstConditionType(alert);
            if (type != null) {
                templateData.put("type", type);
                averageAndThreshold(templateData, alert);
                if (templateLocale != null) {
                    plain = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_PLAIN + "." + type.name() + "."
                            + templateLocale);
                    html = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_HTML + "." + type.name() + "."
                            + templateLocale);
                } else {
                    plain = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_PLAIN + "." + type.name());
                    html = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_HTML + "." + type.name());
                }
            } else {
                if (templateLocale != null) {
                    plain = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_PLAIN + "." + templateLocale);
                    html = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_HTML + "." + templateLocale);
                } else {
                    plain = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_PLAIN);
                    html = getProp(props, defaultProps, EmailPlugin.PROP_TEMPLATE_HTML);
                }
            }

            StringWriter writerPlain = new StringWriter();
            StringWriter writerHtml = new StringWriter();
            if (plain != null) {
                ftlTemplate = Template.getPlainTextTemplate("plainTemplate", plain, ftlCfg);
                ftlTemplate.process(templateData, writerPlain);
            }  else {
                ftlTemplatePlain.process(templateData, writerPlain);
            }
            if (html != null) {
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
        }
        return processed;
    }

    private void subjects(Map<String, Object> props, Alert alert) {
        if (props != null && alert != null && alert.getTrigger() != null && getFirstCondition(alert) != null) {
            Condition.Type type = getFirstConditionType(alert);
            Condition condition = getFirstCondition(alert);
            String subject = SUBJECT;
            String plainSubject = null;
            String htmlSubject = null;
            if (type != null && type.equals(Condition.Type.AVAILABILITY)) {
                plainSubject = "Server " + alert.getTrigger().getName() + " is";
                htmlSubject = "Server is ";
                AvailabilityCondition aCondition = (AvailabilityCondition) condition;
                subject += ": " + alert.getTrigger().getName() + " is";
                if (aCondition.getOperator().equals(AvailabilityCondition.Operator.UP)) {
                    subject += " up";
                    plainSubject += " up";
                    htmlSubject += "up";
                } else if (aCondition.getOperator().equals(AvailabilityCondition.Operator.NOT_UP)) {
                    subject += " not up";
                    plainSubject += " not up";
                    htmlSubject += " not up";
                } else if (aCondition.getOperator().equals(AvailabilityCondition.Operator.DOWN)) {
                    subject += " down";
                    plainSubject += " down";
                    htmlSubject += " down";
                }
            }
            if (type != null && type.equals(Condition.Type.THRESHOLD)) {
                ThresholdCondition tCondition = (ThresholdCondition) condition;
                subject += ": " + alert.getTrigger().getName() + " has response time";
                plainSubject = "Response time";
                if (tCondition.getOperator().equals(ThresholdCondition.Operator.GT)) {
                    subject += " greater than threshold";
                    plainSubject += " greater than threshold";
                } else if (tCondition.getOperator().equals(ThresholdCondition.Operator.GTE)) {
                    subject += " greater or equal than threshold";
                    plainSubject += " greater or equal than threshold";
                } else if (tCondition.getOperator().equals(ThresholdCondition.Operator.LT)) {
                    subject += " less than threshold";
                    plainSubject += " less than threshold";
                } else if (tCondition.getOperator().equals(ThresholdCondition.Operator.LTE)) {
                    subject += " less or equal than threshold";
                    plainSubject += " less or equal than threshold";
                }
                htmlSubject = plainSubject;
                plainSubject += " for " + alert.getTrigger().getName();
            }
            props.put("subject", subject);
            props.put("plainSubject", plainSubject);
            props.put("htmlSubject", htmlSubject);
        }
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

    private Condition getFirstCondition(Alert alert) {
        if (alert != null && alert.getEvalSets() != null && alert.getEvalSets().size() > 0) {
            if (alert.getEvalSets().get(0) != null && alert.getEvalSets().get(0).size() > 0) {
                ConditionEval conditionEval = alert.getEvalSets().get(0).iterator().next();
                if (conditionEval instanceof AvailabilityConditionEval) {
                    return ((AvailabilityConditionEval) conditionEval).getCondition();
                } else if (conditionEval instanceof ThresholdConditionEval) {
                    return ((ThresholdConditionEval) conditionEval).getCondition();
                }
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

    private String getProp(Map<String, String> props, Map<String, String> defaultProps, String prop) {
        if (props != null && props.get(prop) != null) {
            return props.get(prop);
        }
        if (defaultProps != null && defaultProps.get(prop) != null) {
            return defaultProps.get(prop);
        }
        return null;
    }
}
