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
package org.hawkular.alerts.actions.email;

import java.io.File;
import java.io.FileOutputStream;

import javax.mail.Message;

import org.junit.BeforeClass;

/**
 * Helper methods for tests
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class CommonTest {

    @BeforeClass
    public static void setUnitTest() {
        System.setProperty(EmailPlugin.MAIL_SESSION_OFFLINE, "true");
    }

    protected void writeEmailFile(Message msg, String fileName) throws Exception {
        File dir = new File("target/test-emails");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);
        FileOutputStream fos = new FileOutputStream(file);
        msg.writeTo(fos);
        fos.close();
    }

}
