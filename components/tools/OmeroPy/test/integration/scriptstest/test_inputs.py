#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2014 Glencoe Software, Inc. All Rights Reserved.
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
    Specifically test the parseInputs functionality
    which all scripts might want to use.
"""

import os
import test.integration.library as lib
import omero
import omero.processor
import omero.scripts
from omero.rtypes import rint

SENDFILE = """
#!/usr/bin/env python

# Setup to run as an integration test
rundir = "%s"
import os
import sys
sys.path.insert(0, rundir)
sys.path.insert(0, os.path.join(rundir, "target"))

import omero.scripts as s
import omero.util.script_utils as su

client = s.client(
    "test_inputs.py")

for inputs in (
    client.getInputs(wrap=True),
    su.parseInputs()):

    assert "a" in inputs
    assert isinstance(inputs["a"], int)
"""


class TestInputs(lib.ITest):

    def testInputs(self):
        scripts = self.root.getSession().getScriptService()
        sendfile = SENDFILE % self.omeropydir()
        id = scripts.uploadScript(
            "/tests/inputs_py/%s.py" % self.uuid(), sendfile)
        input = {"a": rint(100)}
        impl = omero.processor.usermode_processor(self.root)
        try:
            process = scripts.runScript(id, input, None)
            cb = omero.scripts.ProcessCallbackI(self.root, process)
            cb.block(2000)  # ms
            cb.close()
        finally:
            impl.cleanup()
