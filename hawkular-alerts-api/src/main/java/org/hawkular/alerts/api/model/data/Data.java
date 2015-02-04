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
 * A base class for incoming data into alerts subsystem.  All {@link Data} has an Id and a timestamp. The
 * timestamp is used to ensure that data is time-ordered when being sent into the alerting engine.  If
 * not assigned the timestamp will be assigned to current time.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Data {

    private String id;
    private long timestamp;

    public Data() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this.id = null;
    }

    /**
     * @param id not null.
     * @param timestamp if <=0 assigned currentTime.
     */
    public Data(String id, long timestamp) {
        this.id = id;
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @param timestamp if <=0 assigned currentTime.
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = (timestamp <= 0) ? System.currentTimeMillis() : timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Data other = (Data) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (timestamp != other.timestamp)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Data [id=" + id + ", timestamp=" + timestamp + "]";
    }

}
