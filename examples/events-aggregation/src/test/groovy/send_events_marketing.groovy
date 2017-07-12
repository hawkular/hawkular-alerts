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

def url = "http://" + System.getProperty("host", "localhost:8080") + "/hawkular/alerts/"
def tenant = System.getProperty("tenant", "my-organization")
println "Server: ${url}\nTenant: ${tenant}"

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

def createEvent(text, duration, accountId) {
    def e = [:]
    e.id = uuid()
    e.ctime = now()
    e.dataId = "marketing-source"
    e.category = "TraceCompletion"
    e.text = text
    e.tags = [:]
    e.tags.HawkularExtension = "EventsAggregation"
    e.context = [:]
    e.context.duration = duration
    e.context.accountId = accountId
    return e
}

println("User1 buys 5 times in < 10 seconds")
println("User2 buys 3 times > 10 seconds")

println("...")

println("Time:  [t0]")

println("[user1] E1 - Buy Book")
def e1 = createEvent("E1 - Buy Book", "1000", "user1")

println("[user2] E6 - Buy Book")
def e6 = createEvent("E6 - Buy Book", "1000", "user2")

client.post(path: "events/data", body: Arrays.asList(e1, e6))

sleep(1000)
println("\nTime:  [t0] + 1 seg")

println("[user1] E2 - Buy Music")
def e2 = createEvent("E2 - Buy Music", "2000", "user1")

client.post(path: "events/data", body: Arrays.asList(e2))

sleep(1000)
println("\nTime:  [t0] + 2 seg")

println("[user1] E3 - Buy Groceries")
def e3 = createEvent("E3 - Buy Groceries", "1500", "user1")

client.post(path: "events/data", body: Arrays.asList(e3))

sleep(1000)
println("\nTime:  [t0] + 3 seg")

println("[user1] E4 - Buy VideoGames")
def e4 = createEvent("E4 - Buy VideoGames", "3000", "user1")

client.post(path: "events/data", body: Arrays.asList(e4))

sleep(1000)
println("\nTime:  [t0] + 4 seg")

println("[user1] E5 - Buy VideoGames")
def e5 = createEvent("E5 - Buy VideoGames", "3000", "user1")

client.post(path: "events/data", body: Arrays.asList(e5))

sleep(1000)
println("\nTime:  [t0] + 5 seg")

println("[user2] E7 - Buy Music")
def e7 = createEvent("E7 - Buy Music", "2000", "user2")

client.post(path: "events/data", body: Arrays.asList(e7))

sleep(6000)
println("\nTime:  [t0] + 11 seg")

println("[user2] E8 - Buy Groceries")
def e8 = createEvent("E8 - Buy Groceries", "2000", "user2")

client.post(path: "events/data", body: Arrays.asList(e8))
