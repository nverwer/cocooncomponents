package org.apache.cocoon.components.modules.input;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.thread.ThreadSafe;

/**
 * Returns the first non-empty value in a list. The list items are separated by '|'.
 * Items that are modules must not be surrounded by {}.
 * Literal items must have at least ':' and '%' URL-encoded.
 * @author NicoVerwer
 *
 */
public class FirstNonEmptyModule extends AbstractMetaModule implements ThreadSafe {

  @Override
  public Object getAttribute(String name, Configuration modeConf, @SuppressWarnings("rawtypes") Map objectModel) 
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
        try {
          value = URLDecoder.decode(modules[i], "UTF-8");
        } catch (UnsupportedEncodingException e) {
          value = modules[i];
        }
      }
      if (value != null && !value.equals("")) break;
    }
    return value;
  }

}
