/*
 * Copyright (C) 2013 University of Dundee & Open Microscopy Environment.
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

package ome.services.blitz.repo.path;

import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.FactoryBean;

/**
 * Spring-helper for generating the {@link FilePathRestriction} for the server
 * from within XML config files. Primarily responsible for throwing a
 * {@link FatalBeanException} which will stop the server from starting up if
 * anything goes wrong.
 *
 * @author josh at glencoesoftware.com
 * @since 5.0
 */
public class FilePathRestrictionsFactoryBean implements FactoryBean<FilePathRestrictions> {

    private final FilePathRestrictions filePathRestrictions;

    public FilePathRestrictionsFactoryBean(String pathRules) {
        try {
            this.filePathRestrictions =
                    FilePathRestrictionInstance
                        .getFilePathRestrictionsFromPathRules(pathRules);
        } catch (Exception e) {
            throw new FatalBeanException("unknown rule set named in: " + pathRules);
        }
    }

    @Override
    public FilePathRestrictions getObject() throws Exception {
        return filePathRestrictions;
    }

    @Override
    public Class<?> getObjectType() {
        return FilePathRestrictions.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }


}
