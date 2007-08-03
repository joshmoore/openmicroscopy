/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.blitz.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import ome.api.IAdmin;
import ome.api.IConfig;
import ome.api.ILdap;
import ome.api.IPixels;
import ome.api.IPojos;
import ome.api.IQuery;
import ome.api.ITypes;
import ome.api.IUpdate;
import ome.api.RawFileStore;
import ome.api.RawPixelsStore;
import ome.api.ServiceInterface;
import ome.api.ThumbnailStore;

import ome.api.IRepositoryInfo;
import omero.api.IRepositoryInfoPrx;
import omero.api.IRepositoryInfoPrxHelper;
import omero.api._IRepositoryInfoOperations;
import omero.api._IRepositoryInfoTie;

import ome.conditions.InternalException;
import ome.logic.HardWiredInterceptor;
import ome.services.blitz.fire.AopContextInitializer;
import ome.services.blitz.fire.Session;
import ome.services.blitz.fire.SessionPrincipal;
import ome.services.blitz.util.DestroySessionMessage;
import ome.services.blitz.util.ServantDefinition;
import ome.services.blitz.util.ServantHelper;
import ome.services.blitz.util.UnregisterServantMessage;
import ome.system.OmeroContext;
import ome.system.Principal;
import ome.system.ServiceFactory;
import omeis.providers.re.RenderingEngine;
import omero.ServerError;
import omero.api.IAdminPrx;
import omero.api.IAdminPrxHelper;
import omero.api.IConfigPrx;
import omero.api.IConfigPrxHelper;
import omero.api.ILdapPrx;
import omero.api.ILdapPrxHelper;
import omero.api.IPixelsPrx;
import omero.api.IPixelsPrxHelper;
import omero.api.IPojosPrx;
import omero.api.IPojosPrxHelper;
import omero.api.IQueryPrx;
import omero.api.IQueryPrxHelper;
import omero.api.ITypesPrx;
import omero.api.ITypesPrxHelper;
import omero.api.IUpdatePrx;
import omero.api.IUpdatePrxHelper;
import omero.api.RawPixelsStorePrx;
import omero.api.RawPixelsStorePrxHelper;
import omero.api.RenderingEnginePrx;
import omero.api.RenderingEnginePrxHelper;
import omero.api.ServiceInterfacePrx;
import omero.api.ServiceInterfacePrxHelper;
import omero.api.SimpleCallbackPrx;
import omero.api.ThumbnailStorePrx;
import omero.api.ThumbnailStorePrxHelper;
import omero.api._IAdminOperations;
import omero.api._IAdminTie;
import omero.api._IConfigOperations;
import omero.api._IConfigTie;
import omero.api._ILdapOperations;
import omero.api._ILdapTie;
import omero.api._IPixelsOperations;
import omero.api._IPixelsTie;
import omero.api._IPojosOperations;
import omero.api._IPojosTie;
import omero.api._IQueryOperations;
import omero.api._IQueryTie;
import omero.api._ITypesOperations;
import omero.api._ITypesTie;
import omero.api._IUpdateOperations;
import omero.api._IUpdateTie;
import omero.api._RawFileStoreOperations;
import omero.api._RawFileStoreTie;
import omero.api._RawPixelsStoreOperations;
import omero.api._RawPixelsStoreTie;
import omero.api._RenderingEngineOperations;
import omero.api._RenderingEngineTie;
import omero.api._ServiceFactoryDisp;
import omero.api._ThumbnailStoreOperations;
import omero.api._ThumbnailStoreTie;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

import Ice.Current;
import Ice.ObjectPrx;

/**
 * Responsible for maintaining all servants for a single session.
 *
 * In general, this implementation stores all services (ome.api.*) under the
 * {@link String} representation of the {@link Ice.Identity} and the actual
 * servants are only maintained by the {@link Ice.ObjectAdapter}.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta2
 */
