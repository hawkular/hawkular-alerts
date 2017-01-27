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
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import net.sf.json.groovy.JsonSlurper

def url = "http://localhost:8080/hawkular/alerts/"
def tenant = "my-organization"

RESTClient client = new RESTClient(url, ContentType.JSON)

client.handler.failure = { resp ->
    def mapper = new JsonSlurper()
    def error = mapper.parseText(resp.entity.content.text)
    println "Status error: ${resp.status} \nerrorMsg: [${error.errorMsg}]"
    return resp
}

/*
    Basic header is used only for hawkular-services distribution.
    This header is ignored on standalone scenarios.

    User: jdoe
    Password: password
    String encodedCredentials = Base64.getMimeEncoder()
    .encodeToString("$testUser:$testPasword".getBytes("utf-8"))
 */
client.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
client.defaultRequestHeaders.put("Hawkular-Tenant", tenant)

def uuid() {
    return UUID.randomUUID().toString()
}

def now() {
    return System.currentTimeMillis()
}

def sleep(long milliseconds) {
    println("Sleeping [" + milliseconds + "] ms");
    Thread.sleep(milliseconds);
}

def tags = [:]
tags.HawkularExtension = "EventsAggregation"

def createEvent(category, text, duration, traceId, accountId) {
    def e = [:]
    e.id = uuid()
    e.ctime = now()
    e.dataId = "loyalty-source"
    e.category = category
    e.text = text
    e.tags = [:]
    e.tags.HawkularExtension = "EventsAggregation"
    e.tags.duration = duration
    e.tags.traceId = traceId
    e.tags.accountId = accountId
    return e
}
println("user1 sends 3 transactions")
println("user2 sends 3 transactions")

println("...")

println("Time:  [t0]")

println("[trace1, user1] E1 - Credit Check - Exceptionally Good")
def e1 = createEvent("Credit Check", "Exceptionally Good", "1000", "trace1", "user1")

println("[trace4, user2] E11 - Credit Check - Exceptionally Good")
def e11 = createEvent("Credit Check", "Exceptionally Good", "1000", "trace4", "user2")

client.post(path: "events/data", body: Arrays.asList(e1, e11))

sleep(1000)
println("\nTime:  [t0] + 1 seg")

println("[trace1, user1] E2 - Stock Check - Out of Stock")
def e2 = createEvent("Stock Check", "Out of Stock", "1000", "trace1", "user1")

println("[trace4, user2] E12 - Stock Check - Out of Stock")
def e12 = createEvent("Stock Check", "Out of Stock", "1000", "trace4", "user2")

client.post(path: "events/data", body: Arrays.asList(e2, e12))

sleep(1000)
println("\nTime:  [t0] + 2 seg")

println("[trace2, user1] E3 - Credit Check - Good")
def e3 = createEvent("Credit Check", "Good", "1500", "trace2", "user1")

println("[trace5, user2] E13 - Credit Check - Good")
def e13 = createEvent("Credit Check", "Good", "1000", "trace5", "user2")

client.post(path: "events/data", body: Arrays.asList(e3, e13))

sleep(1000)
println("\nTime:  [t0] + 3 seg")

println("[trace2, user1] E4 - Stock Check - Out of Stock")
def e4 = createEvent("Stock Check", "Out of Stock", "2000", "trace2", "user1")

println("[trace5, user2] E14 - Stock Check - Out of Stock")
def e14 = createEvent("Stock Check", "Out of Stock", "2000", "trace5", "user2")

client.post(path: "events/data", body: Arrays.asList(e4, e14))

sleep(1000)
println("\nTime:  [t0] + 4 seg")

println("[trace3, user1] E5 - Credit Check - Exceptionally Good")
def e5 = createEvent("Credit Check", "Exceptionally Good", "1500", "trace3", "user1")

println("[trace6, user2] E15 - Credit Check - Exceptionally Good")
def e15 = createEvent("Credit Check", "Exceptionally Good", "2000", "trace6", "user2")

client.post(path: "events/data", body: Arrays.asList(e5, e15))

sleep(1000)
println("\nTime:  [t0] + 5 seg")

println("[trace3, user1] E6 - Stock Check - Exceptionally Good")
def e6 = createEvent("Credit Check", "Available", "1500", "trace3", "user1")

println("[trace6, user2] E16 - Stock Check - Exceptionally Good")
def e16 = createEvent("Credit Check", "Available", "2000", "trace6", "user2")

client.post(path: "events/data", body: Arrays.asList(e6, e16))
