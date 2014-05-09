#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Copyright (C) 2008-2014 University of Dundee & Open Microscopy Environment.
# All rights reserved.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

''' Helper functions for views that handle object trees '''

import omero

from datetime import datetime


def parse_permissions_css(permissions, ownerid, conn):
    ''' Parse numeric permissions into a string of space separated
        CSS classes.

        @param permissions Permissions to parse
        @type permissions L{omero.rtypes.rmap}
        @param ownerid Owner Id for the object having Permissions
        @type ownerId Integer
        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
    '''
    restrictions = ('canEdit', 'canAnnotate', 'canLink', 'canDelete')
    permissionsCss = [r for r in restrictions if permissions.get(r)]
    if ownerid == conn.getUserId():
        permissionsCss.append("canChgrp")
    return ' '.join(permissionsCss)


def marshal_plate(conn, row):
    ''' Given a Plate row (list) marshals it into a dictionary.  Order and
        type of columns in row is:
          * id (rlong)
          * name (rstring)
          * details.owner.id (rlong)
          * details.permissions (dict)

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param row The ProjectAcquisition row to marshal
        @type row L{list}
    '''
    plate_id, name, owner_id, permissions = row
    plate = dict()
    plate['id'] = plate_id.val
    plate['name'] = name.val
    plate['permsCss'] = parse_permissions_css(permissions, owner_id.val, conn)
    plate['isOwned'] = owner_id.val == conn.getUserId()
    plate['plateacquisitions'] = list()
    return plate


def marshal_plate_acquisition(conn, row):
    ''' Given a PlateAcquisition row (list) marshals it into a
        dictionary.  Order and type of columns in row is:
          * id (rlong)
          * name (rstring)
          * details.owner.id (rlong)
          * details.permissions (dict)
          * startTime (rtime)
          * endTime (rtime)

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param row The ProjectAcquisition row to marshal
        @type row L{list}
    '''
    pa_id, name, owner_id, permissions, start_time, end_time = row
    plate_acquisition = dict()
    plate_acquisition['id'] = pa_id.val
    if name is not None:
        plate_acquisition['name'] = name.val
    elif start_time is not None and end_time is not None:
        start_time = datetime.fromtimestamp(start_time.val / 1000.0)
        end_time = datetime.fromtimestamp(end_time.val / 1000.0)
        plate_acquisition['name'] = '%s - %s' % (start_time, end_time)
    else:
        plate_acquisition['name'] = 'Run %d' % pa_id.val
    plate_acquisition['permsCss'] = \
        parse_permissions_css(permissions, owner_id.val, conn)
    plate_acquisition['isOwned'] = owner_id.val == conn.getUserId()
    return plate_acquisition


def marshal_dataset(conn, row):
    ''' Given a Dataset row (list) marshals it into a dictionary.  Order
        and type of columns in row is:
          * id (rlong)
          * name (rstring)
          * details.permissions (dict)
          * details.owner.id (rlong)
          * child_count (rlong)

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param row The Dataset row to marshal
        @type row L{list}
    '''
    dataset_id, name, owner_id, permissions, child_count = row
    dataset = dict()
    dataset['id'] = dataset_id.val
    dataset['name'] = name.val
    dataset['isOwned'] = owner_id.val == conn.getUserId()
    dataset['childCount'] = child_count.val
    dataset['permsCss'] = \
        parse_permissions_css(permissions, owner_id.val, conn)
    return dataset


def marshal_datasets_for_projects(conn, project_ids):
    ''' Given a list of project ids, marshals the contained datasets, grouping
        by parent project.

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param project_ids The Project IDs to marshal
        @type project_ids list of longs
    '''
    if len(project_ids) == 0:
        return {}
    projects = {}
    qs = conn.getQueryService()
    params = omero.sys.ParametersI()
    params.addIds(project_ids)
    q = """
        select project.id,
               dataset.id,
               dataset.name,
               dataset.details.owner.id,
               project.details.permissions,
               project.details.owner.id,
               (select count(id) from DatasetImageLink dil
                  where dil.parent=dataset.id)
               from ProjectDatasetLink pdlink
               join pdlink.parent project
               join pdlink.child dataset
        where project.id in (:ids)
        order by dataset.name
        """
    for e in qs.projection(q, params, conn.SERVICE_OPTS):
        p = projects.setdefault(e[0].val, {'datasets': []})
        if 'permsCss' not in p:
            p['permsCss'] = parse_permissions_css(e[4].val, e[5].val, conn)
        p['datasets'].append(marshal_dataset(
            conn, e[1:5] + [e[6]]
        ))
    for p in projects.keys():
        projects[p]['childCount'] = len(projects[p]['datasets'])
    return projects


