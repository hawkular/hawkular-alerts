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
package org.hawkular.alerts.actions.elasticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;

import org.hawkular.alerts.actions.tests.JvmGarbageCollectionData;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ElasticSearchPluginTest {

    @Test
    public void alertTransformation() throws Exception {
        ElasticSearchPlugin plugin = new ElasticSearchPlugin();
        Alert openAlert = JvmGarbageCollectionData.getOpenAlert();
        Action openAction = new Action(openAlert.getTriggerId(), "elastic-search", "action1", openAlert);
        String spec = "{" +
                "\"tenantId\": \"tenant\"," +
                "\"ctime\":\"timestamp\"," +
                "\"dataId\":\"trigger\"," +
                "\"text\":\"description\"," +
                "\"evalSets\":{" +
                        "\"*\":{" +
                            "\"*\":{" +
                                "\"condition\":{" +
                                    "\"operator\":\"details.[&3][&2].operator\"," +
                                    "\"threshold\":\"details.[&3][&2].threshold\"" +
                                "}," +
                                "\"type\":\"details.[&2][&1].type\"," +
                                "\"value\":\"details.[&2][&1].value\"" +
                            "}" +
                        "}" +
                    "}" +
                "}";
        openAction.getProperties().put("transform", spec);
        openAction.getProperties().put("timestamp.pattern", "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
        Map<String, Object> transformed = JsonUtil.fromJson(plugin.transform(openAction), Map.class);
        assertEquals(5, transformed.size());
        assertNotNull(transformed.get("tenant"));
        assertNotNull(transformed.get("timestamp"));
        assertNotNull(transformed.get("description"));
        assertNotNull(transformed.get("details"));
    }

    /*
        -Djavax.net.ssl.trustStore=/tmp/truststore.jks
        -Djavax.net.ssl.trustStorePassword=password
        -Djavax.net.ssl.keyStore=/tmp/admin-key.jks
        -Djavax.net.ssl.keyStorePassword=password
        -Djavax.net.debug=ssl
     */
    @Ignore
    @Test
    public void writeAlert() throws Exception {
        ElasticSearchPlugin plugin = new ElasticSearchPlugin();
        Alert openAlert = JvmGarbageCollectionData.getOpenAlert();
        Action openAction = new Action(openAlert.getTriggerId(), "elastic-search", "action1", openAlert);
        openAction.setProperties(plugin.getDefaultProperties());
        openAction.getProperties().put("url", "https://logging-es:9200");
        plugin.writeAlert(openAction);
    }
}
