/*
 * Copyright (C) 2012 Glencoe Software, Inc. All rights reserved.
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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ome.services.blitz.repo.path.FilePathRestrictionInstance;
import ome.services.blitz.repo.path.FsFile;
import ome.services.blitz.repo.path.MakePathComponentSafe;
import ome.services.blitz.util.ChecksumAlgorithmMapper;
import ome.util.checksum.ChecksumProviderFactory;
import ome.util.checksum.ChecksumProviderFactoryImpl;
import omero.ResourceError;
import omero.ServerError;
import omero.grid.ImportProcessPrx;
import omero.grid._ManagedRepositoryOperations;
import omero.grid._ManagedRepositoryTie;
import omero.model.ChecksumAlgorithm;
import omero.model.Fileset;
import omero.sys.EventContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Ice.Current;

/**
 * Extension of the PublicRepository API which only manages files
 * under ${omero.data.dir}/ManagedRepository.
 *
 * @author Colin Blackburn <cblackburn at dundee dot ac dot uk>
 * @author Josh Moore, josh at glencoesoftware.com
 * @author m.t.b.carroll@dundee.ac.uk
 * @since 5.0
 */
public class ManagedRepositoryI extends PublicRepositoryI
    implements _ManagedRepositoryOperations {

    private final static Logger log = LoggerFactory.getLogger(ManagedRepositoryI.class);

    private final ProcessContainer processes;

    private final ProcessCreator creator;

    /**
     * Creates a {@link ProcessContainer} internally that will not be managed
     * by background threads. Used primarily during testing.
     * @param template
     * @param dao
     */
    public ManagedRepositoryI(RepositoryDao dao, ProcessCreator creator) throws Exception {
        this(dao, creator, new ProcessContainer(), new ChecksumProviderFactoryImpl(),
                null, new MakePathComponentSafe(
                        FilePathRestrictionInstance
                            .getUnixFilePathRestrictions()));
    }

    public ManagedRepositoryI(RepositoryDao dao,
            ProcessCreator creator, ProcessContainer processes,
            ChecksumProviderFactory checksumProviderFactory,
            omero.model.ChecksumAlgorithm checksumAlgorithm,
            MakePathComponentSafe makePathComponentSafe) throws Exception {
        super(dao, checksumProviderFactory, checksumAlgorithm, makePathComponentSafe);
        this.creator = creator;
        this.processes = processes;
    }

    @Override
    public Ice.Object tie() {
        return new _ManagedRepositoryTie(this);
    }

    //
    // INTERFACE METHODS
    //

    /**
     * Return a template based directory path. The path will be created
     * by calling {@link #makeDir(String, boolean, Ice.Current)}.
     */
    public ImportProcessPrx importFiles(List<String> paths,
            Ice.Current __current) throws omero.ServerError {

        if (paths == null || paths.size() < 1) {
            throw new omero.ApiUsageException(null, null, "No paths provided");
        }

        final List<FsFile> fsFiles = new ArrayList<FsFile>();
        for (String path : paths) {
            fsFiles.add(new FsFile(path));
         }

        ProcessContainer.Process proc = creator.createProcess(this, fsFiles, __current);
        processes.addProcess(proc);
        return proc.getProxy();

    }

    public List<ImportProcessPrx> listImports(Ice.Current __current) throws omero.ServerError {

        final List<Long> filesetIds = new ArrayList<Long>();
        final List<ImportProcessPrx> proxies = new ArrayList<ImportProcessPrx>();
        final EventContext ec = repositoryDao.getEventContext(__current);
        final List<ProcessContainer.Process> ps
            = processes.listProcesses(ec.memberOfGroups);

        /*
         * FIXME: remove? extend?
        for (final ProcessContainer.Process p : ps) {
            filesetIds.add(p.getFileset().getId().getValue());
        }
        */

        final List<Fileset> filesets
            = repositoryDao.loadFilesets(filesetIds, __current);

        for (Fileset fs : filesets) {
            if (!fs.getDetails().getPermissions().canEdit()) {
                filesetIds.remove(fs.getId().getValue());
            }
        }

        /*

        FIXME: TBD or removed from the API
        for (final ProcessContainer.Process p : ps) {
            if (filesetIds.contains(p.getFileset().getId())) {
                proxies.add(p.getProxy());
            }
        }

        */

        return proxies;
    }

    public List<ChecksumAlgorithm> listChecksumAlgorithms(Current __current) {
        return ChecksumAlgorithmMapper.getAllChecksumAlgorithms();
    }

    //
    // HELPERS
    //

    /**
     * Checks for the top-level user directory restriction before calling
     * {@link PublicRepositoryI#makeCheckedDirs(LinkedList<CheckedPath>, boolean, Current)}
     */
    protected void makeCheckedDirs(final LinkedList<CheckedPath> paths,
            boolean parents, Current __current) throws ResourceError,
            ServerError {

        final String expanded = creator.expandTemplate(__current);
        final FsFile asfsfile = new FsFile(expanded);
        final List<String> components = asfsfile.getComponents();
        final List<CheckedPath> pathsToFix = new ArrayList<CheckedPath>();

        // hard-coded assumptions: the first element of the template must match
        // user_id and the last is unique in someway (and therefore won't be
        // handled specially.
        for (int i = 0; i < paths.size(); i++) {

            CheckedPath checked = paths.get(i);
            if (checked.isRoot) {
                // This shouldn't happen but just in case.
                throw new ResourceError(null, null, "Cannot re-create root!");
            }
            
            if (i>0 && i>(components.size()-1)) {
                // we always check at least one path element, but after that
                // we only need to check as far as one less than the size of
                // the template
                break;
            }

            if (checked.parent().isRoot) {
                // This is a top-level directory. This must equal
                // "%USERNAME%_%USERID%", in which case if it doesn't exist, it will
                // be created for the user in the "user" group so that it is
                // visible globally.
                String userDirectory = creator.getUserDirectoryName(__current);
                if (!userDirectory.equals(checked.getName())) {
                    throw new omero.ValidationException(null, null, String.format(
                            "User-directory name mismatch! (%s<>%s)",
                            userDirectory, checked.getName()));
                            
                }
            }

            pathsToFix.add(checked);
        }
        
        super.makeCheckedDirs(paths, parents, __current);
        
        // Now that we know that these are the right directories for
        // the current user, we make sure that the directories are in
        // the user group.
        for (final CheckedPath pathToFix : pathsToFix) {
            repositoryDao.createOrFixUserDir(getRepoUuid(), pathToFix, __current);
        }
    }

}
