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

import org.hawkular.alerts.api.model.condition.StringCondition
import org.jboss.logging.Logger
import org.junit.Test

import static org.hawkular.alerts.api.model.condition.StringCondition.Operator
import static org.junit.Assert.assertEquals

/**
 * Notifier REST tests.
 *
 * @author Lucas Ponce
 */
class NotifiersTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(NotifiersTest.class);

    @Test
    void findInitialNotifiers() {
        def resp = client.get(path: "notifiers")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            log.info(data[i])
        }
    }

    @Test
    void createNotifier() {
        Map<String, String> notifier = new HashMap<>();
        notifier.put("NotifierId", "test-notifier");
        notifier.put("NotifierType", "email");
        notifier.put("prop1", "value1");
        notifier.put("prop2", "value2");
        notifier.put("prop3", "value3");

        def resp = client.post(path: "notifiers", body: notifier)
        assertEquals(200, resp.status)

        resp = client.get(path: "notifiers/" + notifier.get("NotifierId"));
        assertEquals(200, resp.status)
        assertEquals("value1", resp.data.prop1)

        notifier.put("prop3", "value3Modified")
        resp = client.put(path: "notifiers/" + notifier.get("NotifierId"), body: notifier)
        assertEquals(200, resp.status)

        resp = client.get(path: "notifiers/" + notifier.get("NotifierId"))
        assertEquals(200, resp.status)
        assertEquals("value3Modified", resp.data.prop3)

        resp = client.delete(path: "notifiers/" + notifier.get("NotifierId"))
        assertEquals(200, resp.status)
    }

}
