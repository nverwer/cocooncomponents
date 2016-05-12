package org.apache.cocoon.acting;

import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;

public class MessageLoggerAction extends AbstractAction {

  @Override
  public Map act(Redirector redirector, SourceResolver resolver, Map objectModel, String source, Parameters parameters) throws Exception {
    String target = parameters.getParameter("target", "");
    String message = parameters.getParameter("message", "");
    if (target.length() == 0 || target.matches(".*\\blog\\b.*"))
      this.getLogger().info(message);
    if (target.matches(".*\\bconsole\\b.*"))
      System.out.println(message);
    return EMPTY_MAP;
  }

}
