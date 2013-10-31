#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2013 University of Dundee & Open Microscopy Environment.
# All rights reserved.
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

"""
Python implementation of ome.formats.importer.ImportLibrary

omero.importer.ImportLibrary permits the transfer of a collection
of files to the server for import as 1 or more filsets (as determined
by the server).

"""

import locale
import logging
import platform

from omero.callbacks import CmdCallbackI
from omero.cmd import ERR
from omero.grid import ImportResponse
from omero.grid import ImportSettings
from omero.grid import ManagedRepositoryPrx
from omero.model import ChecksumAlgorithmI
from omero.model import FilesetVersionInfoI
from omero.rtypes import rbool
from omero.rtypes import rstring
from omero_version import omero_version


class ImportCallback(CmdCallbackI):

    def __init__(self, *args, **kwargs):
        super(ImportCallback, self).__init__(*args, **kwargs)
        self.log = logging.getLogger("omero.importer.ImportCallback")

    def step(self, step, total, current=None):
        if step == 1:
            self.log.info("Metadata imported")
        elif step == 2:
            self.log.info("PixelData processed")
        elif step == 3:
            self.log.info("Thumbnails generated")
        elif step == 4:
            self.log.info("Metadata processed")
        elif step == 5:
            self.log.info("Objects returned")


class Task(object):

    def __init__(self, library):
        self.log = logging.getLogger("omero.importer.%s" % self.__class__.__name__)
        self.library = library

    def __call__():
        raise Exception("No implemention")


class UploadTask(Task):

    def __init__(self, library, paths):
        super(UploadTask, self).__init__(library)
        self.paths = paths

    def __call__(self):

        self.proc = self.library.create_import(self.paths)
        self.checksums = []

        for i in range(len(self.paths)):
            chksum = self.library.upload_file(self.proc, self.paths, i)
            self.checksums.append(chksum)

        # Can throw omero.ChecksumValidationException
        self.proc.verifyUpload(self.checksums)
        return self.proc


class FilesetTask(Task):

    def __init__(self, library, upload, index, settings, loops=1000, millisWait=60*60):
        super(FilesetTask, self).__init__(library)
        self.upload = upload
        self.index = index
        self.settings = self.create_settings()
        self.loops = loops
        self.milliswait = millisWait

        self.fileset = upload.proc.createFileset(0, settings)

    def __call__(self):
        self.handle = self.upload.proc.startImport()
        self.req = self.handle.getRequest()
        self.fs = self.req.activity.getParent()
        self.cb = ImportCallback()
        self.cb.loop(self.loops, self.millisWait)
        self.rsp = self.library.get_import_response(self.cb, self.upload.container, self.fs)
        self.pixels = self.rsp.pixels
        return self.pixels

    def get_fileset(self):
        return self.fs


class ImportLibrary(object):
    """
    Supports uploading a list of files to the server and having them
    be resolved into OMERO.fs filsets.
    """

    def __init__(self, client):
        """
        The ImportLibrary will not close the client passed to it.
        """
        self.log = logging.getLogger("omero.importer.ImportLibrary")
        self.client = client
        self.repo = self.lookup_managed_repository()

    def import_paths(self, paths):
        """
        Primary import method for synchronous import.
        The individual steps of the method can be reproduced
        in order to support asynchronous imports.
        """

        upload = UploadTask(self, paths)
        upload()

        settings = None
        count = upload.proc.getFilesetCount()
        combined_pixels = []
        for i in range(count):
            task = FilesetTask(upload, settings, i)
            pixels = task.call()
            combined_pixels.append(pixels)

        return combined_pixels

    def create_settings(self):
        settings = ImportSettings()
        settings.doThumbnails = rbool(True)
        settings.userSpecifiedTarget = None
        settings.userSpecifiedName = None
        settings.userSpecifiedDescription = None
        settings.userSpecifiedAnnotationList = None
        settings.userSpecifiedPixels = None
        settings.clientVersionInfo = self.create_version_info()
        return settings

    def create_version_info(self):
        system, node, release, version, machine, processor = platform.uname()
        try:
            preferred_locale = locale.getdefaultlocale()[0]
        except:
            preferred_locale = "Unknown"

        clientVersionInfo = FilesetVersionInfoI()
        clientVersionInfo.setBioformatsReader(rstring("DirectoryReader"))
        clientVersionInfo.setBioformatsVersion(rstring("Unknown"))
        clientVersionInfo.setOmeroVersion(rstring(omero_version));
        clientVersionInfo.setOsArchitecture(rstring(machine))
        clientVersionInfo.setOsName(rstring(system))
        clientVersionInfo.setOsVersion(rstring(release))
        clientVersionInfo.setLocale(rstring(preferred_locale))

    def create_import(self, paths):
        """
        Provide initial configuration to the server in order to create the
        {@link ImportProcessPrx} which will manage state server-side.
        @throws IOException if the used files' absolute path could not be found
        """
        self.check_managed_repo()

        # TODO: path transformations #11625

        return self.repo.importFiles(paths)

    def upload_file(self, proc, paths, index):

        ca = ChecksumAlgorithmI()
        ca.setValue(rstring("SHA1-160"))  # FIXME

        path = paths[index]
        rfs = proc.getUploader(index)

        try:
            stream = open(path, "rb")
            self.log.info("FILE_UPLOAD_STARTED")
            info = self.client.write_stream(rfs, stream)
            self.log.info("FILE_UPLOAD_BYTES:%s" % info.bytes_written)


            id = info.original_file.id.val
            path = info.original_file.path.val
            name = info.original_file.name.val
            s_hash = info.original_file.hash.val
            c_hash = info.checksum

            self.log.info("%s/%s id=%s" %
                     path, name, id)
            self.log.debug("checksums: client=%s,server=%s",
                     c_hash, s_hash)
            self.log.info("FILE_UPLOAD_COMPLETE")
        except:
            self.log.warn("FILE_UPLOAD_ERROR")
            raise

    def get_import_response(self, cb):
        """
        Returns a non-null {@link ImportResponse} or throws notifies observers
        and throws a {@link RuntimeException}.
        """
        rsp = cb.getResponse()
        rv = None
        if isinstance(rsp, ERR):
            self.log.error("INTERNAL_EXCEPTION")
            raise Exception(
                    "Failure response on import!\n"
                    "Category: %s\n"
                    "Name: %s\n"
                    "Parameters: %s\n", rsp.category, rsp.name,
                    rsp.parameters)
        elif isinstance(rsp, ImportResponse):
            rv = rsp

        if rv is None:
            self.log.error("INTERNAL_EXCEPTION")
            raise ("Unknown response: %s", rsp)

        self.log.info("IMPORT_DONE")

        return rv

    def lookup_managed_repository(self):
        """
        Retrieves the first managed repository from the list of current active
        repositories.
        @return Active proxy for the legacy repository.
        """
        rv = None
        sf = self.client.sf
        map = sf.sharedResources().repositories()
        for proxy in map.proxies:
            if proxy:
                rv = ManagedRepositoryPrx.checkedCast(proxy)
                if rv != None:
                    return rv
        return None

    def check_managed_repo(self):
        if self.repo == None:
            raise Exception("No FS! Cannot proceed")
