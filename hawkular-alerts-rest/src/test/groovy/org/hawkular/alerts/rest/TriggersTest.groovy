/**
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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

import org.hawkular.alerts.api.model.trigger.Tag
import org.hawkular.alerts.api.model.trigger.Trigger
import org.jboss.logging.Logger
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * Triggers REST tests.
 *
 * @author Lucas Ponce
 */
class TriggersTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(TriggersTest.class);

    @Test
    void findInitialTriggers() {
        def resp = client.get(path: "triggers")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            Trigger t = data[i]
            log.info(t.toString())
        }
    }

    @Test
    void createTrigger() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // remove if it exists
        def resp = client.delete(path: "triggers/test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // Should not be able to create the same triggerId again
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(400, resp.status)

        resp = client.get(path: "triggers/test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("No-Metric", resp.data.name)

        testTrigger.setName("No-Metric-Modified")
        resp = client.put(path: "triggers/test-trigger-1", body: testTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
        assertEquals("No-Metric-Modified", resp.data.name)

        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)

        // Test create w/o assigning a TriggerId, it should generate a UUID
        testTrigger.setId(null);
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)
        assertNotNull(resp.data.id)

        // Delete the trigger
        resp = client.delete(path: "triggers/" + resp.data.id)
        assertEquals(200, resp.status)
    }

    @Test
    void testTag() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // remove if it exists
        def resp = client.delete(path: "triggers/test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        // create the test trigger
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // make sure the test trigger exists
        resp = client.get(path: "triggers/test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("No-Metric", resp.data.name)

        Tag testTag = new Tag("test-trigger-1", "test-category", "test-name", true);
        resp = client.post(path: "triggers/tags", body: testTag)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1/tags", query: [category:"test-category"] );
        assertEquals(200, resp.status)
        assertEquals("test-name", resp.data.iterator().next().name)

        resp = client.get(path: "triggers/test-trigger-1/tags");
        assertEquals(200, resp.status)
        assertEquals("test-name", resp.data.iterator().next().name)

        resp = client.post(path: "triggers/test-trigger-1/tags", query: [category:"test-category"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1/tags");
        assertEquals(204, resp.status)

        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
    }

}
