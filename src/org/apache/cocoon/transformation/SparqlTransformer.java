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
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.cocoon.xml.IncludeXMLConsumer;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.*;
import org.apache.excalibur.source.SourceParameters;
import org.apache.excalibur.xmlizer.XMLizer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *  * @cocoon.sitemap.component.documentation
 * This transformer triggers for the element <code>query</code> in the namespace "http://apache.org/cocoon/sparql/1.0".
 * These elements must not be nested.
 * The mandatory <code>src</code> attribute contains the url of a SPARQL endpoint.
 * The optional <code>method</code> attribute contains the HTTP method for the request (default is GET).
 * For POST requests, parameters are sent in the body if the attribute http:Content-Type is "application/x-www-form-urlencoded".
 * Note that the header name "Content-Type" is case sensitive!
 * Otherwise, the request body is the content of the <code>query</code> element (text or XML).
 * The optional <code>content</code> attribute indicates if the content of the <code>query</code> element is "text" (default for SPARQL queries), or "xml" (useful if you PUT RDF triples).
 * The optional <code>parse</code> attribute indicates how the response should be parsed. It can be "xml" or "text". Default is "xml". Text will be wrapped in an XML element.
 * The optional <code>showErrors</code> attribute can be "true" (default; generate XML elements for HTTP errors) or false (throw exceptions for HTTP errors).
 * Attributes in the "http://www.w3.org/2006/http#" namespace are used as request headers.
 * The header name is the local name of the attribute.
 * Attributes in the "http://apache.org/cocoon/sparql/1.0" namespace are used as request parameters.
 * The parameter name is the local name of the attribute.
 * The text content of the <code>query</code> element is passed as the value of the 'query' parameter in GET and POST (www-form-urlencoded data) requests.
 * In PUT requests, it is the request entity (body). Note that this is text, even if you put RDF statements in it, so XML must be escaped.
 * 
 * Exmple XML input, with content and parse attributes set to their default values:
 * <pre>
 *   <sparql:query
 *     xmlns:sparql="http://apache.org/cocoon/sparql/1.0"
 *     xmlns:http="http://www.w3.org/2006/http#"
 *     src="http://dbpedia.org/sparql"
 *     method="POST"
 *     content="text"
 *     parse="xml"
 *     http:Content-Type="application/x-www-form-urlencoded"
 *     http:Accept="application/sparql-results+xml"
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
 * @author Nico Verwer (nico.verwer@withart.com)
 *
 */
public class SparqlTransformer extends AbstractSAXTransformer {
  
  public static final String SPARQL_NAMESPACE_URI = "http://apache.org/cocoon/sparql/1.0";
  public static final String HTTP_NAMESPACE_URI ="http://www.w3.org/2006/http#";
  public static final String QUERY_ELEMENT = "query";
  public static final String METHOD_ATTR = "method";
  public static final String CONTENT_ATTR = "content";
  public static final String PARSE_ATTR = "parse";
  public static final String SHOW_ERRORS_ATTR = "showErrors";
  public static final String SRC_ATTR = "src";
  public static final String QUERY_PARAM = "query";
  public static final String HTTP_CONTENT_TYPE = "Content-Type";
  
  private boolean inQuery;
  private String src;
  private String method;
  private String contentType;
  private String parse;
  private boolean showErrors;
  private Map httpHeaders;
  private SourceParameters requestParameters;

  public SparqlTransformer() {
    this.defaultNamespaceURI = SPARQL_NAMESPACE_URI;
  }

