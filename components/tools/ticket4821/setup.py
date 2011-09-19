#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright (C) 2011 Glencoe Software, Inc. All Rights Reserved.
# Use is subject to license terms supplied in LICENSE.txt
#

"""
setup.py for OMERO extension
"""

from ez_setup import use_setuptools
use_setuptools()
from setuptools import setup, find_packages
from omero_version import omero_version as ov

setup(name="ticket4821",
      version=ov,
      description="Python bindings for OMERO custom extension.",
      long_description="""\
Python bindings for OMERO custom extension.
""",
      author="Josh Moore",
      author_email="josh@glencoesoftware.com",
      url="https://trac.openmicroscopy.org.uk/omero/wiki/OmeroPy",
      download_url="https://trac.openmicroscopy.org.uk/omero/wiki/OmeroPy",
      package_dir = {"": "target"},
      packages=[''])

