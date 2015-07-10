/*
 * Copyright (C) 2015 University of Dundee & Open Microscopy Environment.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package ome.services.util;

import ome.system.PreferenceContext;
import ome.util.SqlAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring bean run on start-up to remove the now invalidated script
 * parameters after the recent changes to Scripts.ice.
 *
 * @since 5.1.0
 */
public class DBScriptParamFix extends BaseDBCheck {

    private static final Logger log = LoggerFactory.getLogger(DBScriptParamFix.class);

    private final SqlAction sql;

    public DBScriptParamFix(Executor executor, SqlAction sql,
            PreferenceContext prefs) {
        super(executor, prefs);
        this.sql = sql;
    }

    @Override
    protected void doCheck() {
        int count = sql.deleteScriptParams();
        log.info("{} script(s) cleaned.", count);
    }
}
