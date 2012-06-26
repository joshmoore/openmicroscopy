#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2012 Glencoe Software, Inc. All Rights Reserved.
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
An example might speed me along though, what I was currently trying to do was
get a list of all tags, categorized by tagset. Then I hoped to speed it up by
providing a list of tagnames I wanted information on.
"""

import omero
import omero.gateway

c = omero.client()
c.createSession()
u = c.sf.getUpdateService()

ns = omero.rtypes.rstring(omero.constants.metadata.NSINSIGHTTAGSET)
tagset = omero.model.TagAnnotationI()
tagset.ns = ns
tagset.textValue = omero.rtypes.rstring("my-tag-set")
tagset = u.saveAndReturnObject(tagset)
tagset.unload()

for y in ("c", "d"):
    tag = omero.model.TagAnnotationI()
    tag.textValue = omero.rtypes.rstring(y)
    link = omero.model.AnnotationAnnotationLinkI()
    link.parent = tagset
    link.child = tag
    u.saveObject(link)

# List via regular API
metadata = c.sf.getMetadataService()
tagsets = metadata.loadTagSets(None)
for ts in tagsets:
    print ts.textValue.val
    for t in ts.linkedAnnotationList():
        print "\t", t.textValue.val

# List via gateway API
conn = omero.gateway.BlitzGateway(client_obj=c)
tagsets = [omero.gateway.TagAnnotationWrapper(conn, x) for x in tagsets]

for ts in tagsets:
    print ts.textValue
    for t in ts.listTagsInTagset():
        print "\t", t.textValue
