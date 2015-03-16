package org.apache.cocoon.selection;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
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

/**
 * A selector that tests the last-modified time of a resource. Usage example:
 * <pre>
 *    &lt;map:selector name="resource-type" src="org.apache.cocoon.selection.ResourceTypeSelector"/&gt;
 * 
 *    &lt;map:select type="resource-modified"&gt;
 *       &lt;map:parameter name="src" value="this_resource"/&gt;
 *       &lt;map:when test="&amp;lt; 1 hour"&gt;
 *           What to do if the this_resource was modified before one hour ago.
 *       &lt;/map:when&gt;
 *       &lt;map:when test="&amp;gt;2days"&gt;
 *           What to do if the this_resource was modified after 2 days ago (i.e., within the last 2 days).
 *       &lt;/map:when&gt;
 *       &lt;map:when test="&amp;gt; : file://older.txt"&gt;
 *           What to do if the this_resource was modified after older.txt was modified (i.e., it is newer than older.txt).
 *       &lt;/map:when&gt;
 *       &lt;map:otherwise&gt;
 *           ...
 *       &lt;/map:otherwise&gt;
 *    &lt;/map:select&gt;
 * </pre>
 * 
 * The expressions in the when-tests compare the last-modified time of the resource, pointed to by the src-parameter,
 * to a given time.
 * When a resource does not exist, its last-modified-time is zero, which corresponds to long ago.
 * 
 * The first part is a comparison-operator, '<' or '>', specifying before or after the indicated time.
 * The indicated time follows the comparison-operator. This can be either:
 * - A positive integer followed by a time unit, which can be "minutes?", "hours?", "days?" or "weeks?".
 *   This indicates a period before the current time, i.e. it is a number of time units 'ago'.
 * - A colon followed by a resource URI; the last-modified time is compared to the last-modified time of this resource.
 * 
 * Spaces between the part of a when-test are optional.
 * 
 * If all when-tests fail or the resource does not exist, the otherwise-branch is taken.
 */

public class ResourceModifiedSelector extends AbstractSwitchSelector implements
    ThreadSafe, Serviceable, Disposable, Selector {

  private ServiceManager manager;
  private SourceResolver resolver;
  private String src;
  private Source source;
  private long lastModified;
  private long currentTime;
  private Pattern timePeriodPattern;
  private Pattern resourcePattern;

  public void service(ServiceManager manager) throws ServiceException {
    this.manager = manager;
    this.resolver = (SourceResolver) manager.lookup(SourceResolver.ROLE);
  }

  @Override
  public Object getSelectorContext(Map objectModel, Parameters parameters) {
    currentTime = new Date().getTime();
    timePeriodPattern = Pattern.compile("([<>])\\s*(\\d+)\\s*(\\S+)");
    resourcePattern = Pattern.compile("([<>])\\s*:\\s*(.*)");
    src = parameters.getParameter("src", "");
    try {
      source = resolver.resolveURI(src);
      lastModified = source.getLastModified(); // Supposed to be 0 when source doesn't exist.
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    return null;
  }

  @Override
  public boolean select(String expression, Object selectorContext) {
    String operator;
    long compareTime;
    Matcher matcher;
    if ((matcher = timePeriodPattern.matcher(expression)).matches()) {
      operator = matcher.group(1);
      int quantity = Integer.parseInt(matcher.group(2));
      String unit = matcher.group(3);
      long msecs = quantity * 1000; // seconds
      msecs *= 60; // minutes
      if (!unit.matches("minutes?")) {
        msecs *= 60; // hours
        if (!unit.matches("hours?")) {
          msecs *= 24; // days
          if (!unit.matches("days?")) {
            msecs *= 7; // weeks
            if (!unit.matches("week?")) {
              throw new IllegalArgumentException("Invalid time unit: "+unit);
            }
          }
        }
      }
      compareTime = currentTime - msecs;
    } else if((matcher = resourcePattern.matcher(expression)).matches()) {
      operator = matcher.group(1);
      try {
        Source compareSource = resolver.resolveURI(matcher.group(2));
        compareTime = compareSource.getLastModified();
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException(e);
      } catch (IOException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Invalid last-modified specification: "+expression);
    }
    /* Now comes the actual comparison. */
    if (operator.equals("<")) {
      return lastModified < compareTime;
    } else if(operator.equals(">")) {
      return lastModified > compareTime;
    } else {
      throw new IllegalArgumentException("Invalid comparison operator: "+operator);
    }
  }

  @Override
  public void dispose() {
    if (source != null) {
      resolver.release(source);
    }
    this.manager.release(this.resolver);
    this.resolver = null;
    this.manager = null;
  }

}
