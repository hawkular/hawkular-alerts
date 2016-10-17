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

import static org.hawkular.jaxrs.filter.cors.Headers.ACCESS_CONTROL_ALLOW_CREDENTIALS
import static org.hawkular.jaxrs.filter.cors.Headers.ACCESS_CONTROL_ALLOW_HEADERS
import static org.hawkular.jaxrs.filter.cors.Headers.ACCESS_CONTROL_ALLOW_METHODS
import static org.hawkular.jaxrs.filter.cors.Headers.ACCESS_CONTROL_ALLOW_ORIGIN
import static org.hawkular.jaxrs.filter.cors.Headers.ACCESS_CONTROL_MAX_AGE
import static org.hawkular.jaxrs.filter.cors.Headers.ACCESS_CONTROL_REQUEST_METHOD
import static org.hawkular.jaxrs.filter.cors.Headers.DEFAULT_CORS_ACCESS_CONTROL_ALLOW_HEADERS
import static org.hawkular.jaxrs.filter.cors.Headers.DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS
import static org.hawkular.jaxrs.filter.cors.Headers.ORIGIN
import static org.junit.Assert.assertEquals
import static org.junit.Assume.assumeTrue

import org.hawkular.alerts.api.model.event.Event

import org.junit.Before
import org.junit.Test
import org.junit.Ignore

class CORSITest extends AbstractITestBase {
  static final String testOrigin = System.getProperty("hawkular.test.origin", "http://test.hawkular.org")
  static final String testAccessControlAllowHeaders = DEFAULT_CORS_ACCESS_CONTROL_ALLOW_HEADERS + ',' +
    System.getProperty("hawkular.test.access-control-allow-headers");

  @Test
  void testOptionsWithOrigin() {
    def response = client.options(path: "ping",
        headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "POST",
            //this should be ignored by the container and reply with pre-configured headers
            (ACCESS_CONTROL_ALLOW_HEADERS): "test-header",
            (ORIGIN): testOrigin,
        ])

    def responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    //Expected a 200 because this is be a pre-flight call that should never reach the resource router
    assertEquals(200, response.status)
    assertEquals(null, response.getData())
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, testOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)
  }

  @Test
  void testOptionsWithBadOrigin() {
    def response = client.options(path: "events/test", headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "OPTIONS",
            (ORIGIN): "*"
    ])
    assertEquals(400, response.status)

    def wrongSchemeOrigin = testOrigin.replaceAll("http://", "https://")
    response = client.options(path: "events/test", headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "GET",
            (ORIGIN): wrongSchemeOrigin
    ])
    assertEquals(400, response.status)
  }

  @Test
  void testOptionsWithSubdomainOrigin() {
    //construct a subdomain with "tester." as prefix
    def subDomainOrigin = testOrigin.substring(0, testOrigin.indexOf("/") + 2) + "tester." + testOrigin.substring(testOrigin.indexOf("/") + 2)
    def response = client.options(path: "gauges/test/raw",
        headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "GET",
            (ORIGIN): subDomainOrigin
        ])

    def responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    assertEquals(200, response.status)
    assertEquals(null, response.getData())
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, subDomainOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)
  }

  @Test
  void testOptionsWithoutTenantIDAndData() {
    long start = System.currentTimeMillis() - (20 * 60000);
    def tenantId = nextTenantId()

    // First just create a test Event
    Map context = new java.util.HashMap();
    context.put("cors-test-context-name", "cors-test-context-value");
    Map tags = new java.util.HashMap();
    tags.put("cors-test-tag-name", "cors-test-tag-value");
    Event event = new Event(tenantId, "cors-test-event-id", System.currentTimeMillis(), "cors-test-event-data-id",
            "cors-test-category", "cors test event text", context, tags);

    def response = client.post(path: "events", body: event, headers: [(tenantHeaderName): tenantId])
    assertEquals(200, response.status)

    // Now query for the event
    response = client.get(path: "events",
        query: [ids: "cors-test-event-id",tags: "cors-test-tag-name|cors-test-tag-value"],
        headers: [(tenantHeaderName): tenantId])

    assertEquals(200, response.status)
    Event e = response.data
    assertEquals(event, e)

    //Send a CORS pre-flight request and make sure it returns no data
    response = client.options(path: "events",
        query: [ids: "cors-test-event-id"],
        headers: [
            (ACCESS_CONTROL_REQUEST_METHOD): "GET",
            (ORIGIN): testOrigin
        ])

    def responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    //Expected a 200 because this is be a pre-flight call that should never reach the resource router
    assertEquals(200, response.status)
    assertEquals(null, response.getData())
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, testOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)

    //Requery "metrics" endpoint to make sure data gets returned and check headers
    response = client.get(path: "events", query: [ids: "cors-test-event-id"],
        headers: [
            (tenantHeaderName): tenantId,
            (ORIGIN): testOrigin
        ])

    responseHeaders = "==== Response Headers = Start  ====\n"
    response.headers.each { responseHeaders += "${it.name} : ${it.value}\n" }
    responseHeaders += "==== Response Headers = End ====\n"

    assertEquals(200, response.status)
    e = response.data
    assertEquals(event, e)
    assertEquals(responseHeaders, DEFAULT_CORS_ACCESS_CONTROL_ALLOW_METHODS, response.headers[ACCESS_CONTROL_ALLOW_METHODS].value)
    assertEquals(responseHeaders, testAccessControlAllowHeaders, response.headers[ACCESS_CONTROL_ALLOW_HEADERS].value)
    assertEquals(responseHeaders, testOrigin, response.headers[ACCESS_CONTROL_ALLOW_ORIGIN].value)
    assertEquals(responseHeaders, "true", response.headers[ACCESS_CONTROL_ALLOW_CREDENTIALS].value)
    assertEquals(responseHeaders, (72 * 60 * 60) + "", response.headers[ACCESS_CONTROL_MAX_AGE].value)
  }
}
