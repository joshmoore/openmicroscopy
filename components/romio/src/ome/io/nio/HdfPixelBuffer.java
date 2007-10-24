/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import ncsa.hdf.object.Dataset;
import ncsa.hdf.object.Datatype;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.Group;
import ncsa.hdf.object.HObject;
import ncsa.hdf.object.ScalarDS;
import ome.conditions.ApiUsageException;
import ome.conditions.ResourceError;
import ome.model.core.Pixels;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see PixelBuffer
 */
public class HdfPixelBuffer extends PixelsBasedPixelBuffer {

    public final static Log log = LogFactory.getLog(HdfPixelBuffer.class);

    public final static FileFormat HDF5 = FileFormat
            .getFileFormat(FileFormat.FILE_TYPE_HDF5);

    public final static String IMAGE0 = "Image0";

    public final static int X = 0, Y = 1, Z = 2, C = 3, T = 4;

    protected final FileFormat self;
    protected final Group root;
    protected final Datatype dtype;
    protected final Dataset image0;

    protected long[] dims;
    protected long[] maxdims = dims;
    protected long[] chunks = null; // no chunking
    protected int gzip = 0; // no compression
    protected int ncomp = 2;
    protected int interlace = ScalarDS.INTERLACE_PIXEL;

    public HdfPixelBuffer(String path, Pixels pixels) {
        super(path, pixels);

        FileFormat ff = tryOpen();
        if (!ff.canRead()) {
            ff = create();
        }

        dims = new long[] { pixels.getSizeX(), pixels.getSizeY(),
                pixels.getSizeZ(), pixels.getSizeC(), pixels.getSizeT() };
        maxdims = dims;

        self = ff;
        root = configure();
        dtype = createDatatype();
        image0 = createOrOpenDataset(pixels);
        image0.init();
    }

