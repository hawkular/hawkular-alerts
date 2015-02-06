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
package org.hawkular.alerts.api.model.data;

/**
 * A numeric incoming data.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class NumericData extends Data {

    public NumericData() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this(null, 0, Double.NaN);
    }

    public NumericData(String id, long timestamp, Double value) {
        super(id, timestamp, (null == value) ? Double.NaN : value);
    }

    public Double getValue() {
        return (Double) value;
    }

    public void setValue(Double value) {
        super.setValue(null == value ? Double.NaN : value);
    }

    @Override
    public String toString() {
        return "NumericData [id=" + id + ", timestamp=" + timestamp + ", value=" + value + "]";
    }

    @Override
    int compareValue(Object value1, Object value2) {
        Double v1 = (Double) value1;
        Double v2 = (Double) value2;
        return v1.compareTo(v2);
    }

}
