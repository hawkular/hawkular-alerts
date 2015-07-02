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
package org.hawkular.alerts.api.json;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hawkular.alerts.api.model.condition.Alert;
import org.hawkular.alerts.api.model.condition.ConditionEval;

/**
 * Json serialization/deserialization utility for Alerts using GSON implementation.
 * GSON
 * @author Lucas Ponce
 */
public class GsonUtil {

    private static GsonUtil instance = new GsonUtil();
    private Gson gson;
    private Gson gsonThin;

    private GsonUtil() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(ConditionEval.class, new GsonAdapter<ConditionEval>());
        gson = gsonBuilder.create();

        GsonBuilder gsonBuilderThin = new GsonBuilder();
        gsonBuilderThin.registerTypeHierarchyAdapter(ConditionEval.class, new GsonAdapter<ConditionEval>());
        gsonBuilderThin.addDeserializationExclusionStrategy(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                final Alert.Thin thin = f.getAnnotation(Alert.Thin.class);
                return thin != null;
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        });
        gsonThin = gsonBuilderThin.create();
    }

    public static String toJson(Object resource) {
        return instance.gson.toJson(resource);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return fromJson(json,clazz, false);
    }

    public static <T> T fromJson(String json, Class<T> clazz, boolean thin) {
        return thin ? instance.gsonThin.fromJson(json, clazz) : instance.gson.fromJson(json, clazz);
    }
}
