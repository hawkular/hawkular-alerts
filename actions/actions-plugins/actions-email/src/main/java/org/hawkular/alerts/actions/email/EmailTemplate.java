/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.actions.email;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.hawkular.alerts.actions.api.ActionMessage;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;

/**
 * Email builder based on freemarker templates.
 *
 * There are several configuration options for templates location:
 *
 * 1.- Classloader:
 *
 * There are default templates available at classloader.
 * - template.html.default_en_US.ftl (For html email)
 * - template.html.default_en_US.ftl (For plain text email)
 *
 * (Default templates use en_US locale)
 *
 * 2.- Disk:
 *
 * If "HAWKULAR_ALERTS_TEMPLATES" system environment is defined or if "hawkular.alerts.templates" system property is
 * defined with a valid path, the default templates
 * - template.html.default_en_US.ftl (For html email)
 * - template.html.default_en_US.ftl (For plain text email)
 *
 * will be loaded from that path.
 *
 * (Disk templates use en_US locale as well)
 *
 * 3.- Dynamic templates stored at plugin or action level:
 *
 * Templates can be modified dynamically and being stored per plugin (shared by all actions) or for an specific action.
 * Templates text are defined at PluginMessage.getProperties() level.
 *
 * Plain templates are defined under PluginMessage.getProperties().get("template.plain") property.
 * Html templates are defined under PluginMessage.getProperties().get("template.html") property.
 *
 * Dynamic templates supports locale defined by action.
 *
 * If PluginMessage.getProperties().get("template.locale") is present then specific locale templates are supported at
 * properties level.
 * i.e.
 * We may have a default template at plugin level defined under "template.plain"/"template.html" properties.
 * But per action basis we want to add a template for Spanish language.
 * Then for an specific action it will exist a "template.locale" == "es" and the following specific properties will
 * exist for this action: "template.plain.es"/"template.html.es". Under these properties are defined specific
 * templates for Spanish language.
 *
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class EmailTemplate {
    private final MsgLogger log = MsgLogging.getMsgLogger(EmailTemplate.class);

    public static final String DEFAULT_TEMPLATE_PLAIN = "template.plain.default.ftl";
    public static final String DEFAULT_TEMPLATE_HTML = "template.html.default.ftl";
    public static final Locale DEFAULT_LOCALE = new Locale("en", "US");
    public static final String FREEMARKER_VERSION = "2.3.23";

    Configuration ftlCfg;
    Template ftlTemplate;
    Template ftlTemplatePlain;
    Template ftlTemplateHtml;

    public EmailTemplate() {
        ftlCfg = new Configuration(new Version(FREEMARKER_VERSION));
        try {
            // Check if templates are located from disk or if we are loading default ones.
            String templatesDir = System.getenv(EmailPlugin.HAWKULAR_ALERTS_TEMPLATES);
            templatesDir = templatesDir == null ? System.getProperty(EmailPlugin.HAWKULAR_ALERTS_TEMPLATES_PROPERY)
                    : templatesDir;
            boolean templatesFromDir = false;
            if (templatesDir != null) {
                File fileDir = new File(templatesDir);
                if (fileDir.exists()) {
                    ftlCfg.setDirectoryForTemplateLoading(fileDir);
                    templatesFromDir = true;
                }
            }
            if (!templatesFromDir) {
                ftlCfg.setClassForTemplateLoading(this.getClass(), "/");
            }
            ftlTemplatePlain = ftlCfg.getTemplate(DEFAULT_TEMPLATE_PLAIN, DEFAULT_LOCALE);
            ftlTemplateHtml = ftlCfg.getTemplate(DEFAULT_TEMPLATE_HTML, DEFAULT_LOCALE);
        } catch (IOException e) {
            log.debug(e.getMessage(), e);
            throw new RuntimeException("Cannot initialize templates on email plugin: " + e.getMessage());
        }
    }

    /**
     * Process a PluginMessage and creates email content based on templates.
     *
     * @param msg the PluginMessage to be processed
     * @return a Map with following entries:
     * - "emailSubject": Subject of the email
     * - "emailBodyPlain": Content for plain text email
     * - "emailBodyHtml": Content for html email
     * @throws Exception on any problem
     */
    public Map<String, String> processTemplate(ActionMessage msg) throws Exception {
        Map<String, String> emailProcessed = new HashMap<>();

        PluginMessageDescription pmDesc = new PluginMessageDescription(msg);

        // Prepare emailSubject directly from PluginMessageDescription class
        emailProcessed.put("emailSubject", pmDesc.getEmailSubject());

        // Check if templates are defined in properties
        String plain;
        String html;
        String templateLocale = pmDesc.getProps() != null ?
                pmDesc.getProps().get(EmailPlugin.PROP_TEMPLATE_LOCALE) : null;
        if (templateLocale != null) {
            plain = pmDesc.getProps().get(EmailPlugin.PROP_TEMPLATE_PLAIN + "." + templateLocale);
            html = pmDesc.getProps().get(EmailPlugin.PROP_TEMPLATE_HTML + "." + templateLocale);
        } else {
            plain = pmDesc.getProps() != null ? pmDesc.getProps().get(EmailPlugin.PROP_TEMPLATE_PLAIN) : null;
            html = pmDesc.getProps() != null ? pmDesc.getProps().get(EmailPlugin.PROP_TEMPLATE_HTML) : null;
        }

        /*
            Invoke freemarker template with PluginMessageDescription as root object for dynamic data.
            PluginMessageDescription fields are accessible within .ftl templates.
         */
        StringWriter writerPlain = new StringWriter();
        StringWriter writerHtml = new StringWriter();
        if (!isEmpty(plain)) {
            StringReader plainReader = new StringReader(plain);
            ftlTemplate = new Template("plainTemplate", plainReader, ftlCfg);
            ftlTemplate.process(pmDesc, writerPlain);
        }  else {
            ftlTemplatePlain.process(pmDesc, writerPlain);
        }
        if (!isEmpty(html)) {
            StringReader htmlReader = new StringReader(html);
            ftlTemplate = new Template("htmlTemplate", htmlReader, ftlCfg);
            ftlTemplate.process(pmDesc, writerHtml);
        } else {
            ftlTemplateHtml.process(pmDesc, writerHtml);
        }

        writerPlain.flush();
        writerPlain.close();

        emailProcessed.put("emailBodyPlain", writerPlain.toString());

        writerHtml.flush();
        writerHtml.close();

        emailProcessed.put("emailBodyHtml", writerHtml.toString());

        return emailProcessed;
    }

}
