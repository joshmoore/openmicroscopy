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
Tests for the omero.ds package.
"""

"""
Assuming gateway methods of:
    g.add_data_source_type()  # Admin-only
    g.list_data_source_types()
    g.list_data_sources(objects=[], types=[])
    g.open_cursor(data_source, section=0)
"""

import glob
import json
import os
import pytest

# Under test
from omero.ds.drivers import parse_video_id
from omero.ds.drivers import get_iframe
from omero.ds.drivers import get_thumbnail
from omero.ds.core import DataDict
from omero.ds.core import DataField
from omero.ds.core import DataSource
from omero.ds.core import DataSourceType
from omero.ds.core import load_source_type_files
from omero.ds.core import list_source_types

# Test data
location = os.path.dirname(__file__)
json_files = glob.glob(os.path.join(location, "*.json"))


@pytest.mark.parametrize("filename", json_files)
def test_files(filename):
    fhd = open(filename, "r")
    txt = fhd.read()
    fhd.close()
    data = json.loads(txt)
    tuples = list_source_types(filename)
    single = list(tuples)[0]
    assert filename == single.ds_filename
    assert data == single
    assert data["driver"] == single.ds_driver
    assert data["name"] == single.ds_name
    assert data["label"] == single.ds_label
    assert data["driver"] == single.ds_driver
    assert data["required_fields"] == single.ds_required
    for idx, field in enumerate(data["required_fields"]):
        assert field["name"] == single.ds_required[idx].ds_name
        assert field["label"] == single.ds_required[idx].ds_label
        assert field["type"] == single.ds_required[idx].ds_type
        assert field["default"] == single.ds_required[idx].ds_default


@pytest.mark.parametrize("expected,example", [
    ("C2vgICfQawE", "http://www.youtube.com/watch?v=C2vgICfQawE"),
    ("C2vgICfQawE",
    """'<object width="480" height="385">
            <param name="movie" value="http://www.youtube.com/v/C2vgICfQawE?fs=1"></param>
            <param name="allowFullScreen" value="true"></param>
            <param name="allowscriptaccess" value="always"></param><embed src="http://www.youtube.com/v/C2vgICfQawE?fs=1"
                type="application/x-shockwave-flash" allowscriptaccess="always" allowfullscreen="true" width="480" height="385"></embed>
    </object>'"""),
    ('5Y6HSHwhVlY', 'http://www.youtube.com/watch?v=5Y6HSHwhVlY'),
    ('5Y6HSHwhVlY', 'http://youtu.be/5Y6HSHwhVlY'),
    ('5Y6HSHwhVlY', 'http://www.youtube.com/embed/5Y6HSHwhVlY?rel=0" frameborder="0"'),
    ('5Y6HSHwhVlY', 'https://www.youtube-nocookie.com/v/5Y6HSHwhVlY?version=3&amp;hl=en_US'),
    ('', 'http://www.youtube.com/'),
    ('', 'http://www.youtube.com/?feature=ytca'),
])
def test_parsing(expected, example):
    video_id = parse_video_id(example)
    assert expected == video_id, "'%s' <> '%s' for %s" % (expected, video_id, example)


@pytest.mark.parametrize("expected,https,which", [
    ("https://img.youtube.com/vi/FOO/0.jpg", True, 0),
    ("http://img.youtube.com/vi/FOO/0.jpg", False, 0),
    ("http://img.youtube.com/vi/FOO/hqdefault.jpg", False, "hi"),
])
def test_get_thumbnails(expected, https, which):
    url = get_thumbnail("FOO", which=which, https=https)
    assert expected == url


@pytest.mark.parametrize("contains,autoplay,loop", [
    ("autoplay=0&loop=0", 0, 0),
    ("autoplay=0&loop=0", 0, False),
    ("autoplay=0&loop=0", False, False),
    ("autoplay=1&loop=0", 1, 0),
    ("autoplay=1&loop=0", 1, False),
    ("autoplay=1&loop=0", True, False),
    ("autoplay=0&loop=1", 0, 1),
])
def test_get_iframe(contains, autoplay, loop):
    iframe = get_iframe("FOO", autoplay, loop)
    assert contains in iframe
