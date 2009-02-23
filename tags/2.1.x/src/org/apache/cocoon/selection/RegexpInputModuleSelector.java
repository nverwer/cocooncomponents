package org.apache.cocoon.selection;

/**
 * This selector matches the value returned by an input module against a set of regular expressions.
 * Usage is very much like the other regexp-selectors. The (configuration) parameters module and attribute
 * specify which module and attribute are used.
 */

import java.util.Map;

import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.cocoon.components.modules.input.InputModuleHelper;

public class RegexpInputModuleSelector
    extends AbstractRegexpSelector
    implements Configurable, Selector, Serviceable
{

  private String module;
  private String attribute;
  private ServiceManager manager;

  @Override
  public void service(ServiceManager manager) throws ServiceException {
    this.manager = manager;
  }

  @Override
  public void configure(Configuration configuration)
      throws ConfigurationException
  {
    super.configure(configuration);
    this.module = configuration.getChild("module").getValue(null);
    this.attribute = configuration.getChild("attribute").getValue(null);
  }

  @Override
  public Object getSelectorContext(Map objectModel, Parameters parameters)
  {
    String mod = parameters.getParameter("module", this.module);
    String att = parameters.getParameter("attribute", this.attribute);
    if (mod == null || att == null) {
        this.getLogger().warn("Error in module or attribute:"+mod+":"+att+".");
        return null;
    }
    InputModuleHelper imhelper = new InputModuleHelper();
    imhelper.setup(manager);
    return imhelper.getAttribute(objectModel, mod, att, null);
  }

}