public final class ServiceFactoryI extends _ServiceFactoryDisp implements
        Session, ApplicationContextAware, ApplicationListener {

    private final static Log log = LogFactory.getLog(ServiceFactoryI.class);

    Ehcache cache;

    SessionPrincipal principal;

    List<HardWiredInterceptor> cptors;

    OmeroContext context;

    ServantHelper helper;

    AopContextInitializer initializer;

    // ~ Synchronized state
    // =========================================================================

    String adminKey = "IAdmin", configKey = "IConfig", ldapKey = "ILdap", pixelsKey = "IPixels",
            pojosKey = "Pojos", queryKey = "IQuery", typesKey = "ITypes",
            updateKey = "IUpdate", repoKey = "IRepositoryInfo";

    Set<Ice.Identity> ids = Collections
            .synchronizedSet(new HashSet<Ice.Identity>());

    // ~ Initialization and context methods
    // =========================================================================

    public ServiceFactoryI(Ehcache ehcache) {
        this.cache = ehcache;
    }

    /**
     * {@link Session} interface.
     */
    public void setPrincipal(SessionPrincipal p) {
        this.principal = p;
        initializer = new AopContextInitializer(new ServiceFactory(
                this.context), this.principal);
    }

    public void setInterceptors(List<HardWiredInterceptor> interceptors) {
        this.cptors = interceptors;
    }

    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        context = (OmeroContext) applicationContext;
        helper = new ServantHelper(context, cache);
    }

    /**
     * @see ServantHelper#getService(String, Ice.Current)
     */
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof UnregisterServantMessage) {
            UnregisterServantMessage msg = (UnregisterServantMessage) event;
            String key = msg.getServiceKey();
            Ice.Current curr = msg.getCurrent();
            unregisterServant(key, curr);
        }
    }

    // ~ Stateless
    // =========================================================================

    public IAdminPrx getAdminService(Ice.Current current) {
        synchronized (adminKey) {
            Ice.Identity id = getIdentity(current, adminKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IAdminOperations ops = createServantDelegate(
                        _IAdminOperations.class, IAdmin.class, key);
                _IAdminTie servant = new _IAdminTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IAdminPrxHelper.uncheckedCast(prx);
        }
    }

    public IConfigPrx getConfigService(Ice.Current current) {
        synchronized (configKey) {
            Ice.Identity id = getIdentity(current, configKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IConfigOperations ops = createServantDelegate(
                        _IConfigOperations.class, IConfig.class, key);
                _IConfigTie servant = new _IConfigTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IConfigPrxHelper.uncheckedCast(prx);
        }
    }

    public ILdapPrx getLdapService(Ice.Current current) {
        synchronized (ldapKey) {
            Ice.Identity id = getIdentity(current, ldapKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _ILdapOperations ops = createServantDelegate(
                        _ILdapOperations.class, ILdap.class, key);
                _ILdapTie servant = new _ILdapTie(ops);
                prx = registerServant(servant, current, id);
            }
            return ILdapPrxHelper.uncheckedCast(prx);
        }
    }
    
    public IPixelsPrx getPixelsService(Ice.Current current) {
        synchronized (pixelsKey) {
            Ice.Identity id = getIdentity(current, pixelsKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IPixelsOperations ops = createServantDelegate(
                        _IPixelsOperations.class, IPixels.class, key);
                _IPixelsTie servant = new _IPixelsTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IPixelsPrxHelper.uncheckedCast(prx);
        }
    }

    public IPojosPrx getPojosService(Ice.Current current) {
        synchronized (pojosKey) {
            Ice.Identity id = getIdentity(current, pojosKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IPojosOperations ops = createServantDelegate(
                        _IPojosOperations.class, IPojos.class, key);
                _IPojosTie servant = new _IPojosTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IPojosPrxHelper.uncheckedCast(prx);

        }
    }

    public IQueryPrx getQueryService(Ice.Current current) {
        synchronized (queryKey) {
            Ice.Identity id = getIdentity(current, queryKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IQueryOperations ops = createServantDelegate(
                        _IQueryOperations.class, IQuery.class, key);
                _IQueryTie servant = new _IQueryTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IQueryPrxHelper.uncheckedCast(prx);

        }
    }

    public ITypesPrx getTypesService(Ice.Current current) {
        synchronized (typesKey) {
            Ice.Identity id = getIdentity(current, typesKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _ITypesOperations ops = createServantDelegate(
                        _ITypesOperations.class, ITypes.class, key);
                _ITypesTie servant = new _ITypesTie(ops);
                prx = registerServant(servant, current, id);
            }
            return ITypesPrxHelper.uncheckedCast(prx);

        }
    }

    public IUpdatePrx getUpdateService(Ice.Current current) {
        synchronized (updateKey) {
            Ice.Identity id = getIdentity(current, updateKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IUpdateOperations ops = createServantDelegate(
                        _IUpdateOperations.class, IUpdate.class, key);
                _IUpdateTie servant = new _IUpdateTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IUpdatePrxHelper.uncheckedCast(prx);
        }
    }

    public IRepositoryInfoPrx getRepositoryInfoService(Ice.Current current) {
        synchronized (repoKey) {
            Ice.Identity id = getIdentity(current, repoKey);
            String key = Ice.Util.identityToString(id);

            Ice.ObjectPrx prx = servantProxy(id, current);
            if (prx == null) {
                _IRepositoryInfoOperations ops = createServantDelegate(
                        _IRepositoryInfoOperations.class, IRepositoryInfo.class, key);
                _IRepositoryInfoTie servant = new _IRepositoryInfoTie(ops);
                prx = registerServant(servant, current, id);
            }
            return IRepositoryInfoPrxHelper.uncheckedCast(prx);
        }
    }

    // ~ Stateful
    // =========================================================================

    // For symmetry
    String re = "RenderingEngine", fs = "RawFileStore", ps = "RawPixelStore",
            tb = "ThumbnailStore";

    public RenderingEnginePrx createRenderingEngine(Ice.Current current) {
        Ice.Identity id = getIdentity(current, Ice.Util.generateUUID()+re);
        String key = Ice.Util.identityToString(id);
        _RenderingEngineOperations ops = createServantDelegate(
                _RenderingEngineOperations.class, RenderingEngine.class, key);
        _RenderingEngineTie servant = new _RenderingEngineTie(ops);
        Ice.ObjectPrx prx = registerServant(servant, current, id);
        return RenderingEnginePrxHelper.uncheckedCast(prx);
    }

    public omero.api.RawFileStorePrx createRawFileStore(Ice.Current current) {
        Ice.Identity id = getIdentity(current, Ice.Util.generateUUID()+fs);
        String key = Ice.Util.identityToString(id);
        _RawFileStoreOperations ops = createServantDelegate(
                _RawFileStoreOperations.class, RawFileStore.class, key);
        _RawFileStoreTie servant = new _RawFileStoreTie(ops);
        Ice.ObjectPrx prx = registerServant(servant, current, id);
        return omero.api.RawFileStorePrxHelper.uncheckedCast(prx);
    }

    public RawPixelsStorePrx createRawPixelsStore(Ice.Current current) {
        Ice.Identity id = getIdentity(current, Ice.Util.generateUUID()+ps);
        String key = Ice.Util.identityToString(id);
        _RawPixelsStoreOperations ops = createServantDelegate(
                _RawPixelsStoreOperations.class, RawPixelsStore.class, key);
        _RawPixelsStoreTie servant = new _RawPixelsStoreTie(ops);
        Ice.ObjectPrx prx = registerServant(servant, current, id);
        return RawPixelsStorePrxHelper.uncheckedCast(prx);
    }

    public ThumbnailStorePrx createThumbnailStore(Ice.Current current) {
        Ice.Identity id = getIdentity(current, Ice.Util.generateUUID()+tb);
        String key = Ice.Util.identityToString(id);
        _ThumbnailStoreOperations ops = createServantDelegate(
                _ThumbnailStoreOperations.class, ThumbnailStore.class, key);
        _ThumbnailStoreTie servant = new _ThumbnailStoreTie(ops);
        Ice.ObjectPrx prx = registerServant(servant, current, id);
        return ThumbnailStorePrxHelper.uncheckedCast(prx);
    }

    // ~ Other interface methods
    // =========================================================================

    public ServiceInterfacePrx getByName(String name, Current current) throws ServerError {
        Ice.Identity id = getIdentity(current, name);
        String key = Ice.Util.identityToString(id);

        Ice.ObjectPrx prx = servantProxy(id, current);
        if (prx == null) {
            ServantDefinition sd = (ServantDefinition) context.getBean(name);
            Object servant;
            try {
                Object ops = createServantDelegate(sd.getOperationsClass(), sd
                        .getServiceClass(), key);
                Constructor ctor = sd.getTieClass().getConstructor(
                        sd.getOperationsClass());
                servant = ctor.newInstance(ops);
                prx = registerServant(sd.getOperationsClass().cast(servant), current, id);
            } catch (Exception e) {
                // FIXME
                omero.InternalException ie = new omero.InternalException();
                ie.message = e.getMessage();
                throw ie;
            }
        }
        return ServiceInterfacePrxHelper.uncheckedCast(prx);
    }

    public void setCallback(SimpleCallbackPrx callback, Ice.Current current) {
        throw new UnsupportedOperationException();
    }

    public void close(Ice.Current current) {
        if (log.isInfoEnabled()) {
            log.info(String.format("Closing %s session", this));
        }
        Ice.Identity[] copy = (Ice.Identity[]) ids.toArray(new Ice.Identity[ids.size()]);
        for (Ice.Identity id : copy) {
            unregisterServant(id, current);
        }
    }

    public void destroy(Ice.Current current) {
        if (log.isInfoEnabled()) {
            log.info(String.format("Destroying %s session", this));
        }
        close(current);
        DestroySessionMessage msg = new DestroySessionMessage(this,current.id.name,principal);
        try {
            context.publishMessage(msg);
        } catch (Throwable t) {
            // FIXME
            InternalException ie = new InternalException(t.getMessage());
            ie.setStackTrace(t.getStackTrace());
        }
    }

    // ~ Helpers
    // =========================================================================

    /**
     * Constructs an {@link Ice.Identity} from the name of this
     * {@link ServiceFactoryI} and from the given {@link String} which for
     * stateless services are defined by the instance fields {@link #adminKey},
     * {@link #configKey}, etc. and for stateful services are UUIDs.
     */
    protected Ice.Identity getIdentity(Ice.Current curr, String key) {
        Ice.Identity id = new Ice.Identity();
        id.category = curr.id.name;
        id.name = key;
        return id;
    }

    /**
     * Checks for a service with the given {@link Ice.Identity} in the
     * {@link #cache} and if present, constructs a valid {@link Ice.ObjectPrx}
     * for that {@link Ice.Identity}. Otherwise, returns null;
     */
    protected Ice.ObjectPrx servantProxy(Ice.Identity id, Ice.Current current) {
        Element elt = cache.get(Ice.Util.identityToString(id));
        boolean exists = (elt != null);
        if (!exists)
            return null;

        return current.adapter.createProxy(id);
    }

    /**
     * Registers the given servant with the current {@link Ice.ObjectAdapter}
     * and stores the {@link Ice.Identity} for later use by {@link #close()}.
     */
    protected Ice.ObjectPrx registerServant(Ice.Object servant,
            Ice.Current current, Ice.Identity id) {

        if (log.isInfoEnabled()) {
            log.info("Registering servant:" + servantString(id, servant));
        }

        ids.add(id);
        current.adapter.add(servant, id);
        return current.adapter.createProxy(id);

    }

    /**
     * Since all servants are registered under their {@link Ice.Identity} this
     * converts the given {@link String} and passes it to
     * {@link #unregisterServant(Ice.Identity, Ice.Current)}
     */
    protected void unregisterServant(String key, Ice.Current current) {
        Ice.Identity id = Ice.Util.stringToIdentity(key);
        unregisterServant(id, current);
    }

    /**
     * Reverts all the additions made by {@link #registerServant(ServantInterface, Ice.Current, Ice.Identity)}
     */
    protected void unregisterServant(Ice.Identity id, Ice.Current current) {

        Ice.Object obj = current.adapter.remove(id);
        ids.remove(id);

        if (log.isInfoEnabled()) {
            log.info("Unregistered servant:" + servantString(id, obj));
        }
    }

    protected <T, O extends ServiceInterface> T createServantDelegate(
            Class<T> ice, Class<O> ome, String key) {
        createService(key, ome);
        ProxyFactory factory = new ProxyFactory();
        factory.setInterfaces(new Class[]{ice});
        factory.addAdvice(new Interceptor(ome,key,helper));
        return ice.cast(factory.getProxy());
    }

    /**
     * Creates an ome.api.* service (mostly managed by Spring), wraps it with
     * the {@link HardWiredInterceptor interceptors} which are in effect,
     * and stores the instance away in the cache.
     *
     * Note: Since {@link HardWiredInterceptor} implements {@link MethodInterceptor},
     * all the {@link Advice} instances will be wrapped in {@link Advisor}
     * instances and will be returned by {@link Advised#getAdvisors()}.
     */
    protected <T extends ServiceInterface> void createService(String key,
            Class<T> c) {
        Object srv = context.getBean("managed:" + c.getName());
        ProxyFactory factory = new ProxyFactory();
        factory.setInterfaces(new Class[]{c});

        List<HardWiredInterceptor> reversed =
            new ArrayList<HardWiredInterceptor>(cptors);
        Collections.reverse(reversed);
        for (HardWiredInterceptor hwi : reversed) {
            factory.addAdvice(0, hwi);
        }
        factory.addAdvice(0, initializer);
        factory.setTarget(srv);
        cache.put(new Element(key, factory.getProxy()));

    }

    private String servantString(Ice.Identity id, Object obj) {
        StringBuilder sb = new StringBuilder(Ice.Util.identityToString(id));
        sb.append("(");
        sb.append(obj);
        sb.append(")");
        return sb.toString();
    }

    /**
     * For Testing.
     */
    public List<String> getIds() {
        List<String> stringIds = new ArrayList<String>();
        synchronized (ids) {
            for (Ice.Identity id : ids) {
                stringIds.add(Ice.Util.identityToString(id));
            }
        }
        return stringIds;
    }
}
