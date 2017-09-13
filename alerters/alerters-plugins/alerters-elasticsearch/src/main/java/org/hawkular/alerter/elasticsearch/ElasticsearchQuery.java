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

import static java.util.Collections.EMPTY_LIST;

import static org.hawkular.alerter.elasticsearch.ElasticsearchAlerter.getIntervalUnit;
import static org.hawkular.alerter.elasticsearch.ElasticsearchAlerter.getIntervalValue;
import static org.hawkular.alerts.api.model.event.EventField.DATAID;
import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.event.EventField;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * This class performs a query into Elasticsearch system and parses results documents based on Trigger tags/context.
 * @see {@link ElasticsearchAlerter}
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ElasticsearchQuery implements Runnable {
    private static final MsgLogger log = MsgLogging.getMsgLogger(ElasticsearchQuery.class);

    private static final int SIZE_DEFAULT = 10;

    private static final String _ID = "_id";
    private static final String _INDEX = "_index";
    private static final String _SOURCE = "_source";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER = "Bearer";
    private static final String ERROR = "error";
    private static final String FILTER = "filter";
    private static final String FORWARDED_FOR = "forwarded-for";
    private static final String GET = "GET";
    private static final String HITS = "hits";
    private static final String ID = "id";
    private static final String INDEX = "index";
    private static final String INDEX_NOT_FOUND = "index_not_found_exception";
    private static final String INTERVAL = "interval";
    private static final String INTERVAL_DEFAULT = "2m";
    private static final String MAPPING = "mapping";
    private static final String PASS = "pass";
    private static final String PREFERENCE = "preference";
    private static final String PROXY_REMOTE_USER = "proxy-remote-user";
    private static final String USER = "user";
    private static final String TIMESTAMP = "timestamp";
    private static final String TIMESTAMP_PATTERN = "timestamp_pattern";
    private static final String TOKEN = "token";
    private static final String TOTAL = "total";
    private static final String TYPE = "type";
    private static final String RESOURCE_ID = "resource.id";
    private static final String SOURCE = "source";
    private static final String URL = "url";
    private static final String X_FORWARDED = "X-Forwarded-For";
    private static final String X_PROXY_REMOTE_USER = "X-Proxy-Remote-User";

    private static final DateTimeFormatter[] DEFAULT_DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
    };
    private static final ZoneId UTC = ZoneId.of("UTC");

    private Trigger trigger;
    private Map<String, String> properties;
    private AlertsService alerts;

    private Map<String, EventField> mappings = new HashMap<>();

    private RestClient client;

    private Header[] headers = null;

    public ElasticsearchQuery(Trigger trigger, Map<String, String> properties, AlertsService alerts) {
        this.trigger = trigger;
        this.properties = properties == null ? new HashMap<>() : new HashMap<>(properties);
        this.alerts = alerts;
    }

    public void parseProperties() throws Exception {
        if (trigger == null) {
            throw new IllegalStateException("trigger must be not null");
        }
        checkContext(TIMESTAMP, true);
        checkContext(MAPPING, true);
        checkContext(INTERVAL, INTERVAL_DEFAULT);
        checkContext(URL, false);
        checkContext(INDEX, false);
        checkContext(FILTER, false);
        checkContext(TIMESTAMP_PATTERN, false);
        checkContext(USER, false);
        checkContext(PASS, false);
        checkContext(TOKEN, false);
    }

    public void parseMap() throws Exception {
        String rawMap = properties.get(MAPPING);
        if (rawMap == null) {
            throw new IllegalStateException("mapping must be not null");
        }
        String[] rawMappings = rawMap.split(",");
        for (String rawMapping : rawMappings) {
            String[] fields = rawMapping.trim().split(":");
            if (fields.length == 2) {
                EventField eventField = EventField.fromString(fields[1].trim());
                if (eventField == null) {
                    log.warnf("Skipping invalid mapping [%s]", rawMapping);
                } else {
                    mappings.put(fields[0].trim(), eventField);
                }
            } else {
                log.warnf("Skipping invalid mapping [%s]", rawMapping);
            }
        }
        if (!mappings.values().contains(DATAID)) {
            throw new IllegalStateException("Mapping [" + rawMap + "] does not include dataId");
        }
    }

    private void checkContext(String property, boolean required) {
        checkMap(property, required, null);
    }

    private void checkContext(String property, String defaultValue) {
        checkMap(property, true, defaultValue);
    }

    private void checkMap(String property, boolean required, String defaultValue) {
        Map<String, String> map = trigger.getContext();
        if (map.containsKey(property)) {
            properties.put(property, map.get(property));
        } else {
            if (required && defaultValue == null) {
                throw new IllegalStateException(property + " is not present and it has not a default value");
            }
            if (defaultValue != null) {
                properties.put(property, defaultValue);
            }
        }
    }

    public void connect(String url) throws Exception {
        String[] urls = url.split(",");
        HttpHost[] hosts = new HttpHost[urls.length];
        for (int i=0; i<urls.length; i++) {
            hosts[i] = HttpHost.create(urls[0]);
        }
        client = RestClient.builder(hosts)
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.useSystemProperties();
                    CredentialsProvider credentialsProvider = checkBasicCredentials();
                    if (credentialsProvider != null) {
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    return httpClientBuilder;
                })
                .build();
        int nHeaders = 0;
        String token = properties.get(TOKEN);
        Header bearer = null;
        if (!isEmpty(token)) {
            bearer = new BasicHeader(AUTHORIZATION, BEARER + " " + token);
            nHeaders++;
        }
        String forwarded = properties.get(FORWARDED_FOR);
        Header xforwarded = null;
        if (!isEmpty(forwarded)) {
            xforwarded = new BasicHeader(X_FORWARDED, forwarded);
            nHeaders++;
        }
        String proxyRemoteUser = properties.get(PROXY_REMOTE_USER);
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
    }

    private CredentialsProvider checkBasicCredentials() {
        String user = properties.get(USER);
        String password = properties.get(PASS);
        if (!isEmpty(user)){
            if (!isEmpty(password)) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password));
                return credentialsProvider;
            } else {
                log.warnf("User [%s] without password ", user);
            }
        }
        return null;
    }

    public List<Map<String, Object>> query(String filter, String indices) throws Exception {
        if (filter == null || filter.isEmpty()) {
            throw new IllegalArgumentException("filter must be not null");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        String json = rawQuery(filter);

        List<String> index = indices == null ? EMPTY_LIST : new ArrayList<>(Arrays.asList(indices.split(",")));
        Response response = null;
        //  Using a loop in case results are greater than default results size
        Map<String, String> params = new HashMap<>();
        params.put(PREFERENCE, UUID.randomUUID().toString());
        String jsonQuery = "{" +
                "\"from\":0," +
                "\"size\":" + SIZE_DEFAULT + "," +
                "\"query\":" + json +
                "}";
        HttpEntity entity = new NStringEntity(jsonQuery, ContentType.APPLICATION_JSON);
        boolean retry = false;
        String endpoint = null;
        do {
            try {
                endpoint = "/" + String.join(",", index) + "/_search";
                response = headers == null ? client.performRequest(GET, endpoint, params, entity) :
                        client.performRequest(GET, endpoint, params, entity, headers);
                retry = false;
            } catch (ResponseException e) {
                log.warn(e.toString());
                Map<String, Object> exception = JsonUtil.getMapper()
                        .readValue(e.getResponse().getEntity().getContent(), Map.class);
                Map<String, Object> error = (Map<String, Object>) exception.get(ERROR);
                if (error != null) {
                    String type = (String) error.get(TYPE);
                    String badIndex = (String) error.get(RESOURCE_ID);
                    if (type != null && type.equals(INDEX_NOT_FOUND)) {
                        retry = true;
                        index.remove(badIndex);
                        if (index.isEmpty()) {
                            return results;
                        }
                    }
                }
            }
        } while(retry);
        Map<String, Object> responseMap = JsonUtil.getMapper().readValue(response.getEntity().getContent(), Map.class);
        Map<String, Object> allHits = (Map<String, Object>) responseMap.get(HITS);
        List<Map<String, Object>> hits = (List<Map<String, Object>>) allHits.get(HITS);
        results.addAll(hits);
        long totalHits = new Long((Integer) allHits.get(TOTAL));
        long currentHits = hits.size();
        while (currentHits < totalHits) {
            long pageSize = SIZE_DEFAULT;
            if ((currentHits + SIZE_DEFAULT) > totalHits) {
                pageSize = totalHits - currentHits;
            }
            jsonQuery = "{" +
                    "\"from\":" + currentHits + "," +
                    "\"size\":" + pageSize + "," +
                    "\"query\":" + json +
                    "}";
            entity = new NStringEntity(jsonQuery, ContentType.APPLICATION_JSON);
            response = headers == null ? client.performRequest(GET, endpoint, params, entity) :
                    client.performRequest(GET, endpoint, params, entity, headers);
            responseMap = JsonUtil.getMapper().readValue(response.getEntity().getContent(), Map.class);
            allHits = (Map<String, Object>) responseMap.get(HITS);
            hits = (List<Map<String, Object>>) allHits.get(HITS);
            currentHits += hits.size();
            log.debugf("currentHits [%s] totalHits [%s]", currentHits, totalHits);
            results.addAll(hits);
        }
        log.debugf("Results %s", results.size());
        return results;
    }

    public List<Event> parseEvents(List<Map<String, Object>> hits) {
        List<Event> parsed = new ArrayList<>();
        if (hits != null && !hits.isEmpty()) {
            for (Map<String, Object> hit : hits) {
                Event newEvent = new Event();
                newEvent.getContext().put(SOURCE, JsonUtil.toJson(hit));
                Map<String, Object> source = (Map<String, Object>) hit.get(_SOURCE);
                for (Map.Entry<String, EventField> entry : mappings.entrySet()) {
                    boolean isIndex = entry.getKey().equals(INDEX);
                    boolean isId = entry.getKey().equals(ID);
                    switch (entry.getValue()) {
                        case ID:
                            newEvent.setId(isId ? (String) hit.get(_ID) : getField(source, entry.getKey()));
                            break;
                        case CTIME:
                            newEvent.setCtime(parseTimestamp(getField(source, entry.getKey())));
                            break;
                        case DATAID:
                            newEvent.setDataId(isIndex ? (String) hit.get(_INDEX) : getField(source, entry.getKey()));
                            break;
                        case DATASOURCE:
                            newEvent.setDataSource(getField(source, entry.getKey()));
                            break;
                        case CATEGORY:
                            newEvent.setCategory(getField(source, entry.getKey()));
                            break;
                        case TEXT:
                            newEvent.setText(getField(source, entry.getKey()));
                            break;
                        case CONTEXT:
                            newEvent.getContext().put(entry.getKey(), getField(source, entry.getKey()));
                            break;
                        case TAGS:
                            if (isIndex) {
                                newEvent.getTags().put(INDEX, (String) hit.get(_INDEX));
                            } else {
                                newEvent.getTags().put(entry.getKey(), getField(source, entry.getKey()));
                            }
                            break;
                    }
                }
                if (newEvent.getId() == null) {
                    newEvent.setId(UUID.randomUUID().toString());
                }
                parsed.add(newEvent);
            }
        }
        return parsed;
    }

    protected String getField(Map<String, Object> source, String name) {
        if (source == null || name == null) {
            return null;
        }
        if (name.charAt(0) == '\'' && name.charAt(name.length() - 1) == '\'') {
            return name.substring(1, name.length() - 1);
        }
        String[] names = name.split("\\|");
        String defaultValue = "";
        if (names.length > 1) {
            if (names[1].charAt(0) == '\'' && names[1].charAt(names[1].length() - 1) == '\'') {
                defaultValue = names[1].substring(1, names[1].length() - 1);
            }
            name = names[0];
        }
        String[] fields = name.split("\\.");
        for (int i=0; i < fields.length; i++) {
            Object value = source.get(fields[i]);
            if (value instanceof String) {
                return (String) value;
            }
            if (value instanceof Map) {
                source = (Map<String, Object>) value;
            }
        }
        return defaultValue;
    }

    public long parseTimestamp(String timestamp) {
        String definedPattern = properties.get(TIMESTAMP_PATTERN);
        if (definedPattern != null) {
            DateTimeFormatter formatter = null;
            try {
                formatter = DateTimeFormatter.ofPattern(definedPattern);
                return ZonedDateTime.parse(timestamp, formatter).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.debugf("Not able to parse [%s] with format [%s]", timestamp, formatter);
            }
        }
        for (DateTimeFormatter formatter : DEFAULT_DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(timestamp, formatter).toInstant().toEpochMilli();
            } catch (Exception e) {
                log.debugf("Not able to parse [%s] with format [%s]", timestamp, formatter);
            }
        }
        try {
            return new Long(timestamp).longValue();
        } catch (Exception e) {
            log.debugf("Not able to parse [%s] as plain timestamp", timestamp);
        }
        return System.currentTimeMillis();
    }

    public String formatTimestamp(Date date) {
        String definedPattern = properties.get(TIMESTAMP_PATTERN);
        if (definedPattern != null) {
            DateTimeFormatter formatter = null;
            try {
                formatter = DateTimeFormatter.ofPattern(definedPattern);
                return formatter.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), UTC));
            } catch (Exception e) {
                log.debugf("Not able to format [%s] with pattern [%s]", date, formatter);
            }
        }
        return DEFAULT_DATE_FORMATS[0].format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), UTC));
    }

    private String rawQuery(String filter) {
        return "{" +
                    "\"constant_score\":{" +
                        "\"filter\":{" +
                            "\"bool\":{" +
                                "\"must\":" +
                                    filter +
                            "}" +
                        "}" +
                    "}" +
                "}";
    }

    private Date intervalDate() {
        String interval = properties.get(INTERVAL);
        int value = getIntervalValue(interval);
        switch (getIntervalUnit(interval)) {
            case HOURS:
                value = value * 3600 * 1000;
                break;
            case MINUTES:
                value = value * 60 * 1000;
                break;
            case SECONDS:
                value = value * 1000;
                break;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis() - value);
        return cal.getTime();
    }

    private String prepareQuery() {
        String range = new StringBuilder("{\"range\":{\"").append(properties.get(TIMESTAMP))
                .append("\":{\"gt\":\"").append(formatTimestamp(intervalDate())).append("\"}}}").toString();
        String filter = properties.get(FILTER);
        String filters;
        if (filter != null) {
            filters =new StringBuilder("[").append(filter).append(",").append(range).append("]").toString();
        } else {
            filters = new StringBuilder("[").append(range).append("]").toString();
        }
        return rawQuery(filters);
    }

    public void disconnect() throws Exception {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void run() {
        try {
            parseProperties();
            parseMap();
            connect(properties.get(URL));
            String preparedQuery = prepareQuery();
            log.debugf("Fetching documents from Elasticsearch [%s] %s", preparedQuery, trigger.getContext());
            List<Event> events = parseEvents(query(preparedQuery, properties.get(INDEX)));
            log.debugf("Found [%s]", events.size());
            disconnect();
            events.stream().forEach(e -> e.setTenantId(trigger.getTenantId()));
            alerts.sendEvents(events);
        } catch (Exception e) {
            log.error("Error querying Elasticsearch.", e);
        }
    }
}
