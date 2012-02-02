#!/bin/bash
# This script will shell into an OMERO.VM

KEY="omerovmkey"
USER="omero"
HOST="localhost"
PORT="2222"
OPTIONS="StrictHostKeyChecking=no"

chmod 0600 omerovmkey

ssh -o $OPTIONS -i $KEY -p $PORT -t $USER@$HOST