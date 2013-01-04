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
package org.apache.cocoon.components.language.markup.xsp;

import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.xscript.XScriptManager;
import org.apache.cocoon.components.xscript.XScriptObject;
import org.apache.cocoon.components.xscript.XScriptObjectInlineXML;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.excalibur.source.SourceUtil;
import org.xml.sax.InputSource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Helper for the SOAP logicsheet.
 *
 * @author <a href="mailto:ovidiu@cup.hp.com">Ovidiu Predescu</a>
 * @author <a href="mailto:vgritsenko@apache.org">Vadim Gritsenko</a>
 * @version CVS $Id: SOAPHelper.java 454485 2006-10-09 20:11:47Z joerg $
 * @since July 16, 2001
 */
public class XSPSOAPHelper {
    XScriptManager xscriptManager;
    URL url;
    String action = "";
    XScriptObject xscriptObject;
    String authorization = "";
    int timeoutSeconds;
    String defaultResponseEncoding = "us-ascii";

    public XSPSOAPHelper(ComponentManager manager, String urlContext, String url,
                      String action, String authorization, XScriptObject xscriptObject)
            throws MalformedURLException, ComponentException
    {
        this(manager, urlContext, url, action, authorization, xscriptObject, "us-ascii");
    }
    
    public XSPSOAPHelper(ComponentManager manager, String urlContext, String url,
                      String action, String authorization, XScriptObject xscriptObject,
                      String defaultResponseEncoding)
            throws MalformedURLException, ComponentException
    {
        this.xscriptManager = (XScriptManager) manager.lookup(XScriptManager.ROLE);
        URL context = new URL(urlContext);
        this.url = new URL(context, url);
        this.action = action;
        this.authorization = authorization;
        this.xscriptObject = xscriptObject;
        this.defaultResponseEncoding = defaultResponseEncoding;
        this.timeoutSeconds = 60*5; // Default timeout is 5 minutes. I don't know how to change this via the soap logicsheet.
    }

    public XScriptObject invoke() throws ProcessingException
    {
        HttpConnection conn = null;

        try {
            if (this.action == null || this.action.length() == 0) {
                this.action = "\"\"";
            }

            String host = this.url.getHost();
            int port = this.url.getPort();
            Protocol protocol = Protocol.getProtocol(this.url.getProtocol());

            if (System.getProperty("http.proxyHost") != null) {
                String proxyHost = System.getProperty("http.proxyHost");
                int proxyPort = Integer.parseInt(System.getProperty("http.proxyPort"));
                conn = new HttpConnection(proxyHost, proxyPort, host, null, port, protocol);
            } else {
                conn = new HttpConnection(host, port, protocol);
            }
            conn.setSoTimeout(1000*timeoutSeconds);

            PostMethod method = new PostMethod(this.url.getFile());
            String request;

            try {
                // Write the SOAP request body
                if (this.xscriptObject instanceof XScriptObjectInlineXML) {
                    // Skip overhead
                    request = ((XScriptObjectInlineXML)this.xscriptObject).getContent();
                } else {
                    StringBuffer bodyBuffer = new StringBuffer();
                    InputSource saxSource = this.xscriptObject.getInputSource();

                    Reader r = null;
                    // Byte stream or character stream?
                    if (saxSource.getByteStream() != null) {
                        r = new InputStreamReader(saxSource.getByteStream());
                    } else {
                        r = saxSource.getCharacterStream();
                    }

                    try {
                        char[] buffer = new char[1024];
                        int len;
                        while ((len = r.read(buffer)) > 0) {
                            bodyBuffer.append(buffer, 0, len);
                        }
                    } finally {
                        if (r != null) {
                            r.close();
                        }
                    }

                    request = bodyBuffer.toString();
                }

            } catch (Exception ex) {
                throw new ProcessingException("Error assembling request", ex);
            }

            method.setRequestHeader(
                    new Header("Content-type", "text/xml; charset=utf-8"));
            method.setRequestHeader(new Header("SOAPAction", this.action));
            method.setRequestBody(request);

            if (this.authorization != null && !this.authorization.equals("")) {
               method.setRequestHeader(
                       new Header("Authorization",
                                  "Basic " + SourceUtil.encodeBASE64(this.authorization)));
            }

            method.execute(new HttpState(), conn);

            String contentType = method.getResponseHeader("Content-type").toString();
            // Check if charset given, if not, use "UTF-8" (cannot just use
            // getResponseCharSet() as it fills in "ISO-8859-1" if
            // the charset is not specified)
            String charset = contentType.indexOf("charset=") == -1
                    ? this.defaultResponseEncoding
                    : method.getResponseCharSet();
            String ret = new String(method.getResponseBody(), charset);
            if (ret.indexOf("BWBR0007211") > -1) {
                ret = ret;
                System.out.println("Boe!");
            }
        
            return new XScriptObjectInlineXML(this.xscriptManager, ret);
        } catch (ProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProcessingException("Error invoking remote service: " + ex, ex);
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception ex) {
            }
        }
    }
}
