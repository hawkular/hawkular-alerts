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
package org.hawkular.actions.pagerduty.listener;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.annotation.PostConstruct;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.MessageListener;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.squareup.pagerduty.incidents.NotifyResult;
import com.squareup.pagerduty.incidents.PagerDuty;
import com.squareup.pagerduty.incidents.Trigger;

import org.hawkular.actions.api.log.MsgLogger;
import org.hawkular.actions.api.model.ActionMessage;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.bus.common.consumer.BasicMessageListener;

import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;

/**
 * Listens to pagerduty bus notifications and interacts with PagerDuty REST API.
 *
 * @author Thomas Segismont
 */
@MessageDriven(messageListenerInterface = MessageListener.class, activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Topic"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "HawkularAlertsActionsTopic"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "actionPlugin like 'pagerduty'")
})
public class PagerDutyListener extends BasicMessageListener<ActionMessage> {
    static final String API_KEY_PROPERTY = "org.hawkular.actions.pagerduty.api.key";
    static final String API_KEY = System.getProperty(API_KEY_PROPERTY);

    private final MsgLogger msgLog = MsgLogger.LOGGER;

    PagerDuty pagerDuty;

    @PostConstruct
    void setup() {
        if (isBlank(API_KEY)) {
            String msg = "Configure " + API_KEY;
            msgLog.errorCannotBeStarted("pagerduty", msg);
            return;
        }
        try {
            InstanceCreator<NotifyResult> notifyResultCreator = buildNotifyResultCreator();
            Gson gson = new GsonBuilder().registerTypeAdapter(NotifyResult.class, notifyResultCreator).create();

            RestAdapter restAdapter = new RestAdapter.Builder().setEndpoint("https://events.pagerduty.com")
                                                               .setConverter(new GsonConverter(gson)).build();

            pagerDuty = PagerDuty.create(API_KEY, restAdapter);
        } catch (Exception e) {
            msgLog.errorCannotBeStarted("pagerduty", e.getLocalizedMessage());
        }
    }

    InstanceCreator<NotifyResult> buildNotifyResultCreator() {
        Constructor<NotifyResult> constructor;
        try {
            constructor = NotifyResult.class.getDeclaredConstructor(
                    String.class,
                    String.class, String.class
            );
            if (!constructor.isAccessible()) {
                constructor.setAccessible(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Pager Duty Java client is not compatible", e);
        }
        NotifyResult notifyResult;
        try {
            notifyResult = constructor.newInstance("1", "2", "3");
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Pager Duty Java client is not compatible", e);
        }
        if (!(
                "1".equals(notifyResult.status())
                && "2".equals(notifyResult.message())
                && "3".equals(notifyResult.incidentKey())
        )) {
            throw new RuntimeException("Pager Duty Java client is not compatible");
        }
        return type -> {
            try {
                return constructor.newInstance("", "", "");
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String prepareMessage(ActionMessage msg) {
        String preparedMsg = null;
        if (msg != null) {
            Alert alert = msg.getAlert() != null ? JsonUtil.fromJson(msg.getAlert(), Alert.class) : null;
            if (alert != null) {
                preparedMsg = "Alert : " + alert.getTriggerId() + " at " + alert.getCtime() + " -- Severity: " +
                        alert.getSeverity().toString();
            } else if (msg.getMessage() != null) {
                preparedMsg = msg.getMessage();
            } else {
                preparedMsg = "Message received without data at " + System.currentTimeMillis();
                msgLog.warnMessageReceivedWithoutPayload("pagerduty");
            }
        }
        return preparedMsg;
    }

    protected void onBasicMessage(ActionMessage msg) {
        msgLog.infoActionReceived("pagerduty", msg.toString());

        if (pagerDuty == null) {
            msgLog.errorCannotSendMessage("pagerduty", "Plugin is not started");
            return;
        }

        Trigger trigger = new Trigger.Builder(prepareMessage(msg)).build();
        NotifyResult result = pagerDuty.notify(trigger);
        if (!"success".equals(result.status())) {
            msgLog.errorCannotSendMessage("pagerduty", result.message());
        }
    }
}
