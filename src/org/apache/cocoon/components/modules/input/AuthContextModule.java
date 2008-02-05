package org.apache.cocoon.components.modules.input;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.webapps.authentication.components.DefaultAuthenticationManager;
import org.apache.cocoon.webapps.authentication.user.UserHandler;
import org.apache.cocoon.webapps.authentication.user.UserState;
import org.apache.cocoon.webapps.session.SessionManager;


public class AuthContextModule extends AbstractInputModule implements
		ThreadSafe, Serviceable {
	
	protected ServiceManager manager;
	
	final static Vector returnNames;
    static {
        Vector<String> tmp = new Vector<String>();
        tmp.add("userId");
        returnNames = tmp;
    }

	@Override
	public Object getAttribute(String name, Configuration modeConf, Map objectModel)
			throws ConfigurationException {		
		
		try {
			SessionManager sm = (SessionManager)this.manager.lookup(SessionManager.ROLE);
			Session ses = sm.getSession(false);
			String sessionKey = DefaultAuthenticationManager.SESSION_ATTRIBUTE_USER_STATUS;
			UserState us = (UserState)ses.getAttribute(sessionKey);
			
			String handlerName = name.substring(0, name.indexOf(":"));
			
//			TODO via xpath de hele context beschikbaar maken 		
//			String xpathExppression = name.substring(name.indexOf(":") + 1);
			
			UserHandler uh = us.getHandler(handlerName);			
			return uh.getUserId();	
		} catch (Exception ex) {			
			return null;
		}	
	}
	
	@Override
	public Iterator getAttributeNames( Configuration modeConf, Map objectModel ) throws ConfigurationException {
		return DateInputModule.returnNames.iterator();
	}
	
	 public Object[] getAttributeValues(String name, Configuration modeConf, Map objectModel)
     throws ConfigurationException {		 
		 List<Object> values = new LinkedList<Object>();
         values.add( this.getAttribute(name, modeConf, objectModel) );
         return values.toArray();         
	 }

	public void service(ServiceManager manager) throws ServiceException {	
		this.manager = manager;	
	}	
}
