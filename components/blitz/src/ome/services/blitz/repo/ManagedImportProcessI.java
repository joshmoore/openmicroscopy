/*
 * Copyright (C) 2012-2013 Glencoe Software, Inc. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import loci.formats.FormatReader;
import ome.formats.importer.ImportConfig;
import ome.services.blitz.impl.AbstractAmdServant;
import ome.services.blitz.impl.ServiceFactoryI;
import ome.services.blitz.repo.PublicRepositoryI.AMD_submit;
import ome.services.blitz.repo.path.FilePathNamingValidator;
import ome.services.blitz.repo.path.FsFile;
import ome.services.blitz.util.ServiceFactoryAware;
import omero.ServerError;
import omero.api.RawFileStorePrx;
import omero.cmd.HandlePrx;
import omero.grid.ImportLocation;
import omero.grid.ImportProcessPrx;
import omero.grid.ImportProcessPrxHelper;
import omero.grid.ImportRequest;
import omero.grid.ImportSettings;
import omero.grid._ImportProcessOperations;
import omero.grid._ImportProcessTie;
import omero.model.ChecksumAlgorithm;
import omero.model.ChecksumAlgorithmI;
import omero.model.Fileset;
import omero.model.FilesetEntry;
import omero.model.FilesetEntryI;
import omero.model.FilesetI;
import omero.model.FilesetJobLink;
import omero.model.FilesetVersionInfo;
import omero.model.IndexingJobI;
import omero.model.Job;
import omero.model.MetadataImportJob;
import omero.model.MetadataImportJobI;
import omero.model.OriginalFile;
import omero.model.PixelDataJobI;
import omero.model.ThumbnailGenerationJob;
import omero.model.ThumbnailGenerationJobI;
import omero.model.UploadJob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import Ice.Current;

/**
 * Represents a single import within a defined-session
 * all running server-side.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 4.5
 */
