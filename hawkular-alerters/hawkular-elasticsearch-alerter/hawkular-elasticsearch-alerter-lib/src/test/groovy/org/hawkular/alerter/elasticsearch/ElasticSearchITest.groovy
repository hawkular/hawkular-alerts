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
package org.hawkular.alerter.elasticsearch

import org.hawkular.alerts.api.model.condition.EventCondition
import org.hawkular.alerts.api.model.trigger.FullTrigger
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger

import java.text.SimpleDateFormat

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.junit.Ignore
import org.junit.Test

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
class ElasticSearchITest {

    def elasticsearch, hawkular, format, definitions, response

    ElasticSearchITest() {
        elasticsearch = new RESTClient(System.getProperty('elasticsearch.base-uri') ?: 'http://127.0.0.1:9200/', ContentType.JSON)
        elasticsearch.handler.failure = { it }
        hawkular = new RESTClient(System.getProperty('hawkular.base-uri') ?: 'http://127.0.0.1:8080/hawkular/alerts/', ContentType.JSON)
        hawkular.handler.failure = { it }
        /*
            Basic header is used only for hawkular-services distribution.
            This header is ignored on standalone scenarios.

            User: jdoe
            Password: password
            String encodedCredentials = Base64.getMimeEncoder()
                .encodeToString("$testUser:$testPasword".getBytes("utf-8"))
        */
        hawkular.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
        hawkular.headers.put("Hawkular-Tenant", "my-organization")
        format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ")
        definitions = new File("src/test/resources/elasticsearch-trigger.json").text
    }

    /*
        These tests are ignored as they need an external ElasticSearch instance.
        Used for manual testing
     */

    @Ignore
    @Test
    void hawkularCreateTestDefinitions() {
        response = hawkular.post(path: "import/all", body: definitions)
        assertTrue(response.status < 300)
    }

    @Ignore
    @Test
    void elasticsearchDeleteTestIndexes() {
        elasticsearch.delete("alerts_full")
        elasticsearch.delete("alerts_summary")
    }

    @Ignore
    @Test
    void elasticsearchSendOperationsLog() {
        def index = ".operations.2017.02.21"
        def type = "com.redhat.viaq.common"
        def operations = "{\n" +
                "          \"@timestamp\": \"${format.format(new Date())}\",\n" +
                "          \"UNKNOWN1\": \"1\",\n" +
                "          \"UNKNOWN2\": \"2\",\n" +
                "          \"hostname\": \"localhost\",\n" +
                "          \"message\": \"3240243d-9065-4ec2-983b-c0dd90d5d72c-05 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001\",\n" +
                "          \"pipeline_metadata\": {\n" +
                "            \"collector\": {\n" +
                "              \"inputname\": \"fluent-plugin-systemd\",\n" +
                "              \"name\": \"fluentd openshift\",\n" +
                "              \"received_at\": \"2017-02-21T16:26:28.348924+00:00\",\n" +
                "              \"version\": \"0.12.29 1.4.0\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"systemd\": {\n" +
                "            \"t\": {\n" +
                "              \"BOOT_ID\": \"0937011437e44850b3cb5a615345b50f\",\n" +
                "              \"COMM\": \"3240243d-9065-4ec2-983b-c0dd90d5d72c\",\n" +
                "              \"GID\": \"1000\",\n" +
                "              \"HOSTNAME\": \"localhost\",\n" +
                "              \"PID\": \"19698\",\n" +
                "              \"SOURCE_REALTIME_TIMESTAMP\": \"1487694388348924\",\n" +
                "              \"TRANSPORT\": \"stderr\",\n" +
                "              \"UID\": \"1000\"\n" +
                "            },\n" +
                "            \"u\": {\n" +
                "              \"SYSLOG_FACILITY\": \"1\",\n" +
                "              \"SYSLOG_IDENTIFIER\": \"3240243d-9065-4ec2-983b-c0dd90d5d72c\"\n" +
                "            }\n" +
                "          }\n" +
                "        }"
        println operations
        response = elasticsearch.post(path: "${index}/${type}", body: operations)
        assertTrue(response.status < 300)
    }

