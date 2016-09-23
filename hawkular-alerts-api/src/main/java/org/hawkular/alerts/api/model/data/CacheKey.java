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
package org.hawkular.alerts.api.model.data;

import java.io.Serializable;

public class CacheKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String dataIdPrefix;
    private String dataIdSuffix;

    public CacheKey(String tenantId, String dataIdPrefix, String dataIdSuffix) {
        this.tenantId = tenantId;
        this.dataIdPrefix = null != dataIdPrefix ? dataIdPrefix : "";
        this.dataIdSuffix = dataIdSuffix;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getDataIdPrefix() {
        return dataIdPrefix;
    }

    public void setDataIdPrefix(String dataIdPrefix) {
        this.dataIdPrefix = dataIdPrefix;
    }

    public String getDataIdSuffix() {
        return dataIdSuffix;
    }

    public void setDataIdSuffix(String dataIdSuffix) {
        this.dataIdSuffix = dataIdSuffix;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dataIdPrefix == null) ? 0 : dataIdPrefix.hashCode());
        result = prime * result + ((dataIdSuffix == null) ? 0 : dataIdSuffix.hashCode());
        result = prime * result + ((tenantId == null) ? 0 : tenantId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CacheKey other = (CacheKey) obj;
        if (dataIdPrefix == null) {
            if (other.dataIdPrefix != null)
                return false;
        } else if (!dataIdPrefix.equals(other.dataIdPrefix))
            return false;
        if (dataIdSuffix == null) {
            if (other.dataIdSuffix != null)
                return false;
        } else if (!dataIdSuffix.equals(other.dataIdSuffix))
            return false;
        if (tenantId == null) {
            if (other.tenantId != null)
                return false;
        } else if (!tenantId.equals(other.tenantId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CacheKey [tenantId=" + tenantId + ", dataIdPrefix=" + dataIdPrefix + ", dataIdSuffix=" + dataIdSuffix
                + "]";
    }

}
