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
 * The elements cannot be nested.
 * <p>
 * Example XML input:
 * <p>
 * <pre>
 * {@code
 *   <csrf:token/>
 * }
 * </pre>
 * <pre>
 * All supported actions:
 * {@code
 *   <csrf:token/>
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
public class CSRFTransformer extends AbstractSAXPipelineTransformer {

    public static final String CSRF_NAMESPACE_URI = "http://apache.org/cocoon/csrf/1.0";

    private static final String GENERATE_ELEMENT = "generate";

    private char[] csrf_token = null;

    public CSRFTransformer() {
        this.defaultNamespaceURI = CSRF_NAMESPACE_URI;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.avalon.framework.configuration.Configurable#configure(org.
     * apache.avalon.framework.configuration.Configuration)
     */
    public void configure(Configuration configuration)
            throws ConfigurationException {
        super.configure(configuration);
    }

    public void setup(SourceResolver resolver, Map objectModel, String src,
            Parameters params) throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, params);
        csrf_token = UUID.randomUUID().toString().toCharArray();
    }


    public void startTransformingElement(String uri, String name, String raw, Attributes attr)
            throws ProcessingException, IOException, SAXException {
        if (uri.equals(CSRF_NAMESPACE_URI)) {
            
            if (name.equals(GENERATE_ELEMENT)) {
                xmlConsumer.characters(crsf_token, 0, crsf_token.length);;
            }
            
            else {
                super.startTransformingElement(uri, name, raw, attr);
            }
        }
    }

    public void endTransformingElement(String uri, String name, String raw)
            throws ProcessingException, IOException, SAXException {
        if (uri.equals(CSRF_NAMESPACE_URI)) {

            if (name.equals(GENERATE_ELEMENT)) {
            }
        } else {
            super.endTransformingElement(uri, name, raw);
        }
    }

}
