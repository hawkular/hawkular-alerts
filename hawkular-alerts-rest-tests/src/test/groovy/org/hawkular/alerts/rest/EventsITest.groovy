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

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.runners.MethodSorters.NAME_ASCENDING

import org.hawkular.alerts.api.model.event.Event

import org.junit.Test

/**
 * Events REST tests.
 *
 * @author Lucas Ponce
 */
class EventsITest extends AbstractITestBase {

    @Test
    void findEvents() {
        def resp = client.get(path: "events")
        assertEquals(200, resp.status)
    }

    @Test
    void findEventsByCriteria() {
        String now = String.valueOf(System.currentTimeMillis());
        def resp = client.get(path: "events", query: [endTime:now, startTime:"0",triggerIds:"Trigger-01,Trigger-02"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events",
                          query: [endTime:now, startTime:"0",eventIds:"Trigger-01|"+now+","+"Trigger-02|"+now] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [endTime:now, startTime:"0",categories:"ALERT,LOG"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [tags:"tag-01|*,tag-02|*"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [tags:"tag-01|value-01,tag-02|value-02",thin:true] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [tagQuery:"tagA or (tagB and tagC in ['e.*', 'f.*'])"] )
        assertEquals(200, resp.status)
    }

    @Test
    void deleteEvents() {
        String now = String.valueOf(System.currentTimeMillis());

        def resp = client.delete(path: "events/badEventId" )
        assertEquals(404, resp.status)

        resp = client.put(path: "events/delete",
                          query: [endTime:now, startTime:"0",triggerIds:"Trigger-01,Trigger-02"] )
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete",
                          query: [endTime:now, startTime:"0", eventIds:"Trigger-01|"+now+","+"Trigger-02|"+now] )
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete",
                          query: [endTime:now, startTime:"0",categories:"A,B,C"] )
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete", query: [tags:"tag-01|*,tag-02|*"] )
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete", query: [tags:"tag-01|value-01,tag-02|value-02"] )
        assertEquals(200, resp.status)

        resp = client.put(path: "events/delete", query: [tagQuery:"tagA or (tagB and tagC in ['e.*', 'f.*'])"] )
        assertEquals(200, resp.status)
    }

    @Test
    void createEvent() {
        String now = String.valueOf(System.currentTimeMillis());

        Map context = new java.util.HashMap();
        context.put("event-context-name", "event-context-value");
        Map tags = new java.util.HashMap();
        tags.put("event-tag-name", "event-tag-value");
        Event event = new Event("test-tenant", "test-event-id", System.currentTimeMillis(), "test-event-data-id",
                "test-category", "test event text", context, tags);

        client.delete(path: "events/test-event-id" )

        def resp = client.post(path: "events", body: event )
        assertEquals(200, resp.status)
        event = resp.data
        assertEquals("test-event-id", event.getId())

        resp = client.post(path: "events", body: event )
        assertEquals(400, resp.status)

        resp = client.get(path: "events/event/test-event-id" )
        assert resp.status == 200 : resp.status
        Event e = resp.data
        assertEquals(event, e)
        assertEquals("test-category", e.getCategory())
        assertEquals("test event text", e.getText())
        assertEquals(context, e.getContext())
        assertEquals(tags, e.getTags())

        resp = client.get(path: "events", query: [startTime:now,tags:"event-tag-name|event-tag-value"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size)

        e = resp.data[0]

        resp = client.put(path: "events/tags", query: [eventIds:e.id,tags:"tag1name|tag1value"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [startTime:now,tags:"tag1name|tag1value"] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())

        e = resp.data[0]
        assertEquals(2, e.tags.size())
        assertEquals("event-tag-value", e.tags.get("event-tag-name"))
        assertEquals("tag1value", e.tags.get("tag1name"))

        resp = client.delete(path: "events/tags", query: [eventIds:e.id,tagNames:"tag1name"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "events", query: [startTime:now,tags:"tag1name|tag1value"] )
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())

        resp = client.delete(path: "events/test-event-id" )
        assert resp.status == 200 : resp.status

        resp = client.get(path: "events/event/test-event-id" )
        assert resp.status == 404 : resp.status
    }

    @Test
    void sendAndNoPersistEvents() {
        String now = String.valueOf(System.currentTimeMillis());

        Event event = new Event("test-tenant", "test-event-id", System.currentTimeMillis(), "test-event-data-id",
                "test-category", "test event text");
        Collection<Event> events = Arrays.asList(event);

        def resp = client.post(path: "events/data", body: events )
        assertEquals(200, resp.status)

        resp = client.get(path: "events/event/test-event-id" )
        assert resp.status == 404 : resp.status

        resp = client.get(path: "events", query: [startTime:now] )
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size())
    }

    @Test
    void testQueryEventsWithSpacesInValues() {
        Event e1 = new Event();
        e1.setId("test_1");
        e1.setCtime(1499452337498L);
        e1.setCategory("test");
        e1.setText("Avail-changed:[UP] WildFly Server");
        e1.getContext().put("resource_path", "/t;hawkular/f;my-agent/r;Local%20DMR~~");
        e1.getContext().put("message", "Avail-changed:[UP] WildFly Server");
        e1.getTags().put("test_tag", "/t;hawkular/f;my-agent/r;Local%20DMR~~_Server Availability");

        def resp = client.post(path: "events", body: e1)
        assertEquals(200, resp.status)
        def event = resp.data
        assertEquals("test_1", event.id)

        Event e2 = new Event();
        e2.setId("test_2");
        e2.setCtime(1499445265683L);
        e2.setCategory("test");
        e2.setText("Avail-changed:[DOWN] Deployment");
        e2.getContext().put("resource_path", "/t;hawkular/f;my-agent/r;Local%20DMR~%2Fdeployment%3Dcfme_test_ear_middleware.ear");
        e2.getContext().put("message", "Avail-changed:[DOWN] Deployment");
        e2.getTags().put("test_tag", "/t;hawkular/f;my-agent/r;Local%20DMR~%2Fdeployment%3Dcfme_test_ear_middleware.ear_Deployment Status");

        resp = client.post(path: "events", body: e2)
        assertEquals(200, resp.status)
        event = resp.data
        assertEquals("test_2", event.id)

        def tagQuery = "test_tag = '\\/t;hawkular\\/f;my-agent\\/r;Local%20DMR\\~\\~_Server Availability'"

        resp = client.get(path: "events", query: [tagQuery: tagQuery] )
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size())
    }

    // HWKALERTS-275
    @Test
    void testCreateAndQueryEvents() {
        def resp = client.put(path: "events/delete")
        assertEquals(200, resp.status)

        def numEvents = 20000
        for (int i = 0; i < numEvents; i++) {
            Event eventX = new Event()
            eventX.setId("event" + i)
            eventX.setCategory("test")
            eventX.setText("Event message " + i)
            eventX.getTags().put("tag" + (i % 3), "value" + (i % 3))
            eventX.getTags().put("miq.event_type", "type" + (i % 3))
            resp = client.post(path: "events", body: eventX)
            assertEquals(200, resp.status)
        }

        def concurrentQueries = 10

        Thread[] clients = new Thread[concurrentQueries]
        for (int i = 0; i < concurrentQueries; i++) {
            clients[i] = Thread.start {
                def clientX = new RESTClient(baseURI, ContentType.JSON)
                clientX.handler.failure = { it }
                clientX.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
                clientX.headers.put("Hawkular-Tenant", testTenant)
                def tags = "miq.event_type|*"
                def respX = clientX.get(path: "events", query: [tags: tags])
                assertEquals(200, respX.status)
                assertEquals(20000, respX.data.size())
            }
        }

        for (int i = 0; i < clients.size(); i++) {
            clients[i].join()
        }

    }
}