public class ManagedImportProcessI extends AbstractAmdServant
    implements _ImportProcessOperations, ServiceFactoryAware,
                ProcessContainer.Process {

    private final static Logger log = LoggerFactory.getLogger(ManagedImportProcessI.class);

    private final static int parentDirsToRetain = 3;

    static class UploadState {
        final RawFileStorePrx prx;
        /** Next byte which should be written */
        long offset = 0;

        UploadState(RawFileStorePrx prx) {
            if (prx == null) {
                throw new RuntimeException("Null not allowed!");
            }
            this.prx = prx;
        }

        void setOffset(long offset) {
            this.offset = offset;
        }
    }

    /*
     * One instance created per fileset found by Bio-Formats among the uploaded
     * files.
     */
    static class FilesetState {

        /**
         * Indexes into the {@link FsFile} array given to this process
         * which represent the files in this {@link Fileset}. The first
         * is taken to be the value which should be passed to setId.
         */
        final int[] indexes;

        final Class<? extends FormatReader> readerClass;

        Fileset fs;

        FilesetState(int[] indexes, Class<? extends FormatReader> readerClass) {
            this.indexes = indexes;
            this.readerClass = readerClass;
        }
    }

    //
    // FINAL STATE
    //

    /**
     * Current which created this instance.
     */
    private final Ice.Current current;

    /**
     * The managed repo instance which created (and ultimately is reponsible
     * for) this import process.
     */
    private final ManagedRepositoryI repo;

    /**
     * Used during {@link #suggestImportPaths(FsFile, FsFile, List, Class, ChecksumAlgorithm, Current)}
     * to determine if any of the desired path names would be invalid of a server
     * with the given restrictions (usually based on operating system).
     */
    private final FilePathNamingValidator filePathNamingValidator;

    /**
     * A proxy to this servant which can be given to clients to monitor the
     * import process.
     */
    private final ImportProcessPrx proxy;

    /**
     * {@link FsFile} instances parsed from the {@link #clientPaths} provided
     * by the client.
     */
    private final FsFile relFile;

    private final FsFile baseFile;

    /**
     * {@link FsFile} instances parsed from the {@link #clientPaths} provided
     * by the client.
     */
    private final List<FsFile> fsFiles;

    /**
     * SessionI/ServiceFactoryI that this process is running in.
     * Set via setter injection.
     */
    private/* final */ServiceFactoryI sf;

    /**
     * A sparse and often empty map of the {@link UploadState} instances which
     * this import process is aware of. In a single-threaded model, this map
     * will likely only have at most one element, but depending on threads,
     * pauses, and restarts, this may contain more elements. After close
     * is called on each of the proxies, {@link #closeCalled(int)} will be
     * invoked with the integer lookup to this map, in which case the instance
     * will be purged.
     */
    private final ConcurrentHashMap<Integer, UploadState> uploaders
        = new ConcurrentHashMap<Integer, UploadState>();

    /**
     * Index of the files in {@link #fsFiles} which should be considered
     * the target files for Bio-Formats (i.e. will be passed to setId).
     */
    private final List<FilesetState> targets = new ArrayList<FilesetState>();

    //
    // WRITE-ONCE POST-UPLOAD STATE
    //

    /**
     * The settings as passed in by the user. Never null.
     */
    private/* final */ImportSettings settings;

    /**
     * The import location as defined by the managed repository during
     * importFileset. Never null.
     */
    private/* final */ImportLocation location;

    /**
     * Handle which is the initial first step of import.
     */
    private/* final */HandlePrx handle;

    /**
     * Create and register a servant for servicing the import process
     * within a managed repository.
     *
     * @param repo
     * @param fs
     * @param location
     * @param settings
     * @param __current
     */
    public ManagedImportProcessI(ManagedRepositoryI repo,
             FilePathNamingValidator filePathNamingValidator,
            FsFile relFile, FsFile baseFile, List<FsFile> fsFiles, Current __current)
                throws ServerError {

        super(null, null);
        this.repo = repo;
        this.relFile = relFile;
        this.baseFile = baseFile;
        this.fsFiles = fsFiles;
        this.filePathNamingValidator = filePathNamingValidator;
        // Note: initialization of
        //    Fileset fs, ImportLocation location, ImportSettings settings
        // has been moved to verifyUpload() after all files are present
        // for scanning.
        this.current = __current;
        this.proxy = registerProxy(__current);
        setApplicationContext(repo.context);
        // TODO: The above could be moved to SessionI.internalServantConfig as
        // long as we're careful to remove all other, redundant calls to setAC.
    }

    public void setServiceFactory(ServiceFactoryI sf) throws ServerError {
        this.sf = sf;
    }

    /**
     * Adds this instance to the current session so that clients can communicate
     * with it. Once we move to opening a new session for this import, care
     * must be taken to guarantee that these instances don't leak:
     * i.e. who's responsible for closing them and removing them from the
     * adapter.
     */
    protected ImportProcessPrx registerProxy(Ice.Current ignore) throws ServerError {
        _ImportProcessTie tie = new _ImportProcessTie(this);
        Ice.Current adjustedCurr = repo.makeAdjustedCurrent(current);
        Ice.ObjectPrx prx = repo.registerServant(tie, this, adjustedCurr);
        return ImportProcessPrxHelper.uncheckedCast(prx);
   }

    public ImportProcessPrx getProxy() {
        return this.proxy;
    }

    public ImportSettings getImportSettings(Current __current) {
        return this.settings;
    }

    //
    // ProcessContainer INTERFACE METHODS
    //

    public long getGroup() {
        throw new RuntimeException("NYI");
    }

    public void ping() {
        throw new RuntimeException("NYI");
    }

    public void shutdown() {
        throw new RuntimeException("NYI");
    }
    //
    // ICE INTERFACE METHODS
    //

    public RawFileStorePrx getUploader(int i, Current ignore)
            throws ServerError {

        UploadState state = uploaders.get(i);
        if (state != null) {
            return state.prx; // EARLY EXIT!
        }

        final FsFile fsFile = fsFiles.get(i);

        boolean success = false;
        RawFileStorePrx prx = repo.file(fsFile.toString(), "rw", this.current);
        try {
            state = new UploadState(prx); // Overwrite
            if (uploaders.putIfAbsent(i, state) != null) {
                // The new object wasn't used.
                // Close it.
                prx.close();
                prx = null;
            } else {
                success = true;
            }
            return prx;
        } finally {
            if (!success && prx != null) {
                try {
                    prx.close(); // Close if anything happens.
                } catch (Exception e) {
                    log.error("Failed to close RawFileStorePrx", e);
                }
            }
        }
    }

    public void verifyUpload(List<String> hashes, Current __current)
            throws ServerError {

        final int size = fsFiles.size();
        if (hashes == null) {
            throw new omero.ApiUsageException(null, null,
                    "hashes list cannot be null");
        } else if (hashes.size() != size) {
            throw new omero.ApiUsageException(null, null,
                    String.format("hashes size should be %s not %s", size,
                            hashes.size()));
        }

        Map<Integer, String> failingChecksums = new HashMap<Integer, String>();
        for (int i = 0; i < size; i++) {
            FsFile fsFile = fsFiles.get(i);
            CheckedPath cp = repo.checkPath(fsFile.toString(),
                    settings.checksumAlgorithm, this.current);
            final String clientHash = hashes.get(i);
            final String serverHash = cp.hash();
            if (!clientHash.equals(serverHash)) {
                failingChecksums.put(i, serverHash);
            }
        }

        if (!failingChecksums.isEmpty()) {
            throw new omero.ChecksumValidationException(null,
                    omero.ChecksumValidationException.class.toString(),
                    "A checksum mismatch has occured.",
                    failingChecksums);
        }

        // Now that all the files are in place and known to be valid
        // we can run Bio-Formats over them and pick up the key files.
        Class<? extends FormatReader> readerClass = null;
        // TODO: for the moment we're just assuming the first file
        // is the target, which is the standard at the moment.
        int[] indexes = new int[fsFiles.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i]=i;
        }
        targets.add(new FilesetState(indexes, readerClass));

        // TODO: readerClass is actually a list!
        location = suggestImportPaths(relFile, baseFile, fsFiles, readerClass,
                settings.checksumAlgorithm, __current);
    }

    public Fileset createFileset(int index, ImportSettings settings,
            Current __current) throws ServerError {

        // Initialization version info
        final FilesetState filesetState = targets.get(index);
        final ImportConfig config = new ImportConfig(); // Inject? Include in log?
        final String readerClass = filesetState.readerClass.getName();
        final FilesetVersionInfo serverVersionInfo = config.createVersionInfo(readerClass);

        if (settings == null) {
            settings = new ImportSettings();
            settings.checksumAlgorithm = new ChecksumAlgorithmI();
            settings.checksumAlgorithm.setValue(omero.rtypes.rstring("SHA1-160"));
            // FIXME: this is being set as the default so that just because
            // a client doesn't pass a settings, the checksum for the uploaded
            // files will still be calculated. Perhaps unnecessary since if
            // a client doesn't pass the checksum, there's no way to verify
            // the upload.
        }

        List<String> clientPaths = null;
        if (settings.clientPaths != null) {
            clientPaths = settings.clientPaths;
        } else {
            clientPaths = Collections.<String>emptyList();
        }

        // CheckedPath objects for use by saveFileset later
        final List<CheckedPath> checkedPaths = new ArrayList<CheckedPath>();

        final Fileset fs = new FilesetI();
        for (int i : filesetState.indexes) {
            final FsFile fsFile = fsFiles.get(i);
            final FilesetEntry entry = new FilesetEntryI();
            final CheckedPath checked = repo.checkPath(fsFile.toString(),
                    settings.checksumAlgorithm, __current);
            checkedPaths.add(checked);

            final OriginalFile ofile = repo.findInDb(checked, "r", __current);
            entry.setOriginalFile(ofile);
            fs.addFilesetEntry(entry);

            if (clientPaths.size() > i) {
                entry.setClientPath(omero.rtypes.rstring(clientPaths.get(i)));
            }

        }

        // Create and validate jobs
        if (fs.sizeOfJobLinks() != 1) {
            throw new omero.ValidationException(null, null,
                    "Found more than one job link. "+
                    "Link only updateJob on creation!");
        }
        final FilesetJobLink jobLink = fs.getFilesetJobLink(0);
        final Job job = jobLink.getChild();
        if (job == null) {
            throw new omero.ValidationException(null, null,
                    "Found null-UploadJob on creation");
        }
        if (!(job instanceof UploadJob)) {
            throw new omero.ValidationException(null, null,
                    "Found non-UploadJob on creation: "+
                    job.getClass().getName());
        }

        MetadataImportJob metadata = new MetadataImportJobI();
        metadata.setVersionInfo(serverVersionInfo);
        fs.linkJob(metadata);

        fs.linkJob(new PixelDataJobI());

        if (settings.doThumbnails != null && settings.doThumbnails.getValue()) {
            ThumbnailGenerationJob thumbnail = new ThumbnailGenerationJobI();
            fs.linkJob(thumbnail);
        }

        fs.linkJob(new IndexingJobI());


        filesetState.fs = repo.repositoryDao.saveFileset(
                repo.getRepoUuid(), fs, settings.checksumAlgorithm,
                checkedPaths, __current);
        return filesetState.fs;

    }

    public HandlePrx startImport(Current __current) throws ServerError {

        if (targets.size() != 0) {
            throw new omero.ValidationException(null, null,
                    "Server currently only supports single filesets");
        }

        Fileset fs = targets.get(0).fs;
        // i==0 is the upload job which is implicit.
        FilesetJobLink link = fs.getFilesetJobLink(0);
        repo.repositoryDao.updateJob(link.getChild(),
                "Finished", "Finished", this.current);

        // Now move on to the metadata import.
        link = fs.getFilesetJobLink(1);

        final String reqId = ImportRequest.ice_staticId();
        final ImportRequest req = (ImportRequest)
                repo.getFactory(reqId, this.current).create(reqId);
        req.repoUuid = repo.getRepoUuid();
        req.activity = link;
        req.location = location;
        req.settings = settings;
        final AMD_submit submit = repo.submitRequest(sf, req, this.current);
        this.handle = submit.ret;
        return submit.ret;
    }

    //
    // GETTERS
    //

    public ImportLocation getLocation(Current __current) throws ServerError {
        return location;
    }

    public int getFilesetCount(Current __current) throws ServerError {
        // TODO: should likely be Map<Int, List<String>> from target id
        // to all the files in that target (usedFiles).
        return targets.size();
    }

    public long getUploadOffset(int i, Current ignore) throws ServerError {
        UploadState state = uploaders.get(i);
        if (state == null) {
            return 0;
        }
        return state.offset;
    }

    public HandlePrx getHandle(Ice.Current ignore) {
        return handle;
    }

    //
    // OTHER LOCAL INVOCATIONS
    //

    public void setOffset(int idx, long offset) {
        UploadState state = uploaders.get(idx);
        if (state == null) {
            log.warn(String.format("setOffset(%s, %s) - no such object", idx, offset));
        } else {
            state.setOffset(offset);
            log.debug(String.format("setOffset(%s, %s) successfully", idx, offset));
        }
    }

    public void closeCalled(int idx) {
        UploadState state = uploaders.remove(idx);
        if (state == null) {
            log.warn(String.format("closeCalled(%s) - no such object", idx));
        } else {
            log.debug(String.format("closeCalled(%s) successfully", idx));
        }
    }

    //
    // HELPERS
    //


    /**
     * Take a relative path that the user would like to see in his or her
     * upload area, and provide an import location instance whose paths
     * correspond to existing directories corresponding to the sanitized
     * file paths.
     * @param relPath Path parsed from the template
     * @param basePath Common base of all the listed paths ("/my/path")
     * @param reader BioFormats reader for data, may be null
     * @param checksumAlgorithm the checksum algorithm to use in verifying the integrity of uploaded files
     * @return {@link ImportLocation} instance
     */
    protected ImportLocation suggestImportPaths(FsFile relPath, FsFile basePath, List<FsFile> paths,
            Class<? extends FormatReader> reader, ChecksumAlgorithm checksumAlgorithm, Ice.Current __current)
                    throws omero.ServerError {
        final Paths trimmedPaths = trimPaths(basePath, paths, reader);
        basePath = trimmedPaths.basePath;
        paths = trimmedPaths.fullPaths;

        // validate paths
        this.filePathNamingValidator.validateFilePathNaming(relPath);
        this.filePathNamingValidator.validateFilePathNaming(basePath);
        for (final FsFile path : paths) {
            this.filePathNamingValidator.validateFilePathNaming(path);
        }

        // Static elements which will be re-used throughout
        final ManagedImportLocationI data = new ManagedImportLocationI(); // Return value
        data.logFile = repo.checkPath(relPath.toString()+".log", checksumAlgorithm, __current);

        // try actually making directories
        final FsFile newBase = FsFile.concatenate(relPath, basePath);
        data.sharedPath = newBase.toString();
        data.checkedPaths = new ArrayList<CheckedPath>(paths.size());
        for (final FsFile path : paths) {
            final String relativeToEnd = path.getPathFrom(basePath).toString();
            final String fullRepoPath = data.sharedPath + FsFile.separatorChar + relativeToEnd;
            // FIXME: Why is CheckedPath created with "new" here? That should
            // be avoided since each servant could specifiy it's own implementation.
            data.checkedPaths.add(new CheckedPath(repo.serverPaths, fullRepoPath,
                    repo.checksumProviderFactory, checksumAlgorithm));
        }

        repo.makeDir(data.sharedPath, true, __current);

        // Assuming we reach here, then we need to make
        // sure that the directory exists since the call
        // to saveFileset() requires the parent dirs to
        // exist.
        for (CheckedPath checked : data.checkedPaths) {
            repo.makeDir(checked.getRelativePath(), true, __current);
        }

        return data;
    }


    /**
     * Trim off the start of long client-side paths.
     * @param basePath the common root
     * @param fullPaths the full paths from the common root down to the filename
     * @param readerClass BioFormats reader for data, may be null
     * @return possibly trimmed common root and full paths
     */
    protected Paths trimPaths(FsFile basePath, List<FsFile> fullPaths,
            Class<? extends FormatReader> readerClass) {
        // find how many common parent directories to retain according to BioFormats
        Integer commonParentDirsToRetain = null;
        final String[] localStylePaths = new String[fullPaths.size()];
        int index = 0;
        for (final FsFile fsFile : fullPaths)
            localStylePaths[index++] = repo.serverPaths.getServerFileFromFsFile(fsFile).getAbsolutePath();
        try {
            commonParentDirsToRetain = readerClass.newInstance().getRequiredDirectories(localStylePaths);
        } catch (Exception e) { }

        final List<String> basePathComponents = basePath.getComponents();
        final int baseDirsToTrim;
        if (commonParentDirsToRetain == null) {
            // no help from BioFormats

            // find the length of the shortest path, including file name
            int smallestPathLength;
            if (fullPaths.isEmpty())
                smallestPathLength = 1; /* imaginary file name */
            else {
                smallestPathLength = Integer.MAX_VALUE;
                for (final FsFile path : fullPaths) {
                    final int pathLength = path.getComponents().size();
                    if (smallestPathLength > pathLength)
                        smallestPathLength = pathLength;
                }
            }

            // plan to trim to try to retain a certain number of parent directories
            baseDirsToTrim = smallestPathLength - parentDirsToRetain - (1 /* file name */);
        }
        else
            // plan to trim the common root according to BioFormats' suggestion
            baseDirsToTrim = basePathComponents.size() - commonParentDirsToRetain;
        if (baseDirsToTrim < 0)
            return new Paths(basePath, fullPaths);
        // actually do the trimming
        basePath = new FsFile(basePathComponents.subList(baseDirsToTrim, basePathComponents.size()));
        final List<FsFile> trimmedPaths = new ArrayList<FsFile>(fullPaths.size());
        for (final FsFile path : fullPaths) {
            final List<String> pathComponents = path.getComponents();
            trimmedPaths.add(new FsFile(pathComponents.subList(baseDirsToTrim, pathComponents.size())));
        }
        return new Paths(basePath, trimmedPaths);
    }


    /** Return value for {@link #trimPaths}. */
    private static class Paths {
        final FsFile basePath;
        final List<FsFile> fullPaths;

        Paths(FsFile basePath, List<FsFile> fullPaths) {
            this.basePath = basePath;
            this.fullPaths = fullPaths;
        }
    }

}
