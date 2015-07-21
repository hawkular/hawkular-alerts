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

/**
 * Main class to store common properties for email plugin.
 *
 * @author Lucas Ponce
 */
public class EmailPlugin {
    public static final String INIT_PLUGIN = "init";
    public static final String PLUGIN_NAME = "email";

    public static final String MESSAGE_ID = "Message-ID";
    public static final String IN_REPLY_TO = "in-reply-to";

    public static final String DEFAULT_FROM = "noreply@hawkular.org";
    public static final String DEFAULT_FROM_NAME = "Hawkular";

    public static final String HAWKULAR_BASE_URL = "HAWKULAR_BASE_URL";

    public static final String HAWKULAR_ALERTS_TEMPLATES = "HAWKULAR_ALERTS_TEMPLATES";
    public static final String HAWKULAR_ALERTS_TEMPLATES_PROPERY = "hawkular.alerts.templates";

    /*
        This is the list of properties supported for the Email plugin.
        Properties are personalized per action.
        If not properties found per action, then plugin looks into default properties set at plugin level.
        If not default properties found at plugin level, then it takes to default one defines inside plugin.
     */
    /**
     * "from" property defines the sender of the plugin email.
     * It optionally supports values by alert state:
     * - "from.open": sender when alert is in open state
     * - "from.acknowledged": sender when alert is in acknowledge state
     * - "from.resolved": sender when alert is in acknowledge state
     */
    public static final String PROP_FROM = "from";

    /**
     * "from-name" property defines the name of the sender of the plugin email.
     * It optionally supports values by alert state:
     * - "from-name.open": name of the sender when alert is in open state
     * - "from-name.acknowledged": name of the sender when alert is in acknowledge state
     * - "from-name.resolved": name of the sender when alert is in acknowledge state
     */
    public static final String PROP_FROM_NAME = "from-name";

    /**
     * "to" property defines the recipient of the plugin email.
     * It optionally supports values by alert state:
     * - "to.open": recipient when alert is in open state
     * - "to.acknowledged": recipient when alert is in acknowledge state
     * - "to.resolved": recipient when alert is in acknowledge state
     */
    public static final String PROP_TO = "to";

    /**
     * "cc" property defines the extra recipients of the plugin email.
     * It optionally supports values by alert state:
     * - "cc.open": extra recipients when alert is in open state
     * - "cc.acknowledged": extra recipients when alert is in acknowledge state
     * - "cc.resolved": extra recipients when alert is in acknowledge state
     */
    public static final String PROP_CC = "cc";

    /**
     * "template.hawkular.url" property defines the URL that will be used in the template email to point to hawkular
     * server. If not "template.hawkular.url" defined, then the plugin looks into system env HAWKULAR_BASE_URL.
     */
    public static final String PROP_TEMPLATE_HAWKULAR_URL = "template.hawkular.url";

    /**
     * Email plugin support localization templates.
     * "template.locale" is the property used to define which template to use for specific locale.
     */
    public static final String PROP_TEMPLATE_LOCALE = "template.locale";

    /**
     * "template.plain" property defines the template used for plain text email.
     * It supports localization:
     * - "template.plain.LOCALE": where LOCALE is a variable that can point to specific localization.
     *
     * Email plugin uses templates based on http://freemarker.org/ engine.
     * Email plugin processes the alert payload and adds a set of pre-defined variables to be used into the template.
     * This is the list of the variables used in the templates:
     *
     * - "subject":             Subject of the alert email.
     * - "plainSubject":        Subject for the body of the plain text email template.
     * - "htmlSubject":         Subject for the body of the html email template.
     * - "alert":               Alert object as payload of the ActionMessage message.
     * - "message":             ActionMessage.message property, added only when alert object is not present.
     * - "url":                 Url defined in "template.hawkular.url" property to point hawkular server.
     * - "status":              Status of the alert. Shortcut of "alert.status".
     * - "numCondition":        Number of conditions defined for the given alert.
     * - "condDescs":           List of EmailTemplate.ConditionDesc objects when numConditions > 1.
     *                          It parses the definition of the condition in an string based definition easy to ready
     *                          in a template.
     */
    public static final String PROP_TEMPLATE_PLAIN = "template.plain";

    /**
     * "template.html" property defines the template used for html email.
     * It supports localization:
     * - "template.html.LOCALE": where LOCALE is a variable that can point to specific localization.
     *
     * Email plugin uses templates based on http://freemarker.org/ engine.
     * Email plugin processes the alert payload and adds a set of pre-defined variables to be used into the template.
     * This is the list of the variables used in the templates:
     *
     * - "subject":             Subject of the alert email.
     * - "plainSubject":        Subject for the body of the plain text email template.
     * - "htmlSubject":         Subject for the body of the html email template.
     * - "alert":               Alert object as payload of the ActionMessage message.
     * - "message":             ActionMessage.message property, added only when alert object is not present.
     * - "url":                 Url defined in "template.hawkular.url" property to point hawkular server.
     * - "status":              Status of the alert. Shortcut of "alert.status".
     * - "numCondition":        Number of conditions defined for the given alert.
     * - "condDescs":           List of EmailTemplate.ConditionDesc objects when numConditions > 1.
     *                          It parses the definition of the condition in an string based definition easy to ready
     *                          in a template.
     */
    public static final String PROP_TEMPLATE_HTML = "template.html";
}
