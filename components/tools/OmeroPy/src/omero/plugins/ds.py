#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2013 Glencoe Software, Inc. All Rights Reserved.
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
External data source plugin
"""


import sys

from omero.cli import CLI
from omero.cli import CmdControl
from omero.cli import ExceptionHandler
from omero.cli import GraphArg

HELP = """Data tool methods"""


class DataControl(UserGroupControl):

    def _configure(self, parser):

        self.exc = ExceptionHandler()

        HELP_TXT = """

Data source attachement and loading

Any OMERO type can be annotated with external data sources
each of which is backed by a platform-specfic driver (e.g.
Oracle, HDF5, YouTube.com, ...) for reading the data from
an external resource.

More information is available at:
https://openmicroscopy.org/info/data-sources
        """

        parser.add_login_arguments()
        sub = parser.sub()
        list_types = parser.add(
            sub, self.list_types, "List available data source types")

        add_type = parser.add(
            sub, self.add_type, "Add a json-based type file to the given location")

        list_source = parser.add(
            sub, self.add_source," Add")

        add_source = parser.add(
            sub, self.add_source,
            "Add")
        add_source.add_argument(
            "obj", type=GraphArg(),



try:
    register("data", DataControl, HELP)
except NameError:
    if __name__ == "__main__":
        cli = CLI()
        cli.register("data", DataControl, HELP)
        cli.invoke(sys.argv[1:])
