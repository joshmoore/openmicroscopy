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
Core methods and classes for the OMERO.data_sources facility
intended to allow attaching arbitrary data sources to OMERO
data types.

Assuming gateway methods of:
    g.add_data_source_type()  # Admin-only
    g.list_data_source_types()
    g.list_data_sources(objects=[], types=[])
    g.open_cursor(data_source, section=0)
"""

import json
import os


class DataDict(dict):

    def __init__(self, json_data):
        super(DataDict, self).__init__(json_data)
        self.ds_name = json_data["name"]
        self.ds_label = json_data["label"]


class DataField(DataDict):

    def __init__(self, json_data):
        super(DataField, self).__init__(json_data)
        self.ds_type = json_data["type"]
        self.ds_default = json_data["default"]


class DataSource(DataDict):

    SKELETON = """
        {
            "name": "unique_source#1",
            "label": "Something readable",
            "type": "org.openmicroscopy.type.example",
            "inputs": [
                {
                    "name": "foo",
                    "value": "bar"
                }
            ]
        }
    """

    def __init__(self, json_data):
        super(DataSource, self).__init__(json_data)
        self.ds_type = json_data["type"]
        self.ds_inputs = json_data["inputs"]


class DataSourceType(DataDict):

    SKELETON = """
        {
            "name": "unique_type_name#B",
            "label": "Cool type",
            "driver": "omero_data_drivers.some_driver",
            "inputs": [
                {
                    "name": "foo",
                    "value": "bar",
                    "type": "string",
                    "default": none
                }
            ],
            "connection_info": [
            ]
        }
    """

    def __init__(self, json_data):
        super(DataSourceType, self).__init__(json_data)
        self.ds_driver = json_data["driver"]
        self.ds_required = [DataField(x) for x in json_data["required_fields"]]


def load_source_type_files(dir_path):
    if not os.path.isdir(dir_path):
        yield dir_path  # Single-target
    else:
        for root, dirs, files in os.walk(dir_path):
            for file in files:
                target = os.path.join(root, file)
                if target.endswith(".json"):
                    yield target


def list_source_types(dir_path):
    for file in load_source_type_files(dir_path):
        f = open(file, "r")
        try:
            yield DataSourceType(json.load(f))
        finally:
            f.close()
