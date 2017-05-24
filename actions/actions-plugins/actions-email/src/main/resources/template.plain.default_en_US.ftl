HAWKULAR

--

${emailSubject}

Start time:
<#if event??>
${event.ctime?number_to_datetime}
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
${alert.currentLifecycle.stime?number_to_datetime}
<#if alert.currentLifecycle.user?? >

by ${alert.currentLifecycle.user}
</#if>
</#if>
<#if alert?? && alert.status?? && alert.status == 'RESOLVED'>

Resolved time:
${alert.currentLifecycle.stime?number_to_datetime}
<#if alert.currentLifecycle.user?? >

by ${alert.currentLifecycle.user}
</#if>
</#if>
<#if alert?? && alert.notes?? && alert.notes?has_content>

Notes:

<#list alert.notes as note>
<#if note.text?? && note.user??>${note.text} (${note.user}, ${note.ctime?number_to_datetime})</#if>
</#list>

</#if>
<#if baseUrl??>
To view metrics of this alert, access your Hawkular account:
${baseUrl}
</#if>