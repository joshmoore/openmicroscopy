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
External data source plugin wrapping the omero.ds module.
"""


import sys
import path

from omero.cli import CLI
from omero.cli import CmdControl
from omero.cli import DirectoryType
from omero.cli import ExceptionHandler
from omero.cli import ExistingFile
from omero.cli import GraphArg

from omero.ds.core import DataSourceType
from omero.ds.core import list_source_types

from omero.util import edit_path


class DataControl(CmdControl):
    """
    Data source attachement and loading

    Any OMERO type can be annotated with external data sources
    each of which is backed by a platform-specfic driver (e.g.
    Oracle, HDF5, YouTube.com, ...) for reading the data from
    an external resource.

    More information is available at:
    https://openmicroscopy.org/info/data-sources
    """

    def _configure(self, parser):

        self.exc = ExceptionHandler()

        parser.add_login_arguments()
        sub = parser.sub()

        list_types = parser.add(
            sub, self.list_types, "List available data source types")

        add_type = parser.add(
            sub, self.add_type,
            help="Add a json-based type file to the configured location")
        add_type.add_argument(
            "-f", "--file", type=ExistingFile(),
            help="File-source to add or - for stdin")
        add_type.add_argument(
            "--skeleton", help="Type to use as the skeleton")
        add_type.add_argument(
            "path", help="Relative path where the file should be stored")

        for x in (list_types, add_type):
            x.add_argument(
                "--location", type=DirectoryType(), help="Alternate location",
                default=self.ctx.dir / "lib" / "data-sources")

        add_source = parser.add(
            sub, self.add_source, "Add a data source to some OMERO object")
        add_source.add_argument(
            "obj", type=GraphArg)

    def get_ds_path(self, args):
        ds_path = path.path(args.location)
        if not ds_path.exists():
            self.ctx.die(100, "Location does not exist: %s" % ds_path)
        return ds_path

    def list_types(self, args):
        ds_path = self.get_ds_path(args)
        for ds_type in list_source_types(ds_path):
            print ds_type

    def add_type(self, args):

        # Imported late to speed up plugin loading
        from omero.util.temp_files import create_path, remove_path

        ds_path = self.get_ds_path(args)
        file_path = ds_path / args.path

        start_text = DataSourceType.SKELETON
        temp_file = create_path()
        try:
            try:
                edit_path(temp_file, start_text)
            except RuntimeError, re:
                self.ctx.die(200, "%s: Failed to edit %s" % (getattr(re, "pid", "Unknown"), temp_file))
            file_path.write_text(temp_file.text())
        finally:
            remove_path(temp_file)

    def add_source(self, args):
        client = self.ctx.conn(args)

try:
    register("ds", DataControl, DataControl.__doc__)
except NameError:
    if __name__ == "__main__":
        cli = CLI()
        cli.register("data", DataControl, DataControl.__doc__)
        cli.invoke(sys.argv[1:])
