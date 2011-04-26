package org.apache.cocoon.transformation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.cocoon.xml.IncludeXMLConsumer;
import org.apache.cocoon.xml.XMLUtils;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpURL;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.IOUtils;
import org.apache.excalibur.xmlizer.XMLizer;
import org.w3c.dom.DocumentFragment;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * <p>This transformer processes <code>request</code> elements in the 
 * <code>http://org.apache.cocoon.transformation/rest/1.0</code> namespace. The 
 * <code>target</code> attribute contains the URI of the RESTful service. The
 * <code>method</code> attribute contains one of the following HTTP methods:
 * <code>GET</code>, <code>DELETE</code>, <code>PUT</code> and 
 * <code>POST</code>.</p>
 * 
 * <p>The <code>request</code> element can contain <code>header</code> elements.
 * Each header element has a <code>name</code> and <code>value</code> attribute.
 * These are added to the request for the URI request.</p>
 * 
 * <p>The <code>request</code> element can also contain a <code>body</code>
 * element. This body element contains XML which is submitted to the URI 
 * request. For example the <code>PUT</code> and <code>POST</code> method
 * support the <code>body</code> element.</p>
 * 
 * <h2>Configure</h2>
 * 
 * <pre>
 * &lt;map:transformer name="rest"
 *	src="org.apache.cocoon.transformation.RESTTransformer" logger="rest"/&gt;
 *		
 * &lt;map:transformer name="rest"
 *	src="org.apache.cocoon.transformation.RESTTransformer" logger="rest"&gt;
 *	&lt;
 *
 *
 *
 * /&gt;
 * &lt;/map:transformer&gt;
 * </pre>
 * 
 * <h2>Definition</h2>
 * 
 * <pre>
 * &lt;map:transformer name="rest"
 *	src="org.apache.cocoon.transformation.RESTTransformer" logger="rest"/&gt;
 *		
 * &lt;map:transformer name="rest"
 *	src="org.apache.cocoon.transformation.RESTTransformer" logger="rest"&gt;
 *	&lt;map:parameter name="username" value="user"/&gt;
 *	&lt;map:parameter name="password" value="secret"/&gt;
 *	&lt;map:parameter name="preemptive-authentication" value="true"/&gt;
 * &lt;/map:transformer&gt;
 * </pre>
 * 
 * <h2>Invocation</h2>
 * 
 * <pre>
 * &lt;map:transform type="rest"/&gt;
 * </pre>
 * 
 * <h2>Tags</h2>
 * 
 * <pre>
 * &lt;rest:request target="uri" method="POST|GET|DELETE|PUT"&gt;
 *  &lt;rest:header name="request-header-name" value="value"/&gt;
 *  &lt;rest:body&gt; - Required for POST, PUT method.
 *   &lt;xml-content/&gt;
 *  &lt;/rest:body&gt;
 * &lt;/rest:request&gt;
 * </pre>
 * 
 * <h2>Input XML document example (GET)</h2>
 * 
 * <pre>
 * &lt;page&gt;
 *   ...
 *   &lt;rest:request target="http://localhost/rest/resource/42" method="GET"&gt;
 *    &lt;rest:header name="Content-Type" value="text/xml; charset=UTF-8"/&gt;
 *   &lt;/rest:request&gt;
 *   ...
 * &lt;/page&gt;
 * </pre>
 * 
 * <h2>Input XML document example (POST)</h2>
 * 
 * <pre>
 * &lt;page&gt;
 *   ...
 *   &lt;rest:request target="http://localhost/rest/resource/42" method="POST"&gt;
 *    &lt;rest:header name="Content-Type" value="text/xml; charset=UTF-8"/&gt;
 *    &lt;rest:body&gt; 
 *     &lt;xml-content/&gt;
 *    &lt;/rest:body&gt;
 *   &lt;/rest:request&gt;
 *   ...
 * &lt;/page&gt;
 * </pre>
 * 
 * <h2>Input XML document example (PUT)</h2>
 *
 * <pre>
 * &lt;page&gt;
 *   ...
 *   &lt;rest:request target="http://localhost/rest/resource/42" method="PUT"&gt;
 *    &lt;rest:header name="Content-Type" value="text/xml; charset=UTF-8"/&gt;
 *    &lt;rest:body&gt;
 *     &lt;xml-content/&gt;
 *    &lt;/rest:body&gt;
 *   &lt;/rest:request&gt;
 *   ...
 * &lt;/page&gt;
 * </pre>
 * 
 * <h2>Input XML document example (DELETE)</h2>
 * 
 * <pre>
 * &lt;page&gt;
 *   ...
 *   &lt;rest:request target="http://localhost/rest/resource/42" method="DELETE"&gt;
 *    &lt;rest:header name="Content-Type" value="text/xml; charset=UTF-8"/&gt;
 *   &lt;/rest:request&gt;
 *   ...
 * &lt;/page&gt;
 * </pre>
 * 
 * <h2>Output XML document example (no body)</h2>
 * 
 *   &lt;rest:response target="http://localhost/rest/resource/42" method="PUT"&gt;
 *    &lt;rest:status code="201" msg="Content created"/&gt;
 *    &lt;rest:header name="Content-Type" value="text/html; charset=UTF-8"/&gt;
 *    &lt;rest:header name="ServerInfo" value="Jetty/6.1"/&gt;
 *   &lt;/rest:request&gt;
 *
 * <h2>Output XML document example (body)</h2>
 * 
 *   &lt;rest:response target="http://localhost/rest/resource/42" method="GET"&gt;
 *    &lt;rest:status code="200" msg=""/&gt;
 *    &lt;rest:header name="Content-Type" value="text/html; charset=UTF-8"/&gt;
 *    &lt;rest:header name="ServerInfo" value="Jetty/6.1"/&gt;
 *    &lt;rest:body&gt;
 *     &lt;xml-content/&gt;
 *    &lt;/rest:body&gt;
 *   &lt;/rest:request&gt;
 *
 * @author Hubert A. Klein Ikkink
 * @author Huib Verweij
 */
