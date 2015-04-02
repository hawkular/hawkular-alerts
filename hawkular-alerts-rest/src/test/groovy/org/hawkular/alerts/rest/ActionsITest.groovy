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

import static org.junit.Assert.assertEquals

import org.jboss.logging.Logger
import org.junit.Test

/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class ActionsITest extends AbstractITestBase {
    private static final Logger log = Logger.getLogger(ActionsITest.class);

    @Test
    void findInitialActions() {
        def resp = client.get(path: "actions")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            log.info(data[i])
        }
    }

    @Test
    void createAction() {
        Map<String, String> action = new HashMap<>();
        action.put("actionId", "test-action");
        action.put("actionPlugin", "email");
        action.put("prop1", "value1");
        action.put("prop2", "value2");
        action.put("prop3", "value3");

        def resp = client.post(path: "actions", body: action)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + action.get("actionId"));
        assertEquals(200, resp.status)
        assertEquals("value1", resp.data.prop1)

        action.put("prop3", "value3Modified")
        resp = client.put(path: "actions/" + action.get("actionId"), body: action)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + action.get("actionId"))
        assertEquals(200, resp.status)
        assertEquals("value3Modified", resp.data.prop3)

        resp = client.delete(path: "actions/" + action.get("actionId"))
        assertEquals(200, resp.status)
    }

}
