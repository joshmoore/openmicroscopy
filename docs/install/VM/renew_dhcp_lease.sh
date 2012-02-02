#!/bin/bash

set -e -u -x

PASSWORD=${PASSWORD:-"omero"}

echo $PASSWORD | sudo -S dhclient -r
echo $PASSWORD | sudo -S dhclient eth0

echo $PASSWORD | sudo -S ifconfig eth0 | grep 'inet ' | awk '{print $2}' | sed 's/addr://'