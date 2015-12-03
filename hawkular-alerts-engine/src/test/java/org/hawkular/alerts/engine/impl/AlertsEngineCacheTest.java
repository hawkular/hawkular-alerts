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
package org.hawkular.alerts.engine.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.hawkular.alerts.engine.impl.AlertsEngineCache.DataEntry;
import org.junit.Test;

/**
 * Testing AlertsEngineCache for caching.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertsEngineCacheTest {

    @Test
    public void basicTest() {
        AlertsEngineCache cache = new AlertsEngineCache();

        DataEntry entry1 = new DataEntry("o1", "t1", "c1", "d1");
        DataEntry entry2 = new DataEntry("o1", "t1", "c2", "d2");
        DataEntry entry3 = new DataEntry("o1", "t2", "c1", "d3");
        DataEntry entry4 = new DataEntry("o1", "t4", "c1", "d1");

        cache.add(entry1);
        assertTrue(cache.isDataIdActive("d1"));
        cache.add(entry2);
        assertTrue(cache.isDataIdActive("d2"));
        cache.add(entry3);
        assertTrue(cache.isDataIdActive("d3"));
        cache.add(entry4);

        cache.remove("o1", "t1");
        assertTrue(cache.isDataIdActive("d1"));
        cache.remove("o1", "t4");
        assertFalse(cache.isDataIdActive("d1"));
        cache.remove("o1", "t2");
        assertFalse(cache.isDataIdActive("d3"));
    }

}
