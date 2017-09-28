package org.hawkular.alerter.gnocchi;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.hawkular.alerts.alerters.api.Alerter;
import org.hawkular.alerts.alerters.api.AlerterPlugin;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerKey;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.DistributedEvent;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;

/**
 * This is the main class of the GnocchiAlerter Alerter.
 *
 * The Gnocchi Alerter will listen for triggers tagged with "Gnocchi" tag. The Alerter will schedule a
 * periodic query to an Gnocchi system with the info provided from the tagged trigger context. The Alerter
 * will convert Gnocchi metric measures into Hawkular Alerting data and send them into the Alerting engine.
 *
 * The Gnocchi Alerter uses the following conventions for trigger tags and context:
 *
 * <pre>
 *
 * - [Required]    trigger.tags["Gnocchi"] = "<value reserved for future uses>"
 *
 *   An "Gnocchi" tag is required for the alerter to detect this trigger will query to an Gnocchi system.
 *   Value is not necessary, it can be used as a description, it is reserved for future uses.
 *
 *   i.e.   trigger.tags["Gnocchi"] = ""                              // Empty value is valid
 *          trigger.tags["Gnocchi"] = "OpenStack Development System"  // It can be used as description
 *
 * - [Optional]    trigger.context["interval"] = "[0-9]+[smh]"  (i.e. 30s, 2h, 10m)
 *
 *   Defines the periodic interval when a query will be performed against an Gnocchi system.
 *   If not value provided, default one is "2m" (two minutes).
 *
 *   i.e.   trigger.context["interval"] = "30s" will perform queries each 30 seconds fetching new data generated
 *          on the last 30 seconds.
 *
 * - [Optional]    trigger.context["url"] = "<Gnocchi server url>"
 *
 *   Gnocchi url can be defined in several ways in the alerter.
 *   If can be defined globally as system properties:
 *
 *      hawkular-alerts.gnocchi-url
 *
 *   It can be defined globally from system env variables:
 *
 *      GNOCCHI_URL
 *
 *   Or it can be overwritten per trigger using
 *
 *      trigger.context["url"]
 *
 *   By default it will point to
 *
 *      trigger.context["url"] = "http://localhost:8041"
 *
 * - [Optional]    trigger.context["user"] = "<Gnocchi server user>"
 *
 *   Gnocchi user can be defined in several ways in the alerter.
 *   If can be defined globally as system properties:
 *
 *      hawkular-alerts.gnocchi-user
 *
 *   It can be defined globally from system env variables:
 *
 *      GNOCCHI_USER
 *
 *   Or it can be overwritten per trigger using
 *
 *      trigger.context["user"]
 *
 *   By default it will point to
 *
 *      trigger.context["user"] = "admin"
 *
 * - [Optional]    trigger.context["password"] = "Gnocchi server password>"
 *
 *   Gnocchi password can be defined in several ways in the alerter.
 *   If can be defined globally as system properties:
 *
 *      hawkular-alerts.gnocchi-password
 *
 *   It can be defined globally from system env variables:
 *
 *      GNOCCHI_PASSWORD
 *
 *   Or it can be overwritten per trigger using
 *
 *      trigger.context["password"]
 *
 *   By default it will point to
 *
 *      trigger.context["password"] = "admin"
 *
 * - [Optional]    trigger.context["metric.ids"] = "<list of Gnocchi metric ids to fetch>"
 *
 *   The alerter fetches for Gnocchi metrics to use them into the alerting engine.
 *   Metrics can be defined in several ways, first one is using Gnocchi metric ids.
 *
 *   i.e.
 *
 *      trigger.context["metric.ids"] = "0062038b-af87-4f5c-b250-72c986037fa2,01320e1e-0aeb-4e17-9402-d2450b2e0024"
 *
 *   The metric id will be used as Data.dataId field in Hawkular Alerting to be referred in conditions.
 *
 *   If "metric.ids" property is present, the alerter will ignore other ways to define Gnocchi metrics to fetch.
 *
 * - [Optional]    trigger.context["metric.names"] = "<list of Gnocchi metric names to fetch>"
 *
 *   If "metric.ids" is not present the alerter will use the "metric.names" property.
 *   A list of metrics names will be used to fetch Gnocchi metrics.
 *
 *   i.e.
 *
 *      trigger.context["metric.names"] = "cpu-0@cpu-user-0,cpu-1@cpu-user-0,cpu-2@cpu-user-0"
 *
 *   The metric name will be used as Data.dataId field in Hawkular Alerting to be referred in conditions.
 *   Metric names are not unique in Gnocchi, so user should be aware that the alerter will use first match with the
 *   metric name defined, on the contrary, metric names are more clear to use in trigger definitions rather than
 *   metric ids (based on UUID formats).
 *
 *  In priorities, "metric.names" will be used if not "metric.ids" property is defined, other ways to define Gnocchi
 *  metrics will be ingored.
 *
 * - [Optional]    trigger.context["metric.names.regexp"] = "<regular expression>"
 *
 *   If "metric.ids" or "metric.names" are not defined, the alerter will use "metric.names.regexp" property.
 *   A regular expression will be used to match metric names.
 *
 *   i.e.
 *
 *      trigger.context["metric.names.regexp"] = "cpu-.@cpu.*"
 *
 *   The metric name will be used as Data.dataId field in Hawkular Alerting to be referred in conditions.
 *   Metric names are not unique in Gnocchi, so user should be aware that the alerter will use first match with the
 *   metric name defined, on the contrary, metric names are more clear to use in trigger definitions rather than
 *   metric ids (based on UUID formats).
 *
 * - [Optional]    trigger.context["metric.resource.query"] = "<a Gnocchi search resource query>"
 *
 *   By default, the alerter will search metrics from all resources.
 *   It is possible to define a Gnocchi query to limit the metric search to a particular set of resources.
 *
 *   i.e.
 *
 *      trigger.context["metric.resource.query"] = "{\"like\":{\"type\":\"c%\"}}"
 *
 *   This parameter will search metrics only (by ids, names or regexp, as described below) on resources which type starts with "c".
 *
 *   For more details about format and supported Gnocchi queries
 *
 *      http://gnocchi.xyz/stable_3.0/rest.html#searching-for-resources
 *
 * - [Optional]     trigger.context["metric.aggregation"] = "<aggregated_metric_name>=<aggregated_function>(<list_of_metric_names>|<regexp>);..."
 *
 *   A powerful feature of Gnocchi is to work with aggregated metrics.
 *   The alerter can define an aggregated metric name and the expression to calculate it.
 *
 *   i.e.
 *
 *      Aggregated metrics can be defined explicitly from a list of metric names:
 *
 *      trigger.context["metric.aggregation"] = "cpu-user=mean(cpu-0@cpu-user-0,cpu-1@cpu-user-0);cpu-nice=mean(cpu-0@cpu-nice-0,cpu-1@cpu-nice-0)"
 *
 *      or they can be defined using regular expressions:
 *
 *      trigger.context["metric.aggregation"] = "cpu-user=mean(cpu-.@cpu-user-.*);cpu-nice=mean(cpu-.@cpu-nice-.*)"
 *
 *   The aggregated metric will be used as Data.dataId field in Hawkular Alerting to be referred in conditions.
 *
 * - [Optional]     trigger.context["metric.granularity"] = "<Gnocchi metric granularity>"
 *
 *   The alerter can define the granularity of the metrics defined.
 *   If not present granularity is set to 300.
 *
 *   i.e.
 *
 *      trigger.context["metric.granularity"] = "1"
 *
 *   Granularity defined will be annotated in the context of Hawkular Data.
 *
 * </pre>
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Alerter(name = "gnocchi")
public class GnocchiAlerter implements AlerterPlugin {
    private static final MsgLogger log = MsgLogging.getMsgLogger(GnocchiAlerter.class);

    public static final String GNOCCHI_ALERTER = "hawkular-alerts.gnocchi-alerter";
    public static final String GNOCCHI_ALERTER_ENV = "GNOCCHI_ALERTER";
    public static final String GNOCCHI_ALERTER_DEFAULT = "true";
    public boolean gnocchiAlerter;

    public static final String GNOCCHI_URL = "hawkular-alerts.gnocchi-url";
    public static final String GNOCCHI_URL_ENV = "GNOCCHI_URL";
    public static final String GNOCCHI_URL_DEFAULT = "http://localhost:8041";

    public static final String GNOCCHI_USER = "hawkular-alerts.gnocchi-user";
    public static final String GNOCCHI_USER_ENV = "GNOCCHI_USER";
    public static final String GNOCCHI_USER_DEFAULT = "admin";

    public static final String GNOCCHI_PASSWORD ="hawkular-alerts.gnocchi-password";
    public static final String GNOCCHI_PASSWORD_ENV = "GNOCCHI_PASSWORD";
    public static final String GNOCCHI_PASSWORD_DEFAULT = "admin";

    public static final String INTERVAL = "interval";
    public static final String INTERVAL_DEFAULT = "2m";
    public static final String URL = "url";
    public static final String USER = "user";
    public static final String PASSWORD = "password";

    private static final String ALERTER_NAME = "Gnocchi";

    private Map<TriggerKey, Trigger> activeTriggers = new ConcurrentHashMap<>();

    private static final Integer THREAD_POOL_SIZE = 20;


    private ScheduledThreadPoolExecutor scheduledExecutor;
    private Map<TriggerKey, ScheduledFuture<?>> queryFutures = new HashMap<>();

    private Map<String, String> defaultProperties;

    private DefinitionsService definitions;

    private AlertsService alerts;

    private ExecutorService executor;

    @Override
    public void init(DefinitionsService definitions, AlertsService alerts, ExecutorService executor) {
        if (definitions == null || alerts == null || executor == null) {
            throw new IllegalStateException("Gnocchi Alerter cannot connect with Hawkular Alerting");
        }
        this.definitions = definitions;
        this.alerts = alerts;
        this.executor = executor;
        gnocchiAlerter = Boolean.parseBoolean(HawkularProperties.getProperty(GNOCCHI_ALERTER, GNOCCHI_ALERTER_ENV, GNOCCHI_ALERTER_DEFAULT));
        defaultProperties = new HashMap();
        defaultProperties.put(URL, HawkularProperties.getProperty(GNOCCHI_URL, GNOCCHI_URL_ENV, GNOCCHI_URL_DEFAULT));
        defaultProperties.put(USER, HawkularProperties.getProperty(GNOCCHI_USER, GNOCCHI_USER_ENV, GNOCCHI_USER_DEFAULT));
        defaultProperties.put(PASSWORD, HawkularProperties.getProperty(GNOCCHI_PASSWORD, GNOCCHI_PASSWORD_ENV, GNOCCHI_PASSWORD_DEFAULT));

        if (gnocchiAlerter) {
            this.definitions.registerDistributedListener(events -> refresh(events));
            initialRefresh();
        }
    }

    @Override
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

    private void initialRefresh() {
        try {
            Collection<Trigger> triggers = definitions.getAllTriggersByTag(ALERTER_NAME, "*");
            triggers.stream().forEach(trigger -> activeTriggers.put(new TriggerKey(trigger.getTenantId(), trigger.getId()), trigger));
            update();
        } catch (Exception e) {
            log.error("Failed to fetch Triggers for external conditions.", e);
        }
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
                .scheduleAtFixedRate(new GnocchiQuery(t, defaultProperties, alerts), 0L,
                        getIntervalValue(interval), getIntervalUnit(interval)));
        }
    }

    public static int getIntervalValue(String interval) {
        if (isEmpty(interval)) {
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

}
