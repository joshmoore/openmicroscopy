package ome.server.utests;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ome.model.internal.Permissions;
import ome.system.EventContext;

/**
 * Simple {@link EventContext} implementation which returns default values.
 */
public class MockEventContext implements EventContext {

    public static MockEventContext admin() {
        MockEventContext ec = new MockEventContext();
        ec.gname = "system";
        ec.gid = 0L;
        ec.uid = 0L;
        ec.uname = "root";
        ec.admin = true;
        return ec;
    }

    public Long eid = 100L;
    public String etype = "test";
    public Long gid = 100L;
    public String gname = "my-group";
    public Permissions perms = Permissions.READ_ONLY;
    public Long sid = 100L;
    public String uuid = UUID.randomUUID().toString();
    public Long share = null;
    public Long uid = 100L;
    public String uname = "my-user";
    public List<Long> leader = new ArrayList<Long>();
    public List<Long> member = new ArrayList<Long>();
    public boolean admin = false;
    public boolean readOnly = true;

    @Override
    public Long getCurrentEventId() {
        return eid;
    }

    @Override
    public String getCurrentEventType() {
        return etype;
    }

    @Override
    public Long getCurrentGroupId() {
        return gid;
    }

    @Override
    public String getCurrentGroupName() {
        return gname;
    }

    @Override
    public Permissions getCurrentGroupPermissions() {
        return perms;
    }

    @Override
    public Long getCurrentSessionId() {
        return sid;
    }

    @Override
    public String getCurrentSessionUuid() {
        return uuid;
    }

    @Override
    public Long getCurrentShareId() {
        return share;
    }

    @Override
    public Long getCurrentUserId() {
        return uid;
    }

    @Override
    public String getCurrentUserName() {
        return uname;
    }

    @Override
    public List<Long> getLeaderOfGroupsList() {
        return leader;
    }

    @Override
    public List<Long> getMemberOfGroupsList() {
        return member;
    }

    @Override
    public boolean isCurrentUserAdmin() {
        return admin;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }
    
}