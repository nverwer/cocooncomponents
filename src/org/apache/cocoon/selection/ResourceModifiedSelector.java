package org.apache.cocoon.selection;

import java.util.Map;
import java.util.regex.*;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceNotFoundException;
import org.apache.excalibur.source.SourceResolver;

public class ResourceModifiedSelector extends AbstractLogEnabled implements
    ThreadSafe, Serviceable, Disposable, Selector {

  private ServiceManager manager;
  private SourceResolver resolver;
  private Source source;

  public void service(ServiceManager manager) throws ServiceException {
    this.manager = manager;
    this.resolver = (SourceResolver) manager.lookup(SourceResolver.ROLE);
  }

  public void dispose() {
    this.manager.release(this.resolver);
    this.resolver = null;
    this.manager = null;
  }

  public boolean select(String expression, Map objectModel, Parameters parameters) {
    String src = parameters.getParameter("src", "");
    Pattern modifiedPattern = Pattern.compile("([<>])\\s*(\\d+)\\s*(\\S+)");
    Matcher modifiedMatcher = modifiedPattern.matcher(expression);
    String operator = modifiedMatcher.group(1);
    
    try {
      source = resolver.resolveURI(src);
      return source.exists();
    } catch (SourceNotFoundException e) {
      return false;
    } catch (Exception e) {
      getLogger().warn("Exception resolving resource " + src, e);
      return false;
    } finally {
      if (source != null) {
        resolver.release(source);
      }
    }
  }
}
