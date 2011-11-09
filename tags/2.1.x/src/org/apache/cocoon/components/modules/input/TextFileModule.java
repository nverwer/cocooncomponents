package org.apache.cocoon.components.modules.input;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.Map;

import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.commons.io.IOUtils;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceResolver;

public class TextFileModule extends AbstractInputModule implements Composable, ThreadSafe {
    
    String encoding;
    SourceResolver resolver;
    ComponentManager manager;
    
    @Override
    public void configure(Configuration conf) throws ConfigurationException {
        super.configure(conf);
        encoding = conf.getAttribute("encoding", "UTF-8");
    }

    @Override
    public Object getAttribute(String name, Configuration modeConf, Map objectModel) throws ConfigurationException {
        String result;
        try {
            Source source = resolver.resolveURI(name);
            InputStream inputStream = source.getInputStream();
            StringWriter writer = new StringWriter();
            IOUtils.copy(inputStream, writer, encoding);
            result = writer.toString();
        } catch (Exception e) {
            throw new ConfigurationException(e.getMessage(), e);
        } finally {
            
        }
        return result;
    }

    @Override
    public Object[] getAttributeValues(String name, Configuration modeConf, Map objectModel) throws ConfigurationException {
        return new Object[] {this.getAttribute(name, modeConf, objectModel)};
    }

    @Override
    public void compose(ComponentManager manager) throws ComponentException {
        this.manager = manager;
        this.resolver = (SourceResolver) manager.lookup(SourceResolver.ROLE);
    }

    public void dispose() {
        super.dispose();
        if (this.manager != null) {
            this.manager.release((Component)this.resolver);
            this.resolver = null;
            this.manager = null;
        }
    }

}
