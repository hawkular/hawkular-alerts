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

import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;

/**
 * Builds an HTTP client that can be used to talk to Prometheus.  The Prometheus endpoint itself does not have
 * any security but we may need to navigate through a reverse proxy and so we may need some of this security.  Without
 * a reverse proxy just set useAuthorization and useSSL to false. This builder has methods that you can use to build
 * requests.
 */
public class HttpClientBuilder extends BaseHttpClientGenerator {
    /**
     * Creates the object that can be used to create a fully configured HTTP client.
     * Note that if sslContext is null, this object will use the configured keystorePath
     * and keystorePassword to build one itself. If sslContext is provided (not-null)
     * then the configuration's keystorePath and keystorePassword are ignored.
     *
     * Note that if the configuration tells use to NOT use SSL in the first place,
     * the given SSL context (if any) as well as the configured keystorePath and keystorePassword
     * will all be ignored since we are being told to not use SSL.
     *
     * @param configuration configuration settings to determine things about the HTTP connections
     *                      any built clients will be asked to make
     * @param sslContext if not null, and if the configuration tells use to use SSL, this will
     *                   be the SSL context to use.
     */
    public HttpClientBuilder(boolean useAuthorization, String username, String password, boolean useSSL,
            SSLContext sslContext, X509TrustManager x509TrustManager, String keystorePath, String keystorePassword,
            int connectTimeoutSeconds, int readTimeoutSeconds) {
        super(new BaseHttpClientGenerator.Configuration.Builder()
                .useAuthorization(useAuthorization)
                .username(username)
                .password(password)
                .useSsl(useSSL)
                .sslContext(sslContext)
                .x509TrustManager(x509TrustManager)
                .keystorePath(keystorePath)
                .keystorePassword(keystorePassword)
                .connectTimeout(connectTimeoutSeconds)
                .readTimeout(readTimeoutSeconds)
                .build());
    }

    public Request buildJsonGetRequest(String url, Map<String, String> headers) {
        Builder requestBuilder = buildJsonBaseRequest(url, headers);

        return requestBuilder.get().build();
    }

    public Request buildJsonDeleteRequest(String url, Map<String, String> headers) {
        Builder requestBuilder = buildJsonBaseRequest(url, headers);

        return requestBuilder.delete().build();
    }

    public Request buildJsonPostRequest(String url, Map<String, String> headers, String jsonPayload) {
        Builder requestBuilder = buildJsonBaseRequest(url, headers);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonPayload);

        return requestBuilder.post(body).build();
    }

    public Request buildJsonPutRequest(String url, Map<String, String> headers, String jsonPayload) {
        Builder requestBuilder = buildJsonBaseRequest(url, headers);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonPayload);

        return requestBuilder.put(body).build();
    }

    private Builder buildJsonBaseRequest(String url, Map<String, String> headers) {
        Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json");

        if (this.getConfiguration().isUseAuthorization()) {
            // make sure we are authenticated. see http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Credentials = buildBase64Credentials();
            requestBuilder.addHeader("Authorization", "Basic " + base64Credentials);
        }

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        return requestBuilder;
    }

}