def marshal_datasets(conn, dataset_ids):
    ''' Marshal datasets with ids matching dataset_ids.

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param dataset_ids The dataset IDs to marshal
        @type dataset_ids list of longs
    '''
    if len(dataset_ids) == 0:
        return []
    datasets = []
    params = omero.sys.ParametersI()
    params.addIds(dataset_ids)
    qs = conn.getQueryService()
    q = """
        select dataset.id,
               dataset.name,
               dataset.details.owner.id,
               dataset.details.permissions,
               (select count(id) from DatasetImageLink dil
                 where dil.parent=dataset.id)
               from Dataset dataset
        where dataset.id in (:ids)
        order by dataset.name
        """
    for e in qs.projection(q, params, conn.SERVICE_OPTS):
        datasets.append(marshal_dataset(conn, e[0:5]))
    return datasets


def marshal_plates_for_screens(conn, screen_ids):
    ''' Given a list of screen ids, marshals the contained plates, grouping
        by parent screen.

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param screen_ids The screen IDs to marshal
        @type screen_ids list of longs
    '''
    if len(screen_ids) == 0:
        return {}
    screens = {}
    params = omero.sys.ParametersI()
    params.addIds(screen_ids)
    qs = conn.getQueryService()
    q = """
        select screen.id,
               plate.id,
               plate.name,
               plate.details.owner.id,
               plate.details.permissions,
               pa.id,
               pa.name,
               pa.details.owner.id,
               pa.details.permissions,
               pa.startTime,
               pa.endTime
               from Screen screen
               join screen.plateLinks splink
               join splink.child plate
               join plate.plateAcquisitions pa
        where screen.id in (:ids)
        order by screen.name, plate.name, pa.id
        """
    for e in qs.projection(q, params, conn.SERVICE_OPTS):
        s = screens.setdefault(e[0].val, {'plateids': [], 'plates': {}})
        pid = e[1].val
        p = s['plates'].setdefault(pid, marshal_plate(conn, e[1:5]))
        if pid not in s['plateids']:
            s['plateids'].append(pid)
        plate_acquisition_id = e[5]
        if plate_acquisition_id is not None:
            # We have a Plate that has PlateAcquisitions
            p['plateacquisitions'].append(
                marshal_plate_acquisition(conn, e[5:11])
            )
    for s in screens.keys():
        screens[s]['childCount'] = len(screens[s]['plates'])
        # keeping plates ordered
        screens[s]['plates'] = [screens[s]['plates'][x]
                                for x in screens[s]['plateids']]
        for p in screens[s]['plates']:
            p['plateAcquisitionsCount'] = len(p['plateacquisitions'])
    return screens


def marshal_plates(conn, plate_ids):
    ''' Marshal plates with ids matching plate_ids.

        @param conn OMERO gateway.
        @type conn L{omero.gateway.BlitzGateway}
        @param plate_ids The plate IDs to marshal
        @type plate_ids list of longs
    '''
    if len(plate_ids) == 0:
        return []
    plates = {}
    plateids = []
    params = omero.sys.ParametersI()
    params.addIds(plate_ids)
    qs = conn.getQueryService()
    q = """
        select plate.id,
               plate.name,
               plate.details.owner.id,
               plate.details.permissions,
               pa.id,
               pa.name,
               pa.details.owner.id,
               pa.details.permissions,
               pa.startTime,
               pa.endTime
               from Plate plate
               left join plate.plateAcquisitions pa
        where plate.id in (:ids)
        order by plate.name, pa.id
        """
    for e in qs.projection(q, params, conn.SERVICE_OPTS):
        pid = e[0].val
        p = plates.setdefault(pid, marshal_plate(conn, e[0:4]))
        if pid not in plateids:
            plateids.append(pid)
        plate_acquisition_id = e[4]
        if plate_acquisition_id is not None:
            # We have a Plate that has PlateAcquisitions
            p['plateacquisitions'].append(
                marshal_plate_acquisition(conn, e[4:10])
            )
    # keeping plates ordered
    plates = [plates[x] for x in plateids]
    for p in plates:
        p['plateAcquisitionsCount'] = len(p['plateacquisitions'])
    return plates
