<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
    and other contributors as indicated by the @author tags.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xalan="http://xml.apache.org/xalan"
                xmlns:ds="urn:jboss:domain:datasources:3.0"
                xmlns:ra="urn:jboss:domain:resource-adapters:3.0"
                xmlns:ejb3="urn:jboss:domain:ejb3:3.0"
                xmlns:undertow="urn:jboss:domain:undertow:2.0"
                xmlns:tx="urn:jboss:domain:transactions:3.0"
                version="2.0"
                exclude-result-prefixes="xalan ds ra ejb3 undertow tx">

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no"/>
  <xsl:strip-space elements="*"/>

  <xsl:template match="node()[name(.)='cache-container'][1]">
    <xsl:copy>
      <xsl:copy-of select="node()|@*"/>
    </xsl:copy>
    <cache-container name="hawkular-alerts" default-cache="triggers" statistics-enabled="true">
      <transport lock-timeout="60000"/>
      <replicated-cache name="partition" mode="SYNC">
        <transaction mode="BATCH"/>
      </replicated-cache>
      <replicated-cache name="triggers" mode="ASYNC">
        <transaction mode="BATCH"/>
      </replicated-cache>
      <replicated-cache name="data" mode="ASYNC">
        <transaction mode="BATCH"/>
      </replicated-cache>
      <replicated-cache name="publish" mode="ASYNC">
        <transaction mode="BATCH"/>
      </replicated-cache>
      <replicated-cache name="dataIds" mode="ASYNC">
        <transaction mode="BATCH"/>
      </replicated-cache>
      <replicated-cache name="schema" mode="SYNC">
        <transaction mode="NON_XA"/>
        <locking acquire-timeout="100000" />
      </replicated-cache>
      <replicated-cache name="globalActions" mode="ASYNC">
        <transaction mode="BATCH"/>
      </replicated-cache>
    </cache-container>
  </xsl:template>

  <xsl:template match="node()[name(.)='periodic-rotating-file-handler']">
    <xsl:copy>
      <xsl:copy-of select="node()|@*"/>
    </xsl:copy>
    <logger category="org.hawkular.alerts.engine.impl.PartitionManagerImpl">
      <level name="DEBUG"/>
    </logger>
    <logger category="org.hawkular.alerts.engine.impl.AlertsEngineImpl">
      <level name="DEBUG"/>
    </logger>
    <logger category="org.hawkular.alerts.engine.impl.DroolsRulesEngineImpl">
      <level name="DEBUG"/>
    </logger>
  </xsl:template>

  <xsl:template match="node()[name(.)='server-groups']">
    <server-groups>
      <server-group name="cassandra-group" profile="default" >
        <jvm name="default">
          <heap size="64m" max-size="512m"/>
        </jvm>
        <socket-binding-group ref="standard-sockets"/>
      </server-group>
      <server-group name="hawkular-alerts-group" profile="ha" >
        <jvm name="default">
          <heap size="64m" max-size="512m"/>
          <jvm-options>
            <option value="-Dhawkular.allowed-cors-origins=http://test.hawkular.org,https://secure.hawkular.io"/>
            <option value="-Dhawkular.allowed-cors-access-control-allow-headers=random-header1,random-header2"/>
            <option value="-Dmail.smtp.host=localhost"/>
            <option value="-Dmail.smtp.port=2525"/>
          </jvm-options>
        </jvm>
        <socket-binding-group ref="ha-sockets"/>
      </server-group>
    </server-groups>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
