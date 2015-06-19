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
<#if url??>
To view metrics of this alert, access your Hawkular account:
${url}
</#if>