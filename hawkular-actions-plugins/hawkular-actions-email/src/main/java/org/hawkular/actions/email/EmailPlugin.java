/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.actions.email;

/**
 * Main class to store common properties for email plugin
 *
 * @author Lucas Ponce
 */
public class EmailPlugin {
    public static final String INIT_PLUGIN = "init";
    public static final String PLUGIN_NAME = "email";

    public static final String PROP_FROM = "from";
    public static final String PROP_FROM_NAME = "from-name";
    public static final String PROP_TO = "to";
    public static final String PROP_CC = "cc";
    public static final String PROP_MESSAGE_ID = "message-id";
    public static final String PROP_IN_REPLY_TO = "in-reply-to";

    public static final String DEFAULT_FROM = "noreply@hawkular.org";
    public static final String DEFAULT_FROM_NAME = "Hawkular";

    public static final String HAWKULAR_BASE_URL = "HAWKULAR_BASE_URL";
    public static final String PROP_TEMPLATE_HAWKULAR_URL = "template.hawkular.url";
    public static final String PROP_TEMPLATE_LOCALE = "template.locale";
    public static final String PROP_TEMPLATE_PLAIN = "template.plain";
    public static final String PROP_TEMPLATE_HTML = "template.html";
}
