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

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.alerts.actions.api.ActionPluginListener;
import org.hawkular.alerts.actions.api.ActionPluginSender;
import org.hawkular.alerts.actions.api.ActionResponseMessage;
import org.hawkular.alerts.actions.api.MsgLogger;
import org.hawkular.alerts.actions.api.Plugin;
import org.hawkular.alerts.actions.api.Sender;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.action.Action;

import com.bazaarvoice.jolt.Shiftr;
/**
 * Action ElasticSearch Plugin.
 *
 * It processes Actions writing Event/Alerts into an ElasticSearch system.
 *
 * It supports optional JOLT Shiftr transformations to process Events/Alerts into custom JSON formats.
 *
 * i.e.  {"tenantId":"tenant", "ctime":"timestamp", "dataId":"dataId","context":"context"}
 *
 * https://github.com/bazaarvoice/jolt/blob/master/jolt-core/src/main/java/com/bazaarvoice/jolt/Shiftr.java
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Plugin(name = "elasticsearch")
public class ElasticSearchPlugin implements ActionPluginListener {
    public static final String PLUGIN_NAME = "elasticsearch";

    /**
     * "url" property is used to indicate the ElasticSearch server or servers to connect.
     */
    public static final String PROP_URL = "url";

    /**
     * "index" property is used to indicate the index where the Events/Alerts will be written.
     */
    public static final String PROP_INDEX = "index";

    /**
     * "type" property is used to define the type under the inxed where the Events/Alerts will be written.
     */
    public static final String PROP_TYPE = "type";

    /**
     * "transform" defines an optional transformation expression based on JOLT Shiftr format to convert an
     * Event/Alert into a custom JSON format.
     */
    public static final String PROP_TRANSFORM = "transform";

    /**
     * "user" property is used as username for Basic credential authentication
     */
    public static final String PROP_USER = "user";

    /**
     * "pass" property is used as password for Basic credentical authentication
     */
    public static final String PROP_PASS = "pass";

    /**
     * "forwarded-for" property is used for X-Forwarded-For HTTP header
     */
    public static final String PROP_FORWARDED_FOR = "forwarded-for";

    /**
     * "proxy-remote-user" property is used for X-Proxy-Remote-User HTTP header
     */
    public static final String PROP_PROXY_REMOTE_USER = "proxy-remote-user";

    /**
     * "token" property is used for Bearer HTTP authentication
     */
    public static final String PROP_TOKEN = "token";

    /**
     * "timestamp.pattern" used on ctime transformations
     */
    private static final String PROP_TIMESTAMP_PATTERN = "timestamp.pattern";

    private static final String ELASTICSEARCH_URL="hawkular-alerts.elasticsearch-url";
    private static final String ELASTICSEARCH_URL_ENV = "ELASTICSEARCH_URL";
    private static final String ELASTICSEARCH_URL_DEFAULT = "http://localhost:9200";

    private static final String ELASTICSEARCH_INDEX = "hawkular-alerts.elasticsearch-index";
    private static final String ELASTICSEARCH_INDEX_ENV = "ELASTICSEARCH_INDEX";
    private static final String ELASTICSEARCH_INDEX_DEFAULT = "alerts";

    private static final String ELASTICSEARCH_TYPE = "hawkular-alerts.elasticsearch-type";
    private static final String ELASTICSEARCH_TYPE_ENV = "ELASTICSEARCH_TYPE";
    private static final String ELASTICSEARCH_TYPE_DEFAULT = "hawkular";

    private static final String ELASTICSEARCH_FORWARDED_FOR = "hawkular-alerts.elasticsearch-forwarded-for";
    private static final String ELASTICSEARCH_FORWARDED_FOR_ENV = "ELASTICSEARCH_FORWARDED_FOR";
    private static final String ELASTICSEARCH_FORWARDED_FOR_DEFAULT = "";

    private static final String ELASTICSEARCH_TOKEN = "hawkular-alerts.elasticsearch-token";
    private static final String ELASTICSEARCH_TOKEN_ENV = "ELASTICSEARCH_TOKEN";
    private static final String ELASTICSEARCH_TOKEN_DEFAULT = "";

    private static final String ELASTICSEARCH_PROXY_REMOTE_USER = "hawkular-alerts.elasticsearch-proxy-remote-user";
    private static final String ELASTICSEARCH_PROXY_REMOTE_USER_ENV = "ELASTICSEARCH_PROXY_REMOTE_USER";
    private static final String ELASTICSEARCH_PROXY_REMOTE_USER_DEFAULT = "";

    /*
        Timestamp fields
     */
    private static final Set<String> TIMESTAMP_FIELDS = new HashSet<>();
    static {
        TIMESTAMP_FIELDS.add("ctime");
        TIMESTAMP_FIELDS.add("stime");
        TIMESTAMP_FIELDS.add("evalTimestamp");
        TIMESTAMP_FIELDS.add("dataTimestamp");
    }

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer";
    private static final String X_FORWARDED = "X-Forwarded-For";
    private static final String X_PROXY_REMOTE_USER = "X-Proxy-Remote-User";

    private final MsgLogger msgLog = MsgLogger.LOGGER;
    Map<String, String> defaultProperties = new HashMap<>();

    @Sender
    ActionPluginSender sender;

    private static final String MESSAGE_PROCESSED = "PROCESSED";
    private static final String MESSAGE_FAILED = "FAILED";

    public ElasticSearchPlugin() {
        defaultProperties.put(PROP_URL, getProperty(ELASTICSEARCH_URL, ELASTICSEARCH_URL_ENV,
                ELASTICSEARCH_URL_DEFAULT));
        defaultProperties.put(PROP_INDEX, getProperty(ELASTICSEARCH_INDEX, ELASTICSEARCH_INDEX_ENV,
                ELASTICSEARCH_INDEX_DEFAULT));
        defaultProperties.put(PROP_TYPE, getProperty(ELASTICSEARCH_TYPE, ELASTICSEARCH_TYPE_ENV,
                ELASTICSEARCH_TYPE_DEFAULT));
        defaultProperties.put(PROP_FORWARDED_FOR, getProperty(ELASTICSEARCH_FORWARDED_FOR,
                ELASTICSEARCH_FORWARDED_FOR_ENV, ELASTICSEARCH_FORWARDED_FOR_DEFAULT));
        defaultProperties.put(PROP_TOKEN, getProperty(ELASTICSEARCH_TOKEN, ELASTICSEARCH_TOKEN_ENV,
                ELASTICSEARCH_TOKEN_DEFAULT));
        defaultProperties.put(PROP_PROXY_REMOTE_USER, getProperty(ELASTICSEARCH_PROXY_REMOTE_USER,
                ELASTICSEARCH_PROXY_REMOTE_USER_ENV, ELASTICSEARCH_PROXY_REMOTE_USER_DEFAULT));
        defaultProperties.put(PROP_TRANSFORM, "");
        defaultProperties.put(PROP_USER, "");
        defaultProperties.put(PROP_PASS, "");
        defaultProperties.put(PROP_TIMESTAMP_PATTERN, "");
    }

    private String getProperty(String property, String env, String defaultValue) {
        String value = System.getProperty(property);
        if (value != null) {
            return value;
        }
        value = System.getenv(env);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    @Override
    public Set<String> getProperties() {
        return defaultProperties.keySet();
    }

    @Override
    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    @Override
    public void process(ActionMessage msg) throws Exception {
        if (msg == null || msg.getAction() == null) {
            msgLog.warnMessageReceivedWithoutPayload(PLUGIN_NAME);
        }
        try {
            writeAlert(msg.getAction());
            msgLog.infoActionReceived(PLUGIN_NAME, msg.toString());
            Action successAction = msg.getAction();
            successAction.setResult(MESSAGE_PROCESSED);
            sendResult(successAction);
        } catch (Exception e) {
            msgLog.errorCannotProcessMessage(PLUGIN_NAME, e.getMessage());
            Action failedAction = msg.getAction();
            failedAction.setResult(MESSAGE_FAILED);
            sendResult(failedAction);
        }
    }

    protected String transform(Action a) {
        String spec = a.getProperties().get(PROP_TRANSFORM);
        if (spec == null || spec.isEmpty()) {
            return JsonUtil.toJson(a.getEvent());
        }
        try {
            Shiftr transformer = new Shiftr(JsonUtil.fromJson(spec, Map.class));
            Map<String, Object> eventMap = JsonUtil.getMap(a.getEvent());
            String timestampPattern = a.getProperties().get(PROP_TIMESTAMP_PATTERN);
            if (!isEmpty(timestampPattern)) {
                transformTimestamp(timestampPattern, eventMap);
            }
            return JsonUtil.toJson(transformer.transform(eventMap));
        } catch (Exception e) {
            msgLog.warnf("Plugin elasticsearch can not apply spec [%s]", spec);
            return JsonUtil.toJson(a.getEvent());
        }
    }

    private void transformTimestamp(String pattern, Object input) {
        if (input == null) {
            return;
        }
        if (input instanceof Map.Entry) {
            Map.Entry<String, Object> entry = (Map.Entry<String, Object>) input;
            if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                transformTimestamp(pattern, entry.getValue());
            } else {
                if (TIMESTAMP_FIELDS.contains(entry.getKey())) {
                    try {
                        Long timestamp = (Long) entry.getValue();
                        entry.setValue(new SimpleDateFormat(pattern).format(new Date(timestamp)));
                    } catch (Exception e) {
                        msgLog.warnf("Cannot parse %s timestamp", entry.getKey());
                    }
                }
            }
        } else if (input instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) input;
            map.entrySet().stream().forEach(e -> transformTimestamp(pattern, e));
        } else if (input instanceof List) {
            List list = (List) input;
            list.stream().forEach(e -> transformTimestamp(pattern, e));
        }
    }

    protected void writeAlert(Action a) throws Exception {
        String url = a.getProperties().get(PROP_URL);
        String index = a.getProperties().get(PROP_INDEX);
        String type = a.getProperties().get(PROP_TYPE);
        String[] urls = url.split(",");
        HttpHost[] hosts = new HttpHost[urls.length];
        for (int i=0; i<urls.length; i++) {
            hosts[i] = HttpHost.create(urls[0]);
        }
        RestClient client = RestClient.builder(hosts)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.useSystemProperties();
                    CredentialsProvider credentialsProvider = checkBasicCredentials(a);
                    if (credentialsProvider != null) {
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    return httpClientBuilder;
                }).build();

        HttpEntity document = new NStringEntity(transform(a), ContentType.APPLICATION_JSON);
        String endpoint = "/" + index + "/" + type;
        Header[] headers = checkHeaders(a);
        Response response = headers == null ? client.performRequest("POST", endpoint, Collections.EMPTY_MAP, document) :
                client.performRequest("POST", endpoint, Collections.EMPTY_MAP, document, headers);
        msgLog.debugf(response.toString());
        client.close();
    }

    private CredentialsProvider checkBasicCredentials(Action a) {
        String user = a.getProperties().get(PROP_USER);
        String password = a.getProperties().get(PROP_PASS);
        if (!isEmpty(user)){
            if (!isEmpty(password)) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
                return credentialsProvider;
            } else {
                msgLog.warnf("User [%s] without password ", user);
            }
        }
        return null;
    }

    private Header[] checkHeaders(Action a) {
        Header[] headers = null;
        int nHeaders = 0;
        String token = a.getProperties().get(PROP_TOKEN);
        Header bearer = null;
        if (!isEmpty(token)) {
            bearer = new BasicHeader(AUTHORIZATION, BEARER + " " + token);
            nHeaders++;
        }
        String forwarded = a.getProperties().get(PROP_FORWARDED_FOR);
        Header xforwarded = null;
        if (!isEmpty(forwarded)) {
            xforwarded = new BasicHeader(X_FORWARDED, forwarded);
            nHeaders++;
        }
        String proxyRemoteUser = a.getProperties().get(PROP_PROXY_REMOTE_USER);
        Header xProxyRemoteUser = null;
        if (!isEmpty(proxyRemoteUser)) {
            xProxyRemoteUser = new BasicHeader(X_PROXY_REMOTE_USER, proxyRemoteUser);
            nHeaders++;
        }
        if (nHeaders > 0) {
            headers = new Header[nHeaders];
            int i = 0;
            if (bearer != null) {
                headers[i] = bearer;
                i++;
            }
            if (xforwarded != null) {
                headers[i] = xforwarded;
                i++;
            }
            if (xProxyRemoteUser != null) {
                headers[i] = xProxyRemoteUser;
            }
        }
        return headers;
    }

    private void sendResult(Action action) {
        if (sender == null) {
            throw new IllegalStateException("ActionPluginSender is not present in the plugin");
        }
        if (action == null) {
            throw new IllegalStateException("Action to update result must be not null");
        }
        ActionResponseMessage newMessage = sender.createMessage(ActionResponseMessage.Operation.RESULT);
        newMessage.getPayload().put("action", JsonUtil.toJson(action));
        try {
            sender.send(newMessage);
        } catch (Exception e) {
            msgLog.error("Error sending ActionResponseMessage", e);
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }
}
