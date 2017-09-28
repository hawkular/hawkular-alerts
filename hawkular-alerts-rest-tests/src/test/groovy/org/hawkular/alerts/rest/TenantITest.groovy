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

import org.hawkular.alerts.api.model.action.ActionDefinition
import org.hawkular.alerts.api.model.condition.AvailabilityCondition
import org.hawkular.alerts.api.model.condition.Condition
import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.data.Data
import org.hawkular.alerts.api.model.trigger.Mode
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.model.trigger.TriggerAction
import org.hawkular.alerts.rest.AbstractITestBase
import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test

import static org.hawkular.alerts.api.model.event.Alert.Status
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

/**
 * Tenant header validation in REST calls.
 *
 * @author Lucas Ponce
 */
class TenantITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(TenantITest.class)

    @Test
    void findPlugins() {
        client.headers.put("Hawkular-Tenant", null)
        def resp = client.get(path: "plugins")
        assertEquals(400, resp.status)

        client.headers.put("Hawkular-Tenant", "")
        resp = client.get(path: "plugins")
        assertEquals(400, resp.status)
    }

}
