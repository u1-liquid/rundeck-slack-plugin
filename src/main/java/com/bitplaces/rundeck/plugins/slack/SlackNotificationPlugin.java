/*
 * Copyright 2014 Andrew Karpow
 * based on Slack Plugin from Hayden Bakkum
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.bitplaces.rundeck.plugins.slack;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyScope;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends Rundeck job notification messages to a Slack room.
 *
 * @author Hayden Bakkum
 */
@Plugin(service= "Notification", name="SlackNotification")
@PluginDescription(title="Slack Incoming WebHook", description="Sends Rundeck Notifications to Slack")
public class SlackNotificationPlugin implements NotificationPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(SlackNotificationPlugin.class);

    private static final String SLACK_MESSAGE_COLOR_GREEN = "good";
    private static final String SLACK_MESSAGE_COLOR_YELLOW = "warning";
    private static final String SLACK_MESSAGE_COLOR_RED = "danger";

    private static final String SLACK_MESSAGE_TEMPLATE_SUCCESS = "slack-template-success.ftl";
    private static final String SLACK_MESSAGE_TEMPLATE_FAILED = "slack-template-error.ftl";
    private static final String SLACK_MESSAGE_TEMPLATE_STARTED = "slack-template-started.ftl";

    private static final String TRIGGER_START = "start";
    private static final String TRIGGER_SUCCESS = "success";
    private static final String TRIGGER_FAILURE = "failure";

    private static final Map<String, SlackNotificationPlugin.SlackNotificationData> TRIGGER_NOTIFICATION_DATA = new HashMap<String, SlackNotificationPlugin.SlackNotificationData>();

    private static final Configuration FREEMARKER_CFG = new Configuration();

    @PluginProperty(title = "WebHook URL", description = "Slack Incoming WebHook URL", scope = PropertyScope.Project)
    private String webhook_url;

    @PluginProperty(title = "Notification template", description = "Custom notification template, if not supplied the default will be used", scope = PropertyScope.Project)
    private String slack_template;

    /**
     * Ctor.
     */
    public SlackNotificationPlugin() {
        SlackNotificationPlugin.TRIGGER_NOTIFICATION_DATA.put(
            SlackNotificationPlugin.TRIGGER_START,
            new SlackNotificationPlugin.SlackNotificationData(
                SlackNotificationPlugin.SLACK_MESSAGE_TEMPLATE_STARTED,
                SlackNotificationPlugin.SLACK_MESSAGE_COLOR_YELLOW
            )
        );
        SlackNotificationPlugin.TRIGGER_NOTIFICATION_DATA.put(
            SlackNotificationPlugin.TRIGGER_SUCCESS,
            new SlackNotificationPlugin.SlackNotificationData(
                SlackNotificationPlugin.SLACK_MESSAGE_TEMPLATE_SUCCESS,
                SlackNotificationPlugin.SLACK_MESSAGE_COLOR_GREEN
            )
        );
        SlackNotificationPlugin.TRIGGER_NOTIFICATION_DATA.put(
            SlackNotificationPlugin.TRIGGER_FAILURE,
            new SlackNotificationPlugin.SlackNotificationData(
                SlackNotificationPlugin.SLACK_MESSAGE_TEMPLATE_FAILED,
                SlackNotificationPlugin.SLACK_MESSAGE_COLOR_RED
            )
        );
        try {
            final TemplateLoader[] loaders = {
                new FileTemplateLoader(new File("/etc/rundeck")),
                new ClassTemplateLoader(SlackNotificationPlugin.class, "/templates")
            };
            SlackNotificationPlugin.FREEMARKER_CFG.setTemplateLoader(
                new MultiTemplateLoader(loaders)
            );
            SlackNotificationPlugin.FREEMARKER_CFG.setSetting(
                Configuration.CACHE_STORAGE_KEY, "strong:20, soft:250"
            );
        } catch (TemplateException e) {
            System.err.printf("Got a TemplateException from Freemarker: %s", e.getMessage());
        } catch (IOException e) {
            System.err.printf("Got a IOException from Freemarker: %s", e.getMessage());
        }
    }

    /**
     * Sends a message to a Slack room when a job notification event is raised by Rundeck.
     *
     * @param trigger name of job notification event causing notification
     * @param executionData job execution data
     * @param config plugin configuration
     * @throws SlackNotificationPluginException when any error occurs sending the Slack message
     * @return true, if the Slack API response indicates a message was successfully delivered to a chat room
     */
    public boolean postNotification(final String trigger, final Map executionData, final Map config) {
        if (!SlackNotificationPlugin.TRIGGER_NOTIFICATION_DATA.containsKey(trigger)) {
            throw new IllegalArgumentException(
                String.format("Unknown trigger type: [%s].", trigger)
            );
        }
        final String message = this.generateMessage(trigger, executionData, config);
        final String response = this.invokeSlackAPIMethod(message);
        final boolean result = "ok".equals(response);
        if (!result) {
            SlackNotificationPlugin.LOG.error(
                String.format(
                    "Unknown status returned from Slack API: [%s].\npayload=%s",
                    response,
                    SlackNotificationPlugin.urlEncode(message)
                )
            );
        }
        return result;
    }

    private String generateMessage(final String trigger, final Map executionData, final Map config) {
        final String template = SlackNotificationPlugin.TRIGGER_NOTIFICATION_DATA.get(trigger).template;
        final String color = SlackNotificationPlugin.TRIGGER_NOTIFICATION_DATA.get(trigger).color;

        final Map<String, Object> model = new HashMap<String, Object>();
        model.put("trigger", trigger);
        model.put("color", color);
        model.put("executionData", executionData);
        model.put("config", config);
        final StringWriter writer = new StringWriter();
        try {
            Template ftl = SlackNotificationPlugin.FREEMARKER_CFG.getTemplate(template);
            if (this.slack_template != null && !"".equals(this.slack_template)) {
                ftl = SlackNotificationPlugin.FREEMARKER_CFG.getTemplate(this.slack_template);
            }
            ftl.process(model, writer);
        } catch (final TemplateException|IOException ioe) {
            throw new SlackNotificationPluginException(
                String.format("Exception loading Slack notification message template: [%s]", ioe.getMessage()),
                ioe
            );
        }
        return writer.toString();
    }

    private String invokeSlackAPIMethod(final String message) {
        final URL url = SlackNotificationPlugin.toURL(this.webhook_url);

        HttpURLConnection connection = null;
        InputStream response = null;
        final String body = String.format(
            "payload=%s",
            SlackNotificationPlugin.urlEncode(message)
        );
        try {
            connection = SlackNotificationPlugin.openConnection(url);
            SlackNotificationPlugin.putRequestStream(connection, body);
            response = this.getResponseStream(connection);
            return this.getSlackResponse(response);
        } finally {
            this.closeQuietly(response);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String urlEncode(final String txt) {
        try {
            return URLEncoder.encode(txt, "UTF-8");
        } catch (final UnsupportedEncodingException uee) {
            throw new SlackNotificationPluginException(
                String.format("URL encoding error: [%s].", uee.getMessage()),
                uee
            );
        }
    }

    private static URL toURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException malformedURLEx) {
            throw new SlackNotificationPluginException("Slack API URL is malformed: [" + malformedURLEx.getMessage() + "].", malformedURLEx);
        }
    }

    private static HttpURLConnection openConnection(URL requestUrl) {
        final HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("charset", "utf-8");
            connection.setDoInput(true);
            connection.setDoOutput(true);
        } catch (final IOException ioe) {
            throw new SlackNotificationPluginException(
                String.format("Error opening connection to Slack URL: [%s].", ioe.getMessage()),
                ioe
            );
        }
        return connection;
    }

    private static void putRequestStream(HttpURLConnection connection, String message) {
        try (final DataOutputStream writer = new DataOutputStream(connection.getOutputStream())) {
            writer.writeBytes(message);
            writer.flush();
        } catch (IOException ioe) {
            throw new SlackNotificationPluginException(
                String.format("Error putting data to Slack URL: [%s].", ioe.getMessage()),
                ioe
            );
        }
    }

    private InputStream getResponseStream(HttpURLConnection connection) {
        InputStream input = null;
        try {
            input = connection.getInputStream();
        } catch (IOException ioe) {
            input = connection.getErrorStream();
        }
        return input;
    }

    private String getSlackResponse(InputStream responseStream) {
        try {
            return new Scanner(responseStream,"UTF-8").useDelimiter("\\A").next();
        } catch (Exception ioEx) {
            throw new SlackNotificationPluginException("Error reading Slack API JSON response: [" + ioEx.getMessage() + "].", ioEx);
        }
    }

    private void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ioEx) {
                // ignore
            }
        }
    }

    private static class SlackNotificationData {
        private String template;
        private String color;
        public SlackNotificationData(String template, String color) {
            this.color = color;
            this.template = template;
        }
    }

}
