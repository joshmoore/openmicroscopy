package ome.server.utests;

import java.util.ArrayList;
import java.util.List;

import ome.model.internal.Permissions;
import ome.system.EventContext;

class MockEventContext implements EventContext {

    @Override
    public Long getCurrentEventId() {
        return -1l;
    }

    @Override
    public String getCurrentEventType() {
        return "test";
    }

    @Override
    public Long getCurrentGroupId() {
        return -1l;
    }

    @Override
    public String getCurrentGroupName() {
        return "foo";
    }

    @Override
    public Permissions getCurrentGroupPermissions() {
        return Permissions.READ_ONLY;
    }

    @Override
    public Long getCurrentSessionId() {
        return -1l;
    }

    @Override
    public String getCurrentSessionUuid() {
        return "Fake-uuid";
    }

    @Override
    public Long getCurrentShareId() {
        return null;
    }

    @Override
    public Long getCurrentUserId() {
        return -1l;
    }

    @Override
    public String getCurrentUserName() {
        return "bar";
    }

    @Override
    public List<Long> getLeaderOfGroupsList() {
        return new ArrayList<Long>();
    }

    @Override
    public List<Long> getMemberOfGroupsList() {
        return new ArrayList<Long>();
    }

    @Override
    public boolean isCurrentUserAdmin() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
    
}