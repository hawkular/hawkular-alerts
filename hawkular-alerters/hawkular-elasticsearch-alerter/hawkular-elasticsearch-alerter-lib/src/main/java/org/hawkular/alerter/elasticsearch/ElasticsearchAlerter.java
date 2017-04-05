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

import static org.hawkular.alerter.elasticsearch.ServiceNames.Service.ALERTS_SERVICE;
import static org.hawkular.alerter.elasticsearch.ServiceNames.Service.DEFINITIONS_SERVICE;
import static org.hawkular.alerter.elasticsearch.ServiceNames.Service.PROPERTIES_SERVICE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.DistributedEvent;
import org.hawkular.alerts.api.services.PropertiesService;
import org.jboss.logging.Logger;

/**
 * This is the main class of the Elasticsearch Alerter.
 *
 * The Elasticsearch Alerter will listen for triggers tagged with "Elasticsearch" tag. The Alerter will schedule a
 * periodic query to an Elasticsearch system with the info provided from the tagged trigger context. The Alerter
 * will convert Elasticsearch documents into Hawkular Alerting Events and send them into the Alerting engine.
 *
 * The Elasticsearch Alerter uses the following conventions for trigger tags and context:
 *
 * <pre>
 *
 * - [Required]    trigger.tags["Elasticsearch"] = "<value reserved for future uses>"
 *
 *   An "Elasticsearch" tag is required for the alerter to detect this trigger will query to an Elasticsearch system.
 *   Value is not necessary, it can be used as a description, it is reserved for future uses.
 *
 *   i.e.   trigger.tags["Elasticsearch"] = ""                          // Empty value is valid
 *          trigger.tags["Elasticsearch"] = "OpenShift Logging System"  // It can be used as description
 *
 * - [Required]    trigger.context["timestamp"] = "<timestamp field>"
 *
 *   Documents fetched from Elasticsearch need a date field to indicate the timestamp.
 *   This timestamp will be used in the queries to fetch documents in interval basis.
 *
 *   If there is not defined a specific pattern under the trigger.context["timestamp_pattern"] it will follow the
 *   default patterns:
 *
 *      "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"
 *      "yyyy-MM-dd'T'HH:mm:ssZ"
 *
 * - [Required]    trigger.context["mapping"] = "<mapping_expression>"
 *
 *   A mapping expressions defines how to convert an Elasticsearch document into a Hawkular Event:
 *
 *   <mapping_expression> ::= <mapping> | <mapping> "," <mapping_expression>
 *   <mapping> ::= <elasticsearch_field> [ "|" "'" <DEFAULT_VALUE> "'" ] ":" <hawkular_event_field>
 *   <elasticsearch_field> ::= "index" | "id" | <SOURCE_FIELD>
 *   <hawkular_event_field> ::= "id" | "ctime" | "dataSource" | "dataId" | "category" | "text" | "context" | "tags"
 *
 *   A minimum mapping for the "dataId" is required.
 *   If a mapping is not present in an Elasticsearch document it will return an empty value.
 *   It is possible to define a default value for cases when the Elasticsearch field is not present.
 *   Special Elasticsearch metafields "_index" and "_id" are supported under "index" and "id" labels.
 *
 *   i.e.   trigger.context["mapping"] = "level|'INFO':category,@timestamp:ctime,message:text,hostname:dataId,index:tags"
 *
 * - [Optional]    trigger.context["interval"] = "[0-9]+[smh]"  (i.e. 30s, 2h, 10m)
 *
 *   Defines the periodic interval when a query will be performed against an Elasticsearch system.
 *   If not value provided, default one is "2m" (two minutes).
 *
 *   i.e.   trigger.context["interval"] = "30s" will perform queries each 30 seconds fetching new documents generated
 *          on the last 30 seconds, using the timestamp field provided in the Alerter tag.
 *
 * - [Optional]    trigger.context["timestamp_pattern"] = "<date and time pattern>"
 *
 *   Defines a new time pattern for the trigger.context["timestamp"]. It must follow supported formats of
 *   {@link java.time.format.DateTimeFormatter}. If it is not present, it will expect default patterns:
 *
 *      "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"
 *      "yyyy-MM-dd'T'HH:mm:ssZ"
 *
 * - [Optional]    trigger.context["index"] = "<elastic_search_index>"
 *
 *   Defines the index where the documents will be queried. If not index defined, query will search under all indexes
 *   defined.
 *
 * - [Optional]    trigger.context["filter"] = "<elastic_search_query_filter>"
 *
 *   By default the Elasticsearch Alerter performs a range query over the timestamp field provided in the alerter tag.
 *   This query accepts additional filter in Elasticsearch format. The final query should be built from:
 *
 *      {
 *        "query":{
 *          "constant_score":{
 *              "filter":{
 *                  "bool":{
 *                      "must": [<range_query_on_timestamp>, <elastic_search_query_filter>]
 *                  }
 *              }
 *          }
 *        }
 *      }
 *
 * - [Optional]    trigger.context["url"]
 *
 *   Elasticsearch url can be defined in several ways in the alerter.
 *   If can be defined globally as system properties:
 *
 *      hawkular-alerts.elasticsearch-url
 *
 *   It can be defined globally from system env variables:
 *
 *      ELASTICSEARCH_URL
 *
 *   Or it can be overwritten per trigger using
 *
 *      trigger.context["url"]
 *
 *   Url can be a list of valid {@link org.apache.http.HttpHost} urls. By default it will point to
 *
 *      trigger.context["url"] = "http://localhost:9200"
 *
 *   i.e.
 *
 *      trigger.context["url"] = "http://host1:9200,http://host2:9200,http://host3:9200"
 *
 * </pre>
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Startup
@Singleton
public class ElasticsearchAlerter {
    private static final Logger log = Logger.getLogger(ElasticsearchAlerter.class);

    private static final String ELASTICSEARCH_ALERTER = "hawkular-alerts.elasticsearch-alerter";
    private static final String ELASTICSEARCH_ALERTER_ENV = "ELASTICSEARCH_ALERTER";
    private static final String ELASTICSEARCH_ALERTER_DEFAULT = "true";
    private boolean elasticSearchAlerter;

    private static final String ELASTICSEARCH_URL="hawkular-alerts.elasticsearch-url";
    private static final String ELASTICSEARCH_URL_ENV = "ELASTICSEARCH_URL";
    private static final String ELASTICSEARCH_URL_DEFAULT = "http://localhost:9200";

    private static final String ELASTICSEARCH_FORWARDED_FOR = "hawkular-alerts.elasticsearch-forwarded-for";
    private static final String ELASTICSEARCH_FORWARDED_FOR_ENV = "ELASTICSEARCH_FORWARDED_FOR";
    private static final String ELASTICSEARCH_FORWARDED_FOR_DEFAULT = "";

    private static final String ELASTICSEARCH_TOKEN = "hawkular-alerts.elasticsearch-token";
    private static final String ELASTICSEARCH_TOKEN_ENV = "ELASTICSEARCH_TOKEN";
    private static final String ELASTICSEARCH_TOKEN_DEFAULT = "";

    private static final String ELASTICSEARCH_PROXY_REMOTE_USER = "hawkular-alerts.elasticsearch-proxy-remote-user";
    private static final String ELASTICSEARCH_PROXY_REMOTE_USER_ENV = "ELASTICSEARCH_PROXY_REMOTE_USER";
    private static final String ELASTICSEARCH_PROXY_REMOTE_USER_DEFAULT = "";

    private static final String ALERTER_NAME = "Elasticsearch";

    private Map<TriggerKey, Trigger> activeTriggers = new ConcurrentHashMap<>();

    private static final Integer THREAD_POOL_SIZE = 20;

    private static final String INTERVAL = "interval";
    private static final String INTERVAL_DEFAULT = "2m";
    private static final String URL = "url";
    private static final String FORWARDED_FOR = "forwarded-for";
    private static final String PROXY_REMOTE_USER = "proxy-remote-user";
    private static final String TOKEN = "token";

    private ScheduledThreadPoolExecutor scheduledExecutor;
    private Map<TriggerKey, ScheduledFuture<?>> queryFutures = new HashMap<>();

    private PropertiesService properties;

    private Map<String, String> defaultProperties;

    private DefinitionsService definitions;

    private AlertsService alerts;

    @Resource
    private ManagedExecutorService executor;

    @PostConstruct
    public void init() {
        try {
            InitialContext ctx = new InitialContext();
            properties = (PropertiesService) ctx.lookup(ServiceNames.getServiceName(PROPERTIES_SERVICE));
            definitions = (DefinitionsService) ctx.lookup(ServiceNames.getServiceName(DEFINITIONS_SERVICE));
            alerts = (AlertsService) ctx.lookup(ServiceNames.getServiceName(ALERTS_SERVICE));
        } catch (NamingException e) {
            log.errorf("Cannot access to JNDI context", e);
        }
        if (properties == null || definitions == null || alerts == null) {
            throw new IllegalStateException("Elasticsearch Alerter cannot connect with Hawkular Alerting");
        }
        elasticSearchAlerter = Boolean.parseBoolean(properties.getProperty(ELASTICSEARCH_ALERTER,
                ELASTICSEARCH_ALERTER_ENV, ELASTICSEARCH_ALERTER_DEFAULT));
        defaultProperties = new HashMap();
        defaultProperties.put(URL, properties.getProperty(ELASTICSEARCH_URL, ELASTICSEARCH_URL_ENV,
                ELASTICSEARCH_URL_DEFAULT));
        defaultProperties.put(TOKEN, properties.getProperty(ELASTICSEARCH_TOKEN, ELASTICSEARCH_TOKEN_ENV,
                ELASTICSEARCH_TOKEN_DEFAULT));
        defaultProperties.put(FORWARDED_FOR, properties.getProperty(ELASTICSEARCH_FORWARDED_FOR,
                ELASTICSEARCH_FORWARDED_FOR_ENV, ELASTICSEARCH_FORWARDED_FOR_DEFAULT));
        defaultProperties.put(PROXY_REMOTE_USER, properties.getProperty(ELASTICSEARCH_PROXY_REMOTE_USER,
                ELASTICSEARCH_PROXY_REMOTE_USER_ENV, ELASTICSEARCH_PROXY_REMOTE_USER_DEFAULT));

        if (elasticSearchAlerter) {
            definitions.registerDistributedListener(events -> refresh(events));
        }
    }

    @PreDestroy
    public void stop() {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            scheduledExecutor = null;
        }
    }

    private void refresh(Set<DistributedEvent> distEvents) {
        log.debugf("Events received %s", distEvents);
        executor.submit(() -> {
            try {
                for (DistributedEvent distEvent : distEvents) {
                    TriggerKey triggerKey = new TriggerKey(distEvent.getTenantId(), distEvent.getTriggerId());
                    switch (distEvent.getOperation()) {
                        case REMOVE:
                            activeTriggers.remove(triggerKey);
                            break;
                        case ADD:
                            if (activeTriggers.containsKey(triggerKey)) {
                                break;
                            }
                        case UPDATE:
                            Trigger trigger = definitions.getTrigger(distEvent.getTenantId(), distEvent.getTriggerId());
                            if (trigger != null && trigger.getTags().containsKey(ALERTER_NAME)) {
                                if (!trigger.isLoadable()) {
                                    activeTriggers.remove(triggerKey);
                                    break;
                                } else {
                                    activeTriggers.put(triggerKey, trigger);
                                }
                            }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch Triggers for external conditions.", e);
            }
            update();
        });
    }

    private synchronized void update() {
        final Set<TriggerKey> existingKeys = queryFutures.keySet();
        final Set<TriggerKey> activeKeys = activeTriggers.keySet();

        Set<TriggerKey> newKeys = new HashSet<>();
        Set<TriggerKey> canceledKeys = new HashSet<>();

        Set<TriggerKey> updatedKeys = new HashSet<>(activeKeys);
        updatedKeys.retainAll(activeKeys);

        activeKeys.stream().filter(key -> !existingKeys.contains(key)).forEach(key -> newKeys.add(key));
        existingKeys.stream().filter(key -> !activeKeys.contains(key)).forEach(key -> canceledKeys.add(key));

        log.debugf("newKeys %s", newKeys);
        log.debugf("updatedKeys %s", updatedKeys);
        log.debugf("canceledKeys %s", canceledKeys);

        canceledKeys.stream().forEach(key -> {
            ScheduledFuture canceled = queryFutures.remove(key);
            if (canceled != null) {
                canceled.cancel(false);
            }
        });
        updatedKeys.stream().forEach(key -> {
            ScheduledFuture updated = queryFutures.remove(key);
            if (updated != null) {
                updated.cancel(false);
            }
        });

        if (scheduledExecutor == null) {
            scheduledExecutor = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE);
        }

        newKeys.addAll(updatedKeys);

        for (TriggerKey key : newKeys) {
            Trigger t = activeTriggers.get(key);
            String interval = t.getContext().get(INTERVAL) == null ? INTERVAL_DEFAULT : t.getContext().get(INTERVAL);
            queryFutures.put(key, scheduledExecutor
                    .scheduleAtFixedRate(new ElasticsearchQuery(t, defaultProperties, alerts),0L,
                            getIntervalValue(interval), getIntervalUnit(interval)));

        }
    }

    public static int getIntervalValue(String interval) {
        if (interval == null || interval.isEmpty()) {
            interval = INTERVAL_DEFAULT;
        }
        try {
            return new Integer(interval.substring(0, interval.length() - 1)).intValue();
        } catch (Exception e) {
            return new Integer(INTERVAL_DEFAULT.substring(0, interval.length() - 1)).intValue();
        }
    }

    public static TimeUnit getIntervalUnit(String interval) {
        if (interval == null || interval.isEmpty()) {
            interval = INTERVAL_DEFAULT;
        }
        char unit = interval.charAt(interval.length() - 1);
        switch (unit) {
            case 'h':
                return TimeUnit.HOURS;
            case 's':
                return TimeUnit.SECONDS;
            case 'm':
            default:
                return TimeUnit.MINUTES;
        }
    }

    private class TriggerKey {
        private String tenantId;
        private String triggerId;

        public TriggerKey(String tenantId, String triggerId) {
            this.tenantId = tenantId;
            this.triggerId = triggerId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getTriggerId() {
            return triggerId;
        }

        public void setTriggerId(String triggerId) {
            this.triggerId = triggerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TriggerKey that = (TriggerKey) o;

            if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) return false;
            return triggerId != null ? triggerId.equals(that.triggerId) : that.triggerId == null;
        }

        @Override
        public int hashCode() {
            int result = tenantId != null ? tenantId.hashCode() : 0;
            result = 31 * result + (triggerId != null ? triggerId.hashCode() : 0);
            return result;
        }
    }
}
