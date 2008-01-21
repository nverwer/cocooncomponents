package org.apache.cocoon.components.modules.input;

import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;

public class NormalizePathInputModule extends AbstractInputModule {

  @Override
  public Object getAttribute(String name, Configuration modeConf, Map objectModel) throws ConfigurationException {
    return name.replaceAll("/[^/]+/\\.\\.", "");
  }

}
