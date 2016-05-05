<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

  <!-- will indicate if this is a "dev" build or "production" build -->
  <xsl:param name="kettle.build.type" />

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no"/>
  <xsl:strip-space elements="*"/>

  <xsl:template match="node()[name(.)='cache-container'][1]">
    <xsl:copy>
      <xsl:copy-of select="node()|@*"/>
    </xsl:copy>
    <cache-container name="hawkular-alerts" default-cache="triggers" statistics-enabled="true">
      <local-cache name="partition"/>
      <local-cache name="triggers"/>
      <local-cache name="data"/>
    </cache-container>
  </xsl:template>

  <xsl:template match="//*[*[local-name()='log-system-exceptions']]">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
      <mdb>
        <resource-adapter-ref resource-adapter-name="activemq-ra.rar"/>
        <bean-instance-pool-ref pool-name="mdb-strict-max-pool"/>
      </mdb>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="//*[*[local-name()='in-vm-acceptor']]">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
      <jms-topic name="HawkularAlertData" entries="java:/topic/HawkularAlertData"/>
      <jms-topic name="HawkularMetricData" entries="java:/topic/HawkularMetricData java:jboss/exported/topic/HawkularMetricData"/>
      <jms-queue name="HawkularAlertsPluginsQueue" entries="java:/queue/HawkularAlertsPluginsQueue"/>
      <jms-queue name="HawkularAlertsActionsResponseQueue" entries="java:/queue/HawkularAlertsActionsResponseQueue"/>
      <jms-topic name="HawkularAvailData" entries="java:/topic/HawkularAvailData java:jboss/exported/topic/HawkularAvailData"/>
      <jms-topic name="HawkularCommandEvent" entries="java:/topic/HawkularCommandEvent"/>
      <jms-topic name="HawkularAlertsActionsTopic" entries="java:/topic/HawkularAlertsActionsTopic"/>
    </xsl:copy>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>