    @Ignore
    @Test
    void elasticsearchSendErrorProjectLog() {
        def index = "project.this-is-project-01.namespaceid.2017.02.21"
        def type = "com.redhat.viaq.common"
        def level = "ERROR"
        def project = "{\n" +
                "          \"@timestamp\": \"${format.format(new Date())}\",\n" +
                "          \"UNKNOWN1\": \"1\",\n" +
                "          \"UNKNOWN2\": \"2\",\n" +
                "          \"hostname\": \"localhost\",\n" +
                "          \"kubernetes\": {\n" +
                "            \"container_id\": \"4355a46b19d3\",\n" +
                "            \"container_name\": \"this-is-container-01\",\n" +
                "            \"host\": \"localhost\",\n" +
                "            \"labels\": \"openshift_io:this is my label\",\n" +
                "            \"namespace_id\": \"namespaceid\",\n" +
                "            \"namespace_name\": \"this-is-project-01\",\n" +
                "            \"pod_id\": \"870560fe-c22c-4f06-8ee4-c55399d3ed2f\",\n" +
                "            \"pod_name\": \"this-is-pod-01\"\n" +
                "          },\n" +
                "          \"level\": \"${level}\",\n" +
                "          \"message\": \"3240243d-9065-4ec2-983b-c0dd90d5d72c-05 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001\",\n" +
                "          \"pipeline_metadata\": {\n" +
                "            \"collector\": {\n" +
                "              \"inputname\": \"fluent-plugin-systemd\",\n" +
                "              \"name\": \"fluentd openshift\",\n" +
                "              \"received_at\": \"2017-02-21T16:26:28.356504+00:00\",\n" +
                "              \"version\": \"0.12.29 1.4.0\"\n" +
                "            }\n" +
                "          }\n" +
                "        }"
        println project
        response = elasticsearch.post(path: "${index}/${type}", body: project)
        assertTrue(response.status < 300)
    }

    @Ignore
    @Test
    void elasticsearchSendWarnProjectLog() {
        def index = "project.this-is-project-01.namespaceid.2017.02.21"
        def type = "com.redhat.viaq.common"
        def level = "WARN"
        def project = "{\n" +
                "          \"@timestamp\": \"${format.format(new Date())}\",\n" +
                "          \"UNKNOWN1\": \"1\",\n" +
                "          \"UNKNOWN2\": \"2\",\n" +
                "          \"hostname\": \"localhost\",\n" +
                "          \"kubernetes\": {\n" +
                "            \"container_id\": \"4355a46b19d3\",\n" +
                "            \"container_name\": \"this-is-container-01\",\n" +
                "            \"host\": \"localhost\",\n" +
                "            \"labels\": \"openshift_io:this is my label\",\n" +
                "            \"namespace_id\": \"namespaceid\",\n" +
                "            \"namespace_name\": \"this-is-project-01\",\n" +
                "            \"pod_id\": \"870560fe-c22c-4f06-8ee4-c55399d3ed2f\",\n" +
                "            \"pod_name\": \"this-is-pod-01\"\n" +
                "          },\n" +
                "          \"level\": \"${level}\",\n" +
                "          \"message\": \"3240243d-9065-4ec2-983b-c0dd90d5d72c-05 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001\",\n" +
                "          \"pipeline_metadata\": {\n" +
                "            \"collector\": {\n" +
                "              \"inputname\": \"fluent-plugin-systemd\",\n" +
                "              \"name\": \"fluentd openshift\",\n" +
                "              \"received_at\": \"2017-02-21T16:26:28.356504+00:00\",\n" +
                "              \"version\": \"0.12.29 1.4.0\"\n" +
                "            }\n" +
                "          }\n" +
                "        }"
        println project
        response = elasticsearch.post(path: "${index}/${type}", body: project)
        assertTrue(response.status < 300)
    }

    @Ignore
    @Test
    void elasticsearchSendInfoProjectLog() {
        def index = "project.this-is-project-01.namespaceid.2017.02.21"
        def type = "com.redhat.viaq.common"
        def level = "INFO"
        def project = "{\n" +
                "          \"@timestamp\": \"${format.format(new Date())}\",\n" +
                "          \"UNKNOWN1\": \"1\",\n" +
                "          \"UNKNOWN2\": \"2\",\n" +
                "          \"hostname\": \"localhost\",\n" +
                "          \"kubernetes\": {\n" +
                "            \"container_id\": \"4355a46b19d3\",\n" +
                "            \"container_name\": \"this-is-container-01\",\n" +
                "            \"host\": \"localhost\",\n" +
                "            \"labels\": \"openshift_io:this is my label\",\n" +
                "            \"namespace_id\": \"namespaceid\",\n" +
                "            \"namespace_name\": \"this-is-project-01\",\n" +
                "            \"pod_id\": \"870560fe-c22c-4f06-8ee4-c55399d3ed2f\",\n" +
                "            \"pod_name\": \"this-is-pod-01\"\n" +
                "          },\n" +
                "          \"level\": \"${level}\",\n" +
                "          \"message\": \"3240243d-9065-4ec2-983b-c0dd90d5d72c-05 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001\",\n" +
                "          \"pipeline_metadata\": {\n" +
                "            \"collector\": {\n" +
                "              \"inputname\": \"fluent-plugin-systemd\",\n" +
                "              \"name\": \"fluentd openshift\",\n" +
                "              \"received_at\": \"2017-02-21T16:26:28.356504+00:00\",\n" +
                "              \"version\": \"0.12.29 1.4.0\"\n" +
                "            }\n" +
                "          }\n" +
                "        }"
        println project
        response = elasticsearch.post(path: "${index}/${type}", body: project)
        assertTrue(response.status < 300)
    }

}
