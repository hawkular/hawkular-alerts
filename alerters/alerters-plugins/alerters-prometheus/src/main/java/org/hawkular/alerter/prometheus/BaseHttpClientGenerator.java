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

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ws.WebSocketCall;

/**
 * Can be used to generate HTTP clients including those that require SSL. The Prometheus endpoint itself does not have
 * any security but we may need to navigate through a reverse proxy and so we may need some of this security.  Without
 * a reverse proxy just set useSSL to false.
 *
 * Note that if the configuration's sslContext is null, this object will use the configured keystorePath
 * and keystorePassword to build one itself. If sslContext is provided (not-null) then the configuration's
 * keystorePath and keystorePassword are ignored.
 *
 * Note that if the configuration says to NOT use SSL in the first place, the given SSL context (if any)
 * as well as the configured keystorePath and keystorePassword will all be ignored since
 * we are being told to not use SSL at all.
 */
public class BaseHttpClientGenerator {
    private final MsgLogger log = MsgLogging.getMsgLogger(BaseHttpClientGenerator.class);

    public static class Configuration {
        public static class Builder {
            private boolean useAuthorization;
            private String username;
            private String password;
            private boolean useSSL;
            private String keystorePath;
            private String keystorePassword;
            private SSLContext sslContext;
            private X509TrustManager x509TrustManager;
            private Optional<Integer> connectTimeoutSeconds = Optional.empty();
            private Optional<Integer> readTimeoutSeconds = Optional.empty();

            public Builder() {
            }

            public Configuration build() {
                return new Configuration(useAuthorization, username, password, useSSL, keystorePath, keystorePassword,
                        sslContext, x509TrustManager, connectTimeoutSeconds, readTimeoutSeconds);
            }

            public Builder useAuthorization(boolean b) {
                this.useSSL = b;
                return this;
            }

            public Builder username(String s) {
                this.username = s;
                return this;
            }

            public Builder password(String s) {
                this.password = s;
                return this;
            }

            public Builder useSsl(boolean b) {
                this.useSSL = b;
                return this;
            }

            public Builder keystorePath(String s) {
                this.keystorePath = s;
                return this;
            }

            public Builder keystorePassword(String s) {
                this.keystorePassword = s;
                return this;
            }

            public Builder sslContext(SSLContext s) {
                this.sslContext = s;
                return this;
            }

            public Builder x509TrustManager(X509TrustManager x509TrustManager) {
                this.x509TrustManager = x509TrustManager;
                return this;
            }

            public Builder connectTimeout(int connectTimeoutSeconds) {
                this.connectTimeoutSeconds = Optional.of(connectTimeoutSeconds);
                return this;
            }

            public Builder readTimeout(int readTimeoutSeconds) {
                this.readTimeoutSeconds = Optional.of(readTimeoutSeconds);
                return this;
            }
        }

        private boolean useAuthorization;
        private final String username;
        private final String password;
        private final boolean useSSL;
        private final String keystorePath;
        private final String keystorePassword;
        private final SSLContext sslContext;
        private final X509TrustManager x509TrustManager;
        private final Optional<Integer> connectTimeoutSeconds;
        private final Optional<Integer> readTimeoutSeconds;

