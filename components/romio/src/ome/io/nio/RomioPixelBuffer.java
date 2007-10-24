/*
 * ome.io.nio.PixelBuffer
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.io.nio;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import ome.conditions.ApiUsageException;
import ome.model.core.Pixels;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Class implementation of the PixelBuffer interface for standard "proprietary"
 * ROMIO/OMEIS data data format.
 * 
 * @author Chris Allan &nbsp;&nbsp;&nbsp;&nbsp; <a
 *         href="mailto:chris@glencoesoftware.com">chris@glencoesoftware.com</a>
 * @version $Revision$
 * @since 3.0
 * @see PixelBuffer
 */
public class RomioPixelBuffer extends PixelsBasedPixelBuffer {
    /** The logger for this particular class */
    private static Log log = LogFactory.getLog(RomioPixelBuffer.class);

    private FileChannel channel;

    RomioPixelBuffer(String path, Pixels pixels) {
        super(path, pixels);
    }

    private FileChannel getFileChannel() throws FileNotFoundException {
        if (channel == null) {
            RandomAccessFile file = new RandomAccessFile(getPath(), "rw");
            channel = file.getChannel();
        }

        return channel;
    }

    /**
     * Closes the buffer, cleaning up file state.
     * 
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    public PixelData getRegion(Integer size, Long offset) throws IOException {
        FileChannel fileChannel = getFileChannel();

        /*
         * fileChannel should not be "null" as it will throw an exception if
         * there happens to be an error.
         */

        MappedByteBuffer b = fileChannel.map(MapMode.READ_ONLY, offset, size);
        return new PixelData(pixels.getPixelsType(), b);
    }

    public byte[] getRegionDirect(Integer size, Long offset, byte[] buffer)
            throws IOException {
        if (buffer.length != size) {
            throw new ApiUsageException("Buffer size incorrect.");
        }
        ByteBuffer b = getRegion(size, offset).getData();
        b.get(buffer);
        return buffer;
    }

    public PixelData getRow(Integer y, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {
        Long offset = getRowOffset(y, z, c, t);
        Integer size = getRowSize();

        return getRegion(size, offset);
    }

    public byte[] getRowDirect(Integer y, Integer z, Integer c, Integer t,
            byte[] buffer) throws IOException, DimensionsOutOfBoundsException {
        if (buffer.length != getRowSize()) {
            throw new ApiUsageException("Buffer size incorrect.");
        }
        ByteBuffer b = getRow(y, z, c, t).getData();
        b.get(buffer);
        return buffer;
    }

    public byte[] getPlaneRegionDirect(Integer z, Integer c, Integer t,
            Integer count, Integer offset, byte[] buffer) throws IOException,
            DimensionsOutOfBoundsException {
        ByteBuffer b = getPlane(z, c, t).getData();
        b.position(offset);
        b.get(buffer, 0, count * getByteWidth());
        return buffer;
    }

    public PixelData getPlane(Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {
        log.info("Retrieving plane: " + z + "x" + c + "x" + t);
        Long offset = getPlaneOffset(z, c, t);
        Integer size = getPlaneSize();
        PixelData region = getRegion(size, offset);

        byte[] nullPlane = PixelsService.nullPlane;

        for (int i = 0; i < PixelsService.NULL_PLANE_SIZE; i++) {
            if (region.getData().get(i) != nullPlane[i]) {
                return region;
            }
        }

        return null; // All of the nullPlane bytes match, non-filled plane
    }

    public byte[] getPlaneDirect(Integer z, Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        if (buffer.length != getPlaneSize()) {
            throw new ApiUsageException("Buffer size incorrect.");
        }
        ByteBuffer b = getPlane(z, c, t).getData();
        b.get(buffer);
        return buffer;
    }

    public PixelData getStack(Integer c, Integer t) throws IOException,
            DimensionsOutOfBoundsException {
        Long offset = getStackOffset(c, t);
        Integer size = getStackSize();

        return getRegion(size, offset);
    }

    public byte[] getStackDirect(Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        if (buffer.length != getStackSize()) {
            throw new ApiUsageException("Buffer size incorrect.");
        }
        ByteBuffer b = getStack(c, t).getData();
        b.get(buffer);
        return buffer;
    }

    public PixelData getTimepoint(Integer t) throws IOException,
            DimensionsOutOfBoundsException {
        Long offset = getTimepointOffset(t);
        Integer size = getTimepointSize();

        return getRegion(size, offset);
    }

    public byte[] getTimepointDirect(Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        if (buffer.length != getTimepointSize()) {
            throw new ApiUsageException("Buffer size incorrect.");
        }
        ByteBuffer b = getTimepoint(t).getData();
        b.get(buffer);
        return buffer;
    }

    public byte[] getHypercubeDirect(int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {

        checkHypercube(startX, sizeX, startY, sizeY, startZ, sizeZ, startC,
                sizeC, startT, sizeT);

        int bufSize = getHypercubeSize(sizeX, sizeY, sizeZ, sizeC, sizeT);
        int scanSize = sizeX * getByteWidth();

        int buf_offset = 0;
        byte[] buf = new byte[bufSize];
        byte[] row = new byte[scanSize];
        for (int t = startT; t < startT + sizeT; t++) {
            for (int c = startC; c < startC + sizeC; c++) {
                for (int z = startZ; z < startZ + sizeZ; z++) {
                    for (int y = startY; y < startY + sizeY; y++) {
                        long reg_offset = getRowOffset(y, z, c, t) + startX;
                        row = getRegionDirect(scanSize, reg_offset, row);
                        System.arraycopy(row, 0, buf, buf_offset, scanSize);
                        buf_offset += scanSize;
                    }
                }
            }
        }
        return buf;
    }

    public void setRegion(Integer size, Long offset, byte[] buffer)
            throws IOException, BufferOverflowException {
        setRegion(size, offset, MappedByteBuffer.wrap(buffer));
    }

    public void setRegion(Integer size, Long offset, ByteBuffer buffer)
            throws IOException, BufferOverflowException {
        FileChannel fileChannel = getFileChannel();

        /*
         * fileChannel should not be "null" as it will throw an exception if
         * there happens to be an error.
         */
        fileChannel.write(buffer, offset);
    }

    public void setRow(ByteBuffer buffer, Integer y, Integer z, Integer c,
            Integer t) throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        Long offset = getRowOffset(y, z, c, t);
        Integer size = getRowSize();

        setRegion(size, offset, buffer);
    }

    public void setPlane(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        Long offset = getPlaneOffset(z, c, t);
        Integer size = getPlaneSize();
        if (buffer.limit() != size) {
            throw new BufferOverflowException();
        }

        setRegion(size, offset, buffer);
    }

    public void setPlane(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        setPlane(MappedByteBuffer.wrap(buffer), z, c, t);
    }

    public void setStack(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        Long offset = getStackOffset(c, t);
        Integer size = getStackSize();
        if (buffer.limit() != size) {
            throw new BufferOverflowException();
        }

        setRegion(size, offset, buffer);
    }

    public void setStack(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        setStack(MappedByteBuffer.wrap(buffer), z, c, t);
    }

    public void setTimepoint(ByteBuffer buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        Long offset = getTimepointOffset(t);
        Integer size = getTimepointSize();
        if (buffer.limit() != size) {
            throw new BufferOverflowException();
        }

        setRegion(size, offset, buffer);
    }

    public void setTimepoint(byte[] buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        setTimepoint(MappedByteBuffer.wrap(buffer), t);
    }

    public void setHypercube(byte[] buffer, int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        throw new UnsupportedOperationException("NYI");
    }
}
