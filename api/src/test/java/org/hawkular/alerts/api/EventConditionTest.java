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
package org.hawkular.alerts.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hawkular.alerts.api.model.condition.EventCondition;
import org.hawkular.alerts.api.model.event.Event;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class EventConditionTest {

    // TODO: tenant expressions may need to be removed because I think the event and condition will always be in the
    // same tenant.
    @Test
    public void testTenantIdExpression() {

        EventCondition condition = new EventCondition("tenant", "trigger-1", "app.war",
                "tenantId == 'my-organization'");
        Event event1 = new Event();
        event1.setTenantId("my-organization");

        assertTrue(condition.match(event1));

        Event event2 = new Event();
        event2.setTenantId("my-organization2");

        assertFalse(condition.match(event2));

        condition.setExpression("tenantId starts 'my-organiz'");

        assertTrue(condition.match(event1));
        assertTrue(condition.match(event2));

        condition.setExpression("tenantId ends '2'");

        assertFalse(condition.match(event1));
        assertTrue(condition.match(event2));

        condition.setExpression("tenantId contains 'organization'");

        assertTrue(condition.match(event1));
        assertTrue(condition.match(event2));

        condition.setExpression("tenantId matches 'my-organization.*'");

        assertTrue(condition.match(event1));
        assertTrue(condition.match(event2));
    }

    @Test
    public void testCategoryExpressionWithSpaces() {

        EventCondition condition = new EventCondition("tenant", "trigger-1", "app.war", "category == 'my category'");
        Event event1 = new Event();
        event1.setCategory("my category");

        assertTrue(condition.match(event1));

        Event event2 = new Event();
        event2.setCategory("my category 2");

        assertFalse(condition.match(event2));

        condition.setExpression("category starts 'my category '");

        assertTrue(condition.match(event2));

        condition.setExpression("category ends '2'");

        assertTrue(condition.match(event2));
    }

    @Test
    public void testCtimeExpression() {
        EventCondition condition = new EventCondition("tenant", "trigger-1", "app.war", "ctime > 10");

        Event event1 = new Event();
        event1.setCtime(11);

        assertTrue(condition.match(event1));

        Event event2 = new Event();
        event2.setCtime(9);

        assertFalse(condition.match(event2));

        condition.setExpression("ctime == 10");

        Event event3 = new Event();
        event3.setCtime(10);

        assertTrue(condition.match(event3));

        condition.setExpression("ctime != 10");

        assertFalse(condition.match(event3));
    }

    @Test
    public void testTagExpression() {
        EventCondition condition = new EventCondition("tenant", "trigger-1", "app.war", "tags.server == 'MyServer'");

        Event event1 = new Event();
        event1.addTag("server", "MyServer");

        assertTrue(condition.match(event1));

        condition.setExpression("tags.server != 'MyServer'");

        assertFalse(condition.match(event1));

        condition.setExpression("tags.quantity >= 11");

        event1.addTag("quantity", "11");

        assertTrue(condition.match(event1));

        event1.addTag("quantity", "12");

        assertTrue(condition.match(event1));

        event1.addTag("quantity", "10");

        assertFalse(condition.match(event1));

        condition.setExpression("tags.log.category starts 'WARN'");

        event1.addTag("log.category", "WARNING");

        assertTrue(condition.match(event1));
    }

    @Test
    public void testEscapedExpression() {
        EventCondition condition = new EventCondition("tenant", "trigger-1", "app.war",
                "tags.server == '\\,MyServer', tags.log starts 'LOG\\,'");

        Event event1 = new Event();
        event1.addTag("server", ",MyServer");
        event1.addTag("log", "LOG,123-123-123 This is a text log");

        assertTrue(condition.match(event1));
    }

    @Test
    public void testComposeExpression() {
        EventCondition condition = new EventCondition("tenant", "trigger-1", "bpm",
                "tags.url == '/foo/bar', tags.method == 'GET', tags.threshold <= 100");
        Event bpmEvent1 = new Event();
        bpmEvent1.setDataId("bpm");
        bpmEvent1.addTag("url", "/foo/bar");
        bpmEvent1.addTag("method", "GET");
        bpmEvent1.addTag("threshold", "45");

        assertTrue(condition.match(bpmEvent1));

        Event bpmEvent2 = new Event();
        bpmEvent2.setDataId("bpm");
        bpmEvent2.addTag("url", "/foo/bar");
        bpmEvent2.addTag("method", "GET");
        bpmEvent2.addTag("threshold", "101");

        assertFalse(condition.match(bpmEvent2));
    }

}
