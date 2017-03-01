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
package org.hawkular.alerter.elasticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.alerts.api.model.trigger.Trigger;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ElasticSearchQueryTest {

    @Test
    public void checkPropertiesAndMappings() throws Exception {
        Trigger trigger = new Trigger();

        Map<String, String> properties = new HashMap<>();
        properties.put("host", "defaultHost");
        properties.put("port", "defaultPort");

        ElasticSearchQuery esQuery = new ElasticSearchQuery(trigger, properties, null);
        try {
            esQuery.parseProperties();
            fail("Trigger has not mandatory tags/context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("timestamp"));
        }

        trigger.getTags().put("ElasticSearch", "@timestamp");
        try {
            esQuery.parseProperties();
            fail("Trigger has not mandatory tags/context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("map"));
        }

        try {
            esQuery.parseMap();
            fail("Trigger has not mandatory context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("map"));
        }

        trigger.getContext().put("map", "@timestamp:ctime");
        try {
            esQuery.parseProperties();
            esQuery.parseMap();
            fail("EventCondition has not mandatory context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("dataId"));
        }

        trigger.getContext().put("map", "@timestamp:ctime,index:dataId");
        esQuery.parseProperties();
        esQuery.parseMap();
    }

    @Test
    public void checkConstantMapping() throws Exception {
        ElasticSearchQuery esQuery = new ElasticSearchQuery(null, null, null);

        Map<String, Object> source = new HashMap<>();
        String value = esQuery.getField(source, "test");
        assertTrue(value.isEmpty());

        value = esQuery.getField(source, "'value'");
        assertEquals("value", value);

        value = esQuery.getField(source, "test|'value'");
        assertEquals("value", value);

        source.put("test", "newvalue");
        value = esQuery.getField(source, "test|'value'");
        assertEquals("newvalue", value);

        Map<String, Object> test = new HashMap<>();
        test.put("propA", "valueA");
        test.put("propB", "valueB");
        source.put("test", test);

        value = esQuery.getField(source, "test|'value'");
        assertEquals("value", value);

        value = esQuery.getField(source, "|'value'");
        assertEquals("value", value);

        value = esQuery.getField(source, "test.propA|'value'");
        assertEquals("valueA", value);

        value = esQuery.getField(source, "test.propB|'value'");
        assertEquals("valueB", value);
    }
}
