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
 * A string incoming data.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StringData extends Data {

    public StringData() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this(null, 0, "");
    }

    public StringData(String id, long timestamp, String value) {
        super(id, timestamp, (null == value) ? "" : value);
    }

    public String getValue() {
        return (String) value;
    }

    public void setValue(String value) {
        this.value = (null == value) ? "" : value;
    }

    @Override
    public String toString() {
        return "StringData [id=" + id + ", timestamp=" + timestamp + ", value=" + value + "]";
    }

    @Override
    int compareValue(Object value1, Object value2) {
        String v1 = (String) value1;
        String v2 = (String) value2;
        return v1.compareTo(v2);
    }
}
