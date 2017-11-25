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

/*
 * TestVisitor.java
 *
 * Created on January 6, 2006, 4:40 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.xml.xam;

import java.util.List;

/**
 *
 * @author Nam Nguyen
 */
public class TestVisitor {
    
    public void visit(TestComponent2 c) {
        visitChildren(c);
    }
    
    protected void visitChildren(TestComponent2 c) {
        List<TestComponent2> children = c.getChildren();
        for (int i=0; i<children.size(); i++) {
            children.get(i).accept(this);
        }
    }
}
