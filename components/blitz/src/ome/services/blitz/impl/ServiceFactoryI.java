/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.blitz.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import ome.api.IAdmin;
import ome.api.IShare;
import ome.conditions.SessionException;
import ome.logic.HardWiredInterceptor;
import ome.services.blitz.fire.AopContextInitializer;
import ome.services.blitz.fire.Registry;
import ome.services.blitz.fire.TopicManager;
import ome.services.blitz.util.ServantHolder;
import ome.services.blitz.util.ServiceFactoryAware;
import ome.services.blitz.util.UnregisterServantMessage;
import ome.services.sessions.SessionManager;
import ome.services.util.Executor;
import ome.system.EventContext;
import ome.system.OmeroContext;
import ome.system.Principal;
import ome.system.ServiceFactory;
import omero.ApiUsageException;
import omero.InternalException;
import omero.ServerError;
import omero.ShutdownInProgress;
import omero.api.AMD_StatefulServiceInterface_close;
import omero.api.ClientCallbackPrx;
import omero.api.ClientCallbackPrxHelper;
import omero.api.ExporterPrx;
import omero.api.ExporterPrxHelper;
import omero.api.GatewayPrx;
import omero.api.GatewayPrxHelper;
import omero.api.IAdminPrx;
import omero.api.IAdminPrxHelper;
import omero.api.IConfigPrx;
import omero.api.IConfigPrxHelper;
import omero.api.IContainerPrx;
import omero.api.IContainerPrxHelper;
import omero.api.IDeletePrx;
import omero.api.IDeletePrxHelper;
import omero.api.ILdapPrx;
import omero.api.ILdapPrxHelper;
import omero.api.IMetadataPrx;
import omero.api.IMetadataPrxHelper;
import omero.api.IPixelsPrx;
import omero.api.IPixelsPrxHelper;
import omero.api.IProjectionPrx;
import omero.api.IProjectionPrxHelper;
import omero.api.IQueryPrx;
import omero.api.IQueryPrxHelper;
import omero.api.IRenderingSettingsPrx;
import omero.api.IRenderingSettingsPrxHelper;
import omero.api.IRepositoryInfoPrx;
import omero.api.IRepositoryInfoPrxHelper;
import omero.api.IRoiPrx;
import omero.api.IRoiPrxHelper;
import omero.api.IScriptPrx;
import omero.api.IScriptPrxHelper;
import omero.api.ISessionPrx;
import omero.api.ISessionPrxHelper;
import omero.api.ISharePrx;
import omero.api.ISharePrxHelper;
import omero.api.ITimelinePrx;
import omero.api.ITimelinePrxHelper;
import omero.api.ITypesPrx;
import omero.api.ITypesPrxHelper;
import omero.api.IUpdatePrx;
import omero.api.IUpdatePrxHelper;
import omero.api.JobHandlePrx;
import omero.api.JobHandlePrxHelper;
import omero.api.RawPixelsStorePrx;
import omero.api.RawPixelsStorePrxHelper;
import omero.api.RenderingEnginePrx;
import omero.api.RenderingEnginePrxHelper;
import omero.api.SearchPrx;
import omero.api.SearchPrxHelper;
import omero.api.ServiceInterfacePrx;
import omero.api.ServiceInterfacePrxHelper;
import omero.api.StatefulServiceInterfacePrx;
import omero.api.StatefulServiceInterfacePrxHelper;
import omero.api.ThumbnailStorePrx;
import omero.api.ThumbnailStorePrxHelper;
import omero.api._ServiceFactoryDisp;
import omero.api._ServiceInterfaceOperations;
import omero.api._StatefulServiceInterfaceOperations;
import omero.constants.ADMINSERVICE;
import omero.constants.CLIENTUUID;
import omero.constants.CONFIGSERVICE;
import omero.constants.CONTAINERSERVICE;
import omero.constants.DELETESERVICE;
import omero.constants.EXPORTERSERVICE;
import omero.constants.GATEWAYSERVICE;
import omero.constants.JOBHANDLE;
import omero.constants.LDAPSERVICE;
import omero.constants.METADATASERVICE;
import omero.constants.PIXELSSERVICE;
import omero.constants.PROJECTIONSERVICE;
import omero.constants.QUERYSERVICE;
import omero.constants.RAWFILESTORE;
import omero.constants.RAWPIXELSSTORE;
import omero.constants.RENDERINGENGINE;
import omero.constants.RENDERINGSETTINGS;
import omero.constants.REPOSITORYINFO;
import omero.constants.ROISERVICE;
import omero.constants.SCRIPTSERVICE;
import omero.constants.SEARCH;
import omero.constants.SESSIONSERVICE;
import omero.constants.SHAREDRESOURCES;
import omero.constants.SHARESERVICE;
import omero.constants.THUMBNAILSTORE;
import omero.constants.TIMELINESERVICE;
import omero.constants.TYPESSERVICE;
import omero.constants.UPDATESERVICE;
import omero.grid.InteractiveProcessorI;
import omero.grid.SharedResourcesPrx;
import omero.grid.SharedResourcesPrxHelper;
import omero.model.ExperimenterGroup;
import omero.model.IObject;
import omero.model.Share;
import omero.util.IceMapper;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.transaction.annotation.Transactional;

