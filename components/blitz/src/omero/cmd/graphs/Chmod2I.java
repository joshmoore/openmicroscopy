/*
 * Copyright (C) 2014-2015 University of Dundee & Open Microscopy Environment.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package omero.cmd.graphs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

import ome.api.IAdmin;
import ome.model.IObject;
import ome.model.internal.Details;
import ome.model.internal.Permissions;
import ome.model.meta.Experimenter;
import ome.model.meta.ExperimenterGroup;
import ome.security.ACLVoter;
import ome.security.SystemTypes;
import ome.services.delete.Deletion;
import ome.services.graphs.GraphException;
import ome.services.graphs.GraphPathBean;
import ome.services.graphs.GraphPolicy;
import ome.services.graphs.GraphTraversal;
import ome.system.EventContext;
import ome.system.Login;
import ome.util.Utils;
import omero.cmd.Chmod2;
import omero.cmd.Chmod2Response;
import omero.cmd.HandleI.Cancel;
import omero.cmd.ERR;
import omero.cmd.Helper;
import omero.cmd.IRequest;
import omero.cmd.Response;

/**
 * Request to change the permissions on model objects, reimplementing {@link ChmodI}.
 * @author m.t.b.carroll@dundee.ac.uk
 * @since 5.1.2
 */
public class Chmod2I extends Chmod2 implements IRequest, WrappableRequest<Chmod2> {

    private static final ImmutableMap<String, String> ALL_GROUPS_CONTEXT = ImmutableMap.of(Login.OMERO_GROUP, "-1");

    private static final Set<GraphPolicy.Ability> REQUIRED_ABILITIES = ImmutableSet.of(GraphPolicy.Ability.CHMOD);

    private static final String PERMITTED_CLASS = ExperimenterGroup.class.getName();

    private final ACLVoter aclVoter;
    private final SystemTypes systemTypes;
    private final GraphPathBean graphPathBean;
    private final Deletion deletionInstance;
    private GraphPolicy graphPolicy;  /* not final because of adjustGraphPolicy */
    private final SetMultimap<String, String> unnullable;

    private long perm1;
    private List<Function<GraphPolicy, GraphPolicy>> graphPolicyAdjusters = new ArrayList<Function<GraphPolicy, GraphPolicy>>();
    private Helper helper;
    private GraphTraversal graphTraversal;
    private Set<Long> acceptableGroups;

    int targetObjectCount = 0;
    int deletedObjectCount = 0;
    int changedObjectCount = 0;

    /**
     * Construct a new <q>chmod</q> request; called from {@link GraphRequestFactory#getRequest(Class)}.
     * @param aclVoter ACL voter for permissions checking
     * @param systemTypes for identifying the system types
     * @param graphPathBean the graph path bean to use
     * @param deletionInstance a deletion instance for deleting files
     * @param graphPolicy the graph policy to apply for chmod
     * @param unnullable properties that, while nullable, may not be nulled by a graph traversal operation
     */
    public Chmod2I(ACLVoter aclVoter, SystemTypes systemTypes, GraphPathBean graphPathBean, Deletion deletionInstance,
            GraphPolicy graphPolicy, SetMultimap<String, String> unnullable) {
        this.aclVoter = aclVoter;
        this.systemTypes = systemTypes;
        this.graphPathBean = graphPathBean;
        this.deletionInstance = deletionInstance;
        this.graphPolicy = graphPolicy;
        this.unnullable = unnullable;
    }

    @Override
    public Map<String, String> getCallContext() {
        return new HashMap<String, String>(ALL_GROUPS_CONTEXT);
    }

