package org.apache.cocoon.acting;

import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;

/**
 * Set a Java system properties by calling System.setProperty.
 * Example usage:
 *         <map:act type="system-property">
 *           <map:parameter name="javax.net.ssl.trustStore" value="my-trust-store.jks"/>
 *           ...
 *         </map:act>
 */
public class SetSystemPropertyAction extends AbstractAction {

  @Override
  public Map act(Redirector redirector, SourceResolver resolver, Map objectModel, String source, Parameters parameters) throws Exception {
    for (String name : parameters.getNames()) {
      String value = parameters.getParameter(name);
      System.setProperty(name, value);
    }    
    return EMPTY_MAP;
  }

}
