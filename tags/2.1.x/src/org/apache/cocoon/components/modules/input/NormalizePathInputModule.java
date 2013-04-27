package org.apache.cocoon.components.modules.input;

import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;

public class NormalizePathInputModule extends AbstractInputModule {

  @Override
  public Object getAttribute(String name, Configuration modeConf, Map objectModel) throws ConfigurationException {
    /* This works for paths like '[prefix]/part/..[/rest]', which is transformed into '[prefix][/rest]'. */
    while (name.matches(".*/[^.][^/]*/\\.\\..*")) {
      name = name.replaceAll("/[^.][^/]*/\\.\\.", "");
    }
    /* Maybe, a path of the form 'part/../[rest]' is left. */
    name = name.replaceAll("[^.][^/]*/\\.\\./", "");
    /* Some browsers and servers do not like '//' in a path. */
    name = name.replaceAll("//", "/");
    /* Remove all slashes at the end. */
    name = name.replaceAll("/+$", "");
    return name;
  }

}
