package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.Cookie;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.environment.http.HttpCookie;
import org.apache.cocoon.xml.AttributesImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;


/**
 * This class is a cocoon-transformer that is capable of handling cookies. 
 * 
 * Use the following sitemap component declaration to define, configure and parameterize the transformer.
 * in the <code>map:sitemap/map:components/map:transformers</code>:
 * 
 * <pre>
 * &lt;map:transformer name="cookie"; src="org.apache.cocoon.transformation.CookieTransformer" /&gt;
 * </pre>
 * 
 * When the CookieTransformer is declared it can be used in the pipelines there are no parameters to set. Below you find an example:
 * 
 * <pre>
 * &lt;map:transformer type="cookie"/&gt; 
 * </pre>
 * 
 * The CookieTransformer provides the following functions:
 * <ul>
 * 		<li>getCookie</li>
 *  	<li>setCookie</li>
 * </ul>
 * 
 * <i>Example to set a cookie</i>
 * <pre>
 * &lt;cookie:setCookie name="cookie-name" value="cookie-value" maxAge="-1" path="/" xmlns:cookie="http://apache.org/cocoon/transformation/cookie"&gt;
 * </pre> 
 * 
 * <i>Example to get a cookie</i>
 * <pre>
 * &lt;cookie:getCookie name="cookie-name" xmlns:cookie="http://apache.org/cocoon/transformation/cookie"/&gt;
 * </pre>
 * 
 * <i>Attributes:</i>
 * <table>
 * <tr>
 *   <td>attributename</td><td>description</td><td>type</td><td>default-value</td>
 * </tr>
 * <tr>
 *   <td>name</td><td>the name of the cookie</td><td>required</td><td></td>
 * </tr>
 * <tr>
 *   <td>value</td><td>the value of the cookie(to set the cookie with)</td><td>required</td><td></td>   
 * </tr>
 * <tr>
 *   <td>maxAge</td><td>the value of the cookie(to set the cookie with)</td><td>optional</td><td>-1 (until browser quits)</td>   
 * </tr>
 * <tr>
 *   <td>path</td><td>the path of the cookie</td><td>optional</td><td>current-path</td>   
 * </tr>
 * </table>
 * <br/>
 *   
 * @author <a href='mailto:bfroklage@be-value.nl'>Bart Froklage</a>
 *
 */
public class CookieTransformer extends AbstractSAXTransformer {
	
	public static final String NAMESPACE = "http://apache.org/cocoon/transformation/cookie"; 
	public static final String GET_COOKIE_TAG = "getCookie";
	public static final String SET_COOKIE_TAG = "setCookie";
	public static final String PATH_ATTR = "path";
	public static final String NAME_ATTR = "name";
	public static final String VALUE_ATTR = "value";
	public static final String COOKIE_TAG = "cookie";
	private static final String MAX_AGE_ATTR = "maxAge";
	private static final int MAX_AGE = 99999999;
	

	public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params) throws ProcessingException, SAXException, IOException {
		defaultNamespaceURI = NAMESPACE;	
		super.setup(resolver, objectModel, src, params);				
	}

	@Override
	public void startTransformingElement(String namespaceURI, String localName, String qName,
			Attributes attr) throws ProcessingException, IOException,
			SAXException {
		
		if (localName.equals(SET_COOKIE_TAG)) {
			
			/* Set the cookie. */
			Cookie cookie = new HttpCookie(attr.getValue(NAME_ATTR), attr.getValue(VALUE_ATTR));
			
			try {				
				cookie.setMaxAge(attr.getValue(MAX_AGE_ATTR).equals("max") ? MAX_AGE : Integer.parseInt(attr.getValue(MAX_AGE_ATTR)));
			} catch (Exception ex) {
				cookie.setMaxAge(-1);				
			}
			
			if (null != attr.getValue(PATH_ATTR)) {
				cookie.setPath(attr.getValue(PATH_ATTR));
			}
			
			this.response.addCookie(cookie);
			
			/* Don't provide a result, simular to the SessionTransformer. */
		
		} else if (localName.equals(GET_COOKIE_TAG)) {
			
			/* Search for the cookie and send it as a XML-element. */
			
			if (null != request.getCookies()) {
				
				
				for (Cookie cookie: request.getCookies()) {
					if (cookie.getName().equals(attr.getValue(NAME_ATTR))) {
						sendCookie(qName.substring(0, qName.indexOf(':')), cookie); 
						break;
					}
				}
			}
		} else {
			super.startTransformingElement(namespaceURI, localName, qName, attr);		
		}	
	}

	private void sendCookie(String nsPrefix, Cookie cookie) throws SAXException {
		
		AttributesImpl atts = new AttributesImpl();		
		atts.addAttribute("", VALUE_ATTR, VALUE_ATTR, "CDATA", cookie.getValue());
		atts.addAttribute("", NAME_ATTR, NAME_ATTR, "CDATA", cookie.getName());
		atts.addAttribute("", MAX_AGE_ATTR, MAX_AGE_ATTR, "CDATA", Integer.toString(cookie.getMaxAge()));
		
		contentHandler.startElement(NAMESPACE, COOKIE_TAG, nsPrefix + ":" + COOKIE_TAG, atts);		
		contentHandler.endElement(NAMESPACE, COOKIE_TAG, nsPrefix + ":" + COOKIE_TAG);
	}

	@Override
	public void endTransformingElement(String namespaceURI, String localName,
			String qName) throws ProcessingException, IOException, SAXException {
		
		if (!(localName.equals(GET_COOKIE_TAG) || localName.equals(SET_COOKIE_TAG))) {
						
			/* Otherwise just send the XML trough. */
			super.endTransformingElement(namespaceURI, localName, qName);	
		}		
	}
	
}
