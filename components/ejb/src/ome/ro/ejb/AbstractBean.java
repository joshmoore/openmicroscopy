/* ome.ro.ejb.AbstractBean
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2005 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */

package ome.ro.ejb;

//Java imports
import javax.annotation.Resource;
import javax.ejb.SessionContext;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

//Third-party imports
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.ProxyFactoryBean;

//Application-internal dependencies
import ome.conditions.ApiUsageException;
import ome.conditions.InternalException;
import ome.conditions.RootException;
import ome.system.EventContext;
import ome.system.OmeroContext;
import ome.system.Principal;
import ome.system.ServiceFactory;

public class AbstractBean 
{
    
    private static Log log = LogFactory.getLog(AbstractBean.class);

    protected transient OmeroContext  applicationContext;

    protected transient ServiceFactory serviceFactory;
    
    protected transient EventContext  eventContext;
    
    // java:comp.ejb3/EJBContext
    protected @Resource SessionContext sessionContext;
    //protected @Resource(mappedName="security/subject") Subject subject;

    public void create()
    {
        applicationContext = OmeroContext.getManagedServerContext();
        serviceFactory = new ServiceFactory( applicationContext );
        eventContext = (EventContext) applicationContext.getBean("eventContext");
        log.debug("Created:\n"+getLogString());
    }

    public void destroy()
    {
        log.debug("Destroying:\n"+getLogString());
    }

    protected void login( )
    {
        Principal p;
        if ( sessionContext.getCallerPrincipal() instanceof Principal )
        {
            p = (Principal) sessionContext.getCallerPrincipal();
            eventContext.setPrincipal( p );
            if ( log.isDebugEnabled() )
                log.debug( "Running with user: "+p.getName() );
        }
        else
        {
            throw new ApiUsageException(
                    "ome.system.Principal instance must be provided on login.");
        }
        
    }
    
    protected void logout( )
    {
        eventContext.setPrincipal( null );
    }
    
    protected Object wrap( InvocationContext context, String factoryName ) throws Exception
    {
        try {
            login();
            AOPAdapter adapter = 
            AOPAdapter.create( 
                    (ProxyFactoryBean) applicationContext.getBean(factoryName),
                    context );
            return adapter.proceed( );   
        } catch (Throwable t) {
            throw translateException( t );
        } finally {
            logout();
        }

    }
    
    protected Exception translateException( Throwable t )
    {
        if ( Exception.class.isAssignableFrom( t.getClass() ))
        {
            return (Exception) t;
        } else {
            InternalException ie = new InternalException( t.getMessage() );
            ie.setStackTrace( t.getStackTrace() );
            return ie;
        }
    }
    
    // ~ Helpers
    // =========================================================================
    
    protected String getLogString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Bean ");
        sb.append(this);
        sb.append("\n with Context ");
        sb.append(applicationContext);
        return sb.toString();
    }
    
}
