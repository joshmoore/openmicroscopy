#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2008-2014 Glencoe Software, Inc. All Rights Reserved.
# Use is subject to license terms supplied in LICENSE.txt
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
   Integration test for accessing countsPerOwner.

"""

import test.integration.library as lib
from omero_model_ImageI import ImageI
from omero_model_TagAnnotationI import TagAnnotationI
from omero.rtypes import rstring, rtime


class TestCounts(lib.ITest):

    def testBasicUsage(self):
        usr = self.client.sf.getAdminService().getEventContext().userId

        img = ImageI()
        img.name = rstring("name")
        img.acquisitionDate = rtime(0)
        tag = TagAnnotationI()
        img.linkAnnotation(tag)

        img = self.client.sf.getUpdateService().saveAndReturnObject(img)

        img = self.client.sf.getQueryService().findByQuery(
            """
            select img from Image img
            join fetch img.annotationLinksCountPerOwner
            where img.id = %s
            """ % (img.id.val), None
            )
        assert img
        assert img.getAnnotationLinksCountPerOwner()[usr] > 0
