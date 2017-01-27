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

def createEvent(text, duration, accountId, location) {
    def e = [:]
    e.id = uuid()
    e.ctime = now()
    e.dataId = "fraud-source"
    e.category = "TraceCompletion"
    e.text = text
    e.tags = [:]
    e.tags.HawkularExtension = "EventsAggregation"
    e.tags.duration = duration
    e.tags.accountId = accountId
    e.tags.location = location
    return e
}

println("User1 buys 5 times in < 10 seconds from different locations")
println("User2 buys 3 times > 10 seconds from different locations")
println("User3 buys 5 times in < 10 seconds from single location")

println("...")

println("Time:  [t0]")

println("[user1 from ip1] E1 - Buy Book")
def e1 = createEvent("E1 - Buy Book", "1000", "user1", "ip1")

println("[user2 from ip3] E6 - Buy Book")
def e6 = createEvent("E6 - Buy Book", "1000", "user2", "ip3")

println("[user3 from ip10] E11 - Buy Book")
def e11 = createEvent("E11 - Buy Book", "1000", "user3", "ip10")

client.post(path: "events/data", body: Arrays.asList(e1, e6, e11))

sleep(1000)
println("\nTime:  [t0] + 1 seg")

println("[user1 from ip1] E2 - Buy Music")
def e2 = createEvent("E2 - Buy Music", "2000", "user1", "ip1")

println("[user3 from ip10] E12 - Buy Music")
def e12 = createEvent("E12 - Buy Music", "2000", "user3", "ip10")

client.post(path: "events/data", body: Arrays.asList(e2, e12))

sleep(1000)
println("\nTime:  [t0] + 2 seg")

println("[user1 from ip1] E3 - Buy Groceries")
def e3 = createEvent("E3 - Buy Groceries", "1500", "user1", "ip1")

println("[user3 from ip10] E3 - Buy Groceries")
def e13 = createEvent("E3 - Buy Groceries", "1500", "user3", "ip10")

client.post(path: "events/data", body: Arrays.asList(e3, e13))

sleep(1000)
println("\nTime:  [t0] + 3 seg")

println("[user1 from ip2] E4 - Buy VideoGames")
def e4 = createEvent("E4 - Buy VideoGames", "3000", "user1", "ip2")

println("[user3 from ip10] E14 - Buy VideoGames")
def e14 = createEvent("E14 - Buy VideoGames", "3000", "user3", "ip10")

client.post(path: "events/data", body: Arrays.asList(e4, e14))

sleep(1000)
println("\nTime:  [t0] + 4 seg")

println("[user1 from ip1] E5 - Buy VideoGames")
def e5 = createEvent("E5 - Buy VideoGames", "3000", "user1", "ip1")

println("[user3 from ip10] E15 - Buy VideoGames")
def e15 = createEvent("E15 - Buy VideoGames", "3000", "user3", "ip10")

client.post(path: "events/data", body: Arrays.asList(e5, e15))

sleep(11000)
println("\nTime:  [t0] + 15 seg")

println("[user2 from ip4] E7 - Buy Music")
def e7 = createEvent("E7 - Buy Music", "2000", "user2", "ip4")

client.post(path: "events/data", body: Arrays.asList(e7))

sleep(5000)
println("\nTime:  [t0] + 20 seg")

println("[user2 from ip5] E8 - Buy Groceries")
def e8 = createEvent("E8 - Buy Groceries", "2000", "user2", "ip5")

client.post(path: "events/data", body: Arrays.asList(e8))
