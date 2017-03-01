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

import static org.hawkular.alerter.elasticsearch.ElasticSearchAlerter.getIntervalUnit;
import static org.hawkular.alerter.elasticsearch.ElasticSearchAlerter.getIntervalValue;
import static org.hawkular.alerter.elasticsearch.ElasticSearchQuery.EventField.DATAID;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.search.SearchHit;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.jboss.logging.Logger;

/**
 * This class performs a query into ElasticSearch system and parses results documents based on Trigger tags/context.
 * @see {@link ElasticSearchAlerter}
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ElasticSearchQuery implements Runnable {
    private static final Logger log = Logger.getLogger(ElasticSearchQuery.class);

    private static final int SIZE_DEFAULT = 10;

    /*
        Trigger context
     */
    private static final String ALERTER_NAME = "ElasticSearch";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String ID = "id";
    private static final String INTERVAL = "interval";
    private static final String INTERVAL_DEFAULT = "2m";
    private static final String INDEX = "index";
    private static final String TIMESTAMP = "timestamp";
    private static final String MAP = "map";
    private static final String FILTER = "filter";

    private static final SimpleDateFormat[] dateFormats = {
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
    };

    private static final String[] EMPTY_ARRAY = new String[0];

    /*
        Event fields
     */
    enum EventField {
        ID ("id"),
        CTIME ("ctime"),
        DATASOURCE ("dataSource"),
        DATAID ("dataId"),
        CATEGORY ("category"),
        TEXT ("text"),
        CONTEXT ("context"),
        TAGS ("tags");

        private final String name;

        EventField(String name) {
            this.name = name;
        }

        public boolean equalsName(String name) {
            return this.name.equals(name);
        }

        public static EventField fromString(String name) {
            for (EventField field : EventField.values()) {
                if (field.equalsName(name)) {
                    return field;
                }
            }
            return null;
        }

        public String toString() {
            return this.name;
        }
    }

    private Trigger trigger;
    private Map<String, String> properties;
    private AlertsService alerts;

    private Map<String, EventField> mappings = new HashMap<>();

    private TransportClient client;

    public ElasticSearchQuery(Trigger trigger, Map<String, String> properties, AlertsService alerts) {
        this.trigger = trigger;
        this.properties = properties == null ? new HashMap<>() : new HashMap<>(properties);
        this.alerts = alerts;
    }

    public void parseProperties() throws Exception {
        if (trigger == null) {
            throw new IllegalStateException("trigger must be not null");
        }
        String timestamp = trigger.getTags().get(ALERTER_NAME);
        if (timestamp == null || timestamp.isEmpty()) {
            throw new IllegalStateException(ALERTER_NAME + " tag must a timestamp field as value");
        }
        properties.put(TIMESTAMP, timestamp);
        checkContext(HOST, false);
        checkContext(PORT, false);
        checkContext(INTERVAL, INTERVAL_DEFAULT);
        checkContext(INDEX, false);
        checkContext(MAP, true);
        checkContext(FILTER, false);
    }

    public void parseMap() throws Exception {
        String rawMap = properties.get(MAP);
        if (rawMap == null) {
            throw new IllegalStateException("map must be not null");
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

    public void connect(String host, int port) throws Exception {
        client = TransportClient.builder().build()
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
    }

    public Set<SearchHit> query(String filter, String indices) throws Exception {
        if (filter == null || filter.isEmpty()) {
            throw new IllegalArgumentException("filter must be not null");
        }
        Set<SearchHit> results = new HashSet<>();
        String json = rawQuery(filter);
        String preference = UUID.randomUUID().toString();
        List<String> index = indices == null ? null : new ArrayList<>(Arrays.asList(indices.split(",")));
        SearchResponse response = null;
        /*
            Using a loop in case results are greater than default results size
         */
        boolean retry;
        do {
            try {
                SearchRequestBuilder searchBuilder = index == null || index.isEmpty() ? client.prepareSearch()
                        : client.prepareSearch(index.toArray(EMPTY_ARRAY));
                response = searchBuilder.setPreference(preference)
                        .setQuery(json)
                        .setFrom(0)
                        .setSize(SIZE_DEFAULT)
                        .get();
                retry = false;
            } catch (IndexNotFoundException e) {
                log.warn(e.toString());
                retry = true;
                index.remove(e.getIndex());
                /*
                    If all indexes are bad, we should return an empty results instead a global search
                 */
                if (index.isEmpty()) {
                    return results;
                }
            }
        } while(retry);
        SearchHit[] hits = response.getHits().hits();
        results.addAll(Arrays.asList(hits));
        long totalHits = response.getHits().totalHits();
        long currentHits = response.getHits().hits().length;
        while (currentHits < totalHits) {
            long pageSize = SIZE_DEFAULT;
            if ((currentHits + SIZE_DEFAULT) > totalHits) {
                pageSize = totalHits - currentHits;
            }
            SearchRequestBuilder searchBuilder = index == null || index.isEmpty() ? client.prepareSearch()
                    : client.prepareSearch(index.toArray(EMPTY_ARRAY));
            response = searchBuilder.setPreference(preference)
                    .setQuery(json)
                    .setFrom((int) currentHits)
                    .setSize((int) pageSize)
                    .get();
            currentHits += hits.length;
            hits = response.getHits().hits();
            log.debugf("currentHits [%s] totalHits [%s]", currentHits, totalHits);
            results.addAll(Arrays.asList(hits));
        }
        log.debugf("Results %s", results.size());
        return results;
    }

    public Set<Event> parseEvents(Set<SearchHit> hits) {
        Set<Event> parsed = new HashSet<>();
        if (hits != null && !hits.isEmpty()) {
            for (SearchHit hit : hits) {
                Event newEvent = new Event();
                newEvent.getContext().put("source", hit.getSourceAsString());
                Map<String, Object> source = hit.getSource();
                for (Map.Entry<String,EventField> entry : mappings.entrySet()) {
                    boolean isIndex = entry.getKey().equals(INDEX);
                    boolean isId = entry.getKey().equals(ID);
                    switch (entry.getValue()) {
                        case ID:
                            newEvent.setId(isId ? hit.id() : getField(source, entry.getKey()));
                            break;
                        case CTIME:
                            newEvent.setCtime(parseTimestamp(getField(source, entry.getKey())));
                            break;
                        case DATAID:
                            newEvent.setDataId(isIndex ? hit.index() : getField(source, entry.getKey()));
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
                                newEvent.getTags().put(INDEX, hit.index());
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
        for (SimpleDateFormat dateFormat : dateFormats) {
            try {
                return dateFormat.parse(timestamp).getTime();
            } catch (Exception e) {
                log.debugf("Not able to parse [%s] with format [%s]", timestamp, dateFormat);
            }
        }
        try {
            return new Long(timestamp).longValue();
        } catch (Exception e) {
            log.debugf("Not able to parse [%s] as plain timestamp", timestamp);
        }
        return System.currentTimeMillis();
    }

    private String rawQuery(String filter) {
        return "{" +
                    "\"query\":{" +
                        "\"constant_score\":{" +
                            "\"filter\":{" +
                                "\"bool\":{" +
                                    "\"must\":" +
                                        filter +
                                "}" +
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
                .append("\":{\"gt\":\"").append(dateFormats[0].format(intervalDate())).append("\"}}}").toString();
        String filter = properties.get(FILTER);
        String filters;
        if (filter != null) {
            filters =new StringBuilder("[").append(filter).append(",").append(range).append("]").toString();
        } else {
            filters = new StringBuilder("[").append(range).append("]").toString();
        }
        return rawQuery(filters);
    }

    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void run() {
        try {
            parseProperties();
            parseMap();
            connect(properties.get("host"), new Integer(properties.get("port")));
            String preparedQuery = prepareQuery();
            log.debugf("Fetching documents from ElasticSearch [%s] %s", preparedQuery, trigger.getContext());
            Set<Event> events = parseEvents(query(preparedQuery, (String) properties.get(INDEX)));
            log.debugf("Found [%s]", events.size());
            events.stream().forEach(e -> e.setTenantId(trigger.getTenantId()));
            alerts.sendEvents(events);
        } catch (Exception e) {
            log.error("Error querying ElasticSearch.", e);
        } finally {
            disconnect();
        }
    }
}
