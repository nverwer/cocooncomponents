/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceParameters;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *  * @cocoon.sitemap.component.documentation
 * This transformer triggers for the element <code>query</code> in the namespace "http://apache.org/cocoon/sparql/1.0".
 * These elements must not be nested.
 * The <code>src</code> attribute contains the url which points to a SPARQL endpoint.
 * The optional <code>method</code> attribute contains the HTTP method for the request (default is GET).
 * The optional <code>encoding</code> attribute contains the HTTP encoding for the request (default is ISO-8859-1).
 * Attributes in the "http://apache.org/cocoon/sparql/1.0" namespace are used as request parameters (using the local name).
 * This allows for parameters such as 'format' or 'maxrows'.
 * The text of the content of the <code>query</code> element is passed as the value of the 'query' parameter.
 * 
 * The XML input to this transformer would look like:
 * <pre>
 *   <sparql:query xmlns:sparql="http://apache.org/cocoon/sparql/1.0"
 *     method="POST" src="http://dbpedia.org/sparql"
 *     sparql:maxrows="25" sparql:format="XML"
 *   >
 *   <![CDATA[
 *     PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
 *     SELECT *
 *     FROM <http://dbpedia.org>
 *     WHERE {
 *       ?person rdf:type <http://dbpedia.org/class/yago/Person100007846> .
 *     }
 *   ]]>
 *   </sparql:query>
 * </pre>
 * 
 * @author Nico Verwer
 *
 */
public class SparqlTransformer extends AbstractSAXTransformer {
  
  public static final String SPARQL_NAMESPACE_URI = "http://apache.org/cocoon/sparql/1.0";
  public static final String QUERY_ELEMENT = "query";
  public static final String METHOD_ATTR = "method";
  public static final String SRC_ATTR = "src";
  public static final String ENCODING_ATTR = "encoding";
  public static final String QUERY_PARAM = "query";
  
  private boolean inQuery;
  private String src;
  private String method;
  private String encoding;
  private SourceParameters requestParameters;

  public SparqlTransformer() {
    this.defaultNamespaceURI = SPARQL_NAMESPACE_URI;
  }

  public void setup(SourceResolver resolver, Map objectModel, String src,
      Parameters params) throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    inQuery = false;
  }

  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(QUERY_ELEMENT)) {
      if (inQuery) {
        throw new ProcessingException("Nested SPARQL queries are not allowed.");
      }
      inQuery = true;
      src = attr.getValue(SRC_ATTR);
      method = attr.getValue(METHOD_ATTR);
      if (method == null) method = "GET";
      encoding = attr.getValue(ENCODING_ATTR);
      if (encoding == null) encoding = System.getProperty("file.encoding", "ISO-8859-1"); 
      requestParameters = new SourceParameters();
      for (int i = 0; i < attr.getLength(); ++i) {
        if (attr.getURI(i).equals(SPARQL_NAMESPACE_URI)) {
          requestParameters.setParameter(attr.getLocalName(i), attr.getValue(i));
        }
      }
      startTextRecording();
    }
  }

  public void endTransformingElement(String uri, String name, String raw)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(QUERY_ELEMENT)) {
      inQuery = false;
      String query = endTextRecording();
      requestParameters.setParameter(QUERY_PARAM, query);
      Parameters typeParameters = new Parameters();
      typeParameters.setParameter("method", method);
      typeParameters.setParameter("encoding", encoding);
      //typeParameters.setParameter("mime-type", ...) // mime-type hint
      Source source = SourceUtil.getSource(src, typeParameters, requestParameters, resolver);
      SourceUtil.toSAX(source, this.xmlConsumer, typeParameters, true);
    }
  }
  
}
