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
import java.util.Arrays;

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

    private final static int[] DEFAULT_ORDER = new int[] { 4, 3, 2, 1, 0 };

    protected final FileFormat self;
    protected final Group root;
    protected final Datatype dtype;
    protected final Dataset image0;

    /**
     * pointers into all other arrays. To be used with the XYZCT constants.
     */
    protected int[] ptr;
    protected long[] dims = new long[5];
    protected long[] maxdims = dims; // non-extensible
    protected long[] chunks = null; // no chunking
    protected int gzip = 0; // no compression
    protected int ncomp = 2;
    protected int interlace = ScalarDS.INTERLACE_PIXEL;

    public HdfPixelBuffer(String path, Pixels pixels, int[] order) {
        super(path, pixels);

        validateOrders(order);
        ptr = order;

        FileFormat ff = tryOpen();
        if (!ff.canRead()) {
            ff = create();
        }

        long[] sizes = { pixels.getSizeX(), pixels.getSizeY(),
                pixels.getSizeZ(), pixels.getSizeC(), pixels.getSizeT() };

        mapFromToWithPtr(sizes, dims);

        maxdims = dims;

        self = ff;
        root = configure();
        dtype = createDatatype();
        image0 = createOrOpenDataset(pixels);
        image0.init();
    }

    public HdfPixelBuffer(String path, Pixels pixels) {
        this(path, pixels, DEFAULT_ORDER);
    }

    private void validateOrders(int[] order) {
        boolean valid_orders = true;
        if (order == null) {
            valid_orders = false;
        } else if (order.length != 5) {
            valid_orders = false;
        } else {
            int[] copy = new int[5];
            System.arraycopy(order, 0, copy, 0, 5);
            Arrays.sort(copy);
            for (int j = 0; j < order.length; j++) {
                if (copy[j] != j) {
                    valid_orders = false;
                    break;
                }
            }
        }
        if (!valid_orders) {
            throw new ApiUsageException("orders must contain exactly 0,1,2,3,4");
        }

    }

    public void close() throws IOException {
        try {
            self.close();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    // Data Methods

    public byte[] getHypercubeDirect(int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {

        hyperCube(startX, sizeX, startY, sizeY, startZ, sizeZ, startC, sizeC,
                startT, sizeT);

        try {
            return image0.readBytes();
        } catch (Exception e) {
            throw mapException(e);
        }

    }

    public void setHypercube(byte[] buffer, int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {

        hyperCube(startX, sizeX, startY, sizeY, startZ, sizeZ, startC, sizeC,
                startT, sizeT);

        try {
            image0.write(buffer);
        } catch (Exception e) {
            throw mapException(e);
        }

    }

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

    public byte[] getPlaneRegionDirect(Integer z, Integer c, Integer t,
            Integer count, Integer offset, byte[] buffer) throws IOException,
            DimensionsOutOfBoundsException {

        return null;

    }

    public PixelData getPlane(Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {

        subselectPlane(z, c, t);

        try {
            return data(image0.readBytes());
        } catch (Exception e) {
            throw mapException(e);
        }

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

        subselectRow(y, z, c, t);

        try {
            return data(image0.readBytes());
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public PixelData getStack(Integer c, Integer t) throws IOException,
            DimensionsOutOfBoundsException {

        subselectStack(c, t);

        try {
            return data(image0.readBytes());
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public byte[] getStackDirect(Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        subselectStack(c, t);

        try {
            return image0.readBytes();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public PixelData getTimepoint(Integer t) throws IOException,
            DimensionsOutOfBoundsException {

        subselectTimepoint(t);

        try {
            return data(image0.readBytes());
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public byte[] getTimepointDirect(Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        subselectTimepoint(t);

        try {
            return image0.readBytes();
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public void setPlane(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {

        subselectPlane(z, c, t);

        try {
            image0.write(buffer.array());
        } catch (Exception e) {
            throw mapException(e);
        }

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

        subselectStack(c, t);

        try {
            image0.write(buffer.array());
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public void setStack(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {

        subselectStack(c, t);

        try {
            image0.write(buffer);
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public void setTimepoint(ByteBuffer buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {

        subselectTimepoint(t);

        try {
            image0.write(buffer.array());
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    public void setTimepoint(byte[] buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {

        subselectTimepoint(t);

        try {
            image0.write(buffer);
        } catch (Exception e) {
            throw mapException(e);
        }

    }

    // Helpers ~
    // =========================================================================
    protected FileFormat create() {
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

    protected FileFormat tryOpen() {
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

    protected Group configure() {
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
            throw mapException(e);
        }

        Dataset d = null;
        try {
            // d = self.createImage(IMAGE0, root, dtype, dims, maxdims, chunks,
            // gzip, ncomp, interlace, null);
            d = self.createScalarDS(IMAGE0, root, dtype, dims, maxdims, chunks,
                    gzip, null);
            if (d == null) {
                throw new IOException("Null dataset returned.");
            }
            d.open();
        } catch (Exception e) {
            log.error("Could not create Dataset.", e);
            throw mapException(e);
        }
        return d;
    }

    protected Datatype createDatatype() {

        try {
            HObject obj = self.get("/Type0");
            if (obj != null) {
                return (Datatype) obj;
            }
        } catch (Exception e) {
            log.error("Could not acquire datatype.", e);
            throw mapException(e);
        }

        int tsign = isSigned() ? Datatype.SIGN_2 : Datatype.SIGN_NONE;
        int tclass = isFloat() ? Datatype.CLASS_FLOAT : Datatype.CLASS_INTEGER;
        int tsize = getByteWidth();
        int torder = Datatype.ORDER_BE;
        try {
            Datatype dtype = self.createDatatype(tclass, tsize, torder, tsign /*
                                                                                 * ,
                                                                                 * "/Type0"
                                                                                 */);
            dtype.open();
            return dtype;
        } catch (Exception e) {
            throw mapException(e);
        }
    }

    protected void hyperCube(int x1, int x2, int y1, int y2, int z1, int z2,
            int c1, int c2, int t1, int t2)
            throws DimensionsOutOfBoundsException {

        checkHypercube(x1, x2, y1, y2, z1, z2, c1, c2, t1, t2);

        long[] start = image0.getStartDims();
        long[] sizes = image0.getSelectedDims();
        int[] indexes = image0.getSelectedIndex();
        indexes[0] = T;
        indexes[1] = C;
        indexes[2] = Z;

        start[ptr[Z]] = z1;
        start[ptr[C]] = c1;
        start[ptr[T]] = t1;
        start[ptr[X]] = x1;
        start[ptr[Y]] = y1;

        sizes[ptr[X]] = x2;
        sizes[ptr[Y]] = y2;
        sizes[ptr[Z]] = z2;
        sizes[ptr[C]] = c2;
        sizes[ptr[T]] = t2;
    }

    protected void subselectRow(int y, int z, int c, int t)
            throws DimensionsOutOfBoundsException {
        hyperCube(0, getSizeX(), y, 1, z, 1, c, 1, t, 1);
    }

    protected void subselectPlane(int z, int c, int t)
            throws DimensionsOutOfBoundsException {
        hyperCube(0, getSizeX(), 0, getSizeY(), z, 1, c, 1, t, 1);
    }

    protected void subselectStack(int c, int t)
            throws DimensionsOutOfBoundsException {
        hyperCube(0, getSizeX(), 0, getSizeY(), 0, getSizeZ(), c, 1, t, 1);
    }

    protected void subselectTimepoint(int t)
            throws DimensionsOutOfBoundsException {
        hyperCube(0, getSizeX(), 0, getSizeY(), 0, getSizeZ(), 0, getSizeC(),
                t, 1);
    }

    protected RuntimeException mapException(Throwable t) {
        return new RuntimeException(t);
    }

    protected PixelData data(byte[] buf) {
        return new PixelData(pixels.getPixelsType(), ByteBuffer.wrap(buf));
    }

    protected void mapFromToWithPtr(long[] from, long[] to) {
        if (from == null || to == null || from.length != 5 || to.length != 5) {
            throw new ApiUsageException(
                    "From and to must be arrays of 5 elements.");
        }
        to[ptr[X]] = from[X];
        to[ptr[Y]] = from[Y];
        to[ptr[Z]] = from[Z];
        to[ptr[C]] = from[C];
        to[ptr[T]] = from[T];

    }
}