    @Override
    public void init(Helper helper) {
        this.helper = helper;
        helper.setSteps(dryRun ? 1 : 3);

        try {
            perm1 = (Long) Utils.internalForm(Permissions.parseString(permissions));
        } catch (RuntimeException e) {
            throw helper.cancel(new ERR(), e, "bad-permissions");
        }

        /* if the current user is not an administrator then find of which groups the target user is a owner */
        final EventContext eventContext = helper.getEventContext();

        if (eventContext.isCurrentUserAdmin()) {
            acceptableGroups = null;
        } else {
            final Long userId = eventContext.getCurrentUserId();
            final IAdmin iAdmin = helper.getServiceFactory().getAdminService();
            acceptableGroups = ImmutableSet.copyOf(iAdmin.getLeaderOfGroupIds(new Experimenter(userId, false)));
        }

        final List<ChildOptionI> childOptions = ChildOptionI.castChildOptions(this.childOptions);

        if (childOptions != null) {
            for (final ChildOptionI childOption : childOptions) {
                childOption.init();
            }
        }

        GraphPolicy graphPolicyWithOptions = graphPolicy;

        graphPolicyWithOptions = ChildOptionsPolicy.getChildOptionsPolicy(graphPolicyWithOptions, childOptions, REQUIRED_ABILITIES);

        for (final Function<GraphPolicy, GraphPolicy> adjuster : graphPolicyAdjusters) {
            graphPolicyWithOptions = adjuster.apply(graphPolicyWithOptions);
        }
        graphPolicyAdjusters = null;

        final boolean isToGroupReadable = Utils.toPermissions(perm1).isGranted(Permissions.Role.GROUP, Permissions.Right.READ);

        if (isToGroupReadable) {
            /* for permissions change to not-private, no changes are required based on policy rules */
            graphPolicyWithOptions = new BaseGraphPolicyAdjuster(graphPolicyWithOptions) {
                @Override
                protected boolean isBlockedFromAdjustment(Details object) {
                    return true;
                }
            };
        } else {
            /* for permissions change to private, objects not in private groups must have policy rule checks for deletion */
            graphPolicyWithOptions = new BaseGraphPolicyAdjuster(graphPolicyWithOptions) {
                private final Map<Long, Boolean> isGroupReadableById = new HashMap<Long, Boolean>();

                @Override
                public void noteDetails(Session session, IObject object, String realClass, long id) {
                    /* note whether groups are private */
                    if (object instanceof ExperimenterGroup && !isGroupReadableById.containsKey(id)) {
                        final ExperimenterGroup group = (ExperimenterGroup) session.get(ExperimenterGroup.class, id);
                        final Permissions permissions = group.getDetails().getPermissions();
                        final boolean isGroupReadable = permissions.isGranted(Permissions.Role.GROUP, Permissions.Right.READ);
                        isGroupReadableById.put(id, isGroupReadable);
                    }
                    super.noteDetails(session, object, realClass, id);
                }

                @Override
                protected boolean isBlockedFromAdjustment(Details object) {
                    /* for groups that are already private, no changes are required based on policy rules */
                    final Long groupId = object.subject instanceof ExperimenterGroup ? object.subject.getId() : object.groupId;
                    return Boolean.FALSE.equals(isGroupReadableById.get(groupId));
                }
            };
        }

        graphTraversal = new GraphTraversal(helper.getSession(), eventContext, aclVoter, systemTypes, graphPathBean, unnullable,
                graphPolicyWithOptions, dryRun ? new NullGraphTraversalProcessor(REQUIRED_ABILITIES) : new InternalProcessor());
    }

    @Override
    public Object step(int step) throws Cancel {
        helper.assertStep(step);
        try {
            switch (step) {
            case 0:
                /* if targetObjects were an IObjectList then this would need IceMapper.reverse */
                final SetMultimap<String, Long> targetMultimap = HashMultimap.create();
                for (final Entry<String, List<Long>> oneClassToTarget : targetObjects.entrySet()) {
                    String className = oneClassToTarget.getKey();
                    if (className.lastIndexOf('.') < 0) {
                        className = graphPathBean.getClassForSimpleName(className).getName();
                    }
                    for (final long id : oneClassToTarget.getValue()) {
                        targetMultimap.put(className, id);
                        targetObjectCount++;
                    }
                }
                final Entry<SetMultimap<String, Long>, SetMultimap<String, Long>> plan =
                        graphTraversal.planOperation(helper.getSession(), targetMultimap, true);
                return Maps.immutableEntry(plan.getKey(), GraphUtil.arrangeDeletionTargets(helper.getSession(), plan.getValue()));
            case 1:
                graphTraversal.unlinkTargets(false);
                return null;
            case 2:
                graphTraversal.processTargets();
                return null;
            default:
                final Exception e = new IllegalArgumentException("model object graph operation has no step " + step);
                throw helper.cancel(new ERR(), e, "bad-step");
            }
        } catch (GraphException ge) {
            final omero.cmd.GraphException graphERR = new omero.cmd.GraphException();
            graphERR.message = ge.message;
            throw helper.cancel(graphERR, ge, "graph-fail");
        } catch (Throwable t) {
            throw helper.cancel(new ERR(), t, "graph-fail");
        }
    }