  public void setup(SourceResolver resolver, Map objectModel, String src,
      Parameters params) throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    inQuery = false;
  }
  
  private String getAttribute(Attributes attr, String name, String defaultValue) {
    return (attr.getIndex(name) >= 0) ? attr.getValue(name) : defaultValue;
  }

  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(QUERY_ELEMENT)) {
      if (inQuery) {
        throw new ProcessingException("Nested SPARQL queries are not allowed.");
      }
      inQuery = true;
      src = getAttribute(attr, SRC_ATTR, null);
      if (src == null) throw new ProcessingException("The "+SRC_ATTR+" attribute is mandatory for "+QUERY_ELEMENT+" elements.");
      method = getAttribute(attr, METHOD_ATTR, "GET");
      contentType = getAttribute(attr, CONTENT_ATTR, "text");
      parse = getAttribute(attr, PARSE_ATTR, "xml");
      showErrors = getAttribute(attr, SHOW_ERRORS_ATTR, "true").charAt(0) == 't';
      requestParameters = new SourceParameters();
      httpHeaders = new HashMap();
      for (int i = 0; i < attr.getLength(); ++i) {
        if (attr.getURI(i).equals(HTTP_NAMESPACE_URI)) {
          httpHeaders.put(attr.getLocalName(i), attr.getValue(i));
        } else if (attr.getURI(i).equals(SPARQL_NAMESPACE_URI)) {
          requestParameters.setParameter(attr.getLocalName(i), attr.getValue(i));
        }
      }
      if (contentType.equals("text")) {
        startTextRecording();
      } else if (contentType.equals("xml")) {
        startSerializedXMLRecording(null);
      } else {
        throw new ProcessingException("Unsupported query content type: "+contentType);
      }
    }
  }

  public void endTransformingElement(String uri, String name, String raw)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(QUERY_ELEMENT)) {
      inQuery = false;
      String query = null;
      if (contentType.equals("text")) {
        query = endTextRecording();
      } else if (contentType.equals("xml")) {
        query = endSerializedXMLRecording();
      }
      requestParameters.setParameter(QUERY_PARAM, query);
      executeRequest(src, method, httpHeaders, requestParameters);
    }
  }

  private void executeRequest(String url, String method, Map httpHeaders, SourceParameters requestParameters)
      throws ProcessingException, IOException, SAXException {
    HttpClient httpclient = new HttpClient();
    HttpMethod httpMethod = null;
    // Instantiate different HTTP methods.
    if ("GET".equalsIgnoreCase(method)) {
      httpMethod = new GetMethod(url);
      // Do not use empty query parameter.
      if (requestParameters.getParameter(QUERY_PARAM).trim().equals("")) requestParameters.removeParameter(QUERY_PARAM);
      httpMethod.setQueryString(requestParameters.getEncodedQueryString());
    } else if ("POST".equalsIgnoreCase(method)) {
      PostMethod httpPostMethod = new PostMethod(url);
      if (httpHeaders.containsKey(HTTP_CONTENT_TYPE) &&
          httpHeaders.get(HTTP_CONTENT_TYPE).equals("application/x-www-form-urlencoded")) {
        // Do not use empty query parameter.
        if (requestParameters.getParameter(QUERY_PARAM).trim().equals("")) requestParameters.removeParameter(QUERY_PARAM);
        Iterator parNames = requestParameters.getParameterNames();
        while (parNames.hasNext()) {
          String parName = (String) parNames.next();
          httpPostMethod.addParameter(parName, requestParameters.getParameter(parName));
        }
      } else {
        httpPostMethod.setRequestBody(requestParameters.getParameter(QUERY_PARAM));
      }
      httpMethod = httpPostMethod;
    } else if ("PUT".equalsIgnoreCase(method)) {
      PutMethod httpPutMethod = new PutMethod(url);
      httpPutMethod.setRequestBody(requestParameters.getParameter(QUERY_PARAM));
      requestParameters.removeParameter(QUERY_PARAM);
      httpPutMethod.setQueryString(requestParameters.getEncodedQueryString());
      httpMethod = httpPutMethod;
    } else if ("DELETE".equalsIgnoreCase(method)) {
      httpMethod = new DeleteMethod(url);
      // Do not use empty query parameter.
      if (requestParameters.getParameter(QUERY_PARAM).trim().equals("")) requestParameters.removeParameter(QUERY_PARAM);
      httpMethod.setQueryString(requestParameters.getEncodedQueryString());
    } else {
      throw new ProcessingException("Unsupported method: "+method);
    }
    // Add request headers.
    Iterator headers = httpHeaders.entrySet().iterator();
    while (headers.hasNext()) {
      Map.Entry header = (Map.Entry) headers.next();
      httpMethod.addRequestHeader((String) header.getKey(), (String) header.getValue());
    }
    // Declare some variables before the try-block.
    XMLizer xmlizer = null;
    try {
      // Execute the request.
      int responseCode;
      responseCode = httpclient.executeMethod(httpMethod);
      // Handle errors, if any.
      if (responseCode < 200 || responseCode >= 300) {
        if (showErrors) {
          AttributesImpl attrs = new AttributesImpl();
          attrs.addCDATAAttribute("status", ""+responseCode);
          xmlConsumer.startElement("http://apache.org/cocoon/sparql/1.0", "error", "sparql:error", attrs);
          String responseBody = httpMethod.getResponseBodyAsString();
          xmlConsumer.characters(responseBody.toCharArray(), 0, responseBody.length());
          xmlConsumer.endElement("http://apache.org/cocoon/sparql/1.0", "error", "sparql:error");
          return; // Not a nice, but quick and dirty way to end.
        } else {
          throw new ProcessingException("Received HTTP status code "+responseCode+" "+httpMethod.getStatusText()+":\n"+httpMethod.getResponseBodyAsString());
        }
      }
      // Parse the response
      if (responseCode == 204) { // No content.
        String statusLine = httpMethod.getStatusLine().toString();
        xmlConsumer.startElement("http://apache.org/cocoon/sparql/1.0", "result", "sparql:result", EMPTY_ATTRIBUTES);
        xmlConsumer.characters(statusLine.toCharArray(), 0, statusLine.length());
        xmlConsumer.endElement("http://apache.org/cocoon/sparql/1.0", "result", "sparql:result");
      } else if (parse.equalsIgnoreCase("xml")) {
        InputStream responseBodyStream = httpMethod.getResponseBodyAsStream();
        xmlizer = (XMLizer) manager.lookup(XMLizer.ROLE);
        xmlizer.toSAX(responseBodyStream, "text/xml", httpMethod.getURI().toString(), new IncludeXMLConsumer(xmlConsumer));
        responseBodyStream.close();
      } else if (parse.equalsIgnoreCase("text")) {
        xmlConsumer.startElement("http://apache.org/cocoon/sparql/1.0", "result", "sparql:result", EMPTY_ATTRIBUTES);
        String responseBody = httpMethod.getResponseBodyAsString();
        xmlConsumer.characters(responseBody.toCharArray(), 0, responseBody.length());
        xmlConsumer.endElement("http://apache.org/cocoon/sparql/1.0", "result", "sparql:result");
      } else {
        throw new ProcessingException("Unknown parse type: "+parse);
      }
    } catch (ServiceException e) {
      throw new ProcessingException("Cannot find the right XMLizer for "+XMLizer.ROLE, e);
    } finally {
        if (xmlizer != null) manager.release((Component) xmlizer);
        httpMethod.releaseConnection();
    }
  }

}
