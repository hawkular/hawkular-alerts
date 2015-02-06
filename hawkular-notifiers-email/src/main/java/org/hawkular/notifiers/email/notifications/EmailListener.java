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
package org.hawkular.notifiers.email.notifications;

import javax.annotation.Resource;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.hawkular.bus.common.consumer.BasicMessageListener;
import org.hawkular.notifiers.api.log.MsgLogger;
import org.hawkular.notifiers.api.model.NotificationMessage;

/**
 * An example of listener for emails processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "NotificationsTopic"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "NotifierType like 'email'")})
public class EmailListener extends BasicMessageListener<NotificationMessage> {
    private final MsgLogger msgLog = MsgLogger.LOGGER;

    @Resource(mappedName = "java:jboss/mail/Default")
    Session mailSession;

    protected void onBasicMessage(NotificationMessage msg) {
        try {
            Message message = createMimeMessage(msg);
            Transport.send(message);
        } catch (MessagingException e) {
            msgLog.errorCannotSendMessage("email", e.getLocalizedMessage());
        }
    }

    Message createMimeMessage(NotificationMessage msg) throws MessagingException {
        Message message = new MimeMessage(mailSession);
        message.setFrom(new InternetAddress("noreply@hawkular"));
        Address toAddress = new InternetAddress("root@localhost");
        message.addRecipient(RecipientType.TO, toAddress);
        message.setSubject("Hawkular alert " + msg.getNotifierId());
        message.setContent(msg.getMessage(), "text/plain");
        return message;
    }
}
