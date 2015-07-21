HAWKULAR

--

<#if plainSubject??>
${plainSubject}

</#if>
Start time:
<#if alert??>
${alert.ctime?number_to_datetime} <#if alert.dampening?? && alert.dampening.type == 'STRICT_TIME'>(Alert triggered after ${alert.dampening.evalTimeSetting/1000} seconds )</#if>

</#if>
<#if type?? && type == 'THRESHOLD' && average?? && condition??>
Average response time:
${average} ms (threshold is ${condition.threshold} ms)

</#if>
<#if (numConditions > 1)>
Conditions:
    <#list condDescs as condDesc>
    ${condDesc.type}: ${condDesc.description}
    </#list>
</#if>
<#if alert?? && alert.status?? && alert.status == 'ACKNOWLEDGED'>
Acknowledge time:
${alert.ackTime?number_to_datetime}
<#if alert.ackBy?? >

by ${alert.ackBy}
</#if>
<#if alert.ackNotes?? >

${alert.ackNotes}
</#if>
</#if>
<#if alert?? && alert.status?? && alert.status == 'RESOLVED'>

Resolved time:
${alert.resolvedTime?number_to_datetime}
<#if alert.resolvedBy?? >

by ${alert.resolvedBy}
</#if>
<#if alert.resolvedNotes?? >

${alert.resolvedNotes}
</#if>
</#if>

<#if url??>
To view metrics of this alert, access your Hawkular account:
${url}
</#if>