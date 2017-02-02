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

import org.junit.After
import org.junit.Before
import org.junit.BeforeClass

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.HttpResponseException

import java.util.concurrent.atomic.AtomicInteger

/**
 * Base class for REST tests.
 *
 * @author Lucas Ponce
 */
class AbstractITestBase {

    static baseURI = System.getProperty('hawkular.base-uri') ?: 'http://127.0.0.1:8080/hawkular/alerts/'
    static RESTClient client
    static String tenantHeaderName = "Hawkular-Tenant"
    static testTenant = "28026b36-8fe4-4332-84c8-524e173a68bf"
    static final String TENANT_PREFIX = UUID.randomUUID().toString()
    static final AtomicInteger TENANT_ID_COUNTER = new AtomicInteger(0)
    static cluster = System.getProperty('cluster') ? true : false

    @BeforeClass
    static void initClient() {

        client = new RESTClient(baseURI, ContentType.JSON)
        // this prevents 404 from being wrapped in an Exception, just return the response, better for testing
        client.handler.failure = { it }
        /*
        client.handler.failure = { resp, data ->
            resp.setData(data)
            String headers = ""
            resp.headers.each {
                headers = headers+"${it.name} : ${it.value}\n"
            }
            return resp
        }
        */
        /*
            User: jdoe
            Password: password
            String encodedCredentials = Base64.getMimeEncoder()
            .encodeToString("$testUser:$testPasword".getBytes("utf-8"))
         */
        client.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
        client.headers.put("Hawkular-Tenant", testTenant)

        def resp = client.get(path: "status")
        def tries = 100
        while (tries > 0 && resp.data.status != "STARTED") {
            Thread.sleep(500);
            resp = client.get(path: "status")
            tries--
        }
    }

    static String nextTenantId() {
        return "T${TENANT_PREFIX}${TENANT_ID_COUNTER.incrementAndGet()}"
    }

    @Before
    void beforeCluster() {
        if (cluster) {
            Thread.sleep(1000)
        }
    }

    @After
    void afterCluster() {
        if (cluster) {
            Thread.sleep(1000)
        }
    }


}
