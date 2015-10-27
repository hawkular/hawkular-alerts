/*
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
package org.hawkular.alerts.actions.webhook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class WebHooksTest {

    @Before
    public void cleanTestData() throws Exception {
        WebHooks.removeWebHooks();
        WebHooks.releaseFile();
    }

    @Test
    public void basicTest() throws Exception {
        WebHooks.addWebHook("testTenant", null, "test-url");

        assertNotNull(WebHooks.getWebHooks("testTenant"));
        assertEquals(1, WebHooks.getWebHooks("testTenant").size());
        assertEquals("test-url", WebHooks.getWebHooks("testTenant").get(0).get("url"));
    }

    @Test
    public void addAndRemoveTest() throws Exception {
        WebHooks.addWebHook("testTenant", null, "test-url");
        WebHooks.addWebHook("testTenant", null, "test-remove");
        WebHooks.removeWebHook("testTenant", "test-remove");

        assertNotNull(WebHooks.getWebHooks("testTenant"));
        assertEquals(1, WebHooks.getWebHooks("testTenant").size());
        assertEquals("test-url", WebHooks.getWebHooks("testTenant").get(0).get("url"));
    }

    @Test
    public void loadFileTest() throws Exception {
        String testFile = WebHooksTest.class.getClassLoader().getResource("test-webhooks.json").getPath();
        WebHooks.setFile(testFile);
        WebHooks.loadFile();
        assertNotNull(WebHooks.getAllWebHooks());
        assertEquals(2, WebHooks.getAllWebHooks().size());
    }

    @Test
    public void manageFilesTest() throws Exception {
        String testFile = WebHooksTest.class.getClassLoader().getResource("test-webhooks.json").getPath() + "-2";
        WebHooks.setFile(testFile);
        try {
            WebHooks.loadFile();
            throw new Exception("It should fail due this file doesn't exist");
        } catch (Exception e) {
            // Expected
        }
        WebHooks.addWebHook("newTestTenant", "dummyFilter1", "dummy-url1");
        WebHooks.addWebHook("newTestTenant", "dummyFilter2", "dummy-url2");
        WebHooks.loadFile();
        assertEquals(2, WebHooks.getWebHooks("newTestTenant").size());

        WebHooks.addWebHook("newTestTenant", "dummyFilter2-updated", "dummy-url2");
        assertEquals(2, WebHooks.getWebHooks("newTestTenant").size());
    }
}
