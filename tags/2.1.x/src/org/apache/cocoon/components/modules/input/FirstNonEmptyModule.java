package org.apache.cocoon.components.modules.input;

import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;

public class FirstNonEmptyModule extends AbstractMetaModule {

  public Object getAttribute(String name, Configuration modeConf, Map objectModel) 
    throws ConfigurationException
  {
    Object value = null;
    String[] modules = name.split("\\|");
    for (int i = 0; i < modules.length; ++i) { 
      if (modules[i].contains(":")) {
        String type = modules[i].substring(0, modules[i].indexOf(':'));
        String accessor = modules[i].substring(modules[i].indexOf(':')+1);
        InputModule module = obtainModule(type);
        value = module.getAttribute(accessor, modeConf, objectModel);
        releaseModule(module);
      } else {
        /* This is a literal value. If it is the last part of 'name', it may be empty, and the result will be empty. */
        value = modules[i];
      }
      if (value != null && !value.equals("")) break;
    }
    return value;
  }

}
