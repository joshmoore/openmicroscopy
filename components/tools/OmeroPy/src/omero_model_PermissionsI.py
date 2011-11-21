"""
/*
 *   Generated by blitz/templates/resouces/combined.vm
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 *
 */
"""
import Ice, IceImport
IceImport.load("omero_model_Permissions_ice")
_omero = Ice.openModule("omero")
_omero_model = Ice.openModule("omero.model")
__name__ = "omero.model"

"""Permissions class which implements Unix-like rw logic for user/group/world.

>>> p = PermissionsI()
object #0 (::omero::model::Permissions)
{
    perm1 = -35
}
"""
class PermissionsI(_omero_model.Permissions):

      class PermissionsI_generator:
          def __iter__(self):
              return self
          def next(self):
              return PermissionsI()

      def generator(cls):
          return cls.PermissionsI_generator()
      generator = classmethod(generator)

      def __init__(self, l = None):
            super(PermissionsI, self).__init__()
            if isinstance(l, str):
                self._perm1 = -1
                self.from_string(l)
            elif l is not None:
                self._perm1 = long(l)
            else:
                self._perm1 = -1

      def granted(self, mask, shift):
            return (self._perm1 & (mask<<shift)) == (mask<<shift)

      def set(self, mask, shift, on):
            if on:
                  self._perm1 = (self._perm1 | ( 0L | (mask<<shift)))
            else:
                  self._perm1 = (self._perm1 & (-1L ^ (mask<<shift)))

      # shift 8; mask 4
      def isUserRead(self):
            return self.granted(4,8)
      def setUserRead(self, value):
            self.set(4,8,value)

      # shift 8; mask 2
      def isUserWrite(self):
            return self.granted(2,8)
      def setUserWrite(self, value):
            self.set(2,8,value)

      # shift 4; mask 4
      def isGroupRead(self):
            return self.granted(4,4)
      def setGroupRead(self, value):
            self.set(4,4,value)

      # shift 4; mask 2
      def isGroupWrite(self):
            return self.granted(2,4)
      def setGroupWrite(self, value):
            self.set(2,4,value)

      # shift 0; mask 4
      def isWorldRead(self):
            return self.granted(4,0)
      def setWorldRead(self, value):
            self.set(4,0,value)

      # shift 0; mask 2
      def isWorldWrite(self):
            return self.granted(2,0)
      def setWorldWrite(self, value):
            self.set(2,0,value)

      # Accessors; do not use

      def getPerm1(self):
          return self._perm1

      def setPerm1(self, _perm1):
          self._perm1 = _perm1
          pass

      def from_string(self, perms):
          import re
          base = "([rR\-_])([wW\-_])"
          regex = re.compile("^(L?)%s$" % (base*3))
          match = regex.match(perms)
          if match is None:
            raise ValueError("Invalid permission string: %s" % perms)
          l = match.group(1)
          ur = match.group(2)
          uw = match.group(3)
          gr = match.group(4)
          gw = match.group(5)
          wr = match.group(6)
          ww = match.group(7)
          self.setUserRead(ur.lower() == "r")
          self.setUserWrite(uw.lower() == "w")
          self.setGroupRead(gr.lower() == "r")
          self.setGroupWrite(gw.lower() == "w")
          self.setWorldRead(wr.lower() == "r")
          self.setWorldWrite(ww.lower() == "w")

      def __str__(self):
          vals = []
          vals.append(self.isUserRead() and "r" or "-")
          vals.append(self.isUserWrite() and "w" or "-")
          vals.append(self.isGroupRead() and "r" or "-")
          vals.append(self.isGroupWrite() and "w" or "-")
          vals.append(self.isWorldRead() and "r" or "-")
          vals.append(self.isWorldWrite() and "w" or "-")
          return "".join(vals)


      def ice_postUnmarshal(self):
          """
          Provides additional initialization once all data loaded
          Required due to __getattr__ implementation.
          """
          pass # Currently unused


      def ice_preMarshal(self):
          """
          Provides additional validation before data is sent
          Required due to __getattr__ implementation.
          """
          pass # Currently unused

      def __getattr__(self, attr):
          if attr == "perm1":
              return self.getPerm1()
          else:
              raise AttributeError(attr)

      def  __setattr__(self, attr, value):
        if attr.startswith("_"):
            self.__dict__[attr] = value
        else:
            try:
                object.__getattribute__(self, attr)
                object.__setattr__(self, attr, value)
            except AttributeError:
                if attr == "perm1":
                    return self.setPerm1(value)
                else:
                    raise

_omero_model.PermissionsI = PermissionsI

def _test():
    import doctest
    doctest.testmod()

if __name__ == "__main__":
    _test()
