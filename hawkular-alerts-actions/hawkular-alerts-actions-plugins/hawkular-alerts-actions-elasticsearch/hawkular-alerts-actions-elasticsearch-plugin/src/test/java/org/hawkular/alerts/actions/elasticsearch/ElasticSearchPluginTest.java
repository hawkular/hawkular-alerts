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

import static org.elasticsearch.node.NodeBuilder.nodeBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.hawkular.alerts.actions.tests.JvmGarbageCollectionData;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Alert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ElasticSearchPluginTest {

    private static Node node;
    private static String path;

    private static boolean cleanDirectory(File dir) {
        if (dir.isDirectory()) {
            Arrays.stream(dir.listFiles()).forEach(f -> cleanDirectory(f));
        }
        return dir.delete();
    }

    @BeforeClass
    public static void init() {
        path = "target/hawkular-es";
        cleanDirectory(new File(path));
        Settings settings = Settings.builder()
                .put("path.home", path)
                .put("path.conf", path)
                .put("path.data", path)
                .put("path.work", path)
                .put("path.logs", path)
                .put("http.port", 9200)
                .put("transport.tcp.port", 9300)
                .build();
        node = nodeBuilder().settings(settings).client(false).local(false).node();
        node.start();
    }

    @AfterClass
    public static void shutdown() {
        node.close();
    }

    public static long checkResults(Map<String, String> properties) throws Exception {
        String host = properties.get("host");
        String port = properties.get("port");
        String index = properties.get("index");
        String query = "{\"query\":{\"constant_score\":{\"filter\":{\"bool\":{\"must\":[]}}}}}";

        TransportClient client = TransportClient.builder().build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), new Integer(port)));
        SearchResponse response = client.prepareSearch(index).setQuery(query).get();
        System.out.println(response);
        return response.getHits().getTotalHits();
    }

    @Test
    public void writeOnElasticSearch() throws Exception {
        ElasticSearchPlugin plugin = new ElasticSearchPlugin();
        Alert openAlert = JvmGarbageCollectionData.getOpenAlert();
        Action openAction = new Action(openAlert.getTriggerId(), "elastic-search", "action1", openAlert);
        openAction.getProperties().putAll(plugin.getDefaultProperties());
        plugin.writeAlert(openAction);
        // ES needs some time to process
        Thread.sleep(1000);
        assertEquals(1, checkResults(openAction.getProperties()));
    }

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
                "\"evalSets\":\"details\"" +
                "}";
        openAction.getProperties().put("transform", spec);
        Map<String, Object> transformed = JsonUtil.fromJson(plugin.transform(openAction), Map.class);
        assertEquals(5, transformed.size());
        assertNotNull(transformed.get("tenant"));
        assertNotNull(transformed.get("timestamp"));
        assertNotNull(transformed.get("description"));
        assertNotNull(transformed.get("details"));
    }
}
