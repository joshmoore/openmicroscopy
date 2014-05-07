#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Copyright (C) 2008-2014 University of Dundee & Open Microscopy Environment.
# All rights reserved.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

''' Helper functions for views that handle object trees '''

import omero

def parsePermissionsCss (permissions, ownerid, conn):
    ''' Parse numeric permissions into a string of space separated
        CSS classes.

        @param permissions Permissions to parse
        @type permissions Integer
        @param ownerid Owner Id for the object having Permissions
        @type ownerId Integer
        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
    '''
    permissions = omero.model.PermissionsI(permissions)
    permissionsCss = []
    if permissions.canEdit(): permissionsCss.append("canEdit")
    if permissions.canAnnotate(): permissionsCss.append("canAnnotate")
    if permissions.canLink(): permissionsCss.append("canLink")
    if permissions.canDelete(): permissionsCss.append("canDelete")
    if ownerid == conn.getUserId(): permissionsCss.append("canChgrp")
    return ' '.join(permissionsCss)