import Ice.ConnectTimeoutException;
import Ice.ConnectionLostException;
import Ice.ConnectionRefusedException;
import Ice.Current;
import Ice.ObjectPrx;

/**
 * Responsible for maintaining all servants for a single session.
 * 
 * In general, try to reduce access to the {@link Ice.Current} and
 * {@link Ice.Util} objects.
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta2
 */
public final class ServiceFactoryI extends _ServiceFactoryDisp {

    // STATIC
    // ===========

    private final static Log log = LogFactory.getLog(ServiceFactoryI.class);

    // PRIVATE STATE
    // =================
    // These fields are special for this instance of SF alone. It represents
    // a single clients use of a session.

    boolean doClose = true;

    public final String clientId;
    
    public final Glacier2.SessionControlPrx control;

    private ClientCallbackPrx callback;
    
    // SHARED STATE
    // ===================
    // The following elements will all be the same or at least equivalent
    // in different instances of SF attached to the same session.

    final Ice.ObjectAdapter adapter;

    final SessionManager sessionManager;

    final TopicManager topicManager;

    final Registry registry;

    /**
     * {@link Executor} to be used by servant implementations which do not
     * delegate to the server package where all instances are wrapped with AOP
     * for dealing with Hibernate.
     */
    final Executor executor;

    final ServantHolder holder;

    final Principal principal;

    final List<HardWiredInterceptor> cptors;

    final OmeroContext context;

    final AopContextInitializer initializer;

    // ~ Initialization and context methods
    // =========================================================================

    public ServiceFactoryI(Ice.Current current, Glacier2.SessionControlPrx control,
            OmeroContext context,
            SessionManager manager, Executor executor, Principal p,
            List<HardWiredInterceptor> interceptors, TopicManager topicManager,
            Registry registry) throws ApiUsageException {
        this.adapter = current.adapter;
        this.control = control;
        this.clientId = clientId(current);
        this.context = context;
        this.sessionManager = manager;
        this.executor = executor;
        this.principal = p;
        this.cptors = interceptors;
        this.initializer = new AopContextInitializer(new ServiceFactory(
                this.context), this.principal);
        this.topicManager = topicManager;
        this.registry = registry;

        // Setting up in memory store.
        Ehcache cache = manager.inMemoryCache(p.getName());
        ServantHolder local;
        String key = "servantHolder";
        if (!cache.isKeyInCache(key)) {
            local = new ServantHolder();
            cache.put(new Element(key, local));
        } else {
            local = (ServantHolder) cache.get(key).getObjectValue();
        }
        holder = local; // Set the final value
    }

    public Ice.ObjectAdapter getAdapter() {
        return this.adapter;
    }

    public Principal getPrincipal() {
        return this.principal;
    }

    public Executor getExecutor() {
        return this.executor;
    }

    // ~ Security Context
    // =========================================================================

