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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.hawkular.alerts.actions.api.ActionPlugin;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.PluginMessage;
import org.hawkular.alerts.api.model.condition.Alert;
import org.jboss.logging.Logger;

/**
 * Main class Email plugin
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ActionPlugin(name = "email")
public class EmailPlugin implements ActionPluginListener {
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

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(EmailPlugin.class);

    Map<String, String> defaultProperties = new HashMap<>();

    public static final String MAIL_SESSION = "java:jboss/mail/Default";

    @Resource(mappedName = MAIL_SESSION)
    Session mailSession;

    EmailTemplate emailTemplate;

    public EmailPlugin() {

        defaultProperties.put(EmailPlugin.PROP_FROM, EmailPlugin.DEFAULT_FROM);
        defaultProperties.put(EmailPlugin.PROP_FROM_NAME, EmailPlugin.DEFAULT_FROM_NAME);
        defaultProperties.put(EmailPlugin.PROP_TO, "");
        defaultProperties.put(EmailPlugin.PROP_CC, "");
        defaultProperties.put(EmailPlugin.PROP_TEMPLATE_HAWKULAR_URL, "http://localhost:8080/");
        defaultProperties.put(EmailPlugin.PROP_TEMPLATE_PLAIN, "");
        defaultProperties.put(EmailPlugin.PROP_TEMPLATE_HTML, "");

        emailTemplate = new EmailTemplate();
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public void process(PluginMessage msg) throws Exception {
        Message message = createMimeMessage(msg);
        Transport.send(message);

        msgLog.infoActionReceived("email", msg.toString());
    }

    protected Message createMimeMessage(PluginMessage msg) throws Exception {
        Message email = new EmailMimeMessage(mailSession);

        Map<String, String> props = msg.getProperties();
        Alert alert = msg.getAction() != null ? msg.getAction().getAlert() : null;
        Alert.Status status = alert != null && alert.getStatus() != null ? alert.getStatus() : Alert.Status.OPEN;

        String from = props.get(EmailPlugin.PROP_FROM + "." + status.name().toLowerCase());
        from = from == null ? props.get(EmailPlugin.PROP_FROM) : from;
        from = from == null ? EmailPlugin.DEFAULT_FROM : from;

        String fromName = props.get(EmailPlugin.PROP_FROM_NAME + "." + status.name().toLowerCase());
        fromName = fromName == null ? props.get(EmailPlugin.PROP_FROM_NAME) : fromName;
        fromName = fromName == null ? EmailPlugin.DEFAULT_FROM_NAME : fromName;

        email.setFrom(new InternetAddress(from, fromName));
        if (alert != null && alert.getStatus() != null) {
            if (alert.getStatus().equals(Alert.Status.OPEN)) {
                email.setSentDate(new Date(alert.getCtime()));
            } else if (alert.getStatus().equals(Alert.Status.ACKNOWLEDGED)) {
                email.setSentDate(new Date(alert.getAckTime()));
            } else {
                email.setSentDate(new Date(alert.getResolvedTime()));
            }
        }

        if (alert != null) {
            email.addHeader(EmailPlugin.MESSAGE_ID, alert.getAlertId());
            if (alert.getStatus() != null && !alert.getStatus().equals(Alert.Status.OPEN)) {
                email.addHeader(EmailPlugin.IN_REPLY_TO, alert.getAlertId());
            }
        }

        String to = props.get(EmailPlugin.PROP_TO + "." + status.name().toLowerCase());
        to = to == null ? props.get(EmailPlugin.PROP_TO) : to;
        if (to != null) {
            Address toAddress = new InternetAddress(to);
            email.addRecipient(Message.RecipientType.TO, toAddress);
        }

        String ccs = props.get(EmailPlugin.PROP_CC + "." + status.name().toLowerCase());
        ccs = ccs == null ? props.get(EmailPlugin.PROP_CC) : ccs;
        if (ccs != null) {
            String[] multipleCc = ccs.split(",");
            for (String cc : multipleCc) {
                Address toAddress = new InternetAddress(cc);
                email.addRecipient(Message.RecipientType.CC, toAddress);
            }
        }

        String subject = emailTemplate.subject(alert);
        if (subject != null) {
            email.setSubject(subject);
        }

        Map<String, String> body = emailTemplate.body(props, alert);
        if (body != null && body.get("plain") != null && body.get("html") != null) {
            MimeBodyPart text = new MimeBodyPart();
            text.setContent(body.get("plain"), "text/plain");

            MimeBodyPart html = new MimeBodyPart();
            html.setContent(body.get("html"), "text/html");

            Multipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(html);
            multipart.addBodyPart(text);
            email.setContent(multipart);
        }
        return email;
    }
}
