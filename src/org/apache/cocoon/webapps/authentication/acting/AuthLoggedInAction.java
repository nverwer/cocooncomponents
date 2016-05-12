package org.apache.cocoon.webapps.authentication.acting;

import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.acting.ServiceableAction;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.webapps.authentication.AuthenticationManager;
import org.apache.cocoon.webapps.authentication.user.RequestState;
import org.apache.cocoon.webapps.authentication.user.UserHandler;

/**
 *  This is an improved version of the authentication and the loggedin action, 
 *  which checks the role of a user if the role parameter is specified.
 *  If the user is not authenticated, a redirect (as in the authentication action)
 *  will take place if the 'redirect' parameter is true. Otherwise, the action will just fail.
 *  If the user is authenticated, but does not have the required role, the action will fail.
 *
 * The authentication-fw block is deprecated and will be removed in future versions. But the new auth framework is not documented for Cocoon 2.1.
 */
public class AuthLoggedInAction extends ServiceableAction implements ThreadSafe {

  @SuppressWarnings("deprecation")
  @Override
  public Map act(Redirector redirector, SourceResolver resolver, 
                 Map objectModel, String source, Parameters par)
  throws Exception {
    String handlerName = par.getParameter("handler", null);
    String applicationName = par.getParameter("application", null);
    String roleName = par.getParameter("role", null);
    boolean testRedirect = par.getParameterAsBoolean("redirect", false);
    AuthenticationManager authManager = null;
    Map map = null;
    try {
      authManager = (AuthenticationManager) this.manager.lookup(AuthenticationManager.ROLE);
      UserHandler handler = authManager.isAuthenticated(handlerName);
      if (handler == null && !testRedirect) {
        this.getLogger().warn("No authentication handler for "+handlerName);
        map = null;
      } else if (authManager.checkAuthentication(redirector, handlerName, applicationName)) {
        RequestState state = authManager.getState();
        map = state.getHandler().getContext().getContextInfo();
        if (roleName != null) {
          String userRoles = (String)map.get("role");
          if (userRoles == null) userRoles = "";
          if (!userRoles.matches(".*\\b"+roleName+"\\b.*")) {
            this.getLogger().warn("The role "+roleName+" is not included in ["+userRoles+"]");
            map = null;
          }
        }
      } else {
        // Redirection will take care of it.
        this.getLogger().warn("Redirecting for "+handlerName);
      }
    } finally {
      this.manager.release(authManager);
    }
    return map;
  }

}
