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
package org.hawkular.alerts.rest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.enterprise.context.ApplicationScoped;
import javax.servlet.ServletContext;

import com.google.common.collect.ImmutableList;

/**
 * Manifest extraction.
 * Credits to Hawkular Metrics team.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApplicationScoped
public class ManifestUtil {
    private static final String IMPLEMENTATION_VERSION = "Implementation-Version";
    private static final String BUILT_FROM_GIT = "Built-From-Git-SHA1";

    private static final List<String> VERSION_ATTRIBUTES = ImmutableList.of(IMPLEMENTATION_VERSION,
            BUILT_FROM_GIT);

    private Map<String, String> manifestInformation = new HashMap<>();

    public Map<String, String> getFrom(ServletContext servletContext) {
        if (!manifestInformation.containsKey(IMPLEMENTATION_VERSION) &&
                !manifestInformation.containsKey(BUILT_FROM_GIT)) {
            try (InputStream inputStream = servletContext.getResourceAsStream("/META-INF/MANIFEST.MF")) {
                Manifest manifest = new Manifest(inputStream);
                Attributes attr = manifest.getMainAttributes();
                for (String attribute : VERSION_ATTRIBUTES) {
                    manifestInformation.put(attribute, attr.getValue(attribute));
                }
            } catch (Exception e) {
                for (String attribute : VERSION_ATTRIBUTES) {
                    if (manifestInformation.get(attribute) == null) {
                        manifestInformation.put(attribute, "Unknown");
                    }
                }
            }
        }
        return manifestInformation;
    }
}
