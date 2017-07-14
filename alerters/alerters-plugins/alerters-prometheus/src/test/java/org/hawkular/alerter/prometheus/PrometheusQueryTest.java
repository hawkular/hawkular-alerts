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
package org.hawkular.alerter.prometheus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.hawkular.alerts.api.json.JsonUtil;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class PrometheusQueryTest {

    @Ignore // Requires manually running Prom on localhost:9090
    @Test
    public void queryTest() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        StringBuffer url = new StringBuffer("http://localhost:9090");
        url.append("/api/v1/query?query=");
        url.append("up");

        HttpGet getRequest = new HttpGet(url.toString());
        HttpResponse response = client.execute(getRequest);

        if (response.getStatusLine().getStatusCode() != 200) {
            String msg = String.format("Prometheus GET failed. Status=[%d], message=[%s], url=[%s]", response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(), url.toString());
            System.out.println(msg);
            fail();
        } else {
            try {
                QueryResponse queryResponse = JsonUtil.getMapper().readValue(response.getEntity().getContent(), QueryResponse.class);
                assertEquals("success", queryResponse.getStatus());
                QueryResponse.Data data = queryResponse.getData();
                assertEquals("vector", data.getResultType());
                QueryResponse.Result[] result = data.getResult();
                assertEquals(1, result.length);
                QueryResponse.Result r = result[0];
                assertEquals("up", r.getMetric().get("__name__"));
                assertEquals("prometheus", r.getMetric().get("job"));
                assertEquals(2, r.getValue().length);
                long tsMillis = Double.valueOf((double) r.getValue()[0] * 1000).longValue();
                assertEquals(String.valueOf(r.getValue()[0]), String.valueOf(System.currentTimeMillis()).length(),
                        String.valueOf(tsMillis).length());
                assertEquals("1", r.getValue()[1]);
            } catch (IOException e) {
                String msg = String.format("Failed to Map prom response [%s]: %s", response.getEntity(), e);
                System.out.println(msg);
                fail();
            }
        }
        client.close();
    }

    @Ignore
    @Test
    public void queryEncodedUrl() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        StringBuffer url = new StringBuffer("http://localhost:9090");
        url.append("/api/v1/query?");
        BasicNameValuePair param = new BasicNameValuePair("query", "rate(http_requests_total{handler=\"query\",job=\"prometheus\"}[5m])>0");
        url.append(URLEncodedUtils.format(Arrays.asList(param), "UTF-8"));
        HttpGet getRequest = new HttpGet(url.toString());
        HttpResponse response = client.execute(getRequest);
        assertEquals(200, response.getStatusLine().getStatusCode());
        client.close();
    }

}
