package org.hawkular.alerter.gnocchi;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.hawkular.alerts.api.json.JsonUtil;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class GnochiQueryTest {
    private static final MsgLogger log = MsgLogging.getMsgLogger(GnochiQueryTest.class);

    @Ignore
    @Test
    public void fetchResources() throws Exception {
        String gnocchiUrl = "http://localhost:8041/v1/resource/generic";
        String userPassword = "admin:admin";
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPassword.getBytes()));

        URL url = new URL(gnocchiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", basicAuth);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);

        List resources = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
        log.infof("resources %s", resources);

        conn.disconnect();
    }

    @Ignore
    @Test
    public void fetchMetrics() throws Exception {
        String gnocchiUrl = "http://localhost:8041/v1/metric";
        String userPassword = "admin:admin";
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPassword.getBytes()));

        URL url = new URL(gnocchiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", basicAuth);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);

        List metrics = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
        log.infof("metrics %s", metrics);

        conn.disconnect();
    }

    @Ignore
    @Test
    public void fetchMeasures() throws Exception {
        String gnocchiUrl = "http://localhost:8041/v1/metric/0062038b-af87-4f5c-b250-72c986037fa2/measures";
        String userPassword = "admin:admin";
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPassword.getBytes()));

        URL url = new URL(gnocchiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", basicAuth);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);

        List measures = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
        log.infof("measures %s", measures);

        conn.disconnect();
    }

    @Ignore
    @Test
    public void searchRersources() throws Exception {
        String searchUrl = "http://localhost:8041/v1/search/resource/generic";
        String userPassword = "admin:admin";
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userPassword.getBytes()));
        String resourceQuery = "{" +
                                    "\"like\":{" +
                                        "\"type\":\"c%\"" +
                                    "}" +
                                "}";

        URL url = new URL(searchUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", basicAuth);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        os.write(resourceQuery.getBytes());
        os.flush();
        os.close();

        List resources = JsonUtil.getMapper().readValue(conn.getInputStream(), List.class);
        log.infof("resources %s", resources);

        conn.disconnect();
    }

    @Ignore
    @Test
    public void searchMetricsByMetricIds() throws Exception {
        // Case 1
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        trigger.getContext().put("metric.ids", "0062038b-af87-4f5c-b250-72c986037fa2,01320e1e-0aeb-4e17-9402-d2450b2e0024");
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsByMetricNames() {
        // Case 2
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        trigger.getContext().put("metric.names", "cpu-0@cpu-user-0,cpu-1@cpu-user-0,cpu-2@cpu-user-0");
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsByMetricRegexp() {
        // Case 3
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        trigger.getContext().put("metric.names.regexp", "cpu-.@cpu.*");
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsByResourcesAndMetricNames() {
        // Case 4.1
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        trigger.getContext().put("metric.names", "cpu-0@cpu-user-0,cpu-1@cpu-user-0,cpu-2@cpu-user-0");
        String resourceQuery = "{" +
                "\"like\":{" +
                "\"type\":\"c%\"" +
                "}" +
                "}";
        trigger.getContext().put("metric.resource.query", resourceQuery);
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsByResourcesAndMetricRegexp() {
        // Case 4.2
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        trigger.getContext().put("metric.names.regexp", "cpu-.@cpu.*");
        String resourceQuery = "{" +
                "\"like\":{" +
                "\"type\":\"c%\"" +
                "}" +
                "}";
        trigger.getContext().put("metric.resource.query", resourceQuery);
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsByResources() {
        // Case 4.2
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        String resourceQuery = "{" +
                "\"like\":{" +
                "\"type\":\"c%\"" +
                "}" +
                "}";
        trigger.getContext().put("metric.resource.query", resourceQuery);
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetrics() {
        // Case 5
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsAndAggregateMetricNames() {
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        String aggregatedMetrics = "cpu-user=mean(cpu-0@cpu-user-0,cpu-1@cpu-user-0);" +
                "cpu-nice=mean(cpu-0@cpu-nice-0,cpu-1@cpu-nice-0);" +
                "cpu-system=mean(cpu-0@cpu-system-0,cpu-1@cpu-system-0)";
        trigger.addContext("metric.aggregation", aggregatedMetrics);
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void searchMetricsAndAggregateRegExp() {
        Trigger trigger = new Trigger("tenant", "gnochi-test-id", "Gnocchi Trigger");
        String aggregatedMetrics = "cpu-user=mean(cpu-.@cpu-user-.*);" +
                "cpu-nice=mean(cpu-.@cpu-nice-.*);" +
                "cpu-system=mean(cpu-.@cpu-system-.*)";
        trigger.addContext("metric.aggregation", aggregatedMetrics);
        new GnocchiQuery(trigger, null, null).run();
    }

    @Ignore
    @Test
    public void parseGnocchiDates() throws Exception {
        ZonedDateTime.parse("2017-09-14T21:15:00+00:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();
    }
}
