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

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schwering.irc.lib.IRCConfig;
import org.schwering.irc.lib.IRCConfigBuilder;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCConnectionFactory;
import org.schwering.irc.lib.IRCTrafficLogger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IrcPluginTest {

    public static String IRC_HOST = "irc.freenode.net";
    public static int IRC_PORT = 6667;
    public static String IRC_USER = "hwk-alerts-bot";
    public static String IRC_PASSWORD = "H4wk0l43";

    @Test
    public void testConnection() throws Exception {

        IRCConfig config = IRCConfigBuilder.newBuilder()
                .host(IRC_HOST)
                .port(IRC_PORT)
                .nick(IRC_USER)
                .username(IRC_USER)
                .password(IRC_PASSWORD)
                .realname(IRC_USER)
                .trafficLogger(IRCTrafficLogger.SYSTEM_OUT)
                .build();

        IRCConnection conn = IRCConnectionFactory.newConnection(config);

        conn.connect();
        assertTrue(conn.isConnected());
        conn.doJoin("#hawkular-alerts");
        conn.doPrivmsg("#hawkular-alerts", "Hello World!");

        /* Wait to receive some response */
        for (int i = 0; i < 15; i++) {
            System.out.printf("[%s] Waiting response... \n", i);
            Thread.sleep(1000);
        }
        conn.doQuit();
        conn.close();
    }

}
