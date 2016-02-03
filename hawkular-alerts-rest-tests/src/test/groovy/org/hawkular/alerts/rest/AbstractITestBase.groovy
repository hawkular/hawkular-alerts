/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import org.junit.AfterClass
import org.junit.BeforeClass

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

/**
 * Base class for REST tests.
 *
 * @author Lucas Ponce
 */
class AbstractITestBase {

    static baseURI = System.getProperty('hawkular.base-uri') ?: 'http://127.0.0.1:8080/hawkular/alerts/'
    static RESTClient client
    static testTenant = "28026b36-8fe4-4332-84c8-524e173a68bf"

    static TEST_SMTP_HOST = "localhost";
    static TEST_SMTP_PORT = 2525;
    static GreenMail smtpServer;

    @BeforeClass
    static void initClient() {

        client = new RESTClient(baseURI, ContentType.JSON)
        // this prevents 404 from being wrapped in an Exception, just return the response, better for testing
        client.handler.failure = { it }

        /*
            User: jdoe
            Password: password
            String encodedCredentials = Base64.getMimeEncoder()
            .encodeToString("$testUser:$testPasword".getBytes("utf-8"))
         */
        client.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="

        /*
            This is used for non-bus scenarios.
            In Bus scenarios this property is skipped
         */
        client.headers.put("Hawkular-Tenant", testTenant)
    }

    @BeforeClass
    static void initSmtpServer() {
        smtpServer = new GreenMail(new ServerSetup(TEST_SMTP_PORT, TEST_SMTP_HOST, "smtp"));
        smtpServer.start();
    }

    @AfterClass
    static void closeSmtpServer() {
        if (smtpServer != null) {
            smtpServer.stop();
        }
    }
}
