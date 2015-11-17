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
package org.hawkular.alerts.actions.irc;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Event;
import org.schwering.irc.lib.IRCConfig;
import org.schwering.irc.lib.IRCConfigBuilder;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCConnectionFactory;

/**
 * An example of listener for snmp processing.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Plugin(name = "irc")
public class IrcPlugin implements ActionPluginListener {
    static final String IRC_HOST_DEFAULT = "irc.freenode.net";
    static final String IRC_PORT_DEFAULT = "6667";
    static final String IRC_NICK_DEFAULT = "hwk-alerts-bot";
    static final String IRC_PASSWORD_DEFAULT = "H4wk0l43";
    static final String IRC_CHANNEL_DEFAULT = "#hawkular-alerts";
    static final String IRC_DETAIL_DEFAULT = "false";

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    Map<String, String> defaultProperties = new HashMap<>();

    private IRCConnection conn = null;
    private String channel = null;
    private String detail = null;

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public IrcPlugin() {
        defaultProperties.put("host", IRC_HOST_DEFAULT);
        defaultProperties.put("port", IRC_PORT_DEFAULT);
        defaultProperties.put("nick", IRC_NICK_DEFAULT);
        defaultProperties.put("password", IRC_PASSWORD_DEFAULT);
        defaultProperties.put("detail", IRC_DETAIL_DEFAULT);
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
    public void process(ActionMessage msg) throws Exception {
        try {
            if (!isConnected()) {
                connect(msg.getAction().getProperties());
            }
            send(msg.getAction(), Boolean.parseBoolean(detail));
            msgLog.infoActionReceived("irc", msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }
    }

    private boolean isConnected() throws Exception {
        if (conn == null) {
            return false;
        }
        return conn.isConnected();
    }

    private void connect(Map<String, String> props) throws Exception {
        String host = (props == null || !props.containsKey("host")) ? IRC_HOST_DEFAULT : props.get("host");
        String port = (props == null || !props.containsKey("port")) ? IRC_PORT_DEFAULT : props.get("port");
        String nick = (props == null || !props.containsKey("nick")) ? IRC_NICK_DEFAULT : props.get("nick");
        String password = (props == null || !props.containsKey("password")) ? IRC_PASSWORD_DEFAULT
                : props.get("password");
        channel = (props == null || !props.containsKey("channel") ? IRC_CHANNEL_DEFAULT : props.get("channel"));
        detail = (props == null || !props.containsKey("detail") ? IRC_DETAIL_DEFAULT : props.get("detail"));

        IRCConfig config = IRCConfigBuilder.newBuilder()
                .host(host)
                .port(Integer.valueOf(port))
                .nick(nick)
                .username(nick)
                .password(password)
                .realname(nick)
                .build();

        conn = IRCConnectionFactory.newConnection(config);
        conn.connect();
        conn.doJoin(channel);
    }

    private void send(Action action, boolean detail) {
        Event e = action.getEvent();
        if (e != null) {
            StringBuilder msg = new StringBuilder();
            msg.append(e.getEventType());
            msg.append(" ");
            msg.append(e.getId());
            msg.append(" at ");
            msg.append(new Date(e.getCtime()).toString());
            conn.doPrivmsg(channel, msg.toString());
            if (detail) {
                conn.doPrivmsg(channel, JsonUtil.toJson(e));
            }
        }
    }

    private void sendResult(Action action) {
        if (sender == null) {
            throw new IllegalStateException("ActionPluginSender is not present in the plugin");
        }
        if (action == null) {
            throw new IllegalStateException("Action to update result must be not null");
        }
        ActionResponseMessage newMessage = sender.createMessage(ActionResponseMessage.Operation.RESULT);
        newMessage.getPayload().put("action", JsonUtil.toJson(action));
        try {
            sender.send(newMessage);
        } catch (Exception e) {
            msgLog.error("Error sending ActionResponseMessage", e);
        }
    }

}
