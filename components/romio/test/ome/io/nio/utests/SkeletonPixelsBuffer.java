/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio.utests;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import ome.io.nio.DimensionsOutOfBoundsException;
import ome.io.nio.PixelBuffer;
import ome.io.nio.PixelData;
import ome.io.nio.PixelsBasedPixelBuffer;
import ome.model.core.Pixels;

/**
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see PixelBuffer
 */
public abstract class SkeletonPixelsBuffer extends PixelsBasedPixelBuffer {

    public SkeletonPixelsBuffer(String path, Pixels pixels) {
        super(path, pixels);
    }

    public void close() throws IOException {
        throw new UnsupportedOperationException();
    }

    public byte[] getHypercubeDirect(int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public PixelData getPlane(Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public byte[] getPlaneDirect(Integer z, Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public Long getPlaneOffset(Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public byte[] getPlaneRegionDirect(Integer z, Integer c, Integer t,
            Integer count, Integer offset, byte[] buffer) throws IOException,
            DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public PixelData getRegion(Integer size, Long offset) throws IOException {
        throw new UnsupportedOperationException();
    }

    public byte[] getRegionDirect(Integer size, Long offset, byte[] buffer)
            throws IOException {
        throw new UnsupportedOperationException();
    }

    public PixelData getRow(Integer y, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public byte[] getRowDirect(Integer y, Integer z, Integer c, Integer t,
            byte[] buffer) throws IOException, DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public Long getRowOffset(Integer y, Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public PixelData getStack(Integer c, Integer t) throws IOException,
            DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public byte[] getStackDirect(Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public Long getStackOffset(Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public PixelData getTimepoint(Integer t) throws IOException,
            DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public byte[] getTimepointDirect(Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public Long getTimepointOffset(Integer t)
            throws DimensionsOutOfBoundsException {
        throw new UnsupportedOperationException();
    }

    public void setHypercube(byte[] buffer, int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setPlane(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setPlane(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setRegion(Integer size, Long offset, byte[] buffer)
            throws IOException, BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setRegion(Integer size, Long offset, ByteBuffer buffer)
            throws IOException, BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setRow(ByteBuffer buffer, Integer y, Integer z, Integer c,
            Integer t) throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setStack(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setStack(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setTimepoint(ByteBuffer buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        throw new UnsupportedOperationException();
    }

    public void setTimepoint(byte[] buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException {
        throw new UnsupportedOperationException();
    }
}
