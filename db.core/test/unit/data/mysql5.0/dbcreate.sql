--  Licensed to the Apache Software Foundation (ASF) under one
--  or more contributor license agreements.  See the NOTICE file
--  distributed with this work for additional information
--  regarding copyright ownership.  The ASF licenses this file
--  to you under the Apache License, Version 2.0 (the
--  "License"); you may not use this file except in compliance
--  with the License.  You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
--  Unless required by applicable law or agreed to in writing,
--  software distributed under the License is distributed on an
--  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
--  KIND, either express or implied.  See the License for the
--  specific language governing permissions and limitations
--  under the License.
create table simpletable (tinyintc TINYINT,smallintc SMALLINT,varcharc VARCHAR(20),intc INT,bigintc BIGINT,datec DATE,charc CHAR,datetime DATETIME,floatc FLOAT,doublec DOUBLE,doubleprecisionc DOUBLE PRECISION);
INSERT INTO simpletable (tinyintc,smallintc,varcharc,intc,bigintc,datec,charc,datetime,floatc,doublec,doubleprecisionc) VALUES (-80,-32766,'ala',123456,123,'2005-10-10','c','2005-04-01 22:12:32',2.65,-217.5,4.32);