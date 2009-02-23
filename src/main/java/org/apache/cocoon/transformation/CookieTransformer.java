package org.apache.cocoon.transformation;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.Cookie;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * A Cocoon transformer for handling cookies. 
 * 
 * Declaration, configuration and parameters of the transformer
 * in <code>map:sitemap/map:components/map:transformers</code>:
 * 
 * <pre>
 * &lt;map:transformer name="cookie"; src="org.apache.cocoon.transformation.CookieTransformer"/&gt;
 * </pre>
 * 
 * Example use in sitemap:
 * 
 * <pre>
 * &lt;map:transformer type="cookie"/&gt; 
 * </pre>
 * 
 * The CookieTransformer recognizes elements in the "http://apache.org/cocoon/transformation/cookie" namespace/.
 * 
 * <b>To set a cookie:</b>
 * 
 * <pre>
 * &lt;cookie:setCookie name="cookie-name" value="cookie-value" maxAge="max" domain="cookie.com" path="/" xmlns:cookie="http://apache.org/cocoon/transformation/cookie"&gt;
 * </pre>
 * sets the specified cookie.
 * The maxAge="max" specifies the largest possible maximum age of a cookie,
 * "-1" indicates that the cookie lasts until the browser quits, "0" indicates that a cookie should be discarded.
 * 
 * <b>To get a specific cookie:</b>
 * 
 * <pre>
 * &lt;cookie:getCookie name="cookie-name" xmlns:cookie="http://apache.org/cocoon/transformation/cookie"/&gt;
 * </pre>
 * returns the cookie value, for example
 * <pre>
 * &lt;cookie:cookie name="cookie-name" value="cookie-value"/&gt;
 * </pre>
 * 
 * <b>Cookies can contain multiple values in <i>crumbs</i>:</b>
 * 
 * <pre>
 * &lt;cookie:setCrumbs name="cookie-name" maxAge="max" path="/" xmlns:cookie="http://apache.org/cocoon/transformation/cookie"&gt;
 *   &lt;crumb name="foo" value="bar"/&gt;
 *   &lt;crumb name="greet" value="Hello World"/&gt;
 * &lt;/cookie:setCrumbs&gt;
 * </pre>
 * encodes 'crumbs' into a URI/query-string encoded cookie.
 * For example, the value of this cookie would be "foo=bar&greet=Hello%20World".
 * 
 * <pre>
 * &lt;cookie:getCrumbs name="cookie-name" xmlns:cookie="http://apache.org/cocoon/transformation/cookie"/&gt;
 * </pre>
 * returns 'crumbs' from a URI/query-string encoded cookie.
 * For example, if the cookie value is "foo=bar&greet=Hello%20World", this returns:
 * <pre>
 *   &lt;cookie:cookie name="cookie-name"&gt;
 *     &lt;crumb name="foo" value="bar"/&gt;
 *     &lt;crumb name="greet" value="Hello World"/&gt;
 *   &lt;/cookie:cookie&gt;
 * </pre>
 * 
 * <i>Attributes:</i>
 * <table>
 * <tr>
 *   <th>attribute name</th><th>description</th><th>type</th><th>default-value</th>
 * </tr>
 * <tr>
 *   <td>name</td><td>the name of the cookie</td><td>required</td><td></td>
 * </tr>
 * <tr>
 *   <td>value</td><td>the value of the cookie(to set the cookie with)</td><td>required</td><td></td>   
 * </tr>
 * <tr>
 *   <td>maxAge</td><td>the maximum age in seconds of the cookie</td><td>optional</td><td>-1 (until browser quits)</td>   
 * </tr>
 * <tr>
 *   <td>domain</td><td>the domain of the cookie</td><td>optional</td><td>current domain</td>   
 * </tr>
 * <tr>
 *   <td>path</td><td>the path of the cookie</td><td>optional</td><td>current path</td>   
 * </tr>
 * </table>
 * <br/>
 *
 * The browser is expected to support 20 cookies for each web server, and may limit cookie size to 4 kB each.
 * 
 */
public class CookieTransformer extends AbstractSAXTransformer {

    public static final String NAMESPACE = "http://apache.org/cocoon/transformation/cookie";
    public static final String SET_COOKIE_TAG = "setCookie";
    public static final String GET_COOKIE_TAG = "getCookie";
    public static final String SET_CRUMBS_TAG = "setCrumbs";
    public static final String GET_CRUMBS_TAG = "getCrumbs";
    private static final String DOMAIN_ATTR = "domain";
    public static final String PATH_ATTR = "path";
    public static final String NAME_ATTR = "name";
    public static final String VALUE_ATTR = "value";
    public static final String COOKIE_TAG = "cookie";
    public static final String CRUMB_TAG = "crumb";
    private static final String MAX_AGE_ATTR = "maxAge";
    private static final int MAX_AGE = 99999999;
    private HashMap<String, String> crumbs;
    private String cookieName;
    private String maxAge;
    private String domain;
    private String path;

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
            throws ProcessingException, SAXException, IOException {
        defaultNamespaceURI = NAMESPACE;
        super.setup(resolver, objectModel, src, params);
    }

