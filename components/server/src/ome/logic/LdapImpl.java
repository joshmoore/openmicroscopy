/*
 *   $Id$
 *
 *   Copyright 2007 University of Dundee. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import ome.annotations.NotNull;
import ome.annotations.RevisionDate;
import ome.annotations.RevisionNumber;
import ome.annotations.RolesAllowed;
import ome.api.ILdap;
import ome.api.ServiceInterface;
import ome.conditions.ApiUsageException;
import ome.conditions.SecurityViolation;
import ome.conditions.ValidationException;
import ome.model.internal.Permissions;
import ome.model.meta.Experimenter;
import ome.model.meta.ExperimenterGroup;
import ome.model.meta.GroupExperimenterMap;
import ome.parameters.Parameters;
import ome.security.SecuritySystem;
import ome.security.auth.AttributeNewUserGroupBean;
import ome.security.auth.AttributeSet;
import ome.security.auth.GroupAttributeMapper;
import ome.security.auth.LdapConfig;
import ome.security.auth.NewUserGroupBean;
import ome.security.auth.OrgUnitNewUserGroupBean;
import ome.security.auth.PersonContextMapper;
import ome.security.auth.QueryNewUserGroupBean;
import ome.security.auth.RoleProvider;
import ome.system.OmeroContext;
import ome.system.Roles;
import ome.util.SqlAction;

import org.apache.commons.logging.Log;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapOperations;
import org.springframework.ldap.core.LdapRdn;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides methods for administering user accounts, passwords, as well as
 * methods which require special privileges.
 * 
 * Developer note: As can be expected, to perform these privileged the Admin
 * service has access to several resources that should not be generally used
 * while developing services. Misuse could circumvent security or auditing.
 * 
 * @author Aleksandra Tarkowska, A.Tarkowska@dundee.ac.uk
 * @version $Revision: 1552 $, $Date: 2007-05-23 09:43:33 +0100 (Wed, 23 May
 *          2007) $
 * @see SecuritySystem
 * @see Permissions
 * @since 3.0-M3
 */
