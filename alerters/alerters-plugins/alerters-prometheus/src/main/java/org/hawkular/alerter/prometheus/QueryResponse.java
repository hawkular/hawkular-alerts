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

import java.util.Arrays;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @author jshaughn
 */
public class QueryResponse {

    @JsonInclude
    String status;

    @JsonInclude
    Data data;

    public QueryResponse() {
        super();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "QueryResponse [status=" + status + ", data=" + data + "]";
    }

    public static class Data {
        @JsonInclude
        String resultType;

        @JsonInclude
        Result[] result;

        public Data() {
            super();
        }

        public String getResultType() {
            return resultType;
        }

        public void setResultType(String resultType) {
            this.resultType = resultType;
        }

        public Result[] getResult() {
            return result;
        }

        public void setResult(Result[] result) {
            this.result = result;
        }

        @Override
        public String toString() {
            return "Data [resultType=" + resultType + ", result=" + Arrays.toString(result) + "]";
        }

    }

    public static class Result {
        @JsonInclude
        Map<String, String> metric;

        @JsonInclude
        Object[] value;

        public Result() {
            super();
        }

        public Map<String, String> getMetric() {
            return metric;
        }

        public void setMetric(Map<String, String> metric) {
            this.metric = metric;
        }

        public Object[] getValue() {
            return value;
        }

        public void setValue(Object[] value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "MetricData [metric=" + metric + ", value=" + Arrays.toString(value) + "]";
        }

    }

}
