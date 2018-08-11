<#if executionData.job.group??>
    <#assign jobName="${executionData.job.group} / ${executionData.job.name}">
<#else>
    <#assign jobName="${executionData.job.name}">
</#if>
<#assign message="STARTED Job <${executionData.href}|#${executionData.id} ${jobName}>">
<#assign state="Started">

{
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
