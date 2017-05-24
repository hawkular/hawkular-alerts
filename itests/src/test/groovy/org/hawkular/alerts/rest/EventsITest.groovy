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

import static org.junit.Assert.assertEquals

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
                          query: [endTime:now, startTime:"0",alertIds:"Trigger-01|"+now+","+"Trigger-02|"+now] )
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
}
