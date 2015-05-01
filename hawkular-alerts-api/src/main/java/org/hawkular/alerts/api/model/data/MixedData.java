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

import java.util.ArrayList;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A wrapper for grouping or sending alerting data of various types in one object.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class MixedData {

    private Collection<Availability> availability;
    private Collection<NumericData> numericData;
    private Collection<StringData> stringData;

    public MixedData() {
        super();

        availability = new ArrayList<>();
        numericData = new ArrayList<>();
        stringData = new ArrayList<>();
    }

    public Collection<Availability> getAvailability() {
        return availability;
    }

    /**
     * @param availability if null set to empty list
     */
    public void setAvailability(Collection<Availability> availability) {
        if (null == availability) {
            this.availability.clear();
        } else {
            this.availability = availability;
        }
    }

    public Collection<NumericData> getNumericData() {
        return numericData;
    }

    /**
     * @param numericData  if null set to empty list
     */
    public void setNumericData(Collection<NumericData> numericData) {
        if (null == numericData) {
            this.numericData.clear();
        } else {
            this.numericData = numericData;
        }
    }

    public Collection<StringData> getStringData() {
        return stringData;
    }

    /**
     * @param stringData  if null set to empty list
     */
    public void setStringData(Collection<StringData> stringData) {
        if (null == stringData) {
            this.stringData.clear();
        } else {
            this.stringData = stringData;
        }
    }

    // to be safe, slap some JsonIgnore annotations on these helper methods, I know the "isEmpty()" method screwed 
    // up jax-rs, because it thought 'empty' was a field...

    @JsonIgnore
    public boolean isEmpty() {
        return availability.isEmpty() && numericData.isEmpty() && stringData.isEmpty();
    }

    @JsonIgnore
    public int size() {
        return availability.size() + numericData.size() + stringData.size();
    }

    @JsonIgnore
    public void clear() {
        availability.clear();
        numericData.clear();
        stringData.clear();
    }

    @JsonIgnore
    public Collection<Data> asCollection() {
        Collection<Data> result = new ArrayList<>(size());
        result.addAll(availability);
        result.addAll(numericData);
        result.addAll(stringData);
        return result;
    }

    @Override
    public String toString() {
        return "MixedData [availability=" + availability + ", numericData=" + numericData
                + ", stringData=" + stringData + "]";
    }

}
