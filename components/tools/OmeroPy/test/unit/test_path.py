#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
Test custom modifications to path.py.

Copyright 2014 CRS4. All rights reserved.
Use is subject to license terms supplied in LICENSE.txt.
"""

import sys
import path
import pytest
import locale
import random

# Magically creates the setdefaultencoding method
reload(sys)


def locale_supported(loc):
    old = locale.getlocale()
    try:
        locale.setlocale(locale.LC_ALL, loc)
        return True
    except:
        return False
    finally:
        locale.setlocale(locale.LC_ALL, old)


LOCALES = locale.locale_alias.values()
LOCALES = filter(locale_supported, LOCALES)
random.shuffle(LOCALES)
LOCALES = LOCALES[0:min(10, len(LOCALES))]
LOCALES.append(locale.getlocale())
LOCALES.append(locale.getdefaultlocale())
LOCALES.extend([None, "", "C"])


class TestPath(object):

    def test_parpath(self):
        root = path.path('/')
        a1, a2 = [root / _ for _ in 'a1', 'a2']
        b = a1 / 'b'
        for x, y in (root, a1), (root, a2), (a1, b):
            assert len(y.parpath(x)) == 1
            assert len(x.parpath(y)) == 0
        assert len(a1.parpath(a2)) == 0
        assert len(a2.parpath(a1)) == 0
        assert len(b.parpath(root)) == 2
        assert len(root.parpath(b)) == 0

    @pytest.mark.parametrize("loc", LOCALES)
    @pytest.mark.parametrize("enc", ("utf-8", "ascii"))
    def test_encoding(self, loc, enc, tmpdir):
        old_enc = sys.getdefaultencoding()
        old_loc = locale.getlocale()
        try:
            sys.setdefaultencoding(enc)
            try:
                locale.setlocale(locale.LC_ALL, loc)
            except locale.Error:
                pytest.skip(loc)
            t = "Fahrvergnügen"
            d = tmpdir.join(t);
            d.mkdir()
            f = d.join(t)
            f.write("")

            # Using LocalPath.strpath here
            dir_obj = path.path(d.strpath)
            contents = dir_obj.listdir()

            path_obj1 = path.path(contents[0])
            path_obj2 = path.path(f.strpath)
            assert path_obj1 == path_obj2
            objs = {}
            objs[path_obj1] = 1
            objs[path_obj2] = 2
            assert len(objs) == 1

            path_str1 = str(path_obj1)
            path_str2 = str(path_obj2)
            assert path_str1 == path_str2
            strs = {}
            strs[path_str1] = 1
            strs[path_str2] = 2
            assert len(strs) == 1

            path_uni1 = unicode(path_obj1)
            path_uni2 = unicode(path_obj2)
            assert path_uni1 == path_uni2
            unis = {}
            unis[path_uni1] = 1
            unis[path_uni2] = 2
            assert len(unis) == 1

        finally:
            sys.setdefaultencoding(old_enc)
            locale.setlocale(locale.LC_ALL, old_loc)
