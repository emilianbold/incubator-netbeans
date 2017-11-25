/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.netbeans.modules.maven.execute;

import java.util.regex.Matcher;
import static junit.framework.TestCase.fail;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mkleint
 */
public class CommandLineOutputHandlerTest {

    public CommandLineOutputHandlerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Test
    public void testDownloadPattern() {
        String[] lines = {
            "51521/?", "11/12K", "11/12M", "51521/120000b",
            "51521/? 12/25K", "34/263M 464/500b",
            "51521/? 13/25K 4034/4640M",
            // #189465: M3 ConsoleMavenTransferListener.doProgress
            "59/101 KB    ", "1/3 B  ", "55 KB", "300 B  ",
            "10/101 KB   48/309 KB   ", // sometimes seems to jam
        };
        for (String line : lines) {
            if (!CommandLineOutputHandler.DOWNLOAD.matcher(line).matches()) {
                fail("Line " + line + " not skipped");
            }
        }
    }
    
    @Test
    public void testRegExp() throws Exception {
        Matcher m = CommandLineOutputHandler.startPatternM2.matcher("[INFO] [surefire:test]");
        assertTrue(m.matches());
        assertEquals("surefire", m.group(1));
        assertEquals("test", m.group(2));
        m = CommandLineOutputHandler.startPatternM2.matcher("[INFO] [compiler:testCompile {execution: default-testCompile}]");
        assertTrue(m.matches());
        assertEquals("compiler", m.group(1));
        assertEquals("testCompile", m.group(2));
        m = CommandLineOutputHandler.startPatternM3.matcher("[INFO] --- maven-compiler-plugin:2.3.2:compile (default-compile) @ mavenproject3 ---");
        assertTrue(m.matches());
        assertEquals("maven-compiler-plugin", m.group(1));
        assertEquals("compile", m.group(2));
    }

    @Test
    public void testReactorLine() throws Exception {
        //the non event matching..
        Matcher m = CommandLineOutputHandler.reactorFailure.matcher("[INFO] Maven Core ........................................ FAILURE [1.480s]");
        assertTrue(m.matches());
        
        //
        m = CommandLineOutputHandler.reactorSummaryLine.matcher("Maven Core ........................................ FAILURE [1.480s]");
        assertTrue(m.matches());
        
        m = CommandLineOutputHandler.reactorSummaryLine.matcher("Maven Aether Provider ............................. SUCCESS [1.014s]");
        assertTrue(m.matches());

    }
}
