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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ElasticsearchQueryTest {

    @Test
    public void checkPropertiesAndMappings() throws Exception {
        Trigger trigger = new Trigger();

        Map<String, String> properties = new HashMap<>();
        properties.put("host", "defaultHost");
        properties.put("port", "defaultPort");

        ElasticsearchQuery esQuery = new ElasticsearchQuery(trigger, properties, null);
        try {
            esQuery.parseProperties();
            fail("Trigger has not mandatory tags/context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("timestamp"));
        }

        trigger.getContext().put("timestamp", "@timestamp");
        try {
            esQuery.parseProperties();
            fail("Trigger has not mandatory tags/context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("mapping"));
        }

        try {
            esQuery.parseMap();
            fail("Trigger has not mandatory context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("mapping"));
        }

        trigger.getContext().put("mapping", "@timestamp:ctime");
        try {
            esQuery.parseProperties();
            esQuery.parseMap();
            fail("EventCondition has not mandatory context properties");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("dataId"));
        }

        trigger.getContext().put("mapping", "@timestamp:ctime,index:dataId");
        esQuery.parseProperties();
        esQuery.parseMap();
    }

    @Test
    public void checkConstantMapping() throws Exception {
        ElasticsearchQuery esQuery = new ElasticsearchQuery(null, null, null);

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

    /*
        -Djavax.net.ssl.trustStore=/tmp/truststore.jks
        -Djavax.net.ssl.trustStorePassword=password
        -Djavax.net.ssl.keyStore=/tmp/admin-key.jks
        -Djavax.net.ssl.keyStorePassword=password
        -Djavax.net.debug=ssl
    */

    @Ignore
    @Test
    public void querySecure() throws Exception {
        System.setProperty("javax.net.ssl.trustStore", "/tmp/truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "password");

        Map<String, String> properties = new HashMap<>();
        properties.put("token", "Q1KcScGJOgJUFadfOrEL4uX56lqnschT4jcsnoqBDFI");
        properties.put("proxy-remote-user", "kibtest");
        properties.put("forwarded-for", "127.0.0.1");

        ElasticsearchQuery query = new ElasticsearchQuery(null, properties, null);
        query.connect("https://logging-es:9200");
        List<Map<String, Object>> results = query.query("[]", ".operations*");
        System.out.println(results.size());
        List<Event> events = query.parseEvents(results);
        System.out.println(events.size());
        query.disconnect();
    }

    @Ignore
    @Test
    public void validateMapping() throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put("mapping", "level:category,@timestamp:ctime,message:text,app:dataId,index:tags");

        ElasticsearchQuery query = new ElasticsearchQuery(null, properties, null);
        query.parseMap();
        query.connect("http://localhost:9200");
        List<Map<String, Object>> results = query.query("[]", "log");
        List<Event> events = query.parseEvents(results);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
        for (int i=0; i<results.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String,Object>)results.get(i).get("_source");
            String timestamp = (String) source.get("@timestamp");
            System.out.println(timestamp);
            System.out.println(sdf.parse(timestamp).getTime());
            Event event = events.get(i);
            System.out.println(event.getCtime());
            System.out.println(event.getContext());
            System.out.println(sdf.format(new Date(event.getCtime())));
            System.out.println("---");
        }
        query.disconnect();
        System.out.println(results);
        System.out.println(events);
    }

   @Ignore
   @Test
   public void directConnectionTest() throws Exception {
        SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        URL url = new URL("https://logging-es:9200/_search");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        conn.setSSLSocketFactory(sslsocketfactory);
        InputStream inputstream = conn.getInputStream();
        InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
        BufferedReader bufferedreader = new BufferedReader(inputstreamreader);

        String string = null;
        while ((string = bufferedreader.readLine()) != null) {
            System.out.println("Received " + string);
        }
   }
}
