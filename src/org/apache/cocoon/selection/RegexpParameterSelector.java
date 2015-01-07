package org.apache.cocoon.selection;

import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;

/* Regular expression selector matching sitemap parameters.
 * The regular expression patterns are defined in the configuration.
 * <pre>
 * &lt;map:components&gt;
 *   &lt;map:selectors default="..."&gt;
 *     &lt;map:selector name="xyz" src="org.apache.cocoon.selection.RegexpParameterSelector"&gt;
 *       &lt;pattern name="A"&gt;pattern&lt;/pattern&gt;
 *       ...
 *     &lt;/map:selector&gt;
 *  &lt;/map:selectors&gt;
 * &lt;/map:components&gt;
 * </pre>
 * The parameter 'parameter' has the value that is matched:
 * <pre>
 * &lt;map:select type="xyz"&gt;
 *   &lt;map:parameter name="parameter" value="{sitemap:expression}"/&gt;
 *   &lt;map:when test="A"&gt;
 *     ...
 *   &lt;/map:when&gt;
 *  &lt;/map:selectors&gt;
 * &lt;/map:select&gt;
 * </pre>
 */
public class RegexpParameterSelector extends AbstractRegexpSelector {

  @Override
  public Object getSelectorContext(Map objectModel, Parameters parameters) {
    return parameters.getParameter("parameter", null);
  }

}
