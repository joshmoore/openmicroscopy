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

import static omero.rtypes.rstring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import loci.formats.FormatReader;
import loci.formats.ImageReader;

import ome.formats.importer.ImportConfig;
import ome.services.blitz.impl.AbstractAmdServant;
import ome.services.blitz.impl.AbstractCloseableAmdServant;
import ome.services.blitz.impl.ServiceFactoryI;
import ome.services.blitz.repo.PublicRepositoryI.AMD_submit;
import ome.services.blitz.repo.path.FilePathNamingValidator;
import ome.services.blitz.repo.path.FsFile;
import ome.services.blitz.util.ServiceFactoryAware;
import omero.ServerError;
import omero.ValidationException;
import omero.api.RawFileStorePrx;
import omero.cmd.HandlePrx;
import omero.constants.CLIENTUUID;
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
import omero.model.MetadataImportJob;
import omero.model.MetadataImportJobI;
import omero.model.OriginalFile;
import omero.model.PixelDataJobI;
import omero.model.ThumbnailGenerationJob;
import omero.model.ThumbnailGenerationJobI;
import omero.model.UploadJob;
import omero.model.UploadJobI;
import omero.sys.EventContext;
import omero.util.CloseableServant;
import omero.util.IceMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import Ice.Current;

/**
 * Represents a single import within a defined-session
 * all running server-side.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 4.5
 */
