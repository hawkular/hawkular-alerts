package org.hawkular.alerter.gnocchi;

import static org.hawkular.alerter.gnocchi.GnocchiAlerter.GNOCCHI_USER;
import static org.hawkular.alerter.gnocchi.GnocchiAlerter.INTERVAL;
import static org.hawkular.alerter.gnocchi.GnocchiAlerter.INTERVAL_DEFAULT;
import static org.hawkular.alerter.gnocchi.GnocchiAlerter.getIntervalUnit;
import static org.hawkular.alerter.gnocchi.GnocchiAlerter.getIntervalValue;
import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class GnocchiQuery implements Runnable {
    private static final MsgLogger log = MsgLogging.getMsgLogger(GnocchiQuery.class);

    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String ACCEPT = "Accept";
    private static final String APPLICATION_JSON = "application/json";
    private static final String GET = "GET";
    private static final String POST = "POST";

    private static final String METRIC_IDS = "metric.ids";
    private static final String METRIC_NAMES = "metric.names";
    private static final String METRIC_NAMES_REGEXP = "metric.names.regexp";
    private static final String METRIC_RESOURCE_QUERY = "metric.resource.query";
    private static final String METRIC_AGGREGATION = "metric.aggregation";
    private static final String METRIC_GRANULARITY = "metric.granularity";
    private static final String METRIC_GRANULARITY_DEFAULT = "300";

    private Trigger trigger;
    private Map<String, String> properties;
    private AlertsService alerts;

    private String baseUrl;
    private String basicAuth;
    private String metricIds;
    private String metricNames;
    private String metricNamesRegexp;
    private String metricResourceQuery;
    private String metricAggregation;
    private String interval;
    private String granularity;

    private Map<String, String> nameIdMetrics = new HashMap<>();
    private Map<String, List<String>> aggregatedMetrics = new HashMap<>();
    private Map<String, String> queries = new HashMap<>();
    private ExecutorService executorService = Executors.newFixedThreadPool(10);

    public GnocchiQuery(Trigger trigger, Map<String, String> properties, AlertsService alerts) {
        this.trigger = trigger;
        this.properties = properties == null ? new HashMap<>() : new HashMap<>(properties);
        this.alerts = alerts;
        String user = null;
        String password = null;
        if (trigger != null) {
            baseUrl = trigger.getContext().get(GnocchiAlerter.URL);
            user = trigger.getContext().get(GnocchiAlerter.USER);
            password = trigger.getContext().get(GnocchiAlerter.PASSWORD);
            metricIds = trigger.getContext().get(METRIC_IDS);
            metricNames = trigger.getContext().get(METRIC_NAMES);
            metricNamesRegexp = trigger.getContext().get(METRIC_NAMES_REGEXP);
            metricResourceQuery = trigger.getContext().get(METRIC_RESOURCE_QUERY);
            metricAggregation = trigger.getContext().get(METRIC_AGGREGATION);
            interval = trigger.getContext().get(INTERVAL) == null ? INTERVAL_DEFAULT : trigger.getContext().get(INTERVAL);
            granularity = trigger.getContext().get(METRIC_GRANULARITY) == null ? METRIC_GRANULARITY_DEFAULT : trigger.getContext().get(METRIC_GRANULARITY);
        }
        if (isEmpty(baseUrl)) {
            baseUrl = this.properties.getOrDefault(GnocchiAlerter.URL, GnocchiAlerter.GNOCCHI_URL_DEFAULT);
        }
        if (isEmpty(user)) {
            user = this.properties.getOrDefault(GnocchiAlerter.USER, GnocchiAlerter.GNOCCHI_USER_DEFAULT);
        }
        if (isEmpty(password)) {
            password = this.properties.getOrDefault(GnocchiAlerter.PASSWORD, GnocchiAlerter.GNOCCHI_PASSWORD_DEFAULT);
        }
        basicAuth = new StringBuilder("Basic ")
                .append(new String(Base64.getEncoder().encode(new StringBuilder()
                        .append(user)
                        .append(":")
                        .append(password)
                        .toString()
                        .getBytes())))
                .toString();
        searchMetrics();
        processMetricAggregation();
        buildGnocchiQueries();
    }

    public void searchMetrics() {
        // Case 1, fixed list of metric ids, so, no need to query names or anything
        if (!isEmpty(metricIds)) {
            String[] gnocchiIds = metricIds.split(",");
            for (int i = 0; i < gnocchiIds.length; i++) {
                nameIdMetrics.put(gnocchiIds[i].trim(), gnocchiIds[i].trim());
            }
            log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
            return;
        }

        // Case 2, fixed list of metric names, a query to get gnocchi ids is necessary
        if (!isEmpty(metricNames) && isEmpty(metricResourceQuery)) {
            String[] splitMetricNames = metricNames.split(",");
            List<String> gnocchiNames = new ArrayList<>();
            for (int i = 0; i < splitMetricNames.length; i++) {
                gnocchiNames.add(splitMetricNames[i].trim());
            }
            List rawMetrics = getMetrics(gnocchiNames);
            for (int i = 0; i < rawMetrics.size(); i++) {
                Map<String, String> metric = (Map<String, String>) rawMetrics.get(i);
                if (gnocchiNames.contains(metric.get("name"))) {
                    nameIdMetrics.put(metric.get("name"), metric.get("id"));
                }
            }
            log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
            return;
        }

        // Case 3, regexp defined for metrics names
        if (!isEmpty(metricNamesRegexp) && isEmpty(metricResourceQuery)) {
            Pattern pattern = Pattern.compile(metricNamesRegexp);
            List rawAllMetrics = getAllMetrics();
            for (int i = 0; i < rawAllMetrics.size(); i++) {
                Map<String, String> metric = (Map<String, String>) rawAllMetrics.get(i);
                if (pattern.matcher(metric.get("name")).matches()) {
                    nameIdMetrics.put(metric.get("name"), metric.get("id"));
                }
            }
            log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
            return;
        }

        // Case 4, a resource query is defined, so metrics are filtered by this query
        if (!isEmpty(metricResourceQuery)) {
            List rawResourceMetrics = getResourceMetrics();
            // Case 4.1 there is a metricNames list defined
            if (!isEmpty(metricNames)) {
                String[] splitMetricNames = metricNames.split(",");
                List<String> gnocchiNames = new ArrayList<>();
                for (int i = 0; i < splitMetricNames.length; i++) {
                    gnocchiNames.add(splitMetricNames[i].trim());
                }
                for (int i = 0; i < rawResourceMetrics.size(); i++) {
                    Map<String, String> metric = (Map<String, String>) rawResourceMetrics.get(i);
                    if (gnocchiNames.contains(metric.get("name"))) {
                        nameIdMetrics.put(metric.get("name"), metric.get("id"));
                    }
                }
                log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
                return;
            }
            // Case 4.2 there is a regexp defined
            if (!isEmpty(metricNamesRegexp)) {
                Pattern pattern = Pattern.compile(metricNamesRegexp);
                for (int i = 0; i < rawResourceMetrics.size(); i++) {
                    Map<String, String> metric = (Map<String, String>) rawResourceMetrics.get(i);
                    if (pattern.matcher(metric.get("name")).matches()) {
                        nameIdMetrics.put(metric.get("name"), metric.get("id"));
                    }
                }
                log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
                return;
            }
            // Case 4.3 include all metrics found for this resource
            for (int i = 0; i < rawResourceMetrics.size(); i++) {
                Map<String, String> metric = (Map<String, String>) rawResourceMetrics.get(i);
                nameIdMetrics.put(metric.get("name"), metric.get("id"));
            }
            log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
            return;
        }

        // Case 5, nothing defined, then all metrics are fetched, not ideal but it will save configuration steps on trigger
        List rawAllMetrics = getAllMetrics();
        for (int i = 0; i < rawAllMetrics.size(); i++) {
            Map<String, String> metric = (Map<String, String>) rawAllMetrics.get(i);
            nameIdMetrics.put(metric.get("name"), metric.get("id"));
        }
        log.debugf("Gnocchi Metrics configured %s", nameIdMetrics);
        return;
    }

    /*
        <aggregated_metric_name>=<aggregated_function>(<list_of_metric_names>|<regexp>);
     */
    public void processMetricAggregation() {
        if (!isEmpty(metricAggregation)) {
            String[] aggregations = metricAggregation.split(";");
            for (int i = 0; i < aggregations.length; i++) {
                String definition = aggregations[i].trim();
                int index0 = definition.indexOf("=");
                int index1 = definition.indexOf("(");
                int index2 = definition.indexOf(")");
                String aggregatedName = definition.substring(0, index0);
                String operation = definition.substring(index0 + 1, index1);
                String parameters = definition.substring(index1 + 1, index2);
                List<String> metricNames = new ArrayList<>();
                metricNames.add(operation);
                // Case a list of metricNames
                if (parameters.contains(",")) {
                    String[] listMetricNames = parameters.split(",");
                    for (int j = 0; j < listMetricNames.length; j++) {
                        metricNames.add(listMetricNames[j].trim());
                    }
                } else {
                    Pattern pattern = Pattern.compile(parameters);
                    for (String metricName : nameIdMetrics.keySet()) {
                        if (pattern.matcher(metricName).matches()) {
                            metricNames.add(metricName);
                        }
                    }
                }
                aggregatedMetrics.put(aggregatedName, metricNames);
            }
            log.debugf("Gnocchi Aggregated Metrics from %s -> %s", metricAggregation, aggregatedMetrics);
        }
    }

    public void buildGnocchiQueries() {
        if (!aggregatedMetrics.isEmpty()) {
            for (Map.Entry<String, List<String>> entry : aggregatedMetrics.entrySet()) {
                List<String> aggregatedInfo = entry.getValue();
                StringBuilder url = new StringBuilder(baseUrl)
                        .append("/v1/aggregation/metric?aggregation=")
                        .append(aggregatedInfo.get(0))
                        .append("&")
                        .append("granularity=")
                        .append(granularity)
                        .append("&");
                for (int i = 1; i < aggregatedInfo.size(); i++) {
                    if (nameIdMetrics.containsKey(aggregatedInfo.get(i))) {
                        url.append("metric=")
                                .append(nameIdMetrics.get(aggregatedInfo.get(i)))
                                .append("&");
                    }
                }
                queries.put(entry.getKey(), url.toString());
            }
        } else if (!nameIdMetrics.isEmpty()) {
            for (Map.Entry<String, String> entry : nameIdMetrics.entrySet()) {
                StringBuilder url = new StringBuilder(baseUrl)
                        .append("/v1/metric/")
                        .append(entry.getValue())
                        .append("/measures?granularity=")
                        .append(granularity)
                        .append("&");
                queries.put(entry.getKey(), url.toString());
            }
        }
    }

    private List<Map<String, String>> getAllMetrics() {
        String allMetricsUrl = baseUrl + "/v1/metric";
        try {
            URL url = new URL(allMetricsUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty(AUTHORIZATION, basicAuth);
            conn.setRequestMethod(GET);
            conn.setDoInput(true);
            List rawAllMetrics = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
            conn.disconnect();
            log.debugf("Gnocchi Metrics found %s", rawAllMetrics);
            return (List<Map<String, String>>) rawAllMetrics;
        } catch (IOException e) {
            log.errorf(e,"Error querying Gnocchi metrics %s", allMetricsUrl);
        }
        return Collections.EMPTY_LIST;
    }

    private List<Map<String, String>> getMetrics(List<String> gnocchiNames) {
        String metricsUrl = baseUrl + "/v1/metric";
        List<Map<String, String>> rawMetrics = new ArrayList<>();
        if (isEmpty(gnocchiNames)) {
            return rawMetrics;
        }
        for (String metricName : gnocchiNames) {
            String metricUrl = metricsUrl + "?name=" + metricName;
            try {
                URL url = new URL(metricUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty(AUTHORIZATION, basicAuth);
                conn.setRequestMethod(GET);
                conn.setDoInput(true);
                List rawMetric = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
                conn.disconnect();
                log.debugf("Gnocchi Metrics found %s", rawMetric);
                rawMetrics.addAll(rawMetric);
            } catch (IOException e) {
                log.errorf(e,"Error querying Gnocchi metrics %s", metricUrl);
            }
        }
        return rawMetrics;
    }

    private List<Map<String, String>> getResourceMetrics() {
        String resourcesUrl = baseUrl + "/v1/search/resource/generic";
        try {
            URL url = new URL(resourcesUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty(AUTHORIZATION, basicAuth);
            conn.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
            conn.setRequestProperty(ACCEPT, APPLICATION_JSON);
            conn.setRequestMethod(POST);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(metricResourceQuery.getBytes());
            os.flush();
            os.close();
            List rawResources = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
            log.debugf("Gnocchi Resources found %s", rawResources);
            List<Map<String, String>> rawMetrics = new ArrayList<>();
            for (int i = 0; i < rawResources.size(); i++) {
                Map<String, Object> resource = (Map<String, Object>) rawResources.get(i);
                if (resource.containsKey("metrics")) {
                    Map<String, String> metrics = (Map<String, String>) resource.get("metrics");
                    for (Map.Entry<String, String> metric : metrics.entrySet()) {
                        Map<String, String> resultMetric = new HashMap<>();
                        resultMetric.put("name", metric.getKey());
                        resultMetric.put("id", metric.getValue());
                        rawMetrics.add(resultMetric);
                    }
                }
            }
            log.debugf("Gnocchi Metrics found %s", rawMetrics);
            conn.disconnect();
            return rawMetrics;
        } catch (IOException e) {
            log.errorf(e,"Error querying Gnocchi resources %s", resourcesUrl);
        }
        return Collections.EMPTY_LIST;
    }

    private long intervalStart() {
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
        return (long)((System.currentTimeMillis() - value) / 1000);
    }

    public long parseTimestamp(String timestamp) {
        try {
            return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.debugf("Not able to parse [%s] with format [%s]", timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return -1;
    }

    public void run() {
        List<Future> futures = new ArrayList<>();
        for (Map.Entry<String, String> query : queries.entrySet()) {
            futures.add(executorService.submit(() -> {
                String metricName = query.getKey();
                String measuresUrl = query.getValue() + "start=" + intervalStart();
                try {
                    URL url = new URL(measuresUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty(AUTHORIZATION, basicAuth);
                    conn.setRequestMethod(GET);
                    conn.setDoInput(true);
                    List measures = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
                    List<Data> data = new ArrayList<>();
                    for (int i = 0; i < measures.size(); i++) {
                        List measure = (List)measures.get(i);
                        if (measure != null && measure.size() == 3) {
                            String timestamp = (String) measure.get(0);
                            Double granularity = (Double) measure.get(1);
                            Double value = (Double) measure.get(2);
                            Map<String, String> context = new HashMap<>();
                            context.put("granularity", String.valueOf(granularity));
                            data.add(Data.forNumeric(trigger.getTenantId(), metricName, parseTimestamp(timestamp), value, context));
                        }
                    }
                    log.debugf("Sending [%s]", data);
                    if (alerts != null) {
                        alerts.sendData(data, true);
                    }
                } catch (IOException e) {
                    log.errorf(e,"Error querying Gnochi URL %s", measuresUrl);
                } catch (Exception e) {
                    log.errorf(e, "Error sending data to the Alerting Engine");
                }
            }));
        }
        for (Future future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error(e);
            }
        }
    }
}
