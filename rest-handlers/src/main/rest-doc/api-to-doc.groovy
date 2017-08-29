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
import java.util.Map.Entry

import groovy.json.JsonSlurper

def baseFile = new File(baseFile)
if (!baseFile.canRead()) throw new RuntimeException("${baseFile.path} is not readable")

def generatedFile = new File(generatedFile)
if (!generatedFile.canRead()) throw new RuntimeException("${generatedFile.path} is not readable")

def jsonSlurper = new JsonSlurper()
def swagger = jsonSlurper.parse(generatedFile)

def apidocFile = new File(outputFile)

baseFile.withInputStream { stream ->
    apidocFile.append(stream)
}

apidocFile.withWriterAppend('UTF-8') { writer ->

    writer.println """

== Base Path
`${swagger.basePath}`

== REST APIs

"""
    swagger.tags.sort { t1, t2 -> t1.name.compareTo(t2.name) }.each { tag ->
        writer.println "=== [ ${tag.name} ] ${tag.description}"
        writer.println "\n|==="
        swagger.paths.sort { p1, p2 -> p1.key.compareTo(p2.key) }.each { Entry path ->
            path.value.each { Entry method ->

                if (method.value.tags != null && method.value.tags.contains(tag.name)) {
                    writeEndpointLink(writer, path, method)
                }
            }
        }
        writer.println "|==="

        swagger.paths.sort { p1, p2 -> p1.key.compareTo(p2.key) }.each { Entry path ->
            path.value.each { Entry method ->
                if (method.value.tags != null && method.value.tags.contains(tag.name)) {

                    writeEndpointHeader(writer, path, method)

                    def params = method.value.parameters ?: []
                    writeParams(writer, 'Path', params.findAll { it.in == 'path' })
                    writeParams(writer, 'Query', params.findAll { it.in == 'query' })
                    writeBodyParams(writer, params.findAll { it.in == 'body' })

                    writeResponses(writer, method.value.responses ?: [:])

                    writeEndpointFooter(writer)
                }
            }
        }
    }
    writer.println '''== Data Types

'''

    writeDataTypes(writer, swagger.definitions ?: [:])
}

private writeEndpointLink(Writer writer, Entry path, Entry method) {
    def summary = method.value.summary.trim()
    String endpoint = path.key
    def httpMethod = method.key.toUpperCase()
    Object anchor = getEndpointAnchor(httpMethod, endpoint)

    writer.println "| ${httpMethod} | ${endpoint} | link:#++${anchor}++[${summary}]"
}

def String getEndpointAnchor(def httpMethod, String endpoint) {
    def anchor = httpMethod + '_' + endpoint.replace('/', '_').replace('{', '_').replace('}', '_')
    anchor
}

private writeEndpointHeader(Writer writer, Entry path, Entry method) {
    def summary = method.value.summary.trim()
    def description = method.value.description?.trim()
    String endpoint = path.key
    def httpMethod = method.key.toUpperCase()
    def anchor = getEndpointAnchor(httpMethod, endpoint)

    writer.println """

==============================================

[[${anchor}]]
*Endpoint ${httpMethod} `${endpoint}`*

${summary} +
 +
${description ? "${description}" : ''}

"""
}

def writeParams(Writer writer, String paramType, List params) {
    if (!params.empty) {
        writer.println """
*${paramType} parameters*

[cols="15,^10,35,^15,^15", options="header"]
|=======================
|Parameter|Required|Description|Type|Allowable Values
"""
        params.each { param ->
            def name = param.name
            def required = required(param.required)
            def description = param.description ?: '-'
            def type = param.type
            def allowableValues = param.allowableValues != '' ? param.allowableValues : '-'
            writer.println "|${name}|${required}|${description}|${type}|${allowableValues}"
        }
        writer.println '''
|=======================

'''
    }
}

def writeBodyParams(Writer writer, List params) {
    if (!params.empty) {
        writer.println """
*Body*

[cols="^20,55,^25", options="header"]
|=======================
|Required|Description|Data Type
"""
        params.each { param ->
            def required = required(param.required)
            def description = param.description ?: '-'
            def type = (param.typeContainer != '' ? "${param.typeContainer} of " : '') + wrapType(param.type)
            writer.println "|${required}|${description}|${type}"
        }
        writer.println """
|=======================

"""
    }
}

def void writeResponses(Writer writer, Map responses) {
    if (!responses.empty) {
        writer.println """
*Response*

*Status codes*
[cols="^20,55,^25", options="header"]
|=======================
|Status Code|Reason|Response Model
"""
        responses.each { Entry response ->
            def code = response.key
            def description = response.value.description ?: '-'
            def responseType = (response.value.typeContainer != '' ? "${response.value.typeContainer} of " : '') + (response.value.type != 'NULL' ? wrapType(response.value.type) : '-')
            writer.println "|${code}|${description}|${responseType}"
        }
        writer.println """
|=======================

"""
    }
}

def writeEndpointFooter(Writer writer) {
    writer.println """
==============================================

"""
}

def writeDataTypes(Writer writer, Map definitions) {
    definitions.each { Entry definition ->
        def definitionName = definition.key
        def definitionValue = definition.value.allOf ? definition.value.allOf[1] : definition.value
        def definitionParent
        if (definition.value.parent) {
            definitionParent = "Subtype of <<${definition.value.parent}>>"
        }
        writer.println """
[[${definitionName}]]
=== ${definitionName}
${definitionValue.description}

${definitionParent ? definitionParent : ''}
[cols="15,^10,35,^15,^15,^15", options="header"]
|=======================
|Name|Required|Description|Type|Allowable Values|Default Value
"""
        (definitionValue.properties ?: []).sort{ a, b -> a.value.position <=> b.value.position }
        .each { Entry property ->
            def propertyName = property.key
            def required = required((definitionValue.required ?: []).contains(propertyName))
            def description = property.value.description ?: '-'
            def type = property.value.type
            def allowableValues = property.value.allowableValues
            def defaultValue = property.value.defaultValue
            writer.println "|${propertyName}|${required}|${description}|${type}|${allowableValues}|${defaultValue}"
        }
        writer.println '''
|=======================
'''
    }
}

String required(boolean val) {
    val ? 'Yes' : 'No'
}

String wrapType(def type) {
    if (type == 'String' || type == 'Boolean' || type == 'Long') {
        return type;
    } else {
        return "<<${type}>>"
    }
}