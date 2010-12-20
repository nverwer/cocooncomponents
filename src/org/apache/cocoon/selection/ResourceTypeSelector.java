package org.apache.cocoon.selection;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceResolver;
import org.apache.excalibur.source.impl.FileSource;

/**
 * A selector that tests the type of a resource. Usage example:
 * <pre>
 *    &lt;map:selector name="resource-type" src="org.apache.cocoon.selection.ResourceTypeSelector"/&gt;
 * 
 *    &lt;map:select type="resource-type"&gt;
 *       &lt;map:parameter name="src" value="..."/&gt;
 *       &lt;map:when test="not found"&gt;
 *           ...
 *       &lt;/map:when&gt;
 *       &lt;map:when test="directory"&gt;
 *           ...
 *       &lt;/map:when&gt;
 *       &lt;map:when test="file"&gt;
 *           ...
 *       &lt;/map:when&gt;
 *       &lt;map:otherwise&gt;
 *           ...
 *       &lt;/map:otherwise&gt;
 *    &lt;/map:select&gt;
 * </pre>
 */

public class ResourceTypeSelector extends AbstractSwitchSelector implements Serviceable, Disposable {

  private ServiceManager manager;
  private SourceResolver resolver;
  private Source source;

  @Override
  public void service(ServiceManager manager) throws ServiceException {
    this.manager = manager;
    this.resolver = (SourceResolver)manager.lookup(SourceResolver.ROLE);
  }

  @Override
  public Object getSelectorContext(Map objectModel, Parameters parameters) {
    String src = parameters.getParameter("src", "");
    try {
      source = resolver.resolveURI(src);
    } catch (MalformedURLException e) {
      source = null;
    } catch (IOException e) {
      source = null;
    }
    return null;
  }

  @Override
  public boolean select(String expression, Object selectorContext) {
    if (expression.equals("not found")) {
      return source == null || !source.exists();
    } else if (expression.equals("exists")) {
      return source != null && source.exists();
    } else if (source instanceof FileSource) {
      File file = ((FileSource)source).getFile();
      if (expression.equals("directory")) {
        return file.isDirectory();
      } else if (expression.equals("file")) {
        return file.isFile();
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public void dispose() {
    this.manager.release(this.resolver);
    this.resolver = null;
    this.manager = null;
  }

}
