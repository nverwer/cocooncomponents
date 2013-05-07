package org.apache.cocoon.components.modules.input;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.components.modules.input.AbstractInputModule;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.http.HttpCookie;
import org.apache.regexp.RE;

public class CounterInputModule extends AbstractInputModule implements ThreadSafe {
  
  private static HashMap counters = new HashMap();
  
  /**
   * @return Increment the value of a counter on every call.
   * For example, suppose you call this module "counter", and you have a
   * counter called "my-match-1" that does not exist yet.
   * Then in your sitemap, {counter:my-match-1}
   * will yield the value "1". Subsequent matches will return 1, 2, 3, etc.
   */
    public synchronized Object getAttribute(String attribute, Configuration modeConf, Map objectModel)
            throws ConfigurationException {
        Integer value = (Integer) this.counters.get(attribute);
        if (null == value) {
            this.counters.put(attribute, new Integer(1));
        } else {
            this.counters.put(attribute, new Integer(value.intValue() + 1));
        }
        System.out.println("Counter[" + attribute + "]=" + this.counters.get(attribute));
        getLogger().info("Counter[" + attribute + "]=" + this.counters.get(attribute));
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Counter[" + attribute + "]=" + this.counters.get(attribute));
        }
        return this.counters.get(attribute);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.cocoon.components.modules.input.InputModule#getAttributeNames(org.apache.avalon.framework.configuration.Configuration,
     * java.util.Map)
     */
    public synchronized Iterator getAttributeNames(Configuration modeConf, Map objectModel)
            throws ConfigurationException {

        return this.counters.entrySet().iterator();
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.cocoon.components.modules.input.InputModule#getAttributeValues(java.lang.String,
     * org.apache.avalon.framework.configuration.Configuration, java.util.Map)
     */
    public synchronized Object[] getAttributeValues(String name, Configuration modeConf,
            Map objectModel) throws ConfigurationException {

        Object[] objects = new Object[1];
        objects[1] = this.counters.get(name);
        return objects;
    }

}
