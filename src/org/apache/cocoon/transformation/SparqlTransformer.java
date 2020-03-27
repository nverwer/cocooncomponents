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

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.cocoon.xml.IncludeXMLConsumer;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.excalibur.source.SourceParameters;
import org.apache.excalibur.xmlizer.XMLizer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This transformer can send queries to a ReST SPARQL endpoint.
 * It can also be used as a genaral HTTP client.
 *
 * This transformer triggers for the element <code>query</code> in the namespace "http://apache.org/cocoon/sparql/1.0".
 * These elements must not be nested.
 *
 * The mandatory <code>src</code> attribute contains the url of a SPARQL endpoint.
 *
 * The optional <code>method</code> attribute contains the HTTP method for the request (default is GET).
 *
 * The optional <code>credentials</code> attribute contains a username and password, separated by a tab character (&#x9;).
 * The credentials will be sent to <a href="http://hc.apache.org/httpclient-3.x/authentication.html">any authentication realm</a>,
 * so be careful to send them only to the intended service!
 *
 * For POST requests, parameters are sent in the body if the attribute <code>http:Content-Type</code> is
 * "application/x-www-form-urlencoded".
 * (Note that the header name "Content-Type" is case sensitive!)
 * In this case, the content of the <code>query</code> element is passed as the value of a parameter,
 * which has the name specified by the <code>parameter-name</code> attribute (default is "query").
 *
 * Otherwise, the content of the <code>query</code> element (text or XML) goes into the request body.
 *
 * The optional <code>content</code> attribute indicates if the content of the <code>query</code> element is "text"
 * (default for SPARQL queries), or "xml" (useful if you PUT RDF triples).
 * Unfortunately, if you use content="xml" you may run into namespace problems.
 *
 * The optional <code>parse</code> attribute indicates how the response should be parsed.
 * It can be "xml" or "text". Default is "xml". Text will be wrapped in an XML element.
 *
 * The optional <code>showErrors</code> attribute can be "true" (default; generate XML elements for HTTP errors)
 * or false (throw exceptions for HTTP errors).
 *
 * The optional <code>showResponseHeaders</code> attribute can be true (generate output for the response headers)
 * or false (default; no output for response headers).
 *
 * Attributes in the "http://www.w3.org/2006/http#" namespace are used as request headers.
 * The header name is the local name of the attribute.
 *
 * Attributes in the "http://apache.org/cocoon/sparql/1.0" (sparql:) namespace are used as request parameters.
 * The parameter name is the local name of the attribute. Note: This does not allow for multivalued parameters.
 *
 * The text content of the <code>query</code> element is passed as the value of the 'query' parameter in GET and
 * POST (www-form-urlencoded data) requests.
 * In PUT requests, it is the request entity (body). Note that this is text, even if you put RDF statements in it,
 * so XML must be escaped.
 *
 * Example XML input, with content and parse attributes set to their default values:
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
 * @author Nico Verwer (nverwer@rakensi.com)
 *
 */
public class SparqlTransformer extends AbstractSAXPipelineTransformer {

  public static final String SPARQL_NAMESPACE_URI = "http://apache.org/cocoon/sparql/1.0";
  public static final String HTTP_NAMESPACE_URI ="http://www.w3.org/2006/http#";
  public static final String QUERY_ELEMENT = "query";
  public static final String METHOD_ATTR = "method";
  public static final String CREDENTIALS_ATTR = "credentials";
  public static final String CONTENT_ATTR = "content";
  public static final String PARSE_ATTR = "parse";
  public static final String SHOW_ERRORS_ATTR = "showErrors";
  public static final String SHOW_RESPONSE_HEADERS_ATTR = "showResponseHeaders";
  public static final String SRC_ATTR = "src";
  public static final String PARAMETER_NAME_ATTR = "parameter-name";
  public static final String DEFAULT_QUERY_PARAM = "query";
  public static final String HTTP_CONTENT_TYPE = "Content-Type";

  private boolean inQuery;
  private String src;
  private String method;
  private String credentials;
  private String contentType;
  private String parameterName;
  private String parse;
  private boolean showErrors;
  private boolean showResponseHeaders;
  private Map<String, String> httpHeaders;
  private SourceParameters requestParameters;

  public SparqlTransformer() {
    defaultNamespaceURI = SPARQL_NAMESPACE_URI;
  }

  @Override
  public void setup(SourceResolver resolver, @SuppressWarnings("rawtypes") Map objectModel, String src,
      Parameters params) throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    inQuery = false;
  }

  private String getAttribute(Attributes attr, String name, String defaultValue) {
    return (attr.getIndex(name) >= 0) ? attr.getValue(name) : defaultValue;
  }

  @Override
  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(QUERY_ELEMENT)) {
      if (inQuery) {
        throw new ProcessingException("Nested SPARQL queries are not allowed.");
      }
      inQuery = true;
      src = getAttribute(attr, SRC_ATTR, null);
      if (src == null) {
        throw new ProcessingException("The "+SRC_ATTR+" attribute is mandatory for "+QUERY_ELEMENT+" elements.");
      }
      method = getAttribute(attr, METHOD_ATTR, "GET");
      credentials = getAttribute(attr, CREDENTIALS_ATTR, "");
      contentType = getAttribute(attr, CONTENT_ATTR, "text");
      parameterName = getAttribute(attr, PARAMETER_NAME_ATTR, DEFAULT_QUERY_PARAM);
      parse = getAttribute(attr, PARSE_ATTR, "xml");
      showErrors = getAttribute(attr, SHOW_ERRORS_ATTR, "true").charAt(0) == 't';
      showResponseHeaders = getAttribute(attr, SHOW_RESPONSE_HEADERS_ATTR, "false").charAt(0) == 't';
      requestParameters = new SourceParameters();
      httpHeaders = new HashMap<String, String>();
      // Process other attributes.
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

  @Override
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
      requestParameters.setParameter(parameterName, query);
      executeRequest(src, method, httpHeaders, requestParameters);
    }
  }

  //-Dhttp.nonProxyHosts=10.*|localhost|62.112.232.245
  private void executeRequest(String url, String method, Map<String, String> httpHeaders, SourceParameters requestParameters)
      throws ProcessingException, IOException, SAXException {
    HttpClient httpclient = new HttpClient();
    if (System.getProperty("http.proxyHost") != null) {
      // getLogger().warn("PROXY: "+System.getProperty("http.proxyHost"));
      String nonProxyHostsRE = System.getProperty("http.nonProxyHosts", "");
      if (nonProxyHostsRE.length() > 0) {
        String[] pHosts = nonProxyHostsRE.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*").split("\\|");
        nonProxyHostsRE = "";
        for (String pHost : pHosts) {
          nonProxyHostsRE += "|(^https?://"+pHost+".*$)";
        }
        nonProxyHostsRE = nonProxyHostsRE.substring(1);
      }
      if (nonProxyHostsRE.length() == 0 || !url.matches(nonProxyHostsRE)) {
        try {
          HostConfiguration hostConfiguration = httpclient.getHostConfiguration();
          hostConfiguration.setProxy(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort", "80")));
          httpclient.setHostConfiguration(hostConfiguration);
        } catch (Exception e) {
          throw new ProcessingException("Cannot set proxy!", e);
        }
      }
    }
    // Make the HttpMethod.
    HttpMethod httpMethod = null;
    // Do not use empty query parameter.
    if (requestParameters.getParameter(parameterName).trim().equals("")) {
      requestParameters.removeParameter(parameterName);
    }
    // Instantiate different HTTP methods.
    if ("GET".equalsIgnoreCase(method)) {
      httpMethod = new GetMethod(url);
      if (requestParameters.getEncodedQueryString() != null) {
        httpMethod.setQueryString(requestParameters.getEncodedQueryString().replace("\"", "%22")); /* Also escape '"' */
      } else {
        httpMethod.setQueryString("");
      }
    } else if ("POST".equalsIgnoreCase(method)) {
      PostMethod httpPostMethod = new PostMethod(url);
      if (httpHeaders.containsKey(HTTP_CONTENT_TYPE) &&
          httpHeaders.get(HTTP_CONTENT_TYPE).startsWith("application/x-www-form-urlencoded")) {
        // Encode parameters in POST body.
        @SuppressWarnings("unchecked")
        Iterator<String> parNames = requestParameters.getParameterNames();
        while (parNames.hasNext()) {
          String parName = parNames.next();
          httpPostMethod.addParameter(parName, requestParameters.getParameter(parName));
        }
      } else {
        // Use query parameter as POST body
        RequestEntity reqentity = new StringRequestEntity(requestParameters.getParameter(parameterName));
        httpPostMethod.setRequestEntity(reqentity);
        // Add other parameters to query string
        requestParameters.removeParameter(parameterName);
        if (requestParameters.getEncodedQueryString() != null) {
          httpPostMethod.setQueryString(requestParameters.getEncodedQueryString().replace("\"", "%22")); /* Also escape '"' */
        } else {
          httpPostMethod.setQueryString("");
        }
      }
      httpMethod = httpPostMethod;
    } else if ("PUT".equalsIgnoreCase(method)) {
      PutMethod httpPutMethod = new PutMethod(url);
      httpPutMethod.setRequestEntity(new StringRequestEntity(requestParameters.getParameter(parameterName)));
      requestParameters.removeParameter(parameterName);
      httpPutMethod.setQueryString(requestParameters.getEncodedQueryString());
      httpMethod = httpPutMethod;
    } else if ("DELETE".equalsIgnoreCase(method)) {
      httpMethod = new DeleteMethod(url);
      httpMethod.setQueryString(requestParameters.getEncodedQueryString());
    } else {
      throw new ProcessingException("Unsupported method: "+method);
    }
    // Authentication (optional).
    if (credentials != null && credentials.length() > 0) {
      String[] unpw = credentials.split("\t");
      httpclient.getParams().setAuthenticationPreemptive(true);
      httpclient.getState().setCredentials(new AuthScope(httpMethod.getURI().getHost(), httpMethod.getURI().getPort(), AuthScope.ANY_REALM),
                                           new UsernamePasswordCredentials(unpw[0], unpw[1]));
    }
    // Add request headers.
    Iterator<Map.Entry<String, String>> headers = httpHeaders.entrySet().iterator();
    while (headers.hasNext()) {
      Map.Entry<String, String> header = headers.next();
      httpMethod.addRequestHeader(header.getKey(), header.getValue());
    }
    // Declare some variables before the try-block.
    XMLizer xmlizer = null;
    try {
      // Execute the request.
      String responseBody = "";
      String statusText = "";
      int responseCode;
      responseCode = httpclient.executeMethod(httpMethod);
      // Handle errors, if any.
      if (responseCode < 200 || responseCode >= 300) {
        if (showErrors) {
          AttributesImpl attrs = new AttributesImpl();
          attrs.addCDATAAttribute("status", ""+responseCode);
          xmlConsumer.startElement(SPARQL_NAMESPACE_URI, "error", "sparql:error", attrs);
          responseBody = httpMethod.getResponseBodyAsString();
          statusText = httpMethod.getStatusText();
          xmlConsumer.characters(responseBody.toCharArray(), 0, statusText.length());
          xmlConsumer.endElement(SPARQL_NAMESPACE_URI, "error", "sparql:error");
        } else {
          throw new ProcessingException("Received HTTP status code "+responseCode+" "+statusText+":\n"+responseBody);
        }
      }
      if (showResponseHeaders) {
        xmlConsumer.startElement(SPARQL_NAMESPACE_URI, "response-headers", "sparql:response-headers", EMPTY_ATTRIBUTES);
        for (Header responseHeader : httpMethod.getResponseHeaders()) {
          AttributesImpl attributes = new AttributesImpl();
          attributes.addCDATAAttribute(responseHeader.getName(), responseHeader.getValue());
          xmlConsumer.startElement(SPARQL_NAMESPACE_URI, "header", "sparql:header", attributes);
          xmlConsumer.endElement(SPARQL_NAMESPACE_URI, "header", "header");
        }
        xmlConsumer.endElement(SPARQL_NAMESPACE_URI, "response-headers", "sparql:response-headers");
      }
      // Parse the response
      else {
        if (responseCode == 204) { // No content.
          String statusLine = httpMethod.getStatusLine().toString();
          xmlConsumer.startElement(SPARQL_NAMESPACE_URI, "result", "sparql:result", EMPTY_ATTRIBUTES);
          xmlConsumer.characters(statusLine.toCharArray(), 0, statusLine.length());
          xmlConsumer.endElement(SPARQL_NAMESPACE_URI, "result", "sparql:result");
          httpMethod.getResponseBodyAsString();
        } else if (parse.equalsIgnoreCase("xml")) {
          InputStream responseBodyStream = httpMethod.getResponseBodyAsStream();
          xmlizer = (XMLizer) manager.lookup(XMLizer.ROLE);
          xmlizer.toSAX(responseBodyStream, "text/xml", httpMethod.getURI().toString(), new IncludeXMLConsumer(xmlConsumer));
          responseBodyStream.close();
        } else if (parse.equalsIgnoreCase("text")) {
          xmlConsumer.startElement(SPARQL_NAMESPACE_URI, "result", "sparql:result", EMPTY_ATTRIBUTES);
          responseBody = httpMethod.getResponseBodyAsString();
          xmlConsumer.characters(responseBody.toCharArray(), 0, responseBody.length());
          xmlConsumer.endElement(SPARQL_NAMESPACE_URI, "result", "sparql:result");
        } else {
          throw new ProcessingException("Unknown parse type: " + parse);
        }
      }
    } catch (ServiceException e) {
      throw new ProcessingException("Cannot find the right XMLizer for "+XMLizer.ROLE, e);
    } finally {
        if (xmlizer != null) {
          manager.release(xmlizer);
        }
        httpMethod.releaseConnection();
    }
  }

}
