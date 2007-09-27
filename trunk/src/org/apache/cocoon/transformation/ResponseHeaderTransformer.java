// $Id: ResponseHeaderTransformer.java,v 1.4 2004/11/19 14:55:40 verwe00t Exp $
package org.apache.cocoon.transformation;

import java.io.*;
import java.util.*;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Response;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.environment.http.HttpResponse;
import org.apache.cocoon.transformation.AbstractTransformer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Set HTTP response headers from an XML document.
 * Declaration in sitemap:
 * <pre>
 *  &lt;map:transformers default="xslt"&gt;
 *    &lt;map:transformer name="response-headers" src="org.apache.cocoon.transformation.ResponseHeaderTransformer"/&gt;
 *  &lt;/map:transformers&gt;
 * </pre>
 * Usage in pipeline:
 * <pre>
 *  &lt;map:transform type="response-headers"/&gt;
 * </pre>
 * It will look for elements in the "http://apache.org/cocoon/responseHeader/1.0"
 * namespace. The following elements are recognized (assuming xmlns:header="http://apache.org/cocoon/responseHeader/1.0"):
 * <dl>
 * <dt>&lt;header:set name="Xyz" value="some value"/&gt;</dt>
 * <dd>Sets the header "Xyz" to "some value"</dd>
 * <dt>&lt;header:set-status code="nnn"/&gt;</dt>
 * <dd>Sets the HTTP status of the response.</dd>
 * <dt>&lt;header:set-error code="nnn" message="some error description"/&gt;</dt>
 * <dd>Sets the HTTP status code to "nnn" and the status message to the value of the message attribute.
 * 'nnn' should be an integer HTTP status code. Only applicable for HTTP responses and ignored for other response types.
 * Put this tag after other header:-tags otherwise the other tags will be ignored!</dd>
 * </dl>
 */
public class ResponseHeaderTransformer
  extends AbstractTransformer
{

  private static final String NAMESPACE = "http://apache.org/cocoon/responseHeader/1.0";
  private static final String SET_ELEMENT = "set";
  private static final String SET_STATUS_ELEMENT = "set-status";
  private static final String SET_ERROR_ELEMENT = "set-error";
  private Response response;

  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException
  {
    response = ObjectModelHelper.getResponse(objectModel);
  }

  public void startElement(String namespaceURI, String lName, String qName, Attributes attributes)
  throws SAXException
  {
    if (namespaceURI.equals(NAMESPACE)) {
    	if (lName.equals(SET_ELEMENT)){
    		String name = attributes.getValue("name");
    		String value = attributes.getValue("value");
    		response.setHeader(name, value);
    	}
    	else if (lName.equals(SET_STATUS_ELEMENT)){
    		if (response instanceof HttpResponse) {
          int code = 500;
    			try {
		    		code = Integer.parseInt(attributes.getValue("code"));
		        ((HttpResponse)response).setStatus(code);
    			}
    			catch (Exception x) {}
    		}
    	}
    	else if (lName.equals(SET_ERROR_ELEMENT)){
    		if (response instanceof HttpResponse) {
          int code = 500;
          String msg = "Server error";
    			try {
		    		code = Integer.parseInt(attributes.getValue("code"));
		    		msg = attributes.getValue("message");
		        ((HttpResponse)response).sendError(code,msg);
    			}
    			catch (Exception x) {}
    		}
    	}
    } else {
        super.startElement(namespaceURI, lName, qName, attributes);    	
    }  
  }

  public void endElement(String namespaceURI, String lName, String qName)
  throws SAXException
  {
    if (!namespaceURI.equals(NAMESPACE)) {
      super.endElement(namespaceURI, lName, qName);
    }
  }

  public void characters(char[] c, int start, int len)
  throws SAXException
  {
    super.characters(c, start, len);
  }

}
