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

import org.hawkular.alerts.api.model.dampening.Dampening
import org.jboss.logging.Logger
import org.junit.Test

import org.hawkular.alerts.api.model.dampening.Dampening.Type
import org.hawkular.alerts.api.model.trigger.Trigger.Mode
import static org.junit.Assert.assertEquals

/**
 * Dampening REST tests.
 *
 * @author Lucas Ponce
 */
class DampeningTest extends AbstractTestBase {
    private static final Logger log = Logger.getLogger(DampeningTest.class);

    @Test
    void findInitialDampenings() {
        def resp = client.get(path: "trigger/dampening")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            Dampening d = data[i]
            log.info(d.toString())
        }
    }

    @Test
    void createDampening() {
        Dampening d = new Dampening("test-trigger-6", Mode.FIRE, Type.RELAXED_COUNT, 1, 1, 1);

        def resp = client.post(path: "trigger/dampening", body: d)
        assertEquals(200, resp.status)

        resp = client.get(path: "trigger/dampening/" + d.getTriggerId());
        assertEquals(200, resp.status)
        assertEquals("RELAXED_COUNT", resp.data.type)

        d.setType(Type.STRICT)
        resp = client.put(path: "trigger/dampening/" + d.getTriggerId(), body: d)
        assertEquals(200, resp.status)

        resp = client.get(path: "trigger/dampening/" + d.getTriggerId())
        assertEquals(200, resp.status)
        assertEquals("STRICT", resp.data.type)

        resp = client.delete(path: "trigger/dampening/" + d.getTriggerId())
        assertEquals(200, resp.status)
    }

}
