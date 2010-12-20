package org.apache.cocoon.components.modules.input;

import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;

/*
 * name: <delimiter><pattern><delimiter><replacement><delimiter><flags><delimiter><text>
 * e.g., /c(.)t/b$1r//cat => bar
 * The delimiter must not occur in pattern and replacement and flags.
 * Flags are not used at this moment.
 */
public class RegExpMatchInputModule extends AbstractInputModule {

  @Override
  public Object getAttribute(String name, Configuration modeConf, Map objectModel) throws ConfigurationException {
    char delimiter = name.charAt(0);
    int delim1 = name.indexOf(delimiter, 1);
    int delim2 = name.indexOf(delimiter, delim1+1);
    int delim3 = name.indexOf(delimiter, delim2+1);
    String pattern = name.substring(1, delim1);
    String replacement = name.substring(delim1+1, delim2);
    String flags = name.substring(delim2+1, delim3);
    String text = name.substring(delim3+1);
    return text.replaceAll(pattern, replacement);
  }

}
