/*
 *   $Id$
 *
 *   Copyright 2006 University of Dundee. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

/*------------------------------------------------------------------------------
 *
 * Written by:    Josh Moore, josh.moore at gmx.de
 *
 *------------------------------------------------------------------------------
 */

package ome.services.licenses;

// Java imports
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.interceptor.Interceptors;

// Third-party libraries
import org.jboss.annotation.ejb.LocalBinding;
import org.jboss.annotation.ejb.RemoteBinding;
import org.jboss.annotation.security.SecurityDomain;
import org.springframework.transaction.annotation.Transactional;

// Application-internal dependencies
import ome.annotations.RevisionDate;
import ome.annotations.RevisionNumber;
import ome.api.ServiceInterface;
import ome.conditions.InternalException;
import ome.logic.AbstractLevel2Service;
import ome.logic.SimpleLifecycle;
import ome.security.SecuritySystem;
import ome.services.util.OmeroAroundInvoke;

/**
 * Implementation of the {@link ILicense} service interface. {@link LicenseBean}
 * primarily delegates to a {@link LicenseStore} instance which is created from
 * a hard-coded class name. This class name can be changed via the
 * tools/licenses/build.xml script.
 * 
 * @author Josh Moore, josh.moore at gmx.de
 * @since 3.0-RC1
 * @see ILicense
 */
@RevisionDate("$Date: 2006-12-15 11:39:34 +0100 (Fri, 15 Dec 2006) $")
@RevisionNumber("$Revision: 1167 $")
@TransactionManagement(TransactionManagementType.BEAN)
@Transactional
@Stateless
@Remote(ILicense.class)
@RemoteBinding(jndiBinding = "omero/remote/ome.services.licenses.ILicense")
@Local(ILicense.class)
@LocalBinding(jndiBinding = "omero/local/ome.services.licenses.ILicense")
@SecurityDomain("OmeroSecurity")
@Interceptors( { OmeroAroundInvoke.class, SimpleLifecycle.class })
public class LicenseBean extends AbstractLevel2Service implements LicenseStore {

    public final Class<? extends ServiceInterface> getServiceInterface() {
        return ILicense.class;
    }

    /**
     * Hard-coded class name for the {@link LicenseStore} implementation. This
     * value may have been changed via tools/licenses/build.xml
     */
    private final static String STORE_CLASS = "ome.services.licenses.Store";

    private final static LicenseStore STORE;

    // Now we'll try to create an instance of STORE_CLASS and assign it to the
    // STORE constant. 
    static {
        try {
            Class storeClass = Class.forName(STORE_CLASS);
            STORE = (LicenseStore) storeClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create license store:"
				       + STORE_CLASS,e);
        }
    }

    /** 
     * This injector does not synchronize or check for null as specified in
     * the {@link LicenseStore#setStaticSecuritySystem(SecuritySystem)} method, but
     * delegates to the {@link #STORE} instance which should implement that 
     * logic.
     */
    public void setStaticSecuritySystem(SecuritySystem security) {
        STORE.setStaticSecuritySystem(security);
    }
    
    // ~ Service methods
    // =========================================================================
    // All methods delegate to the global static STORE instance.

    /** See {@link ILicense#acquireLicense()} */
    @RolesAllowed("user")
    public byte[] acquireLicense() throws NoAvailableLicensesException {
        return STORE.acquireLicense();
    }

    /** See {@link ILicense#getAvailableLicenseCount()} */
    @RolesAllowed("user")
    public long getAvailableLicenseCount() {
        return STORE.getAvailableLicenseCount();
    }

    /** See {@link ILicense#getTotalLicenseCount()} */
    @RolesAllowed("user")
    public long getTotalLicenseCount() {
        return STORE.getTotalLicenseCount();
    }

    /** See {@link ILicense#getLicenseTimeout()} */
    @RolesAllowed("user")
    public long getLicenseTimeout() {
        return STORE.getLicenseTimeout();
    }

    /** See {@link ILicense#releaseLicense(byte[])} */
    @RolesAllowed("user")
    public boolean releaseLicense(byte[] token) throws InvalidLicenseException {
        return STORE.releaseLicense(token);
    }

    /** See {@link ILicense#resetLicenses()} */
    @RolesAllowed("system")
    public void resetLicenses() {
        STORE.resetLicenses();
    }

    // These methods are not visible to clients

    /** See {@link LicenseStore#hasLicense(byte[])} */
    public boolean hasLicense(byte[] token) {
        return STORE.hasLicense(token);
    }

    /** See {@link LicenseStore#enterValid(byte[])} */
    public void enterMethod(byte[] token, LicensedPrincipal p) {
        STORE.enterMethod(token, p);
    }

    /** See {@link LicenseStore#exitMethod(byte[])} */
    public void exitMethod(byte[] token, LicensedPrincipal p) {
        STORE.exitMethod(token, p);
    }
}
