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
package org.hawkular.actions.email.listener;

import java.util.Date;
import java.util.Map;
import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionMessage;
import org.hawkular.actions.email.EmailPlugin;
import org.hawkular.actions.email.template.EmailTemplate;
import org.hawkular.alerts.api.json.GsonUtil;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.jboss.logging.Logger;

/**
 * An example of listener for emails processing.
 *
 * Destination "HawkularAlertsActionsTopic" is common for all plugins.
 * Specific topics should use a messageSelector filtering by actionPlugin property with its plugin's name.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsTopic"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "actionPlugin like 'email'")})
public class EmailListener extends BasicMessageListener<ActionMessage> {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(EmailListener.class);
    private final EmailTemplate emailTemplate = new EmailTemplate();

    @Resource(mappedName = "java:jboss/mail/Default")
    Session mailSession;

    protected void onBasicMessage(ActionMessage msg) {
        try {
            msgLog.infoActionReceived(EmailPlugin.PLUGIN_NAME, msg.toString());
            Message message = createMimeMessage(msg);
            Transport.send(message);
        } catch (Exception e) {
            msgLog.errorCannotSendMessage(EmailPlugin.PLUGIN_NAME, e.getLocalizedMessage());
        }
    }

    protected Message createMimeMessage(ActionMessage msg) throws Exception {
        Message email = new HawkularMimeMessage(mailSession);

        Map<String, String> props = msg.getProperties();
        Map<String, String> defaultProps = msg.getDefaultProperties();
        String message = msg.getMessage();
        Alert alert = msg.getAlert() != null ? GsonUtil.fromJson(msg.getAlert(), Alert.class) : null;
        Alert.Status status = alert != null && alert.getStatus() != null ? alert.getStatus() : Alert.Status.OPEN;

        String from = getProp(props, defaultProps, EmailPlugin.PROP_FROM + "." + status.name().toLowerCase());
        from = from == null ? getProp(props, defaultProps, EmailPlugin.PROP_FROM) : from;
        from = from == null ? EmailPlugin.DEFAULT_FROM : from;

        String fromName = getProp(props, defaultProps, EmailPlugin.PROP_FROM_NAME + "." + status.name().toLowerCase());
        fromName = fromName == null ? getProp(props, defaultProps, EmailPlugin.PROP_FROM_NAME) : fromName;
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

        String to = getProp(props, defaultProps, EmailPlugin.PROP_TO + "." + status.name().toLowerCase());
        to = to == null ? getProp(props, defaultProps, EmailPlugin.PROP_TO) : to;
        if (to != null) {
            Address toAddress = new InternetAddress(to);
            email.addRecipient(RecipientType.TO, toAddress);
        }

        String ccs = getProp(props, defaultProps, EmailPlugin.PROP_CC + "." + status.name().toLowerCase());
        ccs = ccs == null ? getProp(props, defaultProps, EmailPlugin.PROP_CC) : ccs;
        if (ccs != null) {
            String[] multipleCc = ccs.split(",");
            for (String cc : multipleCc) {
                Address toAddress = new InternetAddress(cc);
                email.addRecipient(RecipientType.CC, toAddress);
            }
        }

        String subject = emailTemplate.subject(alert);
        if (subject != null) {
            email.setSubject(subject);
        }

        Map<String, String> body = emailTemplate.body(props, defaultProps, message, alert);
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

    private String getProp(Map<String, String> props, Map<String, String> defaultProps, String prop) {
        if (props != null && props.get(prop) != null) {
            return props.get(prop);
        }
        if (defaultProps != null && defaultProps.get(prop) != null) {
            return defaultProps.get(prop);
        }
        return null;
    }

    public class HawkularMimeMessage extends MimeMessage {

        public HawkularMimeMessage(Session session) {
            super(session);
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            /*
                Overriding to maintain my own message-id
             */
        }
    }


}
