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
package org.hawkular.alerts.engine.impl.ispn.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.Store;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Indexed(index = "actionPlugin")
public class IspnActionPlugin implements Serializable {
    @Field(store = Store.YES, analyze = Analyze.NO)
    private String actionPlugin;
    private Map<String, String> defaultProperties;

    public IspnActionPlugin() {
    }

    public IspnActionPlugin(String actionPlugin, Map<String, String> defaultProperties) {
        this.actionPlugin = actionPlugin;
        this.defaultProperties = new HashMap<>(defaultProperties);
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public Map<String, String> getDefaultProperties() {
        return new HashMap<>(defaultProperties);
    }

    public void setDefaultProperties(Map<String, String> defaultProperties) {
        this.defaultProperties = new HashMap<>(defaultProperties);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IspnActionPlugin that = (IspnActionPlugin) o;

        if (actionPlugin != null ? !actionPlugin.equals(that.actionPlugin) : that.actionPlugin != null) return false;
        return defaultProperties != null ? defaultProperties.equals(that.defaultProperties) : that.defaultProperties == null;
    }

    @Override
    public int hashCode() {
        int result = actionPlugin != null ? actionPlugin.hashCode() : 0;
        result = 31 * result + (defaultProperties != null ? defaultProperties.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "IspnActionPlugin{" +
                "actionPlugin='" + actionPlugin + '\'' +
                ", defaultProperties=" + defaultProperties +
                '}';
    }
}