    @Override
    public void finish() {
    }

    @Override
    public void buildResponse(int step, Object object) {
        helper.assertResponse(step);
        if (step == 0) {
            /* if the results object were in terms of IObjectList then this would need IceMapper.map */
            final Entry<SetMultimap<String, Long>, SetMultimap<String, Long>> result =
                    (Entry<SetMultimap<String, Long>, SetMultimap<String, Long>>) object;
            if (!dryRun) {
                try {
                    deletionInstance.deleteFiles(GraphUtil.trimPackageNames(result.getValue()));
                } catch (Exception e) {
                    helper.cancel(new ERR(), e, "file-delete-fail");
                }
            }
            final Map<String, List<Long>> changedObjects = new HashMap<String, List<Long>>();
            final Map<String, List<Long>> deletedObjects = new HashMap<String, List<Long>>();
            for (final Entry<String, Collection<Long>> oneChangedClass : result.getKey().asMap().entrySet()) {
                final String className = oneChangedClass.getKey();
                final Collection<Long> ids = oneChangedClass.getValue();
                changedObjectCount += ids.size();
                changedObjects.put(className, new ArrayList<Long>(ids));
            }
            for (final Entry<String, Collection<Long>> oneDeletedClass : result.getValue().asMap().entrySet()) {
                final String className = oneDeletedClass.getKey();
                final Collection<Long> ids = oneDeletedClass.getValue();
                deletedObjectCount += ids.size();
                deletedObjects.put(className, new ArrayList<Long>(ids));
            }
            final Chmod2Response response = new Chmod2Response(changedObjects, deletedObjects);
            helper.setResponseIfNull(response);
            helper.info("in " + (dryRun ? "mock " : "") + "chmod to " + permissions + " of " + targetObjectCount +
                    ", changed " + changedObjectCount + " and deleted " + deletedObjectCount + " in total");
        }
    }

    @Override
    public Response getResponse() {
        return helper.getResponse();
    }

    @Override
    public void copyFieldsTo(Chmod2 request) {
        GraphUtil.copyFields(this, request);
        request.permissions = permissions;
    }

    @Override
    public void adjustGraphPolicy(Function<GraphPolicy, GraphPolicy> adjuster) {
        if (graphPolicyAdjusters == null) {
            throw new IllegalStateException("request is already initialized");
        } else {
            graphPolicyAdjusters.add(adjuster);
        }
    }

    @Override
    public GraphPolicy.Action getActionForStarting() {
        return GraphPolicy.Action.INCLUDE;
    }

    @Override
    public Map<String, List<Long>> getStartFrom(Response response) {
        return ((Chmod2Response) response).includedObjects;
    }

    /**
     * A <q>chmod</q> processor that updates model objects' permissions.
     * @author m.t.b.carroll@dundee.ac.uk
     * @since 5.1.2
     */
    private final class InternalProcessor extends BaseGraphTraversalProcessor {

        private final Logger LOGGER = LoggerFactory.getLogger(InternalProcessor.class);

        public InternalProcessor() {
            super(helper.getSession());
        }

        @Override
        public void processInstances(String className, Collection<Long> ids) throws GraphException {
            final String update = "UPDATE " + className + " SET details.permissions.perm1 = :permissions WHERE id IN (:ids)";
            final int count =
                    session.createQuery(update).setParameter("permissions", perm1).setParameterList("ids", ids).executeUpdate();
            if (count != ids.size()) {
                LOGGER.warn("not all the objects of type " + className + " could be processed");
            }
        }

        @Override
        public Set<GraphPolicy.Ability> getRequiredPermissions() {
            return REQUIRED_ABILITIES;
        }

        @Override
        public void assertMayProcess(String className, long id, Details details) throws GraphException {
            if (!PERMITTED_CLASS.equals(className)) {
                /* chmod may be done only to groups */
                throw new GraphException("may process objects only of type " + PERMITTED_CLASS);
            }
            if (!(acceptableGroups == null || acceptableGroups.contains(id))) {
                throw new GraphException("user is not an owner of group " + id);
            }
        }
    }
}
