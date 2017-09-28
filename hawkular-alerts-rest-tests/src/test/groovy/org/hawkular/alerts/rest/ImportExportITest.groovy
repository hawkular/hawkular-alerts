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

import org.hawkular.commons.log.MsgLogger
import org.hawkular.commons.log.MsgLogging
import org.junit.Test


import static org.junit.Assert.assertEquals

/**
 * Import/Export REST tests.
 *
 * @author Lucas Ponce
 */
class ImportExportITest extends AbstractITestBase {

    static MsgLogger logger = MsgLogging.getMsgLogger(ImportExportITest.class)

    @Test
    void importExportTest() {
        String basePath = new File(".").canonicalPath
        String toImport = new File(basePath + "/src/test/wildfly-data/hawkular-alerts/alerts-data.json").text

        def resp = client.post(path: "import/delete", body: toImport)
        assertEquals(200, resp.status)

        resp = client.get(path: "export")
        assertEquals(200, resp.status)

        // Original definitions from alerts-data.json should be in the backend
        assertEquals(9, resp.data.triggers.size())
        assertEquals(1, resp.data.actions.size())
    }

    @Test
    void importGroupTest() {
        String basePath = new File(".").canonicalPath
        String toImport = new File(basePath + "/src/test/wildfly-data/hawkular-alerts/groups-data.json").text

        def resp = client.post(path: "import/delete", body: toImport)
        assertEquals(200, resp.status)

        resp = client.get(path: "export")
        assertEquals(200, resp.status)

        assertEquals(3, resp.data.triggers.size())
    }


}