    @SuppressWarnings("unchecked")
    public List<IObject> getSecurityContexts(Current __current)
            throws ServerError {
        
        final EventContext ec = sessionManager.getEventContext(principal);
        List<?> objs = (List) executor.execute(principal, new Executor.SimpleWork(this, "getSecurityContext") {
            @Transactional(readOnly = true)
            public Object doWork(Session session, ServiceFactory sf) {
                
                final IAdmin admin = sf.getAdminService();
                final IShare share = sf.getShareService();
                final List<ome.model.IObject> objs = new ArrayList<ome.model.IObject>();
                
                // Groups
                final Set<Long> added = new HashSet<Long>();
                for (Long id : ec.getMemberOfGroupsList()) {
                    objs.add(admin.getGroup(id));
                    added.add(id);
                }
                for (Long id : ec.getLeaderOfGroupsList()) {
                    if (!added.contains(id)) {
                        objs.add(admin.getGroup(id));
                    }
                }
                
                // Shares
                objs.addAll(share.getMemberShares(true));
                objs.addAll(share.getOwnShares(true));

                return objs;
            }});
        IceMapper mapper = new IceMapper();
        return (List<IObject>) mapper.map(objs);
    }
    
    public IObject setSecurityContext(IObject obj, Current __current)
            throws ServerError {
        
        IceMapper mapper = new IceMapper();
        try {
            ome.model.IObject iobj = (ome.model.IObject) mapper.reverse(obj);
            ome.model.IObject old = sessionManager.setSecurityContext(principal, iobj);
            return (IObject) mapper.map(old);
        } catch (Exception e) {
            Ice.UserException iue = mapper.handleException(e, context);
            if (iue instanceof ServerError) {
                throw (ServerError) iue;
            } else {
                InternalException iu = new InternalException();
                iu.initCause(iue);
                IceMapper.fillServerError(iu, e);
                throw iu;
            }
        }

    }


    
    // ~ Stateless
    // =========================================================================

    public IAdminPrx getAdminService(Ice.Current current) throws ServerError {
        return IAdminPrxHelper.uncheckedCast(getByName(ADMINSERVICE.value,
                current));
    }

    public IConfigPrx getConfigService(Ice.Current current) throws ServerError {
        return IConfigPrxHelper.uncheckedCast(getByName(CONFIGSERVICE.value,
                current));
    }

    public IDeletePrx getDeleteService(Ice.Current current) throws ServerError {
        return IDeletePrxHelper.uncheckedCast(getByName(DELETESERVICE.value,
                current));
    }

    public ILdapPrx getLdapService(Ice.Current current) throws ServerError {
        return ILdapPrxHelper.uncheckedCast(getByName(LDAPSERVICE.value,
                current));
    }

    public IPixelsPrx getPixelsService(Ice.Current current) throws ServerError {
        return IPixelsPrxHelper.uncheckedCast(getByName(PIXELSSERVICE.value,
                current));
    }

    public IContainerPrx getContainerService(Ice.Current current)
            throws ServerError {
        return IContainerPrxHelper.uncheckedCast(getByName(
                CONTAINERSERVICE.value, current));
    }

    public IProjectionPrx getProjectionService(Ice.Current current)
            throws ServerError {
        return IProjectionPrxHelper.uncheckedCast(getByName(
                PROJECTIONSERVICE.value, current));
    }

    public IQueryPrx getQueryService(Ice.Current current) throws ServerError {
        return IQueryPrxHelper.uncheckedCast(getByName(QUERYSERVICE.value,
                current));
    }

    public IRoiPrx getRoiService(Ice.Current current) throws ServerError {
        Ice.ObjectPrx prx = getByName(ROISERVICE.value, current);
        return IRoiPrxHelper.uncheckedCast(prx);
    }

    public IScriptPrx getScriptService(Ice.Current current) throws ServerError {
        Ice.ObjectPrx prx = getByName(SCRIPTSERVICE.value, current);
        return IScriptPrxHelper.uncheckedCast(prx);
    }

    public ISessionPrx getSessionService(Current current) throws ServerError {
        return ISessionPrxHelper.uncheckedCast(getByName(SESSIONSERVICE.value,
                current));
    }

