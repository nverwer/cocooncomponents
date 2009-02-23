package org.apache.cocoon.components.modules.input;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.cocoon.components.modules.input.AbstractInputModule;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.http.HttpCookie;
import org.apache.regexp.RE;

public class CrumbInputModule extends AbstractInputModule {
  
  /**
   * @return the value of the cookie-crumb whose name matches the attribute "cookie/crumb",
   * or <code>null</code> if there is no match.
   */
  public Object getAttribute(String attribute, Configuration modeConf, Map objectModel)
    throws ConfigurationException
  {
      String value = null;
      String[] name = attribute.split("/", 2);
      HttpCookie cookie = (HttpCookie) getCookieMap(objectModel).get(name[0]);
      if (cookie != null) {
        String[] parameters = cookie.getValue().split("&");
        for (String parameter: parameters) {
          String[] keyValue = parameter.split("=", 2);
          try {
            if (URLDecoder.decode(keyValue[0], "UTF-8").equals(name[1])) {
              value = URLDecoder.decode(keyValue[1], "UTF-8");
            }
          } catch (UnsupportedEncodingException e) {
            throw new ConfigurationException(e.getMessage());
          }
        }
      }
      if (getLogger().isDebugEnabled()) {
          getLogger().debug("Cookie[" + name + "]=" + value);
      }
      return value;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.cocoon.components.modules.input.InputModule#getAttributeNames(org.apache.avalon.framework.configuration.Configuration,
   *      java.util.Map)
   */
  public Iterator getAttributeNames(Configuration modeConf, Map objectModel)
          throws ConfigurationException {
      return getCookieMap(objectModel).keySet().iterator();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.cocoon.components.modules.input.InputModule#getAttributeValues(java.lang.String,
   *      org.apache.avalon.framework.configuration.Configuration,
   *      java.util.Map)
   */
  public Object[] getAttributeValues(String name, Configuration modeConf,
          Map objectModel) throws ConfigurationException {
      Map allCookies = getCookieMap(objectModel);
      Iterator it = allCookies.values().iterator();
      List matched = new LinkedList();
      RE regexp = new RE(name);
      while (it.hasNext()) {
          HttpCookie cookie = (HttpCookie) it.next();
          if (regexp.match(cookie.getName())) {
              matched.add(cookie.getValue());
          }
      }
      return matched.toArray();
  }

  /**
   * @param objectModel
   *            Object Model for the current request
   * @return a Map of {see: HttpCookie}s for the current request, keyed on
   *         cookie name.
   */
  protected Map getCookieMap(Map objectModel) {
      return ObjectModelHelper.getRequest(objectModel).getCookieMap();
  }

}
