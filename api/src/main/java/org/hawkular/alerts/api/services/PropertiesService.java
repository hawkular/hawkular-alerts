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
package org.hawkular.alerts.api.services;

/**
 * A interface used to share alerts properties across several components.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface PropertiesService {

    /**
     * @param key of the property to retrieve
     * @param defaultValue default value to return in case the property has not a value defined
     * @return the value of the property
     */
    String getProperty(String key, String defaultValue);

    /**
     * @param key of the property to retrieve
     * @param envKey name of the environment variable used as an alternative way to define a property
     * @param defaultValue default value to return in case the property has not a value defined
     * @return the value of the property
     */
    String getProperty(String key, String envKey, String defaultValue);
}
