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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;


/**
 * <p>
 * Adapted from http://blog.xurmo.ai/2014/03/03/handle-java-interfaces-using-gson/.
 * </p>
 * To be used with:
 * <code>
 * gsonBuilder.registerTypeHierarchyAdapter(T, new GsonAdapter());
 * </code>
 * @param T type parameter
 * @author SAURABH SANTHOSH
 * @author jay shaughnessy
 */
public class GsonAdapter<T extends Object> implements JsonSerializer<T>, JsonDeserializer<T> {
    /*
     * Represent The Serialized version of the actual instance
     */
    private static final String PAYLOAD = "pL";
    /*
     * The Type of the instance under consideration
     */
    private static final String INSTANCE_TYPE = "typ";

    /*
     * Another instance of Serializer to give an external context
     */
    private final Gson gson = new GsonBuilder().create();

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        final JsonObject container = (JsonObject) json;
        final JsonPrimitive type = getEmbeddedInfo(container, INSTANCE_TYPE);
        final JsonPrimitive payload = getEmbeddedInfo(container, PAYLOAD);
        final Type instanceType = getInstanceType(type.getAsString());
        return gson.fromJson(payload.getAsString(), instanceType);
    }

    private JsonPrimitive getEmbeddedInfo(final JsonObject container, String key) {
        final JsonPrimitive value = container.getAsJsonPrimitive(key);
        if (value == null) {
            throw new JsonParseException("Essential Key : '" + key
                    + "' Not Found. Corrupt Serialized String!");
        }
        return value;
    }

    private Type getInstanceType(final String type) {
        try {
            return Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw new JsonParseException(e);
        }
    }

    @Override
    public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext serContext) {
        final JsonObject container = new JsonObject();
        String name = src.getClass().getName();
        container.addProperty(INSTANCE_TYPE, name);
        container.addProperty(PAYLOAD, gson.toJson(src));
        return container;
    }

}
