/*
 *   $Id$
 *
 *   Copyright 2009 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.formats;

// Java imports
import java.io.File;
import java.util.List;

import loci.formats.FormatReader;
import ome.formats.importer.ImportCandidates;
import ome.formats.importer.ImportConfig;
import ome.formats.importer.ImportLibrary;
import ome.formats.importer.OMEROWrapper;
import omero.model.Pixels;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * test fixture for importing files without a GUI. Sample usage:
 * 
 * <pre>
 * OMEROMetadataStoreClient client = new OMEROMetadataStoreClient(sf);
 * OMEROImportFixture fixture = new OMEROImportFixture(client);
 * fixture.setUp();
 * fixture.setFile(ResourceUtils.getFile(&quot;classpath:tinyTest.d3d.dv&quot;));
 * fixture.setName(name);
 * fixture.doImport();
 * List&lt;Pixels&gt; p = fixture.getPixels();
 * fixture.tearDown();
 * i.setName(name);
 * i = userSave(i);
 * </pre>
 * 
 * This class is <em>not</em> thread safe.
 * 
 * @author Josh Moore, josh.moore at gmx.de
 * @see OMEROMetadataStoreClient
 * @since 4.0
 */
public class OMEROImportFixture {

    Log log = LogFactory.getLog(OMEROImportFixture.class);

    protected OMEROMetadataStoreClient store;

    private OMEROWrapper reader;

    private ImportLibrary library;

    private File file;

    private List<Pixels> pixels;

    private String name;

    public OMEROImportFixture(OMEROMetadataStoreClient store,
            OMEROWrapper reader) {
        this.store = store;
        this.reader = reader;
    }

    /**
     * checks for the necessary fields and initializes the {@link ImportLibrary}
     * 
     * @throws Exception
     */
    public void setUp() throws Exception {
        this.library = new ImportLibrary(store, reader);
    }

    /**
     * sets {@link ImportLibrary}, {@link OMEROMetadataStore}, and file array to
     * null. Also attempts to call {@link FormatReader#close()}.
     */
    public void tearDown() {
        this.store = null;
        this.library = null;
        try {
            if (this.reader != null) {
                this.reader.close();
                this.reader = null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.reader = null;
        }
    }

    /**
     * Provides one complete import cycle.
     */
    public List<Pixels> fullImport(File f, String name) throws Exception {
        this.setUp();
        try {
            this.setFile(f);
            this.setName(name);
            this.doImport();
            return this.getPixels();
        } finally {
            this.tearDown();
        }
    }

    /**
     * runs import by looping through all files and then calling:
     * <ul>
     * <li>{@link ImportLibrary#open(String)}</li>
     * <li>{@link ImportLibrary#calculateImageCount(String)}</li>
     * <li>{@link ImportLibrary#importMetadata()}</li>
     * <li>
     * {@link ImportLibrary#importData(long, String, ome.formats.testclient.ImportLibrary.Step)}
     * </li>
     * </ul>
     * 
     * @param step
     *            an action to take per plane. not null.
     * @throws Exception
     */
    public void doImport() throws Exception {
        String fileName = file.getAbsolutePath();
        ImportConfig config = new ImportConfig();
        ImportCandidates candidates = new ImportCandidates(reader, new String[]{fileName});
        library.importCandidates(config, candidates);
        throw new RuntimeException("NYI");
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Accessor for the created pixels. Should be called before the next call to
     * {@link #doImport()}
     */
    public List<Pixels> getPixels() {
        return this.pixels;
    }

}