@Transactional(readOnly = true)
@RevisionDate("$Date: 2007-05-23 09:43:33 +0100 (Wed, 23 May 2007) $")
@RevisionNumber("$Revision: 1552 $")
public class LdapImpl extends AbstractLevel2Service implements ILdap,
    ApplicationContextAware {

    private final SqlAction sql;

    private final RoleProvider provider;

    private final ContextSource ctx;

    private final LdapOperations ldap;

    private final LdapConfig config;

    private final Roles roles;

    private OmeroContext appContext;

    public LdapImpl(
            ContextSource ctx,
            LdapOperations ldap, Roles roles,
            LdapConfig config,
            RoleProvider roleProvider,
            SqlAction sql) {
        this.ctx = ctx;
        this.sql = sql;
        this.ldap = ldap;
        this.roles = roles;
        this.config = config;
        this.provider = roleProvider;
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        appContext = (OmeroContext) arg0;
    }

    public Class<? extends ServiceInterface> getServiceInterface() {
        return ILdap.class;
    }

    // ~ System-only interface methods
    // =========================================================================

    @SuppressWarnings("unchecked")
    @RolesAllowed("system")
    public List<Experimenter> searchAll() {
        return ldap.search(DistinguishedName.EMPTY_PATH, config.getUserFilter()
                .encode(), getContextMapper());
    }

    @SuppressWarnings("unchecked")
    @RolesAllowed("system")
    public List<Experimenter> searchByAttribute(String dns, String attr,
            String value) {
        DistinguishedName dn;
        if (dns == null) {
            dn = DistinguishedName.EMPTY_PATH;
        } else {
            dn = new DistinguishedName(dns);
        }

        if (attr != null && !attr.equals("") && value != null
                && !value.equals("")) {
            AndFilter filter = new AndFilter();
            filter.and(config.getUserFilter());
            filter.and(new EqualsFilter(attr, value));

            return ldap.search(dn, filter.encode(),
                    getContextMapper());
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    @RolesAllowed("system")
    public Experimenter searchByDN(String dns) {
        DistinguishedName dn = new DistinguishedName(dns);
        return (Experimenter) ldap.lookup(dn, getContextMapper());
    }

    @RolesAllowed("system")
    @SuppressWarnings("unchecked")
    public String findDN(String username) {

        PersonContextMapper mapper = getContextMapper();
        Experimenter exp = mapUserName(username, mapper);
        return mapper.getDn(exp);

    }


    @RolesAllowed("system")
    @SuppressWarnings("unchecked")
    public Experimenter findExperimenter(String username) {

        PersonContextMapper mapper = getContextMapper();
        return mapUserName(username, mapper);

    }

    /**
     * Mapping a username to an {@link Experimenter}. This handles checking the
     * username for case exactness. This should be done at the LDAP level, but
     * Apache DS (the testing framework used) does not yet support :caseExactMatch:.
     *
     * When it does, the check here can be removed.
     *
     * @param username
     * @param mapper
     * @return a non null Experimenter.
     * @see ticket:2557
     */
    private Experimenter mapUserName(String username, PersonContextMapper mapper) {
        Filter filter = config.usernameFilter(username);
        List<Experimenter> p = ldap.search("", filter.encode(),
            mapper.getControls(), mapper);

        if (p.size() == 1 && p.get(0) != null) {
            Experimenter e = p.get(0);
            if (e.getOmeName().equals(username)) {
                return p.get(0);
            }
        }
        throw new ApiUsageException(String.format(
                    "Cannot find unique DistinguishedName '%s': found=%s",
                    username, p.size()));

    }

    @RolesAllowed("system")
    @SuppressWarnings("unchecked")
    public List<String> searchDnInGroups(String attr, String value) {
        if (attr != null && !attr.equals("") && value != null
                && !value.equals("")) {
            AndFilter filter = new AndFilter();
            filter.and(config.getGroupFilter());
            filter.and(new EqualsFilter(attr, value));
            return ldap.search("", filter.encode(),
                    new GroupAttributeMapper(config));
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    @RolesAllowed("system")
    @SuppressWarnings("unchecked")
    public List<Experimenter> searchByAttributes(String dn,
            String[] attributes, String[] values) {
        if (attributes.length != values.length) {
            return Collections.EMPTY_LIST;
        }
        AndFilter filter = new AndFilter();
        for (int i = 0; i < attributes.length; i++) {
            filter.and(new EqualsFilter(attributes[i], values[i]));
        }
        return ldap.search(new DistinguishedName(dn),
                filter.encode(), getContextMapper());
    }

    @Deprecated
    @RolesAllowed("system")
    @Transactional(readOnly = false)
    public void setDN(@NotNull Long experimenterID, String dn) {
        if (dn != null) {
            String lower = dn.toLowerCase();
            if ("true".equals(lower) || "false".equals(lower)) {
                sql.setUserLdapFlag(experimenterID, Boolean.valueOf(lower));
                return;
            }
        }
        throw new ApiUsageException("No longer supported. Change the DN via LDAP");
    }

    @RolesAllowed("system")
    public boolean getSetting() {
        return config.isEnabled();
    }

    // ~ System-only interface methods
    // =========================================================================


    //
    // WRITES
    //

    public void synchronizeLdapUser(String username) {

        if (!config.isSyncOnLogin()) {
            if (getBeanHelper().getLogger().isTraceEnabled()) {
                getBeanHelper().getLogger().trace("sync_on_login=false");
            }
            return;
        }

        Experimenter omeExp = iQuery.findByString(Experimenter.class, "omeName", username);

        Experimenter ldapExp = findExperimenter(username);
        String ldapDN = getContextMapper().getDn(ldapExp);
        DistinguishedName dn = new DistinguishedName(ldapDN);
        List<Long> ldapGroups = loadLdapGroups(username, dn);
        List<Long> omeGroupIds = sql.getLdapGroupsForUser(omeExp.getId());

        // All the omeGroups not in ldapGroups should be removed.
        modifyGroups(omeExp, omeGroupIds, ldapGroups, false);
        // All the ldapGroups not in omeGroups shoud be added.
        modifyGroups(omeExp, ldapGroups, omeGroupIds, true);

        List<String> fields = Arrays.asList(
                Experimenter.FIRSTNAME,
                Experimenter.MIDDLENAME,
                Experimenter.LASTNAME,
                Experimenter.EMAIL,
                Experimenter.INSTITUTION);

        for (String field : fields) {
            String fieldname = field.substring(field.indexOf("_")+1);
            String ome = (String) omeExp.retrieve(field);
            String ldap = (String) ldapExp.retrieve(field);

            if (ome == null) {
                if (ldap != null) {
                    getBeanHelper().getLogger().info(
                        String.format("Nulling %s for %s, was:",
                                fieldname, username, ome));
                    omeExp.putAt(field, ldap);
                }
            } else if (!ome.equals(ldap)) {
                getBeanHelper().getLogger().info(
                        String.format("Changing %s for %s: %s -> %s",
                                fieldname, username, ome, ldap));
                omeExp.putAt(field, ldap);
            }
        }
        iUpdate.flush();
    }

    /**
     * The ids in "minus" will be removed from the ids in "base" and then
     * the operation chosen by "add" will be run on them. This method
     * ignores all methods known by Roles.
     *
     * @param e
     * @param base
     * @param minus
     * @param add
     */
    private void modifyGroups(Experimenter e, Collection<Long> base,
            Collection<Long> minus, boolean add) {

        final Log log = getBeanHelper().getLogger();

        Set<Long> ids = new HashSet<Long>(base);
        ids.removeAll(minus);
        // Take no actions on system/user group.
        ids.remove(roles.getSystemGroupId());
        ids.remove(roles.getUserGroupId());

        if (ids.size() > 0) {
            log.info(String.format(
                    "%s groups for %s: %s",
                    add ? "Adding" : "Removing",
                    e.getOmeName(), ids));
            Set<ExperimenterGroup> grps = new HashSet<ExperimenterGroup>();
            for (Long id : ids) {
                grps.add(new ExperimenterGroup(id, false));
            }
            if (add) {
                provider.addGroups(e, grps.toArray(new ExperimenterGroup[0]));
            } else {
                provider.removeGroups(e, grps.toArray(new ExperimenterGroup[0]));
            }

            if (add) {
                // If we have just added groups, then it's possible that
                // the "user" groupis at the front of the list, in which
                // case we should assign another specific group.
                e = iQuery.get(Experimenter.class, e.getId());
                log.debug("sizeOfGroupExperimenterMap=" + e.sizeOfGroupExperimenterMap());
                if (e.sizeOfGroupExperimenterMap() > 1) {
                    GroupExperimenterMap primary = e.getGroupExperimenterMap(0);
                    GroupExperimenterMap next = e.getGroupExperimenterMap(1);
                    log.debug("primary=" + primary.parent().getId());
                    log.debug("next=" + next.parent().getId());
                    if (primary.parent().getId().equals(roles.getUserGroupId())) {
                        log.debug("calling setDefaultGroup");
                        provider.setDefaultGroup(e, next.parent());
                    }
                }
            }
        }
    }


    /**
     * Gets user from LDAP for checking him by requirements and setting his
     * details on DB
     *
     * @return {@link ome.system.ServiceFactory}
     */
    public boolean createUserFromLdap(String username, String password) {

        Experimenter exp = findExperimenter(username);
        String ldapDn = getContextMapper().getDn(exp);
        DistinguishedName dn = new DistinguishedName(ldapDn);

        boolean access = validatePassword(dn.toString(), password);

        if (access) {

            List<Long> groups = loadLdapGroups(username, dn);

            if (groups.size() == 0) {
                throw new ValidationException("No group found for: " + dn);
            }

            // Create the unloaded groups for creation
            Long gid = groups.remove(0);
            ExperimenterGroup grp1 = new ExperimenterGroup(gid, false);
            Set<Long> otherGroupIds = new HashSet<Long>(groups);
            ExperimenterGroup grpOther[] = new ExperimenterGroup[otherGroupIds.size()+1];

            int count = 0;
            for (Long id : otherGroupIds) {
                grpOther[count++] = new ExperimenterGroup(id, false);
            }
            grpOther[count] = new ExperimenterGroup(roles.getUserGroupId(), false);

            long uid = provider.createExperimenter(exp, grp1, grpOther);
        }
        return access;
    }

    static private final Pattern p = Pattern.compile(
        "^:(ou|" +
            "attribute|filtered_attribute|" +
            "dn_attribute|filtered_dn_attribute|" +
            "query|bean):(.*)$");

    public List<Long> loadLdapGroups(String username, DistinguishedName dn) {
        final String grpSpec = config.getNewUserGroup();
        final List<Long> groups = new ArrayList<Long>();

        if (!grpSpec.startsWith(":")) {
            // The default case is the original logic: use the spec as name
            groups.add(provider.createGroup(grpSpec, null, false));
            return groups; // EARLY EXIT!
        }

        final Matcher m = p.matcher(grpSpec);
        if (!m.matches()) {
            throw new ValidationException(grpSpec + " spec currently not supported.");
        }

        final String type = m.group(1);
        final String data = m.group(2);
        NewUserGroupBean bean = null;
        AttributeSet attrSet = null;

        if ("ou".equals(type)) {
            bean = new OrgUnitNewUserGroupBean(dn);
            attrSet = getAttributeSet(username, getContextMapper());
        } else if ("filtered_attribute".equals(type)) {
            bean = new AttributeNewUserGroupBean(data, true, false);
            attrSet = getAttributeSet(username, getContextMapper(data));
        } else if ("attribute".equals(type)) {
            bean = new AttributeNewUserGroupBean(data, false, false);
            attrSet = getAttributeSet(username, getContextMapper(data));
        } else if ("filtered_dn_attribute".equals(type)) {
            bean = new AttributeNewUserGroupBean(data, true, true);
            attrSet = getAttributeSet(username, getContextMapper(data));
        } else if ("dn_attribute".equals(type)) {
            bean = new AttributeNewUserGroupBean(data, false, true);
            attrSet = getAttributeSet(username, getContextMapper(data));
        } else if ("query".equals(type)) {
            bean = new QueryNewUserGroupBean(data);
            attrSet = getAttributeSet(username, getContextMapper());
        } else if ("bean".equals(type)) {
            bean = appContext.getBean(data, NewUserGroupBean.class);
            // Likely, this should be added to the API in order to allow bean
            // implementations to provide an attribute set.
            attrSet = getAttributeSet(username, getContextMapper());
        }

        groups.addAll(bean.groups(username, config, ldap, provider, attrSet));
        return groups;
    }

    private AttributeSet getAttributeSet(String username, PersonContextMapper mapper) {
        Experimenter exp = mapUserName(username, mapper);
        String dn = mapper.getDn(exp);
        AttributeSet attrSet = mapper.getAttributeSet(exp);
        attrSet.put("dn", dn); // For queries
        return attrSet;
    }

    //
    // READS
    //

    /**
     * Validates password for base. Base is user's DN. When context was created
     * successful specified requirements are valid.
     *
     * @return boolean
     */
    public boolean validatePassword(String dn, String password) {
        try {
            isAuthContext(dn, password);
            return true;
        } catch (SecurityViolation sv) {
            return false;
        }
    }

    public List<Map<String, Object>> lookupLdapAuthExperimenters() {
        final Map<Long, String> users = sql.getLdapUsers();
        final List<Map<String, Object>> rv = new ArrayList<Map<String, Object>>();
        for (Map.Entry<Long, String> entry :  users.entrySet()){
            String name = entry.getValue();
            Long id = entry.getKey();
            try {
                String dn = findDN(name);
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("dn", dn);
                map.put("experimenter_id", id);
                rv.add(map);
            } catch (ApiUsageException aue) {
                getBeanHelper().getLogger().warn(aue.getMessage());
            }
        }
        return rv;
    }

    public String lookupLdapAuthExperimenter(Long id) {
        boolean isLdap = sql.getUserLdapFlag(id);
        if (!isLdap) {
            return null;
        }
        String name = (String) iQuery.projection(
            "select e.omeName from Experimenter e where id = " + id, null).get(0)[0];
        return findDN(name);
    }

    // Helpers
    // =========================================================================

    private PersonContextMapper getContextMapper() {
        return new PersonContextMapper(config, getBase());
    }

    private PersonContextMapper getContextMapper(String attr) {
        return new PersonContextMapper(config, getBase(), attr);
    }

    /**
     * Creates the initial context with no connection request controls in order
     * to check authentication. If authentication fails, this method throws
     * a {@link SecurityViolation}.
     *
     * @return {@link javax.naming.ldap.LdapContext}
     */
    @SuppressWarnings("unchecked")
    private void isAuthContext(String username, String password) {

        Hashtable<String, String> env = new Hashtable<String, String>(5, 0.75f);
        try {
            env = (Hashtable<String, String>) ctx.getReadOnlyContext()
                    .getEnvironment();

            if (username != null && !username.equals("")) {
                env.put(Context.SECURITY_PRINCIPAL, username);
                if (password != null) {
                    env.put(Context.SECURITY_CREDENTIALS, password);
                }
            }
            new InitialLdapContext(env, null);
        } catch (AuthenticationException authEx) {
            throw new SecurityViolation("Authentication falilure! "
                    + authEx.toString());
        } catch (NamingException e) {
            throw new SecurityViolation("Naming exception! " + e.toString());
        }
    }

    private String getBase() {
        String base = null;
        try {
            base = ctx.getReadOnlyContext().getNameInNamespace();
        } catch (NamingException e) {
            throw new ApiUsageException(
                    "Cannot get BASE from ContextSource. Naming exception! "
                            + e.toString());
        }
        return base;

    }

}
