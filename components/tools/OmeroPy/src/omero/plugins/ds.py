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


import json
import sys
import path

from omero.cli import CLI
from omero.cli import CmdControl
from omero.cli import DirectoryType
from omero.cli import ExceptionHandler
from omero.cli import ExistingFile

from omero.ds.core import attach_source
from omero.ds.core import DataSource
from omero.ds.core import DataSourceType
from omero.ds.core import list_sources
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

        add_source = parser.add(
            sub, self.add_source, "Add a data source to some OMERO object")
        type_group = add_source.add_mutually_exclusive_group()
        type_group.add_argument(
            "-P", "--typepath",
            help="Path to the data source type to use")
        type_group.add_argument(
            "-T", "--typename",
            help="Unique name of the data source type to use")

        add_source.add_argument(
            "--name", help="Unique name for this source. "
            "Will be defined by server if not provided")
        add_source.add_argument(
            "--label", help="User-readable label for this source.")
        add_source.add_argument(
            "-I", "--input", nargs="*",
            help="Value to be set on the data source")

        list_sources = parser.add(
            sub, self.list_sources, "Add a data source to some OMERO object")
        for x in (add_source, list_sources):
            x.add_argument("obj", nargs="+")

        for x in (list_types, add_type, add_source):
            x.add_argument(
                "--location", type=DirectoryType(), help="Alternate location",
                default=self.ctx.dir / "lib" / "data-sources")

    def list_types(self, args):
        from omero.util.text import TableBuilder
        tb = TableBuilder("path", "name", "label", "driver", "inputs")

        ds_path = self.get_ds_path(args)
        for ds_type in list_source_types(ds_path):
            ds_path = path.path(ds_type.ds_filename)
            tb.row(
                self.simplify_path(args, ds_path), ds_type.ds_name,
                ds_type.ds_label, ds_type.ds_driver, ds_type.ds_inputs)
        self.ctx.out(str(tb.build()))

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
                self.ctx.die(200, "%s: Failed to edit %s" %
                             (getattr(re, "pid", "Unknown"), temp_file))
            file_path.write_text(temp_file.text())
        finally:
            remove_path(temp_file)

    def add_source(self, args):
        if args.typepath:
            ds_path = self.get_ds_path(args) / args.typepath
            if not ds_path.exists():
                self.ctx.die(300, "%s: Does not exist" % ds_path)
            elif ds_path.isdir():
                self.ctx.die(301, "%s: Is a directory" % ds_path)

            try:
                source_type = list(list_source_types(ds_path))[0]
            except Exception, e:
                self.ctx.die(302, "Failed to load from path: %s" % e)

        elif args.typename:
            self.ctx.die(304, "NYI")

        user_inputs = {}
        if args.input:
            for user_input in args.input:
                if "=" in user_input:
                    name, value = user_input.split("=", 1)
                else:
                    name, value = user_input, None
                user_inputs[name] = value

        for ds_input in source_type.ds_inputs:
            name = ds_input.ds_name
            label = ds_input.ds_label
            default = ds_input.ds_default
            type = ds_input.ds_type
            required = ds_input.ds_required
            entered = name in user_inputs

            if not entered and required:
                user_input = self.ctx.input(
                    "Please enter a %s value for %s ('%s'): " %
                    (type, name, label))
                user_inputs[name] = user_input

        import json
        blank_data_source = json.loads(DataSource.SKELETON)
        blank_data_source["type"] = source_type["name"]
        blank_data_source["inputs"] = []
        if args.name:
            blank_data_source["name"] = args.name
        else:
            blank_data_source["name"] = ""
        if args.label:
            blank_data_source["label"] = args.label
        else:
            blank_data_source["label"] = ""

        for name, value in user_inputs.items():
            data_field = {"name": name, "value": value}
            blank_data_source["inputs"].append(data_field)

        data_source = DataSource(blank_data_source)
        client = self.ctx.conn(args)
        links = attach_source(client, data_source, args.obj)
        for link in links:
            self.ctx.out("Created link: %s:%s" %
                         (link.__class__.__name__, link.id.val))

    def list_sources(self, args):
        client = self.ctx.conn(args)
        if args.obj:

            from omero.util.text import TableBuilder
            tb = TableBuilder("id", "type")

            annotations = list_sources(client, args.obj)
            for ann in annotations:
                try:
                    source = DataSource(json.loads(ann.textValue.val))
                    tb.row(ann.id.val, source.ds_type)
                except:
                    tb.row(ann.id.val, "invalid")
            self.ctx.out(str(tb.build()))

    #
    # Helper methods
    #

    def get_ds_path(self, args):
        ds_path = path.path(args.location)
        if not ds_path.exists():
            self.ctx.die(100, "Location does not exist: %s" % ds_path)
        return ds_path

    def simplify_path(self, args, ds_path):
        levels = ds_path.parpath(args.location)
        parts = ds_path.splitall()
        return path.path("/".join(parts[-1*len(levels):]))


try:
    register("ds", DataControl, DataControl.__doc__)
except NameError:
    if __name__ == "__main__":
        cli = CLI()
        cli.register("data", DataControl, DataControl.__doc__)
        cli.invoke(sys.argv[1:])