    public ISharePrx getShareService(Current current) throws ServerError {
        return ISharePrxHelper.uncheckedCast(getByName(SHARESERVICE.value,
                current));
    }

    public ITimelinePrx getTimelineService(Ice.Current current)
            throws ServerError {
        return ITimelinePrxHelper.uncheckedCast(getByName(
                TIMELINESERVICE.value, current));
    }

    public ITypesPrx getTypesService(Ice.Current current) throws ServerError {
        return ITypesPrxHelper.uncheckedCast(getByName(TYPESSERVICE.value,
                current));
    }

    public IUpdatePrx getUpdateService(Ice.Current current) throws ServerError {
        Ice.ObjectPrx prx = getByName(UPDATESERVICE.value, current);
        return IUpdatePrxHelper.uncheckedCast(prx);

    }

    public IRenderingSettingsPrx getRenderingSettingsService(Ice.Current current)
            throws ServerError {
        return IRenderingSettingsPrxHelper.uncheckedCast(getByName(
                RENDERINGSETTINGS.value, current));
    }

    public IRepositoryInfoPrx getRepositoryInfoService(Ice.Current current)
            throws ServerError {
        return IRepositoryInfoPrxHelper.uncheckedCast(getByName(
                REPOSITORYINFO.value, current));
    }

    public IMetadataPrx getMetadataService(Ice.Current current)
            throws ServerError {
        return IMetadataPrxHelper.uncheckedCast(getByName(
                METADATASERVICE.value, current));
    }

    // ~ Stateful
    // =========================================================================

    public ExporterPrx createExporter(Current current) throws ServerError {
        return ExporterPrxHelper.uncheckedCast(createByName(
                EXPORTERSERVICE.value, current));
    }

    public GatewayPrx createGateway(Ice.Current current) throws ServerError {
        return GatewayPrxHelper.uncheckedCast(createByName(
                GATEWAYSERVICE.value, current));
    }

    public JobHandlePrx createJobHandle(Ice.Current current) throws ServerError {
        return JobHandlePrxHelper.uncheckedCast(createByName(JOBHANDLE.value,
                current));
    }

    public RenderingEnginePrx createRenderingEngine(Ice.Current current)
            throws ServerError {
        return RenderingEnginePrxHelper.uncheckedCast(createByName(
                RENDERINGENGINE.value, current));
    }

    public omero.api.RawFileStorePrx createRawFileStore(Ice.Current current)
            throws ServerError {
        return omero.api.RawFileStorePrxHelper.uncheckedCast(createByName(
                RAWFILESTORE.value, current));
    }

    public RawPixelsStorePrx createRawPixelsStore(Ice.Current current)
            throws ServerError {
        return RawPixelsStorePrxHelper.uncheckedCast(createByName(
                RAWPIXELSSTORE.value, current));
    }

    public SearchPrx createSearchService(Ice.Current current)
            throws ServerError {
        return SearchPrxHelper
                .uncheckedCast(createByName(SEARCH.value, current));
    }

    public ThumbnailStorePrx createThumbnailStore(Ice.Current current)
            throws ServerError {
        return ThumbnailStorePrxHelper.uncheckedCast(createByName(
                THUMBNAILSTORE.value, current));
    }

    // ~ Other interface methods
    // =========================================================================

    public SharedResourcesPrx sharedResources(Current current) throws ServerError {
        return SharedResourcesPrxHelper.uncheckedCast(createByName(
                SHAREDRESOURCES.value, current));
    }

    public ServiceInterfacePrx getByName(String name, Current current)
            throws ServerError {

        Ice.Identity id = getIdentity(name);

        holder.acquireLock(name);
        try {
            Ice.ObjectPrx prx;
            Ice.Object servant = holder.get(name);
            if (servant == null) {
                servant = createServantDelegate(name);
                // Previously we checked for stateful services here,
                // however the logic is the same so it shouldn't
                // cause any issues.
                prx = registerServant(current, id, servant);
            } else {
                prx = adapter.createDirectProxy(id);
            }
            return ServiceInterfacePrxHelper.uncheckedCast(prx);
        } finally {
            holder.releaseLock(name);
        }
    }

