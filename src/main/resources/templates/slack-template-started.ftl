<#if executionData.job.group??>
    <#assign jobName="${executionData.job.group} / ${executionData.job.name}">
<#else>
    <#assign jobName="${executionData.job.name}">
</#if>
<#assign message="STARTED Job <${executionData.href}|#${executionData.id} ${jobName}>">
<#assign state="Started">

{
<#if  (channel)?has_content> "channel":"${channel}",</#if>
<#if (username)?has_content>"username":"${username}",</#if>
<#if (icon_url)?has_content>"icon_url":"${icon_url}",<#else>"icon_emoji": ":rundeck:",</#if>
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
               "title":"Project",
               "value":"${executionData.project}",
               "short":true
            },
            {
               "title":"Started By",
               "value":"${executionData.user}",
               "short":true
            },
            {
                "title": "Job Start Time",
                "value": "${executionData.dateStartedW3c}",
                "short": true
            },
            {
                "title": "Job Status",
                "value": "Started",
                "short": true
            }
         ]
      }
   ]
}
