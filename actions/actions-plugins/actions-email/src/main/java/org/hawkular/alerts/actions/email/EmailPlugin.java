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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.ActionResponseMessage.Operation;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Alert.Status;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * Action Email plugin.
 *
 * It is designed to work within Hawkular distribution or in standalone deployments.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Plugin(name = "email")
public class EmailPlugin implements ActionPluginListener {
    private final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, EmailPlugin.class);

    public static final String PLUGIN_NAME = "email";

    /**
     * This property is used for testing porpuses.
     * When present javax.mail.Session will not be initialized.
     * This is useful on unit tests scenario where the objective is to validate the email composition instead of
     * email transport cappabilities.
     */
    public static final String MAIL_SESSION_OFFLINE = "org.hawkular.alerts.actions.email.session.offline";

    public static final String MESSAGE_ID = "Message-ID";
    public static final String IN_REPLY_TO = "in-reply-to";

    public static final String DEFAULT_MAIL_SMTP_HOST = "localhost";
    public static final String DEFAULT_MAIL_SMTP_PORT = "25";

    public static final String DEFAULT_FROM_PROPERTY = "org.hawkular.alerts.actions.email.default.from";
    public static final String DEFAULT_FROM = System.getProperty(DEFAULT_FROM_PROPERTY, "noreply@hawkular.org");

    public static final String DEFAULT_FROM_NAME_PROPERTY = "org.hawkular.alerts.actions.email.default.from-name";
    public static final String DEFAULT_FROM_NAME = System.getProperty(DEFAULT_FROM_NAME_PROPERTY, "Hawkular");

    public static final String HAWKULAR_BASE_URL = "HAWKULAR_BASE_URL";
    public static final String DEFAULT_HAWKULAR_BASE_URL = System.getenv(HAWKULAR_BASE_URL) == null ?
            "http://localhost:8080/" : System.getenv(HAWKULAR_BASE_URL);

    public static final String HAWKULAR_ALERTS_TEMPLATES = "HAWKULAR_ALERTS_TEMPLATES";
    public static final String HAWKULAR_ALERTS_TEMPLATES_PROPERY = "hawkular.alerts.templates";

    /*
        This is the list of properties supported for the Email plugin.
        Properties are personalized per action.
        If not properties found per action, then plugin looks into default properties set at plugin level.
        If not default properties found at plugin level, then it takes to default ones defined inside plugin.
     */

    /**
     * "mail" property is used as main prefix for javax.mail.Session properties.
     *
     * All "mail.<protocol>.<value>" properties are passed to mail Session.
     *
     * Properties can be defined per action based.
     * If not properties defined at action level, it takes default plugin properties.
     *
     * For these special "mail" properties, if not properties defined at action plugin, it will search at
     * System.getProperties() level.
     */
    public static final String PROP_MAIL = "mail";

    /**
     * "from" property defines the sender of the plugin email.
     * Additional "from" properties can be defined to discriminate by alert state:
     * - "from.open": sender when alert is in open state
     * - "from.acknowledged": sender when alert is in acknowledge state
     * - "from.resolved": sender when alert is in acknowledge state
     *
     * Discriminated properties have priority.
     */
    public static final String PROP_FROM = "from";

    /**
     * "from-name" property defines the name of the sender of the plugin email.
     * Additional "from-name" properties can be defined to discriminate by alert state:
     * - "from-name.open": name of the sender when alert is in open state
     * - "from-name.acknowledged": name of the sender when alert is in acknowledge state
     * - "from-name.resolved": name of the sender when alert is in acknowledge state
     *
     * Discriminated properties have priority.     *
     */
    public static final String PROP_FROM_NAME = "from-name";

    /**
     * "to" property defines the recipient of the plugin email.
     * Additional "to" properties can be defined to discriminate by alert state:
     * - "to.open": recipient when alert is in open state
     * - "to.acknowledged": recipient when alert is in acknowledge state
     * - "to.resolved": recipient when alert is in acknowledge state
     *
     * Discriminated properties have priority.
     */
    public static final String PROP_TO = "to";

    /**
     * "cc" property defines the extra recipients of the plugin email.
     * Additional "cc" properties can be defined to discriminate by alert state:
     * - "cc.open": extra recipients when alert is in open state
     * - "cc.acknowledged": extra recipients when alert is in acknowledge state
     * - "cc.resolved": extra recipients when alert is in acknowledge state
     *
     * Discriminated properties have priority.
     */
    public static final String PROP_CC = "cc";

    /**
     * "template.hawkular.url" property defines the URL that will be used in the template email to point to hawkular
     * server. If not "template.hawkular.url" defined, then the plugin looks into system env HAWKULAR_BASE_URL.
     */
    public static final String PROP_TEMPLATE_HAWKULAR_URL = "template.hawkular.url";

    /**
     * Email plugin supports localization templates.
     * "template.locale" is the property used to define which template to use for specific locale.
     * i.e. A plugin may have defined several templates to support multiple locales [es,en,fr], but we can define a
     * specific locale per action [es].
     */
    public static final String PROP_TEMPLATE_LOCALE = "template.locale";

    /**
     * "template.plain" property defines the template used for plain text email.
     * Additional "template.plain" properties can be defined to support localization:
     * - "template.plain.LOCALE": where LOCALE is a variable that can point to specific localization.
     *
     * Templates are plain text based on http://freemarker.org/ engine.
     * Email plugin processes the alert payload and adds a set of pre-defined variables to be used into the template.
     * The list of variables available for templates are wrapped into {@see PluginMessageDescription} class.
     *
     */
    public static final String PROP_TEMPLATE_PLAIN = "template.plain";

    /**
     * "template.html" property defines the template used for html email.
     * Additional "template.html" properties can be defined to support localization:
     * - "template.html.LOCALE": where LOCALE is a variable that can point to specific localization.
     *
     * Email plugin uses templates based on http://freemarker.org/ engine.
     * Email plugin processes the alert payload and adds a set of pre-defined variables to be used into the template.
     * The list of variables available for templates are wrapped into {@see PluginMessageDescription} class.
     *
     */
    public static final String PROP_TEMPLATE_HTML = "template.html";



    Map<String, String> defaultProperties = new HashMap<>();

    Session mailSession;

    EmailTemplate emailTemplate;

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public EmailPlugin() {

        defaultProperties.put(PROP_MAIL, "");
        defaultProperties.put(PROP_FROM, DEFAULT_FROM);
        defaultProperties.put(PROP_FROM_NAME, DEFAULT_FROM_NAME);
        defaultProperties.put(PROP_TO, "");
        defaultProperties.put(PROP_CC, "");
        defaultProperties.put(PROP_TEMPLATE_HAWKULAR_URL, DEFAULT_HAWKULAR_BASE_URL);
        defaultProperties.put(PROP_TEMPLATE_PLAIN, "");
        defaultProperties.put(PROP_TEMPLATE_HTML, "");

        emailTemplate = new EmailTemplate();
    }

    public void setSender(ActionPluginSender sender) {
        this.sender = sender;
    }

    private void initMailSession(ActionMessage msg) {
        boolean offLine = System.getProperty(MAIL_SESSION_OFFLINE) != null;
        if (!offLine) {
            Properties emailProperties = new Properties();
            msg.getAction().getProperties().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("mail."))
                    .forEach(e -> {
                        emailProperties.put(e.getKey(), e.getValue());
                    });
            Properties systemProperties = System.getProperties();
            for (String property : systemProperties.stringPropertyNames()) {
                if (property.startsWith("mail.")) {
                    emailProperties.putIfAbsent(property, System.getProperty(property));
                }
            }
            emailProperties.putIfAbsent("mail.smtp.host", DEFAULT_MAIL_SMTP_HOST);
            emailProperties.putIfAbsent("mail.smtp.port", DEFAULT_MAIL_SMTP_PORT);
            if (emailProperties.containsKey("mail.smtp.user")
                    && emailProperties.containsKey("mail.smtp.pass")) {
                String user = emailProperties.getProperty("mail.smtp.user");
                String password = emailProperties.getProperty("mail.smtp.pass");
                mailSession = Session.getInstance(emailProperties, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, password);
                    }
                });
            } else {
                mailSession = Session.getInstance(emailProperties);
            }
        }
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    private void sendResult(Action action) {
        if (sender == null) {
            throw new IllegalStateException("ActionPluginSender is not present in the plugin");
        }
        if (action == null) {
            throw new IllegalStateException("Action to update result must be not null");
        }
        ActionResponseMessage newMessage = sender.createMessage(Operation.RESULT);
        newMessage.getPayload().put("action", JsonUtil.toJson(action));
        try {
            sender.send(newMessage);
        } catch (Exception e) {
            log.error("Error sending ActionResponseMessage", e);
        }
    }

    @Override
    public void process(ActionMessage msg) throws Exception {
        if (msg == null || msg.getAction() == null) {
            log.warnMessageReceivedWithoutPayload("email");
        }
        try {
            String tenantId = msg.getAction() != null ? msg.getAction().getTenantId() : null;
            /**
             * Mail session can change during invocations
             */
            initMailSession(msg);
            Message message = createMimeMessage(msg);
            Transport.send(message);
            log.infoActionReceived("email", msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            log.errorCannotProcessMessage("email", e.getMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }
    }

    protected Message createMimeMessage(ActionMessage msg) throws Exception {
        Message email = new EmailMimeMessage(mailSession);

        Map<String, String> props = msg.getAction().getProperties();
        if (isEmpty(props)) {
            log.warnf("Properties empty on plugin %s.", PLUGIN_NAME);
        }
        Event event = msg.getAction() != null ? msg.getAction().getEvent() : null;
        Alert alert = null != event && (event instanceof Alert) ? (Alert) event : null;
        Status status = alert != null && alert.getStatus() != null ? alert.getStatus() : Status.OPEN;
        String statusStr = status.name().toLowerCase();

        String from = props.get(PROP_FROM + "." + statusStr);
        from = from == null ? props.get(PROP_FROM) : from;
        from = from == null ? DEFAULT_FROM : from;

        String fromName = props.get(PROP_FROM_NAME + "." + statusStr);
        fromName = fromName == null ? props.get(PROP_FROM_NAME) : fromName;
        fromName = fromName == null ? DEFAULT_FROM_NAME : fromName;

        email.setFrom(new InternetAddress(from, fromName));
        if (alert != null && alert.getStatus() != null) {
            if (alert.getStatus().equals(Status.OPEN)) {
                email.setSentDate(new Date(alert.getCtime()));
            } else if (alert.getStatus().equals(Status.ACKNOWLEDGED)) {
                email.setSentDate(new Date(alert.getLastAckTime()));
            } else {
                email.setSentDate(new Date(alert.getLastResolvedTime()));
            }
        } else {
            email.setSentDate(new Date());
        }

        if (alert != null) {
            email.addHeader(EmailPlugin.MESSAGE_ID, alert.getAlertId());
            if (alert.getStatus() != null && !alert.getStatus().equals(Status.OPEN)) {
                email.addHeader(EmailPlugin.IN_REPLY_TO, alert.getAlertId());
            }
        }

        String to = props.get(PROP_TO + "." + statusStr);
        to = to == null ? props.get(PROP_TO) : to;
        if (!isEmpty(to)) {
            Address toAddress = new InternetAddress(to);
            email.addRecipient(Message.RecipientType.TO, toAddress);
        }

        String ccs = props.get(PROP_CC + "." + statusStr);
        ccs = ccs == null ? props.get(PROP_CC) : ccs;
        if (!isEmpty(ccs)) {
            String[] multipleCc = ccs.split(",");
            for (String cc : multipleCc) {
                Address toAddress = new InternetAddress(cc);
                email.addRecipient(Message.RecipientType.CC, toAddress);
            }
        }

        Map<String, String> emailProcessed = emailTemplate.processTemplate(msg);

        String subject = emailProcessed.get("emailSubject");
        if (!isEmpty(subject)) {
            email.setSubject(subject);
        } else {
            log.debugf("Subject not found processing email on message: %s", msg);
        }

        String plain = emailProcessed.get("emailBodyPlain");
        String html = emailProcessed.get("emailBodyHtml");
        if (plain != null && html != null) {
            MimeBodyPart text = new MimeBodyPart();
            text.setContent(plain, "text/plain");

            MimeBodyPart rich = new MimeBodyPart();
            rich.setContent(html, "text/html");

            Multipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(text);
            multipart.addBodyPart(rich);
            email.setContent(multipart);
        }
        return email;
    }
}
