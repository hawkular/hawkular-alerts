HAWKULAR

--

${emailSubject}

Start time:
<#if alert??>
${alert.ctime?number_to_datetime}
<#if dampeningDescription??>(${dampeningDescription})</#if>

</#if>
<#if (numConditions > 1)>
Conditions:
    <#list conditions as condition>
    ${condition.description} <#if condition.averageDescription??>(Average ${condition.averageDescription}) </#if>

    </#list>
</#if>
<#if alert?? && alert.status?? && alert.status == 'ACKNOWLEDGED'>
Acknowledge time:
${alert.ackTime?number_to_datetime}
<#if alert.ackBy?? >

by ${alert.ackBy}
</#if>
</#if>
<#if alert?? && alert.status?? && alert.status == 'RESOLVED'>

Resolved time:
${alert.resolvedTime?number_to_datetime}
<#if alert.resolvedBy?? >

by ${alert.resolvedBy}
</#if>
</#if>
<#if alert.notes?has_content>

Notes:

<#list alert.notes as note>
${note.text} (${note.user}, ${note.ctime?number_to_datetime})
</#list>

</#if>
<#if baseUrl??>
To view metrics of this alert, access your Hawkular account:
${baseUrl}
</#if>