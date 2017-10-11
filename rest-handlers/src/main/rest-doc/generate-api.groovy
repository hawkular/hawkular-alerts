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

import org.hawkular.alerts.api.json.JsonUtil
import org.hawkular.alerts.api.doc.*

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.stream.Collectors

def handlersDir = new File(classesDir, "org/hawkular/alerts/handlers")
def generatedFile = new File(generatedFile)
def cl = Thread.currentThread().getContextClassLoader()
def json = [:]
def definitions = new HashSet<Class>()
def processed = new HashSet<Class>()

json["info"] = [version: alertingVersion, title: "Hawkular Alerting"]
json["basePath"] = "/hawkular/alerts"
json["tags"] = []
json["paths"] = [:]
json["definitions"] = [:]

/*
    Read handlers annotations
 */

String parseType(def field) {
    def cl = Thread.currentThread().getContextClassLoader()
    def fieldType
    if (field.type.isEnum()) {
        fieldType = "String"
    } else if (Collection.class.isAssignableFrom(field.type) || Map.class.isAssignableFrom(field.type)) {
        ParameterizedType genericType = field.getGenericType()
        if (genericType.actualTypeArguments.length > 0) {
            fieldType = (field.type.simpleName == "Set" || field.type.simpleName == "Collection" ? "List" : field.type.simpleName) + " of "
            fieldType += field.type.simpleName == "Map" ? "[" : ""
            for (def j = 0; j < genericType.actualTypeArguments.length; j++) {
                if (genericType.actualTypeArguments[j] instanceof ParameterizedType) {
                    ParameterizedType innerType = genericType.actualTypeArguments[j]
                    if (innerType.actualTypeArguments.length > 0) {
                        fieldType += (innerType.rawType.simpleName == "Set" || field.type.simpleName == "Collection" ? "List" : innerType.rawType.simpleName) + " of "
                        fieldType += (innerType.rawType.simpleName == "Map" ? "[" : "")
                        for (def k = 0; k < innerType.actualTypeArguments.length; k++) {
                            Class clazz = cl.loadClass(innerType.actualTypeArguments[j].typeName)
                            if (clazz.name.startsWith("java")) {
                                fieldType += clazz.simpleName
                            } else {
                                fieldType += "<<" + clazz.simpleName + ">>"
                            }
                            fieldType += (innerType.rawType.simpleName == "Map" && k == 0 ? ", " : "")
                        }
                        fieldType += (innerType.rawType.simpleName == "Map" ? "]" : "")
                    } else {
                        fieldType += innerType.type.simpleName
                    }
                } else {
                    Class clazz = cl.loadClass(genericType.actualTypeArguments[j].typeName)
                    if (clazz.name.startsWith("java")) {
                        fieldType += clazz.simpleName
                    } else {
                        fieldType += "<<" + clazz.simpleName + ">>"
                    }
                }
                fieldType += (field.type.simpleName == "Map" && j == 0 ? ", " : "")
            }
            fieldType += field.type.simpleName == "Map" ? "]" : ""
        } else {
            fieldType = field.type.simpleName
        }
    } else {
        if (field.type.name.startsWith("org.hawkular")) {
            fieldType = "<<" + field.type.simpleName + ">>"
        } else {
            fieldType = field.type.simpleName
        }
    }
    return fieldType
}

String parseAllowableValues(DocModelProperty prop, Field field) {
    if (prop.allowableValues() != "") {
        return prop.allowableValues()
    }
    Class fieldClass = parseSubclass(field)
    if (fieldClass.isEnum()) {
        return Arrays.asList(fieldClass.getEnumConstants())
            .stream()
            .map {c -> c.toString() }
            .collect(Collectors.joining(", "))
    }
    return "-"
}

Class parseSubclass(def field) {
    if (field.type.isEnum() ||
            (!Collection.class.isAssignableFrom(field.type)
                    && !Map.class.isAssignableFrom(field.type))) {
        return field.type
    }

    // Get the hawkular subclass on a List, or Map supporting one level of inner collection
    def cl = Thread.currentThread().getContextClassLoader()
    ParameterizedType genericType = field.getGenericType()
    if (genericType.actualTypeArguments.length > 0) {
        for (def j = 0; j < genericType.actualTypeArguments.length; j++) {
            if (genericType.actualTypeArguments[j] instanceof ParameterizedType) {
                ParameterizedType innerType = genericType.actualTypeArguments[j]
                if (innerType.actualTypeArguments.length > 0) {
                    for (def k = 0; k < innerType.actualTypeArguments.length; k++) {
                        Class clazz = cl.loadClass(innerType.actualTypeArguments[j].typeName)
                        if (clazz.name.startsWith("org.hawkular")) {
                            return clazz
                        }
                    }
                }
            } else {
                Class clazz = cl.loadClass(genericType.actualTypeArguments[j].typeName)
                if (clazz.name.startsWith("org.hawkular")) {
                    return clazz
                }
            }
        }
    }

    return String.class
}