    public StatefulServiceInterfacePrx createByName(String name, Current current)
            throws ServerError {

        Ice.Identity id = getIdentity(Ice.Util.generateUUID() + name);
        if (null != adapter.find(id)) {
            omero.InternalException ie = new omero.InternalException();
            ie.message = name + " already registered for this adapter.";
        }

        Ice.Object servant = createServantDelegate(name);
        Ice.ObjectPrx prx = registerServant(current, id, servant);
        return StatefulServiceInterfacePrxHelper.uncheckedCast(prx);
    }

    public void subscribe(String topicName, ObjectPrx prx, Current __current)
            throws ServerError {

        if (topicName == null || !topicName.startsWith("/public/")) {
            throw new omero.ApiUsageException(null, null,
                    "Currently only \"/public/\" topics allowed.");
        }
        topicManager.register(topicName, prx);
        log.info("Registered " + prx + " for " + topicName);
    }

    public void setCallback(ClientCallbackPrx callback, Ice.Current current) {
        this.callback = callback;
        log.info(Ice.Util.identityToString(this.sessionId())
                + " set callback to " + this.callback);
    }

    public void detachOnDestroy(Ice.Current current) {
        doClose = false;
    }

    @Deprecated
    public void close(Ice.Current current) {
        doClose = false;
    }

    public void closeOnDestroy(Ice.Current current) {
        doClose = true;
    }

    /**
     * Destruction simply decrements the reference count for a session to allow
     * reconnecting to it. This means that the Glacier timeout property is
     * fairly unimportant. If a Glacier connection times out or is otherwise
     * destroyed, a client can attempt to reconnect.
     * 
     * However, in the case of only one reference to the session, if the
     * Glacier2 timeout is greater than the session timeout, exceptions can be
     * thrown when this method tries to clean up the session. Therefore all
     * session access must be guarded by a try/finally block.
     */
    public void destroy(Ice.Current current) {

        // Remove this instance from the adapter: 1) to prevent
        // further remote calls on it, and 2) to reduce the number
        // of calls to destroy() from SessionManagerI.reapSession()
        // If an exception if thrown, there's not much we can do,
        // and it's important to continue cleaning up resources!
        try {
            adapter.remove(sessionId());
        } catch (Ice.NotRegisteredException nre) {
            // It's possible that another thread tried to remove
            // this session first. Logging the fact, but we will
            // continue with the closing which should be safe
            // to call multiple times.
            log.warn("NotRegisteredException: "
                    + Ice.Util.identityToString(sessionId()));
        } catch (Ice.ObjectAdapterDeactivatedException oade) {
            log.warn("Adapter already deactivated. Cannot remove: "
                    + sessionId());
        } catch (Throwable t) {
            log.error("Can't remove service factory", t);
        }

        int ref;
        try {
            // First detach and get the reference count.
            ref = sessionManager.detach(this.principal.getName());
        } catch (SessionException rse) {
            // If the session has already been removed or has timed out,
            // then we should do everything we can to clean up.
            log.info("Session already removed. Cleaning up blitz state.");
            ref = 0;
            doClose = true;
        }

        // If we are supposed to close, do only so if the ref count
        // is < 1.
        if (doClose && ref < 1) {

            // First call back to the client to prevent any further access
            // We do so one way though to prevent hanging this method. We
            // also take steps to not fall into a recursive loop.
            ClientCallbackPrx copy = callback;
            callback = null;
            if (copy != null) {
                try {
                    Ice.ObjectPrx prx = copy.ice_oneway();
                    ClientCallbackPrx oneway = ClientCallbackPrxHelper
                            .uncheckedCast(prx);
                    oneway.sessionClosed();
                } catch (Ice.NotRegisteredException nre) {
                    log.warn(clientId + "'s callback not registered -"
                            + " perhaps wrong proxy?");
                } catch (ConnectionRefusedException cre) {
                    log.warn(clientId + "'s callback refused connection -"
                            + " did the client die?");
                } catch (ConnectionLostException cle) {
                    log.debug(clientId + "'s connection lost as expected");
                } catch (ConnectTimeoutException cte) {
                    log.warn("ConnectTimeoutException on callback:" + clientId);
                } catch (Exception e) {
                    log.error("Unknown error on oneway "
                            + "ClientCallback.sessionClosed to "
                            + this.adapter.getCommunicator().identityToString(
                                    copy.ice_getIdentity()), e);
                }
            }

            // Must check all session access in this method too.
            doDestroy();

            try {
                ref = sessionManager.close(this.principal.getName());
            } catch (SessionException se) {
                // An exception could still theoretically be thrown here
                // if the timeout/removal happened since the last call.
                // Therefore, we'll just let another exception be thrown
                // since the time for shutdown is not overly critical.
            }

        }

    }

