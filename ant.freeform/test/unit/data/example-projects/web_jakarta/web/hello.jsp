<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->
<html>
<head>
<title>Sample Application JSP Page</title>
</head>
<body bgcolor=white>

<h1>Sample Application JSP Page</h1>
This is the output of a JSP page that is part of the Hello, World
application.  It displays several useful values from the request
we are currently processing.

<table border="0" border="100%">
<tr>
  <th align="right">Context Path:</th>
  <td align="left"><%= request.getContextPath() %></td>
</tr>
<tr>
  <th align="right">Path Information:</th>
  <td align="left"><%= request.getPathInfo() %></td>
</tr>
<tr>
  <th align="right">Query String:</th>
  <td align="left"><%= request.getQueryString() %></td>
</tr>
<tr>
  <th align="right">Request Method:</th>
  <td align="left"><%= request.getMethod() %></td>
</tr>
<tr>
  <th align="right">Servlet Path:</th>
  <td align="left"><%= request.getServletPath() %></td>
</tr>
</table>
</body>
</html>