public class ManagedImportProcessI extends AbstractCloseableAmdServant
    implements _ImportProcessOperations, ServiceFactoryAware,
                ProcessContainer.Process, CloseableServant {

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

        Class<? extends FormatReader> readerClass;

        ImportSettings settings;

        Fileset fs;

        FilesetState(int[] indexes) {
            this.indexes = indexes;
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
     * {@link EventContext} loaded from the {@link Ice.Current}. Represents
     * the calling session that is performing the upload. Individual fileset
     * imports may be executed with a separate context.
     */
    private final EventContext eventContext;

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

    /* in descending order of preference */
    protected final ImmutableList<ChecksumAlgorithm> checksumAlgorithms;

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

    /**
     * May be modified by {@link #trimPaths(Class)}
     */
    private FsFile baseFile;

    /**
     * {@link FsFile} instances parsed from the {@link #clientPaths} provided
     * by the client. These contain only the requested user paths, not the
     * final paths with the final, template-based location.
     *
     * May be modified by {@link #trimPaths(Class)}
     */
    private List<FsFile> userRequestedFsFiles;

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
           ImmutableList<ChecksumAlgorithm> checksumAlgorithms,
           FilePathNamingValidator filePathNamingValidator,
           FsFile relFile, FsFile baseFile, List<FsFile> fsFiles, Current __current)
               throws ServerError {

        super(null, null);
        this.repo = repo;
        this.relFile = relFile;
        this.baseFile = baseFile;
        this.userRequestedFsFiles = fsFiles;
        this.checksumAlgorithms = checksumAlgorithms;
        this.filePathNamingValidator = filePathNamingValidator;
        // Note: initialization of
        //    Fileset fs, ImportLocation location, ImportSettings settings
        // has been moved to verifyUpload() after all files are present
        // for scanning.
        this.current = __current;
        // FIXME: This could be refactored here and in ProcessCreator.
        this.eventContext = repo.repositoryDao.getEventContext(__current);
        this.proxy = registerProxy(__current);
        setApplicationContext(repo.context);
        // TODO: The above could be moved to SessionI.internalServantConfig as
        // long as we're careful to remove all other, redundant calls to setAC.

        ChecksumAlgorithm ca = new ChecksumAlgorithmI();
        ca.setValue(omero.rtypes.rstring("SHA1-160"));

        int commonParentDirsToRetain = dirsToRetain(/* FIXME */); 
        trimPaths(commonParentDirsToRetain);
        location = suggestImportPaths(ca, __current);
    }

    public void setServiceFactory(ServiceFactoryI sf) throws ServerError {
        this.sf = sf;
    }

    public FsFile getFsFile(int index) {
        FsFile fsFile = userRequestedFsFiles.get(index);
        return FsFile.concatenate(relFile, fsFile);
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

    //
    // ProcessContainer INTERFACE METHODS
    //

    public long getGroup() {
        return eventContext.groupId;
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

        final FsFile fsFile = getFsFile(i);

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

        final int size = userRequestedFsFiles.size();
        if (hashes == null) {
            throw new omero.ApiUsageException(null, null,
                    "hashes list cannot be null");
        } else if (hashes.size() != size) {
            throw new omero.ApiUsageException(null, null,
                    String.format("hashes size should be %s not %s", size,
                            hashes.size()));
        }

        ChecksumAlgorithm ca = new ChecksumAlgorithmI();
        ca.setValue(omero.rtypes.rstring("SHA1-160"));

        Map<Integer, String> failingChecksums = new HashMap<Integer, String>();
        for (int i = 0; i < size; i++) {
            FsFile fsFile = getFsFile(i);
            CheckedPath cp = repo.checkPath(fsFile.toString(),
                    ca, this.current);
            final String clientHash = hashes.get(i);
            final String serverHash = cp.hash();
            if (!clientHash.equals(serverHash)) {
                failingChecksums.put(i, serverHash);
            }
        }

        if (!failingChecksums.isEmpty()) {
            throw new omero.ChecksumValidationException(null,
                    omero.ChecksumValidationException.class.toString(),
                    "A checksum mismatch has occurred.",
                    failingChecksums);
        }

        // TODO: for the moment we're just assuming the first file
        // is the target, which is the standard at the moment.
        int[] indexes = new int[userRequestedFsFiles.size()];
        for (int i = 0; i < indexes.length; i++) {
            indexes[i]=i;
        }
        targets.add(new FilesetState(indexes));

    }

    @SuppressWarnings("unchecked")
    public Fileset createFileset(int index, ImportSettings settings,
            Current __current) throws ServerError {

        final FilesetState filesetState = targets.get(index);

        if (settings.checksumAlgorithm == null) {
            settings.checksumAlgorithm = this.checksumAlgorithms.get(0);
            // TODO: throw new omero.ApiUsageException(null, null, "must specify checksum algorithm");
        }
        
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
        filesetState.settings = settings;

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
            final FsFile fsFile = getFsFile(i);
            final FilesetEntry entry = new FilesetEntryI();
            final CheckedPath checked = repo.checkPath(fsFile.toString(),
                    settings.checksumAlgorithm, __current);
            checkedPaths.add(checked);

            fs.setTemplatePrefix(rstring(relFile.toString() + FsFile.separatorChar));
            
            final OriginalFile ofile = repo.findInDb(checked, "r", __current);
            entry.setOriginalFile(ofile);
            fs.addFilesetEntry(entry);

            if (clientPaths.size() > i) {
                entry.setClientPath(omero.rtypes.rstring(clientPaths.get(i)));
            }

        }

        // Use the first CheckedPath for Bio-Formats setId
        ImageReader reader = new ImageReader();
        try {
            checkedPaths.get(0).bfSetId(reader);
        } catch (Exception e) {
            ValidationException ve = new ValidationException();
            IceMapper.fillServerError(ve, e);
            throw ve;
        }
        filesetState.readerClass = (Class) reader.getReader().getClass();

        final ImportConfig config = new ImportConfig(); // Inject? Include in log? FIXME
        final String readerClass = filesetState.readerClass.getName();
        final FilesetVersionInfo serverVersionInfo = config.createVersionInfo(readerClass);


        // Create and validate jobs
        UploadJob update = new UploadJobI(); // FIXME: unclear of the value of this post upload. A log file perhaps?
        fs.linkJob(update);

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

        if (targets.size() != 1) {
            throw new omero.ValidationException(null, null,
                    "Server currently only supports single filesets");
        }

        FilesetState state = targets.get(0);
        Fileset fs = state.fs;
        // i==0 is the upload job which is implicit.
        FilesetJobLink link = fs.getFilesetJobLink(0);
        repo.repositoryDao.updateJob(link.getChild(),
                "Finished", "Finished", this.current);

        // Now move on to the metadata import.
        link = fs.getFilesetJobLink(1);
        CheckedPath checkedPath = ((ManagedImportLocationI) location).getLogFile();
        omero.model.OriginalFile logFile =
                repo.registerLogFile(repo.getRepoUuid(),  fs.getId().getValue(), checkedPath,__current);

        final String reqId = ImportRequest.ice_staticId();
        final ImportRequest req = (ImportRequest)
                repo.getFactory(reqId, this.current).create(reqId);

        req.repoUuid = repo.getRepoUuid();
        req.activity = link;
        req.location = location;
        req.settings = state.settings;
        req.logFile = logFile;
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
     * @param checksumAlgorithm the checksum algorithm to use in verifying the integrity of uploaded files
     * @return {@link ImportLocation} instance
     */
    protected ImportLocation suggestImportPaths(ChecksumAlgorithm checksumAlgorithm, Ice.Current __current)
                    throws omero.ServerError {

        // validate paths
        this.filePathNamingValidator.validateFilePathNaming(relFile);
        this.filePathNamingValidator.validateFilePathNaming(baseFile);
        for (final FsFile path : userRequestedFsFiles) {
            this.filePathNamingValidator.validateFilePathNaming(path);
        }

        // Static elements which will be re-used throughout
        final ManagedImportLocationI data = new ManagedImportLocationI(); // Return value
        data.logFile = repo.checkPath(relFile.toString()+".log", checksumAlgorithm, __current);

        // try actually making directories
        final FsFile newBase = FsFile.concatenate(relFile, baseFile);
        data.sharedPath = newBase.toString();
        data.checkedPaths = new ArrayList<CheckedPath>(userRequestedFsFiles.size());
        for (final FsFile path : userRequestedFsFiles) {
            final String relativeToEnd = path.getPathFrom(baseFile).toString();
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
     * Trim off the start of long client-side paths in place.
     *
     * @param commonParentDirsToRetain number of directories to retain or -1
     *          if interrogating the {@link FormatReader} implementations was
     *          not possible.
     * @return possibly trimmed common root and full paths
     */
    protected void trimPaths(int commonParentDirsToRetain) {

        final List<String> basePathComponents = baseFile.getComponents();
        final int baseDirsToTrim;
        if (commonParentDirsToRetain == -1) {
            // no help from BioFormats

            // find the length of the shortest path, including file name
            int smallestPathLength;
            if (userRequestedFsFiles.isEmpty())
                smallestPathLength = 1; /* imaginary file name */
            else {
                smallestPathLength = Integer.MAX_VALUE;
                for (final FsFile path : userRequestedFsFiles) {
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
            return; // EARLY EXIT

        // actually do the trimming
        // baseFile = new FsFile(basePathComponents.subList(baseDirsToTrim, basePathComponents.size()));
        final List<FsFile> trimmedPaths = new ArrayList<FsFile>(userRequestedFsFiles.size());
        for (final FsFile path : userRequestedFsFiles) {
            // FIXME: moving files into an ".inprogress" folder with no trimming at all
            // FIXME final List<String> pathComponents = path.getComponents();
            final List<String> pathComponents = new ArrayList<String>();
            pathComponents.add(".inprogress");
            pathComponents.addAll(path.getComponents());
            // FIXME trimmedPaths.add(new FsFile(pathComponents.subList(baseDirsToTrim, pathComponents.size())));
            trimmedPaths.add(new FsFile(pathComponents.subList(0, pathComponents.size())));
        }
        this.userRequestedFsFiles = trimmedPaths;
    }

    /**
     * find how many common parent directories to retain according to BioFormats

     * @param readerClasses
     * @return
     */
    protected int dirsToRetain(Class<? extends FormatReader>...readerClasses) {
        final String[] localStylePaths = new String[userRequestedFsFiles.size()];
        int index = 0;
        for (final FsFile fsFile : userRequestedFsFiles) {
            localStylePaths[index++] = repo.serverPaths.getServerFileFromFsFile(fsFile).getAbsolutePath();
        }

        int commonParentDirsToRetain = -1;
            for (Class<? extends FormatReader> readerClass : readerClasses) {
                try {
                    FormatReader reader = readerClass.newInstance();
                    int requiredDirs = reader.getRequiredDirectories(localStylePaths);
                    if (requiredDirs > commonParentDirsToRetain) {
                        commonParentDirsToRetain = requiredDirs;
                    }
                } catch (Exception e) {
                    log.warn("Failed to instantiate reader class: {}", readerClass);
                    continue;
                }
            }
        return commonParentDirsToRetain;
    }

    //
    // CLOSE LOGIC
    //

    @Override
    protected void preClose(Current current) throws Throwable {
        // no-op
    }

    @Override
    protected void postClose(Current current) {
        // no-op
    }

}
