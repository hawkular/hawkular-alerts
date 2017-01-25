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
package org.hawkular.alerts.rest;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CommonUtil {

    public static boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static Map<String, String> parseTags(String tags) {
        if (isEmpty(tags)) {
            return null;
        }
        String[] tagTokens = tags.split(",");
        Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
        for (String tagToken : tagTokens) {
            String[] fields = tagToken.split("\\|");
            if (fields.length == 2) {
                tagsMap.put(fields[0], fields[1]);
            } else {
                throw new IllegalArgumentException("Invalid Tag Criteria " + Arrays.toString(fields));
            }
        }
        return tagsMap;
    }

    public static String parseTagQuery(Map<String, String> tags) {
        if (isEmpty(tags)) {
            return null;
        }
        StringBuilder tagQuery = new StringBuilder();
        Iterator it = tags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> tag = (Map.Entry<String, String>)it.next();
            tagQuery.append(tag.getKey());
            if (!"*".equals(tag.getValue())) {
                tagQuery.append(" = ").append("'").append(tag.getValue()).append("'");
            }
            if (it.hasNext()) {
                tagQuery.append(" or ");
            }
        }
        return tagQuery.toString();
    }
}