    /**
     * Performs the actual cleanup operation on all the resources shared between
     * this and other {@link ServiceFactoryI} instances in the same
     * {@link Session}. Since {@link #destroy()} is called regardless by the
     * router, even when a client has just died, we have this internal method
     * for handling the actual closing of resources.
     * 
     * This method must take precautions to not throw a {@link SessionException}
     * . See {@link #destroy(Current)} for more information.
     */
    public void doDestroy() {

        if (log.isInfoEnabled()) {
            log.info(String.format("Closing %s session", this));
        }

        // Cleaning up resources
        // =================================================
        holder.acquireLock("*"); // Protects all the servants on destruction
        try {
            List<String> servants = holder.getServantList();
            for (final String key : servants) {
                final Ice.Object servantOrTie = holder.get(key);
                final Ice.Identity id = getIdentity(key);

                if (servantOrTie == null) {
                    log.warn("Servant already removed: " + key);
                    // But calling unregister just in case
                    unregisterServant(id);
                    continue; // LOOP.
                }

                // All errors are ignored within the loop.
                try {
                    Object servant;
                    if (servantOrTie instanceof Ice.TieBase) {
                        servant = ((Ice.TieBase) servantOrTie).ice_delegate();
                    } else {
                        servant = servantOrTie;
                    }

                    // Now that we have the servant instance, we do what we can
                    // to clean it up. Stateful services must use the callback
                    // mechanism of IceMethodInvoker. InteractiveProcessors must
                    // be stopped and unregistered. Stateless must only be
                    // unregistered.
                    //
                    // TODO: put all of this in the AbstractAmdServant class.
                    if (servant instanceof _StatefulServiceInterfaceOperations) {

                        // Cleanup stateful
                        // ----------------
                        // Here we call the "close()" method on all methods
                        // which
                        // require that logic, allowing the IceMethodInvoker to
                        // raise the UnregisterServantEvent, otherwise there is
                        // a
                        // recursive call back to close
                        final _StatefulServiceInterfaceOperations stateful = (_StatefulServiceInterfaceOperations) servant;
                        final Ice.Current __curr = new Ice.Current();
                        __curr.id = id;
                        __curr.adapter = adapter;
                        __curr.operation = "close";
                        __curr.ctx = new HashMap<String, String>();
                        __curr.ctx.put(CLIENTUUID.value, clientId);
                        // We have to be more intelligent about this. The call
                        // should really happen in the same thread so that it's
                        // complete before the service factory is removed.
                        stateful.close_async(
                                new AMD_StatefulServiceInterface_close() {
                                    public void ice_exception(Exception ex) {
                                        if (ex instanceof omero.SessionException) {
                                            log
                                                    .warn("Stateful session not properly closed: "
                                                            + key);
                                        } else {
                                            log.error(
                                                    "Error on close callback: "
                                                            + key + "="
                                                            + stateful, ex);
                                        }
                                    }

                                    public void ice_response() {
                                        // Ok.
                                    }

                                }, __curr);

                    } else {
                        if (servant instanceof InteractiveProcessorI) {
                            // Cleanup interactive processors
                            // ------------------------------
                            InteractiveProcessorI ip = (InteractiveProcessorI) servant;
                            ip.stop();
                        } else if (servant instanceof _ServiceInterfaceOperations) {
                            // Cleanup stateless
                            // -----------------
                            // Do nothing.
                        } else if (servant instanceof SharedResourcesI) {
                            // Not currently doing anything.
                            // But will eventually need to cleanup cache.
                            ((SharedResourcesI)servant).close();
                        } else {
                            throw new ome.conditions.InternalException(
                                    "Unknown servant type: " + servant);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error destroying servant: " + key + "="
                            + servantOrTie, e);
                } finally {
                    // Now we will again try to remove the servant, which may
                    // have already been done, after the method call, though, it
                    // is guaranteed to no longer be active.
                    unregisterServant(id);
                    log.info("Removed servant from adapter: " + key);
                }
            }
        } finally {
            holder.releaseLock("*");
        }
    }

    public List<String> activeServices(Current __current) {
        return holder.getServantList();
    }

    public long keepAllAlive(ServiceInterfacePrx[] proxies, Current __current) {

        // First take measures to keep the session alive
        sessionManager.getEventContext(this.principal);
        if (log.isInfoEnabled()) {
            log.info("Keep alive: " + __current.id.name);
        }

        if (proxies == null || proxies.length == 0) {
            return -1; // All set to 1
        }

        long retVal = 0;
        for (int i = 0; i < proxies.length; i++) {
            ServiceInterfacePrx prx = proxies[i];
            Ice.Identity id = prx.ice_getIdentity();
            if (null == holder.get(id)) {
                retVal |= 1 << i;
            }
        }
        return retVal;
    }

    /**
     * Currently ignoring the individual proxies
     */
    public boolean keepAlive(ServiceInterfacePrx proxy, Current __current) {

        // First take measures to keep the session alive
        sessionManager.getEventContext(this.principal);
        if (log.isInfoEnabled()) {
            log.info("Keep alive: " + __current.id.name);
        }

        if (proxy == null) {
            return false;
        }
        Ice.Identity id = proxy.ice_getIdentity();
        return null != holder.get(id);
    }

    // ~ Helpers
    // =========================================================================

    /**
     * Constructs an {@link Ice.Identity} from the name of this
     * {@link ServiceFactoryI} and from the given {@link String} which for
     * stateless services are defined by the instance fields {@link #adminKey},
     * {@link #configKey}, etc. and for stateful services are UUIDs.
     */
    protected Ice.Identity getIdentity(String key) {
        Ice.Identity id = new Ice.Identity();
        id.category = this.principal.getName();
        id.name = key;
        return id;
    }

    /**
     * Creates a proxy according to the {@link ServantDefinition} for the given
     * name. Injects the {@link #helper} instance for this session so that all
     * services are linked to a single session.
     * 
     * Creates an ome.api.* service (mostly managed by Spring), wraps it with
     * the {@link HardWiredInterceptor interceptors} which are in effect, and
     * stores the instance away in the cache.
     * 
     * Note: Since {@link HardWiredInterceptor} implements
     * {@link MethodInterceptor}, all the {@link Advice} instances will be
     * wrapped in {@link Advisor} instances and will be returned by
     * {@link Advised#getAdvisors()}.
     */
    protected Ice.Object createServantDelegate(String name) throws ServerError {

        Ice.Object servant = null;
        try {
            servant = (Ice.Object) context.getBean(name);
            // Now setup the servant
            // ---------------------------------------------------------------------
            if (servant instanceof Ice.TieBase) {
                Ice.TieBase tie = (Ice.TieBase) servant;
                Object obj = tie.ice_delegate();
                if (obj instanceof AbstractAmdServant) {
                    AbstractAmdServant amd = (AbstractAmdServant) obj;
                    amd.applyHardWiredInterceptors(cptors, initializer);
                }
                if (obj instanceof ServiceFactoryAware) {
                    ((ServiceFactoryAware) obj).setServiceFactory(this);
                }
            }
            return servant;
        } catch (ClassCastException cce) {
            throw new InternalException(null, null,
                    "Could not cast to Ice.Object:[" + name + "]");
        } catch (NoSuchBeanDefinitionException nosuch) {
            ApiUsageException aue = new ApiUsageException();
            aue.message = name
                    + " is an unknown service. Please check Constants.ice or the documentation for valid strings.";
            throw aue;
        } catch (Exception e) {
            log.warn("Uncaught exception in createServantDelegate. ", e);
            throw new InternalException(null, e.getClass().getName(), e
                    .getMessage());
        }
    }

    /**
     * Registers the servant with the adapter (or throws an exception if one is
     * already registered) as well as configures the servant in any post-Spring
     * way necessary, based on the type of the servant.
     */
    protected Ice.ObjectPrx registerServant(Current current, Ice.Identity id,
            Ice.Object servant) throws ServerError {

        Ice.ObjectPrx prx = null;
        try {
            Ice.Object already = adapter.find(id);
            if (null == already) {
                adapter.add(servant, id);
                prx = adapter.createDirectProxy(id);
                if (log.isInfoEnabled()) {
                    log.info("Added servant to adapter: "
                            + servantString(id, servant));
                }
            } else {
                throw new omero.InternalException(null, null,
                        "Servant already registered: "
                                + servantString(id, servant));
            }
        } catch (Exception e) {
            if (e instanceof omero.InternalException) {
                throw (omero.InternalException) e;
            } else if (e instanceof Ice.ObjectAdapterDeactivatedException) {
                // ticket:1251
                ShutdownInProgress sip = new ShutdownInProgress(null, null,
                        "ObjectAdapter deactivated");
                IceMapper.fillServerError(sip, e);
                throw sip;
            } else {
                omero.InternalException ie = new omero.InternalException();
                IceMapper.fillServerError(ie, e);
                throw ie;
            }
        }

        // Alright to register this servant now.
        // Using just the name because the category essentially == this
        // holder
        holder.put(id.name, servant);

        return prx;

    }

    /**
     * Reverts all the additions made by
     * {@link #registerServant(ServantInterface, Ice.Current, Ice.Identity)}
     * 
     * Now called by {@link ome.services.blitz.fire.SessionManagerI} in response
     * to an {@link UnregisterServantMessage}
     */
    public void unregisterServant(Ice.Identity id) {

        // If this is not found ignore.
        if (null == adapter.find(id)) {
            return; // EARLY EXIT!
        }

        // Here we assume that if the "close()" call is required, that it has
        // already been made, either by a user or by the SF.close() method in
        // which case unregisterServant() is being closed via
        // onApplicationEvent().
        // Otherwise, it is being called directly by SF.close().
        Ice.Object obj = adapter.remove(id);
        Object removed = holder.remove(id);
        if (removed == null) {
            log.error("Adapter and active servants out of sync.");
        }
        if (log.isInfoEnabled()) {
            log.info("Unregistered servant:" + servantString(id, obj));
        }
    }

    private String servantString(Ice.Identity id, Object obj) {
        StringBuilder sb = new StringBuilder(Ice.Util.identityToString(id));
        sb.append("(");
        sb.append(obj);
        sb.append(")");
        return sb.toString();
    }

    // Id Helpers
    // =========================================================================
    // Used for naming service factory instances and creating Ice.Identities
    // from Ice.Currents, etc.

    /**
     * Definition of session ids: "session-<CLIENTID>/<UUID>"
     */
    public static Ice.Identity sessionId(String clientId, String uuid) {
        Ice.Identity id = new Ice.Identity();
        id.category = "session-" + clientId;
        id.name = uuid;
        return id;

    }

    /**
     * Returns the {@link Ice.Identity} for this instance as defined by
     * {@link #sessionId(String, String)}
     * 
     * @return
     */
    public Ice.Identity sessionId() {
        return sessionId(clientId, principal.getName());
    }

    /**
     * Helpers method to extract the {@link CLIENTUUID} out of the given
     * Ice.Current. Throws an {@link ApiUsageException} if none is present,
     * since it is each client's responsibility to set this value.
     * 
     * (Typically done in our SDKs)
     */
    public static String clientId(Ice.Current current) throws ApiUsageException {
        String clientId = null;
        if (current.ctx != null) {
            clientId = current.ctx.get(omero.constants.CLIENTUUID.value);
        }
        if (clientId == null) {
            throw new ApiUsageException(null, null, "No "
                    + omero.constants.CLIENTUUID.value
                    + " key provided in context.");
        }
        return clientId;
    }

}
