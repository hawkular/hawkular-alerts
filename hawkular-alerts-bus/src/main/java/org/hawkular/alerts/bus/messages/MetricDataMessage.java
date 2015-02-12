/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.bus.messages;

import java.util.List;

import com.google.gson.annotations.Expose;

import org.hawkular.bus.common.BasicMessage;

/**
 * A bus message for messages on HawkularMetricData Topic.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */

public class MetricDataMessage extends BasicMessage {

    // the basic message body - it will be exposed to the JSON output
    @Expose
    private MetricData metricData;

    protected MetricDataMessage() {
    }

    public MetricDataMessage(MetricData metricData) {
        this.metricData = metricData;
    }

    public MetricData getMetricData() {
        return metricData;
    }

    public void setMetricData(MetricData metricData) {
        this.metricData = metricData;
    }

    public static class MetricData {
        @Expose
        String tenantId;
        @Expose
        String metricId;
        @Expose
        List<NumericDataPoint> dataPoints;

        public MetricData() {
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getMetricId() {
            return metricId;
        }

        public void setMetricId(String metricId) {
            this.metricId = metricId;
        }

        public List<NumericDataPoint> getDataPoints() {
            return dataPoints;
        }

        public void setDataPoints(List<NumericDataPoint> dataPoints) {
            this.dataPoints = dataPoints;
        }

        @Override
        public String toString() {
            return "MetricData [tenantId=" + tenantId + ", metricId=" + metricId + ", dataPoints=" + dataPoints
                    + "]";
        }
    }

    public static class NumericDataPoint {
        @Expose
        private long timestamp;
        @Expose
        private double value;

        public NumericDataPoint() {
        }

        public NumericDataPoint(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public double getValue() {
            return value;
        }

        public void setValue(double value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "NumericDataPoint [timestamp=" + timestamp + ", value=" + value + "]";
        }

    }

}