public class RESTTransformer extends AbstractSAXTransformer
        implements Disposable {

    private static final String NS_URI = "http://org.apache.cocoon.transformation/rest/1.0";
    private static final String NS_PREFIX = "rest";
    private static final String SETUP_PASSWORD_ATTR = "password";
    private static final String SETUP_PREEMPTIVEAUTHENTICATION_ATTR = "preemptive-authentication";
    private static final String SETUP_USERNAME_ATTR = "username";
    private static final String REQUEST_TAG = "request";
    private static final String METHOD_ATTR = "method";
    private static final String TARGET_ATTR = "target";
    private static final String HEADER_TAG = "header";
    private static final String NAME_ATTR = "name";
    private static final String VALUE_ATTR = "value";
    private static final String BODY_TAG = "body";
    private static final String RESPONSE_TAG = "response";
    private static final String STATUS_TAG = "status";
    private static final String CODE_ATTR = "code";
    private static final String MSG_ATTR = "msg";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";
    private static HttpClient client = new HttpClient(new MultiThreadedHttpConnectionManager());
    private static AuthScope authScope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM);
    private boolean preemptive_authentication = false;
    private HttpState m_state = null;
    private String m_method = null;
    private String m_target = null;
    private Map m_headers = null;
    private DocumentFragment m_requestdocument = null;
    private String username = "";
    private String password = "";

    public RESTTransformer() {
        super.defaultNamespaceURI = NS_URI;
    }

    @Override
    public void configure(Configuration configuration) throws
            ConfigurationException {
        super.configure(configuration);

        preemptive_authentication = false;
        m_state = new HttpState();

        final Configuration authentication = configuration.getChild("authentication");
        if (null != authentication) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Setting authentication details.");
            }
            if (null != authentication.getChild(SETUP_USERNAME_ATTR)) {
                username = authentication.getChild(SETUP_USERNAME_ATTR).getValue("");
                password = authentication.getChild(SETUP_PASSWORD_ATTR).getValue("");
                m_state.setCredentials(authScope, new UsernamePasswordCredentials(username, password));
            }
            if (null != authentication.getChild(SETUP_PREEMPTIVEAUTHENTICATION_ATTR)) {
                preemptive_authentication = Boolean.parseBoolean(authentication.getChild(SETUP_PREEMPTIVEAUTHENTICATION_ATTR).getValue("false"));
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("Setting preemptive-authentication from configuration: " + preemptive_authentication + ".");
                }
            }
        }
    }

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
            throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, par);

        if (null != par.getParameter(SETUP_PREEMPTIVEAUTHENTICATION_ATTR, null)) {
            preemptive_authentication = Boolean.parseBoolean(par.getParameter(SETUP_PREEMPTIVEAUTHENTICATION_ATTR, "false"));
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Setting preemptive-authentication in setup(): " + preemptive_authentication + ".");
            }
        }
        if (null != par.getParameter(SETUP_USERNAME_ATTR, null)) {
            username = par.getParameter(SETUP_USERNAME_ATTR, "");
            password = par.getParameter(SETUP_PASSWORD_ATTR, "");
            m_state.setCredentials(authScope, new UsernamePasswordCredentials(username, password));
        }
    }

    @Override
    public void recycle() {
        super.recycle();

        m_method = null;
        m_target = null;
        m_requestdocument = null;
    }

    @Override
    public void dispose() {
        recycle();
        manager = null;
    }

    @Override
    public void startElement(String uri, String name, String raw, Attributes atts)
            throws SAXException {
        if (name.equals(REQUEST_TAG) && uri.equals(NS_URI)) {
            m_headers = new HashMap();
            m_method = atts.getValue(METHOD_ATTR);
            if (m_method == null) {
                final String msg = "The <request> element must contain a \"method\" attribute";
                throw new IllegalStateException(msg);
            }
            if (!(m_method.equalsIgnoreCase(METHOD_GET) || m_method.equalsIgnoreCase(METHOD_DELETE) || m_method.equalsIgnoreCase(METHOD_PUT) || m_method.equalsIgnoreCase(METHOD_POST))) {
                final String msg = "The <request> element must contain a valid \"method\" attribute. Valid values are \"POST\", \"PUT\", \"GET\" and \"DELETE\".";
                throw new IllegalStateException(msg);
            }
            if ((m_target = atts.getValue(TARGET_ATTR)) == null) {
                throw new IllegalStateException("The <request> element must contain a \"target\" attribute");
            }
        } else if (name.equals(HEADER_TAG) && uri.equals(NS_URI)) {
            final String hname = atts.getValue(NAME_ATTR);
            if (hname == null) {
                throw new SAXException("The <header> element requires a \"name\" attribute");
            }
            final String value = atts.getValue(VALUE_ATTR);
            if (value == null) {
                throw new SAXException("The <header> element requires a \"value\" attribute");
            }
            m_headers.put(hname, value);
        } else if (name.equals(BODY_TAG) && uri.equals(NS_URI)) {
            startRecording();
        } else {
            super.startElement(uri, name, raw, atts);
        }
    }

    @Override
    public void endElement(String uri, String name, String raw)
            throws SAXException {
        if (name.equals(REQUEST_TAG) && uri.equals(NS_URI)) {
            try {
                HttpURL url = new HttpURL(m_target);
                if (url.getUser() != null && !"".equals(url.getUser())) {
                    m_state.setCredentials(authScope, new UsernamePasswordCredentials(
                            url.getUser(),
                            url.getPassword()));
                }
                m_target = url.getURI();

            } catch (Exception e) {
                //ignore
            }

            // create method
            HttpMethod method = null;
            if (m_method.equalsIgnoreCase(METHOD_GET)) {
                method = new GetMethod(m_target);
            } else if (m_method.equalsIgnoreCase(METHOD_DELETE)) {
                method = new DeleteMethod(m_target);
            } else if (m_method.equalsIgnoreCase(METHOD_POST)) {
                method = new PostMethod(m_target);
            } else if (m_method.equalsIgnoreCase(METHOD_PUT)) {
                method = new PutMethod(m_target);
            }

            try {
                // add request headers
                Iterator headers = m_headers.entrySet().iterator();
                while (headers.hasNext()) {
                    Map.Entry header = (Map.Entry) headers.next();
                    method.addRequestHeader((String) header.getKey(), (String) header.getValue());
                }

                if (m_method.equalsIgnoreCase(METHOD_POST) || m_method.equalsIgnoreCase(METHOD_PUT)) {
                    Properties props = XMLUtils.createPropertiesForXML(false);
                    props.put(OutputKeys.ENCODING, "UTF-8");
                    String body = XMLUtils.serializeNode(m_requestdocument, props);
                    // set request body
                    ((EntityEnclosingMethod) method).setRequestEntity(new StringRequestEntity(body, "text/xml", "UTF-8"));
                }

                method.setDoAuthentication(true);


                // execute the request
                executeRequest(method);
            } catch (ProcessingException e) {
                if (getLogger().isErrorEnabled()) {
                    getLogger().debug("Couldn't read request from sax stream", e);
                }
                throw new SAXException("Couldn't read request from sax stream", e);
            } catch (UnsupportedEncodingException e) {
                if (getLogger().isErrorEnabled()) {
                    getLogger().debug("UTF-8 encoding not present", e);
                }
                throw new SAXException("UTF-8 encoding not present", e);
            } finally {
                method.releaseConnection();
                m_headers = null;
            }
        } else if (name.equals(HEADER_TAG) && uri.equals(NS_URI)) {
            // dont do anything
        } else if (name.equals(BODY_TAG) && uri.equals(NS_URI)) {
            m_requestdocument = super.endRecording();
        } else {
            super.endElement(uri, name, raw);
        }
    }

    private String qName(String element) {
        return NS_PREFIX + ":" + element;
    }

    private void executeRequest(HttpMethod method) throws SAXException {
        try {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("using preemptive-authentication=" + preemptive_authentication + ".");
            }
            client.getParams().setAuthenticationPreemptive(preemptive_authentication);
            client.executeMethod(method.getHostConfiguration(), method, m_state);

            super.contentHandler.startPrefixMapping(NS_PREFIX, NS_URI);

            // start <response>
            AttributesImpl atts = new AttributesImpl();
            atts.addCDATAAttribute(TARGET_ATTR, m_target);
            atts.addCDATAAttribute(METHOD_ATTR, m_method);
            super.contentHandler.startElement(NS_URI, RESPONSE_TAG, qName(RESPONSE_TAG), atts);
            atts.clear();

            // <status>
            atts.addCDATAAttribute(CODE_ATTR, String.valueOf(method.getStatusCode()));
            atts.addCDATAAttribute(MSG_ATTR, method.getStatusText());
            super.contentHandler.startElement(NS_URI, STATUS_TAG, qName(STATUS_TAG), atts);
            atts.clear();
            super.contentHandler.endElement(NS_URI, STATUS_TAG, qName(STATUS_TAG));

            // <header>s
            Header[] headers = method.getResponseHeaders();
            for (int i = 0; i < headers.length; i++) {
                atts.addCDATAAttribute(NAME_ATTR, headers[i].getName());
                atts.addCDATAAttribute(VALUE_ATTR, headers[i].getValue());
                super.contentHandler.startElement(NS_URI, HEADER_TAG, qName(HEADER_TAG), atts);
                atts.clear();
                super.contentHandler.endElement(NS_URI, HEADER_TAG, qName(HEADER_TAG));
            }

            // response <body>
            final InputStream in = method.getResponseBodyAsStream();
            if (in != null) {
                String mimeType = null;
                Header header = method.getResponseHeader("Content-Type");
                if (header != null) {
                    mimeType = header.getValue();
                    int pos = mimeType.indexOf(';');
                    if (pos != -1) {
                        mimeType = mimeType.substring(0, pos);
                    }
                }
                if (mimeType != null) {                    
                    if (mimeType.equals("text/plain")) {
                        super.contentHandler.startElement(NS_URI, BODY_TAG, qName(BODY_TAG), atts);
                        String error = IOUtils.toString(in, "UTF-8");
                        super.contentHandler.characters(error.toCharArray(), 0, error.length());
                        super.contentHandler.endElement(NS_URI, BODY_TAG, qName(BODY_TAG));
                    }
                           // Treat everything else as XML
                    else { //if (mimeType.equals("text/xml") || mimeType.equals("application/xml")) {
                        super.contentHandler.startElement(NS_URI, BODY_TAG, qName(BODY_TAG), atts);
                        IncludeXMLConsumer consumer = new IncludeXMLConsumer(super.contentHandler);
                        XMLizer xmlizer = null;
                        try {
                            xmlizer = (XMLizer) manager.lookup(XMLizer.ROLE);
                            xmlizer.toSAX(in, mimeType, m_target, consumer);
                        } catch (ServiceException ce) {
                            throw new SAXException("Missing service dependency: " + XMLizer.ROLE, ce);
                        } finally {
                            manager.release(xmlizer);
                        }
                        super.contentHandler.endElement(NS_URI, BODY_TAG, qName(BODY_TAG));
                    }
                }
            }

            // end <response>
            super.contentHandler.endElement(NS_URI, RESPONSE_TAG, qName(RESPONSE_TAG));

            super.contentHandler.endPrefixMapping(NS_PREFIX);
        } catch (HttpException e) {
            throw new SAXException("Error executing REST request. Server responded " + e.getReasonCode() + " (" + e.getReason() + ") - " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SAXException("Error executing REST request", e);
        }
    }

}
