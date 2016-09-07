/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.bus.api;

import java.util.HashMap;
import java.util.Map;

import org.hawkular.metrics.model.MetricType;

/**
 * A helper class to store prefixes used on integration with Hawkular Metrics.
 *
 * Hawkular Metrics defines a composed key for data points in the form of (tenantId, type, metricId) where type is
 * implicit and infered from the API operation.
 *
 * i.e. /hawkular/metrics/gauges/metricId
 *      /hawkular/metrics/counters/metricId
 *
 * Hawkular Alerting defines a general composed key in the form of (tenantId, dataId) for data referenced in
 * conditions.
 *
 * In this context of Hawkular Metrics integration, dataId should be prefixed with the implicit type used.
 *
 * i.e. /hawkular/metric/gauges/myMetricId1 -> dataId = "hm_g_myMetricId1"
 *      /hawkular/metric/counters/myMetricId2 -> dataId = "hm_g_myMetricId2"
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class DataIdPrefix {
    public static final String ALERT_AVAILABILITY = "hm_a_";
    public static final String ALERT_COUNTER = "hm_c_";
    public static final String ALERT_COUNTER_RATE = "hm_cr_";
    public static final String ALERT_GAUGE = "hm_g_";
    public static final String ALERT_GAUGE_RATE = "hm_gr_";
    public static final String ALERT_STRING = "hm_s_";

    public static final String METRIC_AVAILABILITY = MetricType.AVAILABILITY.getText();
    public static final String METRIC_COUNTER = MetricType.COUNTER.getText();
    public static final String METRIC_COUNTER_RATE = MetricType.COUNTER_RATE.getText();
    public static final String METRIC_GAUGE = MetricType.GAUGE.getText();
    public static final String METRIC_GAUGE_RATE = MetricType.GAUGE_RATE.getText();
    public static final String METRIC_STRING = MetricType.STRING.getText();

    public static final Map<String, String> METRIC_TYPE_PREFIX;
    static {
        METRIC_TYPE_PREFIX = new HashMap<>();
        METRIC_TYPE_PREFIX.put(METRIC_AVAILABILITY, ALERT_AVAILABILITY);
        METRIC_TYPE_PREFIX.put(METRIC_COUNTER, ALERT_COUNTER);
        METRIC_TYPE_PREFIX.put(METRIC_COUNTER_RATE, ALERT_COUNTER_RATE);
        METRIC_TYPE_PREFIX.put(METRIC_GAUGE, ALERT_GAUGE);
        METRIC_TYPE_PREFIX.put(METRIC_GAUGE_RATE, ALERT_GAUGE_RATE);
        METRIC_TYPE_PREFIX.put(METRIC_STRING, ALERT_STRING);
    }
}
