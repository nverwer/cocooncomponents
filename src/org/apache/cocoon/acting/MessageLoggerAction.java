package org.apache.cocoon.acting;

import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;

public class MessageLoggerAction extends AbstractAction {

  @Override
  public Map act(Redirector redirector, SourceResolver resolver, Map objectModel, String source, Parameters parameters) throws Exception {
    String message = parameters.getParameter("message", "");
    this.getLogger().info(message);
    System.out.println(message);
    return EMPTY_MAP;
  }

}
