/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.datacollector.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeMBeanException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Set;

/**
 * Variant of Hadoop's JMXJsonServlet.
 * <p/>
 * CHANGES: removed commons-logging and other Hadoop dependencies.
 */
public class JMXJsonServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private static final String CALLBACK_PARAM = "callback";

  /**
   * MBean server.
   */
  protected transient MBeanServer mBeanServer;

  /**
   * Json Factory to create Json generators for write objects in json format
   */
  protected transient JsonFactory jsonFactory;
  /**
   * Initialize this servlet.
   */
  @Override
  public void init() throws ServletException {
    // Retrieve the MBean server
    mBeanServer = ManagementFactory.getPlatformMBeanServer();
    jsonFactory = new JsonFactory();
  }

  /**
   * Process a GET request for the specified resource.
   *
   * @param request
   *          The servlet request we are processing
   * @param response
   *          The servlet response we are creating
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      JsonGenerator jg = null;
      String jsonpcb = null;
      PrintWriter writer = null;
      try {
        writer = response.getWriter();

        // "callback" parameter implies JSONP outpout
        jsonpcb = request.getParameter(CALLBACK_PARAM);
        if (jsonpcb != null) {
          response.setContentType("application/javascript; charset=utf8");
          writer.write(jsonpcb + "(");
        } else {
          response.setContentType("application/json; charset=utf8");
        }

        jg = jsonFactory.createGenerator(writer);
        jg.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        jg.useDefaultPrettyPrinter();
        jg.writeStartObject();

        // query per mbean attribute
        String getmethod = request.getParameter("get");
        if (getmethod != null) {
          String[] splitStrings = getmethod.split("\\:\\:");
          if (splitStrings.length != 2) {
            jg.writeStringField("result", "ERROR");
            jg.writeStringField("message", "query format is not as expected.");
            jg.flush();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
          }
          listBeans(jg, new ObjectName(splitStrings[0]), splitStrings[1],
                    response);
          return;
        }

        // query per mbean
        String qry = request.getParameter("qry");
        if (qry == null) {
          qry = "*:*";
        }
        listBeans(jg, new ObjectName(qry), null, response);
      } finally {
        if (jg != null) {
          jg.close();
        }
        if (jsonpcb != null) {
          writer.write(");");
        }
        if (writer != null) {
          writer.close();
        }
      }
    } catch (IOException e) {
      
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } catch (MalformedObjectNameException e) {
      
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  // --------------------------------------------------------- Private Methods
  private void listBeans(JsonGenerator jg, ObjectName qry, String attribute,
      HttpServletResponse response)
      throws IOException {
    
    Set<ObjectName> names = null;
    names = mBeanServer.queryNames(qry, null);

    jg.writeArrayFieldStart("beans");
    Iterator<ObjectName> it = names.iterator();
    while (it.hasNext()) {
      ObjectName oname = it.next();
      MBeanInfo minfo;
      String code = "";
      Object attributeinfo = null;
      try {
        minfo = mBeanServer.getMBeanInfo(oname);
        code = minfo.getClassName();
        String prs = "";
        try {
          if ("org.apache.commons.modeler.BaseModelMBean".equals(code)) {
            prs = "modelerType";
            code = (String) mBeanServer.getAttribute(oname, prs);
          }
          if (attribute!=null) {
            prs = attribute;
            attributeinfo = mBeanServer.getAttribute(oname, prs);
          }
        } catch (AttributeNotFoundException e) {
          // If the modelerType attribute was not found, the class name is used
          // instead.
          
        } catch (MBeanException e) {
          // The code inside the attribute getter threw an exception so 
          // and fall back on the class name

        } catch (RuntimeException e) {
          // For some reason even with an MBeanException available to them
          // Runtime exceptionscan still find their way through, so treat them
          // the same as MBeanException

        } catch ( ReflectionException e ) {
          // This happens when the code inside the JMX bean (setter?? from the
          // java docs) threw an exception, so 
          // class name

        }
      } catch (InstanceNotFoundException e) {
        //Ignored for some reason the bean was not found so don't output it
        continue;
      } catch ( IntrospectionException e ) {
        // This is an internal error, something odd happened with reflection so
        // 
        
        continue;
      } catch ( ReflectionException e ) {
        // This happens when the code inside the JMX bean threw an exception, so
        // 
        
        continue;
      }

      jg.writeStartObject();
      jg.writeStringField("name", oname.toString());

      jg.writeStringField("modelerType", code);
      if ((attribute != null) && (attributeinfo == null)) {
        jg.writeStringField("result", "ERROR");
        jg.writeStringField("message", "No attribute with name " + attribute
                                       + " was found.");
        jg.writeEndObject();
        jg.writeEndArray();
        jg.close();
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (attribute != null) {
        writeAttribute(jg, attribute, attributeinfo);
      } else {
        MBeanAttributeInfo attrs[] = minfo.getAttributes();
        for (int i = 0; i < attrs.length; i++) {
          writeAttribute(jg, oname, attrs[i]);
        }
      }
      jg.writeEndObject();
    }
    jg.writeEndArray();
  }

  private void writeAttribute(JsonGenerator jg, ObjectName oname, MBeanAttributeInfo attr) throws IOException {
    if (!attr.isReadable()) {
      return;
    }
    String attName = attr.getName();
    if ("modelerType".equals(attName)) {
      return;
    }
    if (attName.indexOf("=") >= 0 || attName.indexOf(":") >= 0
        || attName.indexOf(" ") >= 0) {
      return;
    }
    Object value = null;
    try {
      value = mBeanServer.getAttribute(oname, attName);
    } catch (RuntimeMBeanException e) {
      // UnsupportedOperationExceptions happen in the normal course of business,
      // so no need to 
      if (e.getCause() instanceof UnsupportedOperationException) {
        
      } else {
        
      }
      return;
    } catch (RuntimeErrorException e) {
      // RuntimeErrorException happens when an unexpected failure occurs in getAttribute
      // for example https://issues.apache.org/jira/browse/DAEMON-120
      
      return;
    } catch (AttributeNotFoundException e) {
      //Ignored the attribute was not found, which should never happen because the bean
      //just told us that it has this attribute, but if this happens just don't output
      //the attribute.
      return;
    } catch (MBeanException e) {
      //The code inside the attribute getter threw an exception so 
      // skip outputting the attribute
      
      return;
    } catch (RuntimeException e) {
      //For some reason even with an MBeanException available to them Runtime exceptions
      //can still find their way through, so treat them the same as MBeanException
      
      return;
    } catch (ReflectionException e) {
      //This happens when the code inside the JMX bean (setter?? from the java docs)
      //threw an exception, so 
      
      return;
    } catch (InstanceNotFoundException e) {
      //Ignored the mbean itself was not found, which should never happen because we
      //just accessed it (perhaps something unregistered in-between) but if this
      //happens just don't output the attribute.
      return;
    }

    writeAttribute(jg, attName, value);
  }

  private void writeAttribute(JsonGenerator jg, String attName, Object value) throws IOException {
    jg.writeFieldName(attName);
    writeObject(jg, value);
  }

  private void writeObject(JsonGenerator jg, Object value) throws IOException {
    if(value == null) {
      jg.writeNull();
    } else {
      Class<?> c = value.getClass();
      if (c.isArray()) {
        jg.writeStartArray();
        int len = Array.getLength(value);
        for (int j = 0; j < len; j++) {
          Object item = Array.get(value, j);
          writeObject(jg, item);
        }
        jg.writeEndArray();
      } else if(value instanceof Number) {
        Number n = (Number)value;
        jg.writeNumber(n.toString());
      } else if(value instanceof Boolean) {
        Boolean b = (Boolean)value;
        jg.writeBoolean(b);
      } else if(value instanceof CompositeData) {
        CompositeData cds = (CompositeData)value;
        CompositeType comp = cds.getCompositeType();
        Set<String> keys = comp.keySet();
        jg.writeStartObject();
        for(String key: keys) {
          writeAttribute(jg, key, cds.get(key));
        }
        jg.writeEndObject();
      } else if(value instanceof TabularData) {
        TabularData tds = (TabularData)value;
        jg.writeStartArray();
        for(Object entry : tds.values()) {
          writeObject(jg, entry);
        }
        jg.writeEndArray();
      } else if (value instanceof GaugeValue) {
        ((GaugeValue)value).serialize(jg);
      } else {
        jg.writeString(value.toString());
      }
    }
  }
}