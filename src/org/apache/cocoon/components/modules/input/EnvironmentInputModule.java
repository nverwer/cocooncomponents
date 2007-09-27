// DEPRECATED. The CocoonComponentManager will disappear soon.
package org.apache.cocoon.components.modules.input;

import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.cocoon.components.CocoonComponentManager;

/**
 * The EnvironmentInputModule accesses properties of the current environment using JXPath syntax.
 * These properties are: URI, URIPrefix, action, context, rootContext, view.
 * A particularly useful property is URIPrefix, which is the absolute URI to the base of the current sitemap.
 * This can be used to make your sitemaps relocatable.
 * @see org.apache.cocoon.environment.Environment
 */

public class EnvironmentInputModule extends AbstractJXPathModule {

  protected Object getContextObject(Configuration modeConf, Map objectModel) {
    return CocoonComponentManager.getCurrentEnvironment();
  }

}
