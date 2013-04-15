/*
 * Copyright 1999-2004 The Apache Software Foundation.
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
package org.apache.cocoon.generation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;
import java.net.URLEncoder;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.util.NetUtils;
import org.xml.sax.SAXException;

/**
 * An adaptation of the FileGenerator, which passes its parameters to the resolved source.
 */
public class UriGenerator extends FileGenerator {

  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, parameterize(src, Parameters.toProperties(par)), par);
  }
  
  /** Adapted from org.apache.cocoon.util.NetUtils
   * Encode and add parameters stored in the Map to the uri string.
   * Map can contain Object values which will be converted to the string,
   * or Object arrays, which will be treated as multivalue attributes.
   * 
   * @param uri The uri to add parameters into
   * @param parameters The map containing parameters to be added
   * @return The uri with added parameters
   */
  private String parameterize(String uri, Map parameters) {
      if (parameters.size() == 0) {
          return uri;
      }
      
      StringBuffer buffer = new StringBuffer(uri);
      if (uri.indexOf('?') == -1) {
          buffer.append('?');
      } else {
          buffer.append('&');
      }
      
      for (Iterator i = parameters.entrySet().iterator(); i.hasNext();) {
          Map.Entry entry = (Map.Entry)i.next();
          if (entry.getValue().getClass().isArray()) {
              Object[] value = (Object[])entry.getValue();
              for (int j = 0; j < value.length; j++) {
                  if (j > 0) {
                      buffer.append('&');
                  }
                  buffer.append(encode(entry.getKey()));
                  buffer.append('=');
                  buffer.append(encode(value[j]));
              }
          } else {
              buffer.append(encode(entry.getKey()));
              buffer.append('=');
              buffer.append(encode(entry.getValue()));
          }
          if (i.hasNext()) {
              buffer.append('&');
          }
      }
      return buffer.toString();
  }
  
  private String encode(Object text) {
    try {
      return URLEncoder.encode(text.toString(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return text.toString();
    }
  }

}
