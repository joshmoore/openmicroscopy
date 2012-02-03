#!/bin/bash

set -e -u -x

PASSWORD=${PASSWORD:-"omero"}

echo $PASSWORD | sudo -S cp /home/omero/renewdhcp /etc/init.d/
echo $PASSWORD | sudo -S chmod a+x /etc/init.d/renewdhcp
echo $PASSWORD | sudo -S update-rc.d -f renewdhcp remove
echo $PASSWORD | sudo -S update-rc.d -f renewdhcp defaults