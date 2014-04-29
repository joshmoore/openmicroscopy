#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2011-2014 Glencoe Software, Inc. All Rights Reserved.
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
   Test of the omero.cmd.Chgrp Request type.
"""

import omero
import test.integration.library as lib

from omero.callbacks import CmdCallbackI


class TestChgrp(lib.ITest):

    def testChgrpImage(self):

        # One user in two groups
        client, exp = self.new_client_and_user()
        update = client.sf.getUpdateService()
        query = client.sf.getQueryService()

        # Data Setup
        img = self.new_image()
        img = update.saveAndReturnObject(img)

        # New method
        chgrp = omero.cmd.Chgrp(type="/Image", id=img.id.val, options=None)
        handle = client.sf.submit(chgrp)
        cb = CmdCallbackI(client, handle)
        cb.loop(20, 750)

        # Check Data
        query.get("Image", img.id.val)