    public void close() throws IOException {
        try {
            self.close();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    // Data Methods

    public byte[] getPlaneDirect(Integer z, Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {

        subselectPlane(z, c, t);

        try {
            return image0.readBytes();
        } catch (Exception e) {
            throw mapException(e);
        }

    }

    public void setPlane(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {

        subselectPlane(z, c, t);

        try {
            image0.write(buffer);
        } catch (Exception e) {
            throw mapException(e);
        }

    }

    public void setRow(ByteBuffer buffer, Integer y, Integer z, Integer c,
            Integer t) throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {

        subselectRow(y, z, c, t);

        try {
            image0.write(buffer.array());
        } catch (Exception e) {
            throw mapException(e);
        }

    }

    public byte[] getRowDirect(Integer y, Integer z, Integer c, Integer t,
            byte[] buffer) throws IOException, DimensionsOutOfBoundsException {

        subselectRow(y, z, c, t);

        try {
            return image0.readBytes();
        } catch (Exception e) {
            throw mapException(e);
        }

    }

    // NOT IMPLEMENTED

    public byte[] getPlaneRegionDirect(Integer z, Integer c, Integer t,
            Integer count, Integer offset, byte[] buffer) throws IOException,
            DimensionsOutOfBoundsException {

        return null;

    }

    public PixelData getPlane(Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {
        return null;
    }

    public Long getPlaneOffset(Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public PixelData getRegion(Integer size, Long offset) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public byte[] getRegionDirect(Integer size, Long offset, byte[] buffer)
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public PixelData getRow(Integer y, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public Long getRowOffset(Integer y, Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public PixelData getStack(Integer c, Integer t) throws IOException,
            DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public byte[] getStackDirect(Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public Long getStackOffset(Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public PixelData getTimepoint(Integer t) throws IOException,
            DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public byte[] getTimepointDirect(Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public Long getTimepointOffset(Integer t)
            throws DimensionsOutOfBoundsException {
        // TODO Auto-generated method stub
        return null;
    }

    public void setPlane(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        // TODO Auto-generated method stub

    }

    public void setRegion(Integer size, Long offset, byte[] buffer)
            throws IOException, BufferOverflowException {
        // TODO Auto-generated method stub

    }

    public void setRegion(Integer size, Long offset, ByteBuffer buffer)
            throws IOException, BufferOverflowException {
        // TODO Auto-generated method stub

    }

    public void setStack(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        // TODO Auto-generated method stub

    }

    public void setStack(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        // TODO Auto-generated method stub

    }

    public void setTimepoint(ByteBuffer buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        // TODO Auto-generated method stub

    }

    public void setTimepoint(byte[] buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        // TODO Auto-generated method stub

    }

    // Helpers ~
    // =========================================================================
    private FileFormat create() {
        FileFormat ff = null;
        try {
            ff = HDF5.create(getPath());
        } catch (Exception e) {
            String msg = "Could not create " + getPath();
            log.error(msg, e);
            throw new ResourceError(msg);
        }
        return ff;
    }

    private FileFormat tryOpen() {
        FileFormat ff = null;
        try {
            ff = HDF5.open(getPath(), FileFormat.WRITE);
        } catch (Exception e) {
            String msg = "Could not open " + getPath();
            log.error(msg, e);
            throw new ResourceError(msg);
        }
        return ff;
    }

    private Group configure() {
        Group node = null;
        try {
            self.open();
            node = (Group) ((javax.swing.tree.DefaultMutableTreeNode) self
                    .getRootNode()).getUserObject();
            if (self.get("/Test") == null) {
                self.createGroup("Test", node);
            }
            if (self.get("/Test1") == null) {
                self.createGroup("Test1", node);
            }
        } catch (Exception e) {
            String msg = "Could not prepare " + getPath();
            log.error(msg, e);
            throw new ResourceError(msg);
        }
        return node;
    }

    protected Dataset createOrOpenDataset(Pixels pixels) {

        try {
            HObject obj = self.get("/" + IMAGE0);
            if (obj != null) {
                return (Dataset) obj;
            }
        } catch (Exception e) {
            log.error("Could not acquire dataset.", e);
            throw new RuntimeException(e);
        }

        Dataset d = null;
        try {
            d = self.createImage(IMAGE0, root, dtype, dims, maxdims, chunks,
                    gzip, ncomp, interlace, null);
            if (d == null) {
                throw new IOException("Null dataset returned.");
            }
            d.open();
        } catch (Exception e) {
            log.error("Could not create Dataset.", e);
            throw new RuntimeException(e);
        }
        return d;
    }

    private Datatype createDatatype() {
        try {
            Datatype dtype = self.createDatatype(Datatype.CLASS_INTEGER, 1,
                    Datatype.NATIVE, Datatype.SIGN_NONE);
            dtype.open();
            return dtype;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void hyperCube(int x1, int x2, int y1, int y2, int z1, int z2,
            int c1, int c2, int t1, int t2) {

        if (x1 < 0 || y1 < 0 || z1 < 0 || c1 < 0 || t1 < 0 || x1 < 0 || y1 < 0
                || z2 < 0 || c2 < 0 || t2 < 0 || x1 + x2 > getSizeX()
                || y1 + y2 > getSizeY() || z1 + z2 > getSizeZ()
                || c1 + c2 > getSizeC() || t1 + t2 > getSizeT()) {
            throw new ApiUsageException("Incorrect hypercube bounds.");
        }

        long[] start = image0.getStartDims();
        long[] sizes = image0.getSelectedDims();

        start[Z] = z1;
        start[C] = c1;
        start[T] = t1;
        start[X] = x1;
        start[Y] = y1;

        sizes[X] = x2;
        sizes[Y] = y2;
        sizes[Z] = z2;
        sizes[C] = c2;
        sizes[T] = t2;
    }

    private void subselectRow(int y, int z, int c, int t) {
        hyperCube(0, pixels.getSizeX(), y, 1, z, 1, c, 1, t, 1);
    }

    private void subselectPlane(int z, int c, int t) {
        hyperCube(0, pixels.getSizeX(), 0, pixels.getSizeY(), z, 1, c, 1, t, 1);
    }

    private RuntimeException mapException(Throwable t) {
        return new RuntimeException(t);
    }
}