    @Override
    public void startTransformingElement(String namespaceURI, String localName, String qName, Attributes attr)
            throws ProcessingException, IOException, SAXException {
        String nsPrefix = (qName.contains(":") ? qName.substring(0, qName.indexOf(':') + 1) : "");
        if (localName.equals(SET_COOKIE_TAG)) {
            setCookie(attr.getValue(NAME_ATTR), attr.getValue(VALUE_ATTR), attr.getValue(MAX_AGE_ATTR), attr.getValue(DOMAIN_ATTR), attr.getValue(PATH_ATTR));
        /* Don't provide a result, similar to the SessionTransformer. */
        } else if (localName.equals(GET_COOKIE_TAG)) {
            if (null != request.getCookies()) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals(attr.getValue(NAME_ATTR))) {
                        sendCookieElement(nsPrefix, cookie);
                        break;
                    }
                }
            }
        } else if (localName.equals(SET_CRUMBS_TAG)) {
            cookieName = attr.getValue(NAME_ATTR);
            crumbs = new HashMap<String, String>();
            maxAge = attr.getValue(MAX_AGE_ATTR);
            domain = attr.getValue(DOMAIN_ATTR);
            path = attr.getValue(PATH_ATTR);
        } else if (localName.equals(CRUMB_TAG)) {
            crumbs.put(attr.getValue(NAME_ATTR), attr.getValue(VALUE_ATTR));
        } else if (localName.equals(GET_CRUMBS_TAG)) {
            if (null != request.getCookies()) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals(attr.getValue(NAME_ATTR))) {
                        startCookieElement(nsPrefix, cookie.getName(), null, Integer.toString(cookie.getMaxAge()));
                        String[] parameters = cookie.getValue().split("&");
                        for (String parameter : parameters) {
                            String[] keyValue = parameter.split("=", 2);
                            sendCrumbElement(nsPrefix, URLDecoder.decode(keyValue[0], "UTF-8"), URLDecoder.decode(keyValue[1], "UTF-8"));
                        }
                        endCookieElement(nsPrefix);
                        break;
                    }
                }
            }
        } else {
            throw new ProcessingException("Unknown cookie element: " + qName);
        }
    }

    @Override
    public void endTransformingElement(String namespaceURI, String localName, String qName)
            throws ProcessingException, IOException, SAXException {
        if (localName.equals(SET_CRUMBS_TAG)) {
            String value = "";
            for (String name : crumbs.keySet()) {
                value += (value.equals("") ? "" : "&") +
                        URLEncoder.encode(name, "UTF-8") +
                        "=" +
                        URLEncoder.encode(crumbs.get(name), "UTF-8");
            }
            setCookie(cookieName, value, maxAge, domain, path);
        }
    }

    private void setCookie(String name, String value, String maxAge, String domain, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge == null ? -1 : (maxAge.equals("max") ? MAX_AGE : Integer.parseInt(maxAge)));
        if (domain != null) {
            cookie.setDomain(domain);
        }
        if (path != null) {
            cookie.setPath(path);
        }
        this.response.addCookie(cookie);
    }

    private void sendCookieElement(String nsPrefix, Cookie cookie) throws
            SAXException {
        startCookieElement(nsPrefix, cookie.getName(), cookie.getValue(), Integer.toString(cookie.getMaxAge()));
        endCookieElement(nsPrefix);
    }

    private void startCookieElement(String nsPrefix, String name, String value, String maxAge)
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", NAME_ATTR, NAME_ATTR, "CDATA", name);
        if (value != null) {
            atts.addAttribute("", VALUE_ATTR, VALUE_ATTR, "CDATA", value);
        }
        atts.addAttribute("", MAX_AGE_ATTR, MAX_AGE_ATTR, "CDATA", maxAge);
        contentHandler.startElement(NAMESPACE, COOKIE_TAG, nsPrefix + COOKIE_TAG, atts);
    }

    private void endCookieElement(String nsPrefix) throws SAXException {
        contentHandler.endElement(NAMESPACE, COOKIE_TAG, nsPrefix + COOKIE_TAG);
    }

    private void sendCrumbElement(String nsPrefix, String name, String value)
            throws SAXException {
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", NAME_ATTR, NAME_ATTR, "CDATA", name);
        atts.addAttribute("", VALUE_ATTR, VALUE_ATTR, "CDATA", value);
        contentHandler.startElement(NAMESPACE, CRUMB_TAG, nsPrefix + CRUMB_TAG, atts);
        contentHandler.endElement(NAMESPACE, CRUMB_TAG, nsPrefix + CRUMB_TAG);
    }
}