try {

    File[] handlers = handlersDir.listFiles()
    for (def i = 0; i < handlers.length; i++) {
        if (handlers[i].name.endsWith("Handler.class")) {
            def className = "org.hawkular.alerts.handlers." + handlers[i].name.substring(0, handlers[i].name.length() - 6)
            def clazz = cl.loadClass(className)
            def endPointPath
            if (clazz.isAnnotationPresent(DocEndpoint.class)) {
                DocEndpoint docEndpoint = clazz.getAnnotation(DocEndpoint.class)
                endPointPath = docEndpoint.value()
                json["tags"] << [name: endPointPath, description: docEndpoint.description()]
            }
            for (def j = 0; j < clazz.methods.length; j++) {
                Method method = clazz.methods[j]
                if (method.isAnnotationPresent(DocPath.class)) {
                    DocPath docPath = method.getAnnotation(DocPath.class)

                    def path = (endPointPath == '/' && docPath.path() != '/' ? '' : endPointPath) + (docPath.path() == '/' ? '' : docPath.path())
                    if (!json["paths"].containsKey(path)) {
                        json["paths"][path] = [:]
                    }
                    json["paths"][path][docPath.method()] = [:]
                    json["paths"][path][docPath.method()]["tags"] = [ endPointPath ]
                    json["paths"][path][docPath.method()]["summary"] = docPath.name()
                    json["paths"][path][docPath.method()]["description"] = docPath.notes()
                    json["paths"][path][docPath.method()]["consumes"] = docPath.consumes()
                    json["paths"][path][docPath.method()]["produces"] = docPath.produces()

                    if (method.isAnnotationPresent(DocParameters.class)) {
                        json["paths"][path][docPath.method()]["parameters"] = []
                        DocParameters docParameters = method.getAnnotation(DocParameters.class)
                        for (def k = 0; k < docParameters.value().length; k++) {
                            DocParameter docParameter = docParameters.value()[k]
                            def param = [:]
                            param["name"] = docParameter.name()
                            param["in"] = docParameter.body() ? "body" : (docParameter.path() ? "path" : "query")
                            param["description"] = docParameter.description()
                            param["required"] = docParameter.required()
                            param["type"] = docParameter.type().simpleName
                            param["typeContainer"] = docParameter.typeContainer()
                            param["allowableValues"] = docParameter.allowableValues()

                            json["paths"][path][docPath.method()]["parameters"] << param

                            definitions.add(docParameter.type())
                        }
                    }

                    if (method.isAnnotationPresent(DocResponses.class)) {
                        json["paths"][path][docPath.method()]["responses"] = [:]
                        DocResponses docResponses = method.getAnnotation(DocResponses.class)
                        for (def k = 0; k < docResponses.value().length; k++) {
                            DocResponse docResponse = docResponses.value()[k]
                            json["paths"][path][docPath.method()]["responses"]["${docResponse.code()}"] = [:]
                            json["paths"][path][docPath.method()]["responses"]["${docResponse.code()}"]["description"] = docResponse.message()
                            json["paths"][path][docPath.method()]["responses"]["${docResponse.code()}"]["type"] = docResponse.response().simpleName
                            json["paths"][path][docPath.method()]["responses"]["${docResponse.code()}"]["typeContainer"] = docResponse.responseContainer()

                            definitions.add(docResponse.response())
                        }
                    }
                }
            }
        }
    }

    while (!definitions.isEmpty()) {

        def subClasses = new HashSet<Class>()
        def defIterator = definitions.iterator()
        while (defIterator.hasNext()) {
            Class clazz = defIterator.next()
            processed.add(clazz)
            if (clazz.isAnnotationPresent(DocModel.class)) {
                def className = clazz.simpleName
                json["definitions"][className] = [:]
                DocModel docModel = clazz.getAnnotation(DocModel.class)
                json["definitions"][className]["description"] = docModel.description()
                if (clazz.getGenericSuperclass().typeName.startsWith("org.hawkular")) {
                    Class parent = cl.loadClass(clazz.getGenericSuperclass().typeName)
                    json["definitions"][className]["parent"] = parent.simpleName
                }
                json["definitions"][className]["properties"] = [:]

                Field[] fields = clazz.getDeclaredFields()
                for (def i = 0; i < fields.length; i++) {
                    if (fields[i].isAnnotationPresent(DocModelProperty.class)) {
                        DocModelProperty docModelProperty = fields[i].getDeclaredAnnotation(DocModelProperty.class)
                        def fieldName = fields[i].name
                        def fieldType = parseType(fields[i])
                        def allowableValues = parseAllowableValues(docModelProperty, fields[i])

                        json["definitions"][className]["properties"][fieldName] = [:]
                        json["definitions"][className]["properties"][fieldName]["type"] = fieldType
                        json["definitions"][className]["properties"][fieldName]["description"] = docModelProperty.description()
                        json["definitions"][className]["properties"][fieldName]["allowableValues"] = allowableValues
                        json["definitions"][className]["properties"][fieldName]["defaultValue"] = docModelProperty.defaultValue()

                        Class subclass = parseSubclass(fields[i])
                        if (!processed.contains(subclass)) {
                            subClasses.add(subclass)
                        }
                    }
                }

                if (docModel.subTypes().length > 0) {
                    for (def i = 0; i < docModel.subTypes().length; i++) {
                        if (!processed.contains(docModel.subTypes()[i])) {
                            subClasses.add(docModel.subTypes()[i])
                        }
                    }
                }
            }
            defIterator.remove()
        }

        if (!subClasses.isEmpty()) {
            definitions.addAll(subClasses)
        }
    }

    /*
        Write output json file
     */

    generatedFile.getParentFile().mkdirs()
    JsonUtil.mapper.writeValue(generatedFile, json)

} catch (Exception e) {

    e.printStackTrace()

}

