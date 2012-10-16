#!/usr/bin/env python

"""
   Integration test focused on the Repository API

   Copyright 2009 Glencoe Software, Inc. All rights reserved.
   Use is subject to license terms supplied in LICENSE.txt

"""

import unittest, time, os, sys
import integration.library as lib

import omero
import omero.cli
import omero.java

from omero.rtypes import *
from omero.util.temp_files import create_path

class TestRepository(lib.ITest):

    def testBasicUsage(self):

        test_file = "FIXME.dv"
        remote_file = "/root/dir1/test.dv"

        write_start = time.time()

        repoMap = self.client.sf.sharedResources().repositories()
        self.assert_( len(repoMap.descriptions) > 1 )
        self.assert_( len(repoMap.proxies) > 1 )

        repoPrx = repoMap.proxies[0]
        self.assert_( repoPrx ) # Could be None

        # This is a write-only (no read, no config)
        # version of this service.
        if False:
            rawFileStore = repoPrx.write(remote_file)
            try:
                offset = 0
                file = open(test_file,"rb")
                try:
                    while True:
                        block = file.read(block_size)
                        if not block:
                            break
                        rawFileStore.write(block, offset, len(block))
                        offset += len(block)
                finally:
                    file.close()
            finally:
                rawFileStore.close()

            write_end = time.time()

            # Check the SHA1
            file = repoPrx.load(remote_file)
            sha1_remote = file.sha1.val
            sha1_local = self.client.sha1(test_file)


        self.fail("HOW ARE WE CHECKING SHA1 HERE")


        read_start = time.time()

        #
        # Raw pixels
        #
        rawPixelsStore = repoPrx.pixels(remote_file)
        try:
            pass
        finally:
            rawPixelsStore.close()

        read_end = time.time()

        #
        # Rendering
        #

        renderingEngine = repoPrx.render(remote_file)
        try:
            planeDef = omero.romio.PlaneDef()
            planeDef.z = 0
            planeDef.t = 0
            rgbBuffer = renderingEngine.render(planeDef)
        finally:
            renderingEngine.close()

        thumbnailStore = repoPrx.thumbs(remote_file)
        thumbnailStore.close()
        rawFileStore = repoPrx.read(remote_file)
        rawFileStore.close()
        repoPrx.rename(remote_file, remote_file + ".old")
        repoPrx.delete(remote_file + ".old")

    def testSanityCheckRepos(self):
        # Repos should behave sensibly when it comes
        # to listing their path and objects as well
        # as what items they return.
        repoMap = self.client.sf.sharedResources().repositories()
        managed = None
        public = None
        script = None
        for obj, prx in zip(repoMap.descriptions, repoMap.proxies):
            if prx:
                root = prx.root()
                assert ".omero" not in prx.list(root.path.val + root.name.val)
                assert ".omero" not in \
                        [x.name.val for x in prx.listFiles(root.path.val + root.name.val)]
                for x in ("id", "path", "name"):
                    a = getattr(obj, x)
                    b = getattr(root, x)
                    if a is None:
                        self.assertEquals(a, b)
                    else:
                        self.assertEquals(a.val, b.val)

    def testManagedRepo(self):
        mrepo = self.getManagedRepo(self.client)

        # Create a file in the repo
        path = mrepo.getCurrentRepoDir(["testManagedRepo.txt"])[0]
        base = os.path.dirname(path)
        mrepo.makeDir(base)
        rfs = mrepo.file(path, "rw")
        rfs.write("hi".encode("utf-8"), 0, 2)
        rfs.close()

        # Query it
        assert "testManagedRepo.txt" in mrepo.list(base)[0]
        mime = mrepo.mimetype(path)

        # Register the file. This is currently necessary,
        # but likely will need to be done one rfs.close()
        obj = mrepo.register(path, omero.rtypes.rstring(mime))

        # Now we try to look it up with __redirect
        rfs = self.client.sf.createRawFileStore()
        rfs.setFileId(obj.id.val)
        self.assertEquals("hi", unicode(rfs.read(0, 2), "utf-8"))
        rfs.close()

    def getManagedRepo(self, client=None):
        if client is None:
            client = self.client
        repoMap = client.sf.sharedResources().repositories()
        prx = None
        found = False
        for prx in repoMap.proxies:
            if not prx: continue
            prx = omero.grid.ManagedRepositoryPrx.checkedCast(prx)
            if prx:
                found = True
                break
        self.assert_(found)
        return prx

    #
    # CLI-based import tests
    # ===============================================================
    # The following tests use import_image to invoke the cli importer
    # and then verify various expectations about import. Once a
    # py-based import is also possible using the MRepo API, each test
    # could simultaneously test the import via that method. This will
    # verify that all functionality is available in the API and that
    # not too much has been encoded in the client-side ImportLibrary.
    #

    def bfconvert(self, source, target):

        dist_dir = self.get_dist_dir()
        client_dir = dist_dir / "lib" / "client"
        classpath = [file.abspath() for file in client_dir.files("*.jar")]
        xargs = ["-cp", os.pathsep.join(classpath)]
        prog = "loci.formats.tools.ImageConverter"

        p = omero.java.popen([prog, source, target], \
                xargs=xargs, stdout=sys.stdout, stderr=sys.stderr)

        ret_val = p.wait()
        self.assertEquals(0, ret_val)

    def makeFake(self):
        dir = create_path("fake_test_", folder=True)
        fake = dir / "test.fake"
        fake.touch()
        return fake

    def makeMultiFake(self):
        """
        Creates a .fake file and then uses Bio-Formats
        to convert it to a multi-file format (ICS)
        """
        fake = self.makeFake()
        ids = fake.__class__(str(fake)[:-4]+"ids")
        ics = fake.__class__(str(fake)[:-4]+"ics")

        self.bfconvert(fake, ids)
        self.assertTrue(ids.exists())
        self.assertTrue(ics.exists())

        return ics

    def doImport(self, path):
        return self.import_image(path)

    def testCliImport(self):
        # Simply verify the methods above.
        fake = self.makeFake()
        self.doImport(fake)

    def testOriginalFilesCreated(self):
        # The import should link an
        # image to all the uploaded
        # files.
        multi_fake = self.makeMultiFake()
        pix_ids = self.doImport(multi_fake)
        images = self.images_from_pix_ids(pix_ids)
        self.assertEquals(1, len(images))
        image = images[0]
        pixels = image.getPrimaryPixels()
        self.assertEquals(2, pixels.sizeOfPixelsFileMaps())

if __name__ == '__main__':
    unittest.main()
