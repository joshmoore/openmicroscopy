#!/usr/bin/env python

"""
   Integration test focused on the omero.api.ISession interface
   a running server.

   Copyright 2008 Glencoe Software, Inc. All rights reserved.
   Use is subject to license terms supplied in LICENSE.txt

"""
import unittest
import integration.library as lib
import omero
import omero_Constants_ice
from omero_model_PixelsI import PixelsI
from omero_model_ImageI import ImageI
from omero_model_DatasetI import DatasetI
from omero_model_ExperimenterI import ExperimenterI
from omero_model_ExperimenterGroupI import ExperimenterGroupI
from omero_model_GroupExperimenterMapI import GroupExperimenterMapI
from omero_model_DatasetImageLinkI import DatasetImageLinkI
from omero.rtypes import *

class TestISession(lib.ITest):

    def testBasicUsage(self):
        isess = self.client.sf.getSessionService()
        admin = self.client.sf.getAdminService()
        sid = admin.getEventContext().sessionUuid

    def testManuallyClosingOwnSession(self):
        client = self.new_client()
        client.killSession()

    def testCreateSessionForUser(self):
        p = omero.sys.Parameters()
        p.theFilter = omero.sys.Filter()
        p.theFilter.limit = rint(1)
        user = self.root.sf.getQueryService().findByQuery("""
            select e from Experimenter e where e.id > 0 and e.omeName != 'guest'
            """, p)
        p = omero.sys.Principal()
        p.name  = user.omeName.val
        p.group = "user"
        p.eventType = "Test"
        sess  = self.root.sf.getSessionService().createSessionWithTimeout(p, 10000) # 10 secs

        client = omero.client()
        user_sess = client.createSession(sess.uuid,sess.uuid)
        new_uuid   = user_sess.getAdminService().getEventContext().sessionUuid
        self.assert_( sess.uuid.val == new_uuid )
        client.closeSession()

    def testJoinSession_Helper(self):
        test_user = self.new_user()
        
        client = omero.client()
        sf = client.createSession(test_user.omeName.val,"ome")
        a = sf.getAdminService()
        return a.getEventContext().sessionUuid
        
    def testJoinSession(self):
        suuid = self.testJoinSession_Helper()
        
        c1 = omero.client()
        sf1 = c1.joinSession(suuid)
        a1 = sf1.getAdminService()
        print a1.getEventContext().sessionUuid
            
        
## Removing test for 'guest' user. 
## This currently fails but there is some question
## as to whether we should have a guest user.
##
##    def testCreateSessionForGuest(self):
##        p = omero.sys.Principal()
##        p.name  = "guest"
##        p.group = "guest"
##        p.eventType = "guest"
##        sess  = self.root.sf.getSessionService().createSessionWithTimeout(p, 10000) # 10 secs
##
##        guest_client = omero.client()
##        guest_sess = guest_client.createSession("guest",sess.uuid)
##        guest_client.closeSession()

    def testCreationDestructionClosing(self):
        c1 = omero.client()
        s1 = c1.createSession()
        s1.detachOnDestroy()
        uuid = s1.ice_getIdentity().name

        # Intermediate "disrupter"
        c2 = omero.client()
        s2 = c2.createSession(uuid, uuid)
        s2.closeOnDestroy()
        s2.getAdminService().getEventContext()
        c2.closeSession()

        # 1 should still be able to continue
        s1.getAdminService().getEventContext()

        # Now if s1 exists another session should be able to connect
        c1.closeSession()
        c3 = omero.client()
        s3 = c3.createSession(uuid, uuid)
        s3.closeOnDestroy()
        s3.getAdminService().getEventContext()
        c3.closeSession()

        # Now a connection should not be possible
        c4 = omero.client()
        import Glacier2
        self.assertRaises(Glacier2.PermissionDeniedException, c4.createSession, uuid, uuid);

    def testSimpleDestruction(self):
        c = omero.client()
        c.ic.getImplicitContext().put(omero.constants.CLIENTUUID,"SimpleDestruction")
        s = c.createSession()
        s.closeOnDestroy()
        c.closeSession()

    def testGetMySessionsTicket1975(self):
        svc = self.client.sf.getSessionService()
        svc.getMyOpenSessions()
        svc.getMyOpenAgentSessions("OMERO.web")
        svc.getMyOpenClientSessions()

    def testTicket2196SetSecurityContext(self):
        ec = self.client.sf.getAdminService().getEventContext()
        exp0 = omero.model.ExperimenterI(ec.userId, False)
        grp0 = omero.model.ExperimenterGroupI(ec.groupId, False)
        grp1 = self.new_group([exp0])

        # Change: should pass
        self.client.sf.setSecurityContext(grp1)

        # Make a stateful service, and change again
        rfs = self.client.sf.createRawFileStore()
        try:
            self.client.sf.setSecurityContext(grp0)
            self.fail("sec vio")
        except omero.SecurityViolation, sv:
            pass # Good
        rfs.close()

        # Service is now closed, should be ok
        self.client.sf.setSecurityContext(grp1)

if __name__ == '__main__':
    unittest.main()
