/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.junit.Assert.assertEquals

/**
 * Import/Export REST tests.
 *
 * @author Lucas Ponce
 */
class ImportExportITest extends AbstractITestBase {

    static Logger logger = LoggerFactory.getLogger(ImportExportITest.class)

    @Test
    void exportTest() {
        def resp = client.get(path: "export")
        assertEquals(200, resp.status)

        for (int i = 0; i < resp.data.triggers.size(); i++) {
            logger.info("Exported trigger: " + resp.data.triggers[i])
        }
        for (int i = 0; i < resp.data.actions.size(); i++) {
            logger.info("Exported action: " + resp.data.actions[i])
        }

        // Original definitions from alerts-data.json should be in the backend
        assertEquals(9, resp.data.triggers.size())
        assertEquals(6, resp.data.actions.size())
    }

    @Test
    void exportImportTest() {
        def resp = client.get(path: "export")
        assertEquals(200, resp.status)

        def exported = resp.data

        resp = client.post(path: "import/delete", body: exported)
        assertEquals(200, resp.status)

        resp = client.get(path: "export")
        assertEquals(200, resp.status)

        // Original definitions from alerts-data.json should be in the backend
        assertEquals(9, resp.data.triggers.size())
        assertEquals(6, resp.data.actions.size())
    }


}
