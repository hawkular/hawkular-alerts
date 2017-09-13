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

def processName = "firefox"

if (System.getProperty("process") != null) {
    processName = System.getProperty("process");
}

while (true) {

    def processStatus = [:]
    processStatus.id = "process-status"
    processStatus.timestamp = System.currentTimeMillis()

    def error = new StringBuffer()
    def output = new StringBuffer()
    def proc = ["sh", "-c", "ps -e | grep ${processName} | wc -l"].execute()
    proc.consumeProcessOutput(output, error)
    proc.waitForOrKill(1000)

    def numProcess = output.toString().toInteger()
    if (numProcess > 0) {
        processStatus.value = "UP"
    } else {
        processStatus.value = "DOWN"
    }

    def data = [processStatus]

    println "Sending data ${data}"

    def resp = client.post(path: "data", body: data)

    if (resp.status != 200) {
        break
    }
    sleep(1000)
}
