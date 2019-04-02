<#if executionData.job.group??>
    <#assign jobName="${executionData.job.group} / ${executionData.job.name}">
<#else>
    <#assign jobName="${executionData.job.name}">
</#if>
<#assign message="FAILED! Job <${executionData.href}|#${executionData.id}  ${jobName}>">
<#assign state="Failed">

<#if executionData.argstring??>
    <#assign args="${executionData.argstring}">
<#else>
    <#assign args="No options to display">
</#if>


<#if executionData.succeededNodeListString??>
    <#assign successNodes="${executionData.succeededNodeListString}">
<#else>
    <#assign successNodes="None">
</#if>

{
<#if channel?has_content >
   "channel":"${channel}",
</#if>
<#if username?has_content >
   "username": "${username}",
</#if>
<#if icon_url?has_content >
   "icon_emoji":"${icon_url}",
</#if>
   "attachments":[
      {
         "fallback":"${state}: ${message}",
         "pretext":"${message}",
         "color":"${color}",
         "fields":[
            {
               "title":"Job Name",
               "value":"<${executionData.job.href}|${jobName}>",
               "short":false
            },
            {
                "title": "Job Start Time",
                "value": "${executionData.dateStartedW3c}",
                "short": true
            },
            {
               "title":"Started By",
               "value":"${executionData.user}",
               "short":true
            },
            {
                "title": "Project",
                "value": "${executionData.project}",
                "short": true
            },
            {
               "title":"Options",
               "value":"${args}",
               "short":false
            }
	     ]
      }
   ]
}
