/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bf2;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Rule;
import java.io.File;

/**
 * Unit test for simple App.
 */
public class PluginTest {
    @Rule
    public TestResources resources = new TestResources();

    @Rule
    public MojoRule rule = new MojoRule() {
        @Override
        protected void before() throws Throwable {
            super.before();
        }

        @Override
        protected void after() {
            super.after();
        }
    };

    public void testSomething() throws Exception {
        File pom = new File("src/test/resources/test-1/pom.xml" );
        assertNotNull(pom);
        assertTrue(pom.exists());

        OlmBundleGeneratorMojo myMojo = (OlmBundleGeneratorMojo) rule.lookupMojo("build", pom);
        assertNotNull(myMojo);
        myMojo.execute();

    }
}
