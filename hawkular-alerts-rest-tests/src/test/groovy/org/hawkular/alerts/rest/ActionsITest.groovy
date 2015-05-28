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
import static org.junit.Assert.assertTrue

import org.junit.Test

/**
 * Actions REST tests.
 *
 * @author Lucas Ponce
 */
class ActionsITest extends AbstractITestBase {

    @Test
    void findInitialActions() {
        def resp = client.get(path: "actions")
        def data = resp.data
        assertEquals(200, resp.status)
        assertTrue(data.size() > 0)
        Map map = (Map)data;
        for (String actionPlugin : map.keySet()) {
            println "ActionPlugin: " + actionPlugin + " - Plugins: " + map.get(actionPlugin)
        }
    }

    @Test
    void createAction() {

        String actionPlugin = "email"
        String actionId = "test-action";

        Map<String, String> actionProperties = new HashMap<>();
        actionProperties.put("actionPlugin", actionPlugin);
        actionProperties.put("actionId", actionId);
        actionProperties.put("prop1", "value1");
        actionProperties.put("prop2", "value2");
        actionProperties.put("prop3", "value3");

        def resp = client.post(path: "actions", body: actionProperties)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId);
        assertEquals(200, resp.status)
        assertEquals("value1", resp.data.prop1)

        actionProperties.put("prop3", "value3Modified")
        resp = client.put(path: "actions/" + actionPlugin + "/" + actionId, body: actionProperties)
        assertEquals(200, resp.status)

        resp = client.get(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
        assertEquals("value3Modified", resp.data.prop3)

        resp = client.delete(path: "actions/" + actionPlugin + "/" + actionId)
        assertEquals(200, resp.status)
    }

}
