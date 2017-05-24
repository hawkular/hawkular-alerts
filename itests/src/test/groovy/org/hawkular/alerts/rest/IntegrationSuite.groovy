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
package org.hawkular.alerts.rest

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Group ITest on a suite to have better control of execution
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RunWith(Suite.class)
@Suite.SuiteClasses([
        ActionsITest.class,
        AlertsITest.class,
        ClusterITest.class,
        ConditionsITest.class,
        CrossTenantITest.class,
        DampeningITest.class,
        EventsITest.class,
        EventsLifecycleITest.class,
        EventsAggregationExtensionITest.class,
        GroupITest.class,
        ImportExportITest.class,
        LifecycleITest.class,
        TenantITest.class,
        TriggersITest.class
])
class IntegrationSuite {
    static Logger log = LoggerFactory.getLogger(IntegrationSuite.class)
    static TEST_SMTP_HOST = "localhost";
    static TEST_SMTP_PORT = 2525;
    static baseURI = System.getProperty('hawkular.base-uri') ?: 'http://127.0.0.1:8080/hawkular/alerts/'
    static GreenMail smtpServer;
    static RESTClient client

    @BeforeClass
    static void checkStatus() {
        client = new RESTClient(baseURI, ContentType.JSON)
        def tries = 100
        def sleep = 10
        def status = "INIT"
        def resp
        while (tries > 0 && status != "STARTED") {
            try {
                resp = client.get(path: "status")
                status = resp.data.status
            } catch (Exception e) {
                log.info("[" + tries + "] Not yet ready [" + e.toString() + "] Sleeping [" + sleep + "s]")
            }
            tries--
            Thread.sleep(sleep * 1000);
        }
    }

    @BeforeClass
    static void initSmtpServer() {
        smtpServer = new GreenMail(new ServerSetup(TEST_SMTP_PORT, TEST_SMTP_HOST, "smtp"));
        smtpServer.start();
    }

    @AfterClass
    static void closeSmtpServer() {
        // Give some time to the server to process last emails before to shutdown the SMTP server
        for ( int i=0; i < 10; ++i ) {
            Thread.sleep(1000);
        }
        if (smtpServer != null) {
            smtpServer.stop();
        }
    }
}
