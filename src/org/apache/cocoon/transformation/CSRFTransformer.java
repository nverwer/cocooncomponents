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

import java.util.UUID;
import java.util.Map;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This transformer generates a random UUID for use as a CSRF-token, https://www.owasp.org/index.php/PHP_CSRF_Guard.
 * <p>
 * This transformer triggers for elements in the namespace "http://apache.org/cocoon/csrf/1.0".
 * <p>
 * For example:
 * <p>
 * <pre>
 * {@code
 *   <csrf:token/>
 * }
 * </pre>
 * will output:
 * <pre>
 * {@code
 *   8a6d1fd4-be12-405c-a0f8-4b14c17e3c3a
 * }
 * </pre>
 * You can also put the token in an attribute by including a @csrf:token attribute on an element. The value of
 * @csrf:token is the name of the attribute that will be created on the element. For instance
 * <pre>
 * {@code
 *   <element @csrf:token="value"/>
 * }
 * </pre>
 * will output
 *  <pre>
 *  {@code
 *    <element value="8a6d1fd4-be12-405c-a0f8-4b14c17e3c3a"/>
 * }
 * </pre>
 * <pre>
 * All supported actions:
 * {@code
 *   <csrf:token/>
 *   <element @csrf:token="'name of attribute'"/>
 * }
 * </pre>
 *       <map:transformer logger="sitemap.transformer.csrf" name="csrf"
 *           pool-grow="2" pool-max="32" pool-min="8"
 *           src="org.apache.cocoon.transformation.CSRFTransformer">
 *       </map:transformer>
 *
 * <p>
 * @author Huib Verweij (hhv@x-scale.nl)
 * </p>
 *
 */
public class CSRFTransformer extends AbstractTransformer {

    public static final String CSRF_NAMESPACE_URI = "http://apache.org/cocoon/csrf/1.0";

    private static final String TOKEN_ELEMENT = "token";

    private String csrf_token = null;

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src,
                      Parameters par) throws ProcessingException, SAXException, IOException {
        // We don't need it.
    }

    @Override
    public void startDocument() throws SAXException {
        this.csrf_token = UUID.randomUUID().toString();
        super.startDocument();
    }

    @Override
    public void endElement(String uri, String loc, String raw)
            throws SAXException {
        if (uri.equals(CSRF_NAMESPACE_URI) && loc.equals(TOKEN_ELEMENT)) {
        }
        else {
            super.endElement(uri, loc, raw);
        }
    }


    @Override
    public void startElement(String uri, String loc, String raw, Attributes a)
            throws SAXException {
//        System.out.println("CSRF_NAMESPACE_URI="+CSRF_NAMESPACE_URI+", uri="+ uri + "loc="+loc);
        // Process any csrf:* attributes, create a new attribute for them using as a name the value of the
        // csrf:* attribute and as a value csrf_token (the CSRF token for the currently processed XML document).
        AttributesImpl csrfAttr = new AttributesImpl(a);
        for (int i=0; i < a.getLength(); i++) {
            if (CSRF_NAMESPACE_URI.equals(a.getURI(i))) {
                csrfAttr.setAttribute(i, "", a.getValue(i), a.getValue(i), "CDATA", this.csrf_token);
            }
        }
        if (uri.equals(CSRF_NAMESPACE_URI)) {
            if (loc.equals(TOKEN_ELEMENT)) {
                xmlConsumer.characters(this.csrf_token.toCharArray(), 0, this.csrf_token.toCharArray().length);;
                return;
            }
        }
        super.startElement(uri, loc, raw, csrfAttr);
    }


}
