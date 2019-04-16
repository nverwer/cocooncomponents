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
package org.apache.cocoon.components.modules.input;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceResolver;

/**
 * Input module for accessing properties in a properties file.
 * 
 * <p>
 * The configuration can contain one or more <code>&lt;file src=".."/&gt;</code>
 * elements. These files are scanned from first to last, until one is found that
 * contains the required property.
 * The properties files can only be configured
 * statically and are resolved via the SourceResolver system.
 * </p>
 * 
 * @author <a href="mailto:unico@apache.org">Unico Hommes</a> for the original PropertiesFileModule.
 * @author Nico Verwer for this version that uses multiple property files.
 */
public class PropertiesFilesModule extends AbstractInputModule implements Serviceable, ThreadSafe {

  private ServiceManager serviceManager;
  private SourceResolver sourceResolver;
  private Properties[] properties;

  /*
   * @see Serviceable#service(ServiceManager)
   */
  @Override
  public void service(ServiceManager manager) throws ServiceException {
    serviceManager = manager;
    sourceResolver = (SourceResolver) serviceManager.lookup(SourceResolver.ROLE);
  }

  /*
   * @see org.apache.avalon.framework.activity.Disposable#dispose()
   */
  @Override
  public void dispose() {
    super.dispose();
    if (this.serviceManager != null) {
      this.serviceManager.release(this.sourceResolver);
      this.serviceManager = null;
      this.sourceResolver = null;
    }
  }

  /**
   * Configure the location of the properties file:
   * <p>
   * <code>&lt;file src="resource://my.properties" /&gt;</code>
   * </p>
   */
  @Override
  public void configure(Configuration configuration) throws ConfigurationException {
    super.configure(configuration);
    Configuration[] filesConf = configuration.getChildren("file");
    properties = new Properties[filesConf.length];
    for (int i = 0; i < filesConf.length; ++i) {
      String file = filesConf[i].getAttribute("src");
      properties[i] = load(file);
    }
  }

  private Properties load(String file) throws ConfigurationException {
    Source source = null;
    InputStream stream = null;
    Properties props = new Properties();
    try {
      source = sourceResolver.resolveURI(file);
      stream = source.getInputStream();
      props.load(stream);
    } catch (IOException e) {
      this.getLogger().warn("Cannot load properties file " + file);
    } finally {
      if (source != null) {
        sourceResolver.release(source);
      }
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignored) {
        }
      }
    }
    return props;
  }

  @Override
  public Object getAttribute(String name, Configuration modeConf, @SuppressWarnings("rawtypes") Map objectModel) 
    throws ConfigurationException
  {
    for (int i = 0; i < properties.length; ++i) {
      if (properties[i].containsKey(name)) {
        return properties[i].get(name);
      }
    }
    return null;
  }
}
