/*
 * Copyright 1999-2005 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.components.modules.input;

import java.util.*;

import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.components.modules.input.AbstractInputModule;
import org.apache.excalibur.source.SourceResolver;
import org.apache.excalibur.source.impl.FileSource;

/**
 * Input Module to get an absolute path from a path relative to the current sitemap.
 *
 * This input module determines the absolute path to a file-resource, relative to
 * the current sitemap. For example, if the sitemap from which this module is used
 * is located in /here/is/my/sitemap.xmap, the following results are returned:
 *
 * <table border="1">
 * <tr><th>accessor</th><th>result</th><tr>
 * <tr><td>{sitemap-path:}</td><td>/here/is/my</td></tr>
 * <tr><td>{sitemap-path:input.xml}</td><td>/here/is/my/input.xml</td></tr>
 * <tr><td>{sitemap-path:../something.xsl}</td><td>/here/is/something.xsl</td></tr>
 * </table>
 * <p>
 */
public class SitemapPathModule extends AbstractInputModule implements Composable, ThreadSafe {

    protected ComponentManager manager = null;

    /**
     * Set the current <code>ComponentManager</code> instance used by this
     * <code>Composable</code>.
     */
    public void compose(ComponentManager manager) throws ComponentException {
        this.manager = manager;
    }

    @Override
    public Object getAttribute(String name, Configuration modeConf, Map objectModel)
            throws ConfigurationException {
        SourceResolver resolver = null;
        String fullPath = null;
        try {
            resolver = (SourceResolver) this.manager.lookup(org.apache.excalibur.source.SourceResolver.ROLE);
            try {
                FileSource source = (FileSource) resolver.resolveURI(name);
                //return source.getFile().getCanonicalPath();
                //return source.getFile().getAbsolutePath();
                fullPath = source.getURI().substring(5); // Without "file:" prefix.
            } catch (Exception e) {
                if (this.getLogger().isWarnEnabled()) {
                    this.getLogger().warn("Exception resolving URL " + name, e);
                }
            }
        } catch (ComponentException e) {
            if (this.getLogger().isErrorEnabled()) {
                this.getLogger().error("Exception obtaining source resolver ", e);
            }
        } finally {
            if (resolver != null) {
                this.manager.release((Component) resolver);
            }
        }
        return fullPath;
    }

    @Override
    public Iterator getAttributeNames(Configuration modeConf, Map objectModel)
            throws ConfigurationException {
        return (new java.util.ArrayList()).iterator();
    }

    @Override
    public Object[] getAttributeValues(String name, Configuration modeConf, Map objectModel)
            throws ConfigurationException {
        List values = new LinkedList();
        values.add(this.getAttribute(name, modeConf, objectModel));
        return values.toArray();
    }
}