        private Configuration(boolean useAuthorization, String username, String password, boolean useSSL,
                String keystorePath,
                String keystorePassword, SSLContext sslContext, X509TrustManager x509TrustManager,
                Optional<Integer> connectTimeoutSeconds,
                Optional<Integer> readTimeoutSeconds) {
            this.useAuthorization = useAuthorization;
            this.username = username;
            this.password = password;
            this.useSSL = useSSL;
            this.keystorePath = keystorePath;
            this.keystorePassword = keystorePassword;
            this.sslContext = sslContext;
            this.x509TrustManager = x509TrustManager;
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public boolean isUseAuthorization() {
            return useAuthorization;
        }

        public void setUseAuthorization(boolean useAuthorization) {
            this.useAuthorization = useAuthorization;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public boolean isUseSSL() {
            return useSSL;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public SSLContext getSslContext() {
            return sslContext;
        }

        public X509TrustManager getX509TrustManager() {
            return x509TrustManager;
        }

        public Optional<Integer> getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public Optional<Integer> getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }
    }

    /** the configuration for our httpclient generator */
    private final Configuration configuration;

    /** The configured client singleton */
    private final OkHttpClient httpClient;

    public BaseHttpClientGenerator(Configuration configuration) {
        this.configuration = configuration;

        OkHttpClient.Builder httpClientBldr = new OkHttpClient.Builder();

        /* set the timeouts explicitly only if they were set through the config */
        configuration.getConnectTimeoutSeconds()
                .ifPresent(timeout -> httpClientBldr.connectTimeout(timeout.intValue(), TimeUnit.SECONDS));
        configuration.getReadTimeoutSeconds()
                .ifPresent(timeout -> httpClientBldr.readTimeout(timeout.intValue(), TimeUnit.SECONDS));

        if (this.configuration.isUseSSL()) {
            SSLContext theSslContextToUse;
            X509TrustManager theTrustManagerToUse = null;

            if (this.configuration.getSslContext() == null) {
                if (this.configuration.getKeystorePath() != null) {
                    KeyStore keystore = loadKeystore(this.configuration.getKeystorePath(),
                            this.configuration.getKeystorePassword());
                    TrustManager[] trustManagers = buildTrustManagers(keystore);
                    theSslContextToUse = buildSSLContext(keystore,
                            this.configuration.getKeystorePassword(), trustManagers);

                    for (TrustManager tm : trustManagers) {
                        if (tm instanceof X509TrustManager) {
                            theTrustManagerToUse = (X509TrustManager) tm;
                            break;
                        }
                    }

                } else {
                    theSslContextToUse = null; // rely on the JVM default
                }
            } else {
                theSslContextToUse = this.configuration.getSslContext();
                theTrustManagerToUse = this.configuration.getX509TrustManager();
            }

            if (theSslContextToUse != null && theTrustManagerToUse != null) {
                httpClientBldr.sslSocketFactory(theSslContextToUse.getSocketFactory(), theTrustManagerToUse);
            } else if (theSslContextToUse != null) {
                log.error("We have a SSLContext without a X509TrustManager.");
            }

            // does not perform any hostname verification when looking at the remote end's cert
            /*
            httpClient.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    log.debugf("HTTP client is blindly approving cert for [%s]", hostname);
                    return true;
                }
            });
            */
        }

        this.httpClient = httpClientBldr.build();
    }

    /**
     * @return the fully configured HTTP client
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Creates a websocket that connects to the given URL.
     *
     * @param url where the websocket server is
     * @param headers headers to pass in the connect request
     * @return the websocket
     */
    public WebSocketCall createWebSocketCall(String url, Map<String, String> headers) {
        String base64Credentials = buildBase64Credentials();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Basic " + base64Credentials)
                .addHeader("Accept", "application/json");

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        }

        Request request = requestBuilder.build();
        WebSocketCall wsc = WebSocketCall.create(getHttpClient(), request);
        return wsc;
    }

    /**
     * @return The configuration used to build the HTTP client.
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * For client requests that need to send a Basic authorization header, this builds the
     * base-64 encoding of the username and password that is needed for that header.
     *
     * @return base-64 encoding of the "username:password" as required for Basic auth.
     */
    public String buildBase64Credentials() {
        // see http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
        return base64Encode(this.configuration.getUsername() + ":" + this.configuration.getPassword());
    }

    private static String base64Encode(String plainTextString) {
        String encoded = new String(Base64.getEncoder().encode(plainTextString.getBytes()));
        return encoded;
    }

    private KeyStore loadKeystore(String keystorePath, String keystorePassword) {
        try {
            return readKeyStore(keystorePath, keystorePassword);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Cannot load keystore [%s]", keystorePath), e);
        }
    }

    private SSLContext buildSSLContext(KeyStore keyStore, String keystorePassword, TrustManager[] trustManagers) {
        try {

            SSLContext sslContext = SSLContext.getInstance("SSL");
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagers,
                    new SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Cannot create SSL context from keystore"), e);
        }
    }

    private TrustManager[] buildTrustManagers(KeyStore keyStore) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            return trustManagerFactory.getTrustManagers();
        } catch (Exception e) {
            throw new RuntimeException("Cannot build TrustManager", e);
        }
    }

    private KeyStore readKeyStore(String keystorePath, String keystorePassword) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        // get user password and file input stream
        char[] password = keystorePassword.toCharArray();
        File file = new File(keystorePath);

        log.infof("Using keystore %s", file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file)) {
            ks.load(fis, password);
        }
        return ks;
    }
}
