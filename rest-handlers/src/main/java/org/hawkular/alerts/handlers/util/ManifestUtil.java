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
package org.hawkular.alerts.handlers.util;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ManifestUtil {
    private static final String IMPLEMENTATION_VENDOR_ID = "Implementation-Vendor-Id";
    private static final String IMPLEMENTATION_VERSION = "Implementation-Version";
    private static final String BUILT_FROM_GIT = "Built-From-Git-SHA1";
    private static final String HAWKULAR_ALERTING = "org.hawkular.alerts";

    private static final List<String> VERSION_ATTRIBUTES = Arrays.asList(IMPLEMENTATION_VERSION, BUILT_FROM_GIT);

    private Map<String, String> manifestInformation = new HashMap<>();

    public Map<String, String> getFrom() {
        extractManifest();
        return manifestInformation;
    }

    private void extractManifest() {
        if (manifestInformation.isEmpty()) {
            try {
                Enumeration<URL> urlResources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
                while (urlResources.hasMoreElements()) {
                    URL url = urlResources.nextElement();
                    Manifest manifest = new Manifest(url.openStream());
                    Attributes attr = manifest.getMainAttributes();
                    if (attr.getValue(IMPLEMENTATION_VENDOR_ID) != null
                            && attr.getValue(IMPLEMENTATION_VENDOR_ID).equals(HAWKULAR_ALERTING)) {
                        for (String attribute : VERSION_ATTRIBUTES) {
                            manifestInformation.put(attribute, attr.getValue(attribute));
                        }
                    }
                }
            } catch (IOException e) {
                for (String attribute : VERSION_ATTRIBUTES) {
                    if (manifestInformation.get(attribute) == null) {
                        manifestInformation.put(attribute, "Unknown");
                    }
                }
            }
        }
    }
}
