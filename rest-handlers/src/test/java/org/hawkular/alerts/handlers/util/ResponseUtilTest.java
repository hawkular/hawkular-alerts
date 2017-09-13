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
package org.hawkular.alerts.handlers.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ResponseUtilTest {

    @Test
    public void replaceQueryParams() {
        String uri = "http://server:8080/prefix/url";
        Assert.assertEquals(uri + "?page=1", ResponseUtil.replaceQueryParam(uri, "page", "1"));

        String uri2 = uri + "?page2=3";
        Assert.assertEquals(uri2 + "&page=1", ResponseUtil.replaceQueryParam(uri2, "page", "1"));

        String uri3 = uri + "?page2=3&page=4";
        String expected3 = uri + "?page2=5&page=4";
        Assert.assertEquals(expected3, ResponseUtil.replaceQueryParam(uri3, "page2", "5"));
    }
}
