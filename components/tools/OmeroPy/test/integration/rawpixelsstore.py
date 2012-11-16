#!/usr/bin/env python

"""
   Tests for the stateful RawPixelsStore service.

   Copyright 2011 Glencoe Software, Inc. All rights reserved.
   Use is subject to license terms supplied in LICENSE.txt

"""

import omero
import unittest
import integration.library as lib

from omero.rtypes import rstring, rlong, rint
from omero.util.concurrency import get_event
from omero.util.tiles import *
from binascii import hexlify as hex


class TestRPS(lib.ITest):

    def check_pix(self, pix):
        pix = self.query.get("Pixels", pix.id.val)
        self.assert_(pix.sha1.val != "")
        rps = self.client.sf.createRawPixelsStore()
        try:
            rps.setPixelsId(pix.id.val, True)
            sha1 = hex(rps.calculateMessageDigest())
            self.assertEquals(sha1, pix.sha1.val)
        finally:
            rps.close()

    def testTicket4737WithClose(self):
        pix = self.pix()
        rps = self.client.sf.createRawPixelsStore()
        try:
            rps.setPixelsId(pix.id.val, True)
            self.write(pix, rps)
        finally:
            rps.close() # save is automatic
        self.check_pix(pix)

    def testTicket4737WithSave(self):
        pix = self.pix()
        rps = self.client.sf.createRawPixelsStore()
        try:
            rps.setPixelsId(pix.id.val, True)
            self.write(pix, rps)
            pix = rps.save()
            self.check_pix(pix)
        finally:
            rps.close()
        self.check_pix(pix)

    def testTicket4737WithForEachTile(self):
        pix = self.pix()
        class Iteration(TileLoopIteration):
            def run(self, data, z, c, t, x, y, tileWidth, tileHeight, tileCount):
                data.setTile([5]*tileWidth*tileHeight, z, c, t, x, y, tileWidth, tileHeight)

        loop = RPSTileLoop(self.client.getSession(), pix)
        loop.forEachTile(256, 256, Iteration())
        pix = self.query.get("Pixels", pix.id.val)
        self.check_pix(pix)

    def testBigPlane(self):
        pix = self.pix(x=4000, y=4000, z=1, t=1, c=1)
        rps = self.client.sf.createRawPixelsStore()
        try:
            rps.setPixelsId(pix.id.val, True)
            self.write(pix, rps)
        finally:
            rps.close()
        self.check_pix(pix)

    def testRomioToPyramid(self):
        """
        Here we create a pixels that is not big,
        then modify its metadata so that it IS big,
        in order to trick the service into throwing
        us a MissingPyramidException
        """
        from omero.util import concurrency
        pix = self.missing_pyramid(self.root)
        rps = self.root.sf.createRawPixelsStore()
        try:
            # First execution should certainly fail
            try:
                rps.setPixelsId(pix.id.val, True)
                fail("Should throw!")
            except omero.MissingPyramidException, mpm:
                self.assertEquals(pix.id.val, mpm.pixelsID)

            # Eventually, however, it should be generated
            i = 10
            success = False
            while i > 0 and not success:
                try:
                    rps.setPixelsId(pix.id.val, True)
                    success = True
                except omero.MissingPyramidException, mpm:
                    self.assertEquals(pix.id.val, mpm.pixelsID)
                    backOff = mpm.backOff/1000
                    event = concurrency.get_event("testRomio")
                    event.wait(backOff) # seconds
                i -=1
            self.assert_(success)
        finally:
            rps.close()

    def testRomioToPyramidWithNegOne(self):
        """
        Here we try the above but pass omero.group:-1
        to see if we can cause an exception.
        """
        all_context = {"omero.group":"-1"}

        from omero.util import concurrency
        pix = self.missing_pyramid(self.root)
        rps = self.root.sf.createRawPixelsStore(all_context)
        try:
            # First execution should certainly fail
            try:
                rps.setPixelsId(pix.id.val, True, all_context)
                fail("Should throw!")
            except omero.MissingPyramidException, mpm:
                self.assertEquals(pix.id.val, mpm.pixelsID)

            # Eventually, however, it should be generated
            i = 10
            success = False
            while i > 0 and not success:
                try:
                    rps.setPixelsId(pix.id.val, True, all_context)
                    success = True
                except omero.MissingPyramidException, mpm:
                    self.assertEquals(pix.id.val, mpm.pixelsID)
                    backOff = mpm.backOff/1000
                    event = concurrency.get_event("testRomio")
                    event.wait(backOff) # seconds
                i -=1
            self.assert_(success)
        finally:
            rps.close()

    ## Some combination of calls on RawPixelsStore
    ## is leaving the pixels object in a bad state.
    ## It looks as if some code path is accessing
    ## the file without obtaining the lock.

    def rnd_pixel(self, client, pix):
        re = client.sf.createRenderingEngine()
        try:
            re.lookupPixels(pix.id.val)
            re.resetDefaults()
            re.lookupPixels(pix.id.val)
            re.lookupRenderingDef(pix.id.val)
            re.getPixels()
        finally:
            re.close()

    def test9904WithClose(self):

        # Create a session that lives for a limited time
        event = get_event("test9904")
        client = self.client

        pix1 = self.pix(x=4000, y=4000, z=1, t=1, c=1, client=client)
        pix2 = self.pix(x=4000, y=4000, z=1, t=1, c=1, client=client)
        rps = client.sf.createRawPixelsStore()
        try:
            rps.setPixelsId(pix1.id.val, True)
            w, h = rps.getTileSize()
        finally:
            # rps.save() # Leads to LockTimeout on rnd_pixels
            rps.close() # Leads to ResourceError on rnd_pixels

        try:
            self.rnd_pixel(client, pix1)
        except (omero.ResourceError, omero.LockTimeout):
            pass # i.e. this pyramid is corrupt

    def test9904WithTimeout(self):
        # This method leaves garbage on the server side
        # -rw-r--r-- 1 hudson hudson          0 2012-11-16 08:17 .4434_pyramid.pyr_lock
        # -rw-r--r-- 1 hudson hudson       4772 2012-11-16 08:17 .4434_pyramid971798142620109656.tmp

        # Create a session that lives for a limited time
        event = get_event("test9904")
        def new_short_client():
            ec = self.client.sf.getAdminService().getEventContext()
            principal = omero.sys.Principal(ec.userName, ec.groupName, "Test")
            root_svc = self.root.sf.getSessionService()
            session = root_svc.createSessionWithTimeout(principal, 5*1000)
            return self.new_client(session = session.uuid.val)

        # Create a pyramid during one session which times out.
        client = new_short_client()
        try:
            pix1 = self.pix(x=4000, y=4000, z=1, t=1, c=1, client=client)
            #2 pix2 = self.pix(x=4000, y=4000, z=1, t=1, c=1, client=client)
            rps = client.sf.createRawPixelsStore()
            try:
                rps.setPixelsId(pix1.id.val, True)
                w, h = rps.getTileSize()
                size = w * h
                buf = [0] * size
                rps.setTile(buf, 0, 0, 0, 0, 0, w, h)

                event.wait(5)

                #2 rps.setPixelsId(pix2.id.val, True)
            finally:
                try:
                    rps.close()
                except omero.ResourceError:
                    print "Got ResourceError"
                    pass # Excepted since not enough tiles
        finally:
            client.__del__()

        # Now try to access from a second client
        client = new_short_client()
        try:

            tb = client.sf.createThumbnailStore()
            try:
                tb.getThumbnailByLongestSideSet(rint(64), [pix1.id.val])
            finally:
                tb.close()
        finally:
            client.__del__()

    def test9904WithConcurrency(self):

        # Create a session that lives for a limited time
        event = get_event("test9904-2")
        client = self.client
        import threading

        pix1 = self.pix(x=4000, y=4000, z=1, t=1, c=1, client=client)
        class T1(threading.Thread):

            def run(this):
                try:
                    rps = client.sf.createRawPixelsStore()
                    try:
                        rps.setPixelsId(pix1.id.val, True)
                        w, h = rps.getTileSize()
                        size = w * h
                        buf = [0] * size
                        for x in range(10): # Roughly a whole row
                            if event.is_set():
                                break
                            rps.setTile(buf, 0, 0, 0, x, 0, w, h)
                            event.wait(1)
                    finally:
                        rps.close()
                except:
                    import traceback
                    traceback.print_exc()

        class T2(threading.Thread):

            def run(this):
                try:
                    while not event.is_set():
                        try:
                            self.rnd_pixel(client, pix1)
                        except omero.LockTimeout:
                            pass # This is the exception we'd expect
                except:
                    import traceback
                    traceback.print_exc()

        t1 = T1()
        t2 = T2()
        t1.start()
        t2.start()
        event.wait(4)
        event.set()
        t2.join()
        t1.join()


if __name__ == '__main__':
    unittest.main()
