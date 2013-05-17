/*
 * Copyright (C) 2013 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package ome.services.blitz.repo;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ome.services.blitz.repo.path.FilePathNamingValidator;
import ome.services.blitz.repo.path.FsFile;
import omero.ServerError;
import omero.sys.EventContext;

import org.apache.commons.lang.text.StrLookup;
import org.apache.commons.lang.text.StrSubstitutor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import Ice.Current;

/**
 * Extensino point which can be modified and injected into
 * {@link ManagedRepositoryI} instances to change which
 * {@link ProcessContainer.Process} is created. By default, a
 * {@link ManagedImportProcessI} will be created.
 *
 * @author Colin Blackburn <cblackburn at dundee dot ac dot uk>
 * @author Josh Moore, josh at glencoesoftware.com
 * @author m.t.b.carroll@dundee.ac.uk
 * @since 5.0
 */
public class ProcessCreator {

    public interface DirectoryMaker {

        void makeDir(String path, boolean parents, Current __current)
                throws ServerError;

    }

    public interface ProcessState {
        Ice.Current getCurrent();
    }

    private final static Log log = LogFactory.getLog(ProcessCreator.class);

    /**
     * Fields used in date-time calculations.
     */
    private static final DateFormatSymbols DATE_FORMAT = new DateFormatSymbols();

    private final String template;

    private final RepositoryDao repositoryDao;

    private final FilePathNamingValidator namingValidator;

    /**
     * Creates a {@link Process} instance which can be stored in the
     * {@link ProcessCreator}.
     */
    public ProcessCreator(String template,
            RepositoryDao repositoryDao,
            FilePathNamingValidator namingValidator) {
        this.template = template;
        this.repositoryDao = repositoryDao;
        this.namingValidator = namingValidator;
        log.info("Repository template: " + this.template);
    }

    /**
     */
    public ProcessContainer.Process createProcess(DirectoryMaker dirMaker,
            List<FsFile> fsFiles,
            Ice.Current __current) throws ServerError {

        if (fsFiles == null || fsFiles.size() < 1) {
            throw new omero.ApiUsageException(null, null, "No paths provided");
        }

        // This is the first part of the string which comes after:
        // ManagedRepository/, e.g. %user%/%year%/etc.
        FsFile relPath = new FsFile(expandTemplate(__current));
        // at this point, relPath should not yet exist on the filesystem
        createTemplateDir(dirMaker, relPath, __current);

        // The next part of the string which is chosen by the user:
        // /home/bob/myStuff
        FsFile basePath = commonRoot(fsFiles);

        // If any two files clash in that chosen basePath directory, then
        // we'' want to suggest a similar alternative.
        return newProcess(relPath, basePath, fsFiles, __current);
    }

    protected ProcessContainer.Process newProcess(FsFile relPath, FsFile basePath,
            List<FsFile> fsFiles, Ice.Current __current) throws ServerError {
        return new ManagedImportProcessI(null, namingValidator, relPath, basePath, fsFiles, __current);
    }

    /**
     * From a list of paths, calculate the common root path that all share. In
     * the worst case, that may be "/". May not include the last element, the filename.
     * @param some paths
     * @return the paths' common root
     */
    protected FsFile commonRoot(List<FsFile> paths) {
        final List<String> commonRoot = new ArrayList<String>();
        int index = 0;
        boolean isCommon = false;
        while (true) {
            String component = null;
            for (final FsFile path : paths) {
                final List<String> components = path.getComponents();
                if (components.size() <= index + 1)  // prohibit very last component
                    isCommon = false;  // not long enough
                else if (component == null) {
                    component = components.get(index);
                    isCommon = true;  // first path
                } else  // subsequent long-enough path
                    isCommon = component.equals(components.get(index));
                if (!isCommon)
                    break;
            }
            if (isCommon)
                commonRoot.add(paths.get(0).getComponents().get(index++));
            else
                break;
        }
        return new FsFile(commonRoot);
    }

    /**
     * Turn the current template into a relative path. Makes use of the data
     * returned by {@link #replacementMap(Ice.Current)}.
     *
     * @param curr
     * @return
     */
    protected String expandTemplate(Ice.Current curr) {

        if (template == null) {
            return ""; // EARLY EXIT.
        }

        final Map<String, String> map = replacementMap(curr);
        final StrSubstitutor strSubstitutor = new StrSubstitutor(
                new StrLookup() {
                    @Override
                    public String lookup(final String key) {
                        return map.get(key);
                    }
                }, "%", "%", '%');
        return strSubstitutor.replace(template);
    }

    /**
     * Generates a map with most of the fields (as strings) from the
     * {@link EventContext} for the current user as well as fields from
     * a current {@link Calendar} instance. Implementors need only
     * provide the fields that are used in their templates. Any keys that
     * cannot be found by {@link #expandeTemplate(String, Ice.Current)} will
     * remain untouched.
     *
     * @param curr
     * @return
     */
    protected Map<String, String> replacementMap(Ice.Current curr) {
        final EventContext ec = this.repositoryDao.getEventContext(curr);
        final Map<String, String> map = new HashMap<String, String>();
        final Calendar now = Calendar.getInstance();
        map.put("user", ec.userName);
        map.put("userId", Long.toString(ec.userId));
        map.put("group", ec.groupName);
        map.put("groupId", Long.toString(ec.groupId));
        map.put("year", Integer.toString(now.get(Calendar.YEAR)));
        map.put("month", String.format("%02d", now.get(Calendar.MONTH)+1));
        map.put("monthname", DATE_FORMAT.getMonths()[now.get(Calendar.MONTH)]);
        map.put("day", String.format("%02d", now.get(Calendar.DAY_OF_MONTH)));
        map.put("time", String.format("%02d-%02d-%02d.%03d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND), now.get(Calendar.MILLISECOND)));
        map.put("session", ec.sessionUuid);
        map.put("sessionId", Long.toString(ec.sessionId));
        map.put("eventId", Long.toString(ec.eventId));
        map.put("perms", ec.groupPermissions.toString());
        return map;
    }

    /**
     * Take the relative path created by
     * {@link #expandTemplate(String, Ice.Current)} and call
     * {@link makeDir(String, boolean, Ice.Current)} on each element of the path
     * starting at the top, until all the directories have been created.
     * The full path must not already exist, although a prefix of it may.
     */
    protected void createTemplateDir(DirectoryMaker dirMaker, FsFile relPath,
            Ice.Current curr) throws ServerError {

        final List<String> relPathComponents = relPath.getComponents();
        final int relPathSize = relPathComponents.size();
        if (relPathSize == 0)
            throw new IllegalArgumentException("no template directory");
        if (relPathSize > 1) {
            final List<String> pathPrefix = relPathComponents.subList(0, relPathSize - 1);
            dirMaker.makeDir(new FsFile(pathPrefix).toString(), true, curr);
        }
        dirMaker.makeDir(relPath.toString(), false, curr);
    }

    protected String getUserDirectoryName(Current __current) {
        EventContext ec = repositoryDao.getEventContext(__current);
        return String.format("%s_%s", ec.userName, ec.userId);
    }

}
