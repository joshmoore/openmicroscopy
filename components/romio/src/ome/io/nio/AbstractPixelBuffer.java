/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Basic methods for {@link PixelBuffer} implementations. Contains calculations
 * for byte array sizes.
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see PixelBuffer
 */
public abstract class AbstractPixelBuffer extends AbstractBuffer implements
        PixelBuffer {

    public AbstractPixelBuffer(String path) {
        super(path);
    }

    protected Integer rowSize, planeSize, stackSize, timepointSize, totalSize;

    public void checkHypercube(int x1, int x2, int y1, int y2, int z1, int z2,
            int c1, int c2, int t1, int t2)
            throws DimensionsOutOfBoundsException {
        if (x1 < 0 || y1 < 0 || z1 < 0 || c1 < 0 || t1 < 0 || x2 < 1 || y2 < 1
                || z2 < 1 || c2 < 1 || t2 < 1 || x1 + x2 > getSizeX()
                || y1 + y2 > getSizeY() || z1 + z2 > getSizeZ()
                || c1 + c2 > getSizeC() || t1 + t2 > getSizeT()) {
            throw new DimensionsOutOfBoundsException(
                    "Incorrect hypercube bounds.");
        }
    }

    public void checkBounds(Integer y, Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        if (y != null && (y > getSizeY() - 1 || y < 0)) {
            throw new DimensionsOutOfBoundsException("Y '" + y
                    + "' greater than sizeY '" + getSizeY() + "'.");
        }

        if (z != null && (z > getSizeZ() - 1 || z < 0)) {
            throw new DimensionsOutOfBoundsException("Z '" + z
                    + "' greater than sizeZ '" + getSizeZ() + "'.");
        }

        if (c != null && (c > getSizeC() - 1 || c < 0)) {
            throw new DimensionsOutOfBoundsException("C '" + c
                    + "' greater than sizeC '" + getSizeC() + "'.");
        }

        if (t != null && (t > getSizeT() - 1 || t < 0)) {
            throw new DimensionsOutOfBoundsException("T '" + t
                    + "' greater than sizeT '" + getSizeT() + "'.");
        }
    }

    public Integer getRowSize() {
        if (rowSize == null) {
            rowSize = getSizeX() * getByteWidth();
        }

        return rowSize;
    }

    public Integer getPlaneSize() {
        if (planeSize == null) {
            planeSize = getSizeX() * getSizeY() * getByteWidth();
        }

        return planeSize;
    }

    public Integer getStackSize() {
        if (stackSize == null) {
            stackSize = getPlaneSize() * getSizeZ();
        }

        return stackSize;
    }

    public Integer getTimepointSize() {
        if (timepointSize == null) {
            timepointSize = getStackSize() * getSizeC();
        }

        return timepointSize;
    }

    public Integer getTotalSize() {
        if (totalSize == null) {
            totalSize = getTimepointSize() * getSizeT();
        }

        return totalSize;
    }

    public Integer getHypercubeSize(int sizeX, int sizeY, int sizeZ, int sizeC,
            int sizeT) {
        return getByteWidth() * sizeX * sizeY * sizeZ * sizeC * sizeT;
    }

    public Long getRowOffset(Integer y, Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        checkBounds(y, z, c, t);

        Integer rowSize = getRowSize();
        Integer timepointSize = getTimepointSize();
        Integer stackSize = getStackSize();
        Integer planeSize = getPlaneSize();

        return (long) rowSize * y + (long) timepointSize * t + (long) stackSize
                * c + (long) planeSize * z;
    }

    public Long getPlaneOffset(Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        checkBounds(null, z, c, t);

        Integer timepointSize = getTimepointSize();
        Integer stackSize = getStackSize();
        Integer planeSize = getPlaneSize();

        return (long) timepointSize * t + (long) stackSize * c
                + (long) planeSize * z;
    }

    public Long getStackOffset(Integer c, Integer t)
            throws DimensionsOutOfBoundsException {
        checkBounds(null, null, c, t);

        Integer timepointSize = getTimepointSize();
        Integer stackSize = getStackSize();

        return (long) timepointSize * t + (long) stackSize * c;
    }

    public Long getTimepointOffset(Integer t)
            throws DimensionsOutOfBoundsException {
        checkBounds(null, null, null, t);

        Integer timepointSize = getTimepointSize();

        return (long) timepointSize * t;
    }

    public byte[] calculateMessageDigest() throws IOException {
        MessageDigest md;

        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "Required SHA-1 message digest algorithm unavailable.");
        }

        for (int t = 0; t < getSizeT(); t++) {
            try {
                ByteBuffer buffer = getTimepoint(t).getData();
                md.update(buffer);
            } catch (DimensionsOutOfBoundsException e) {
                // This better not happen. :)
                throw new RuntimeException(e);
            }
        }

        return md.digest();
    }

}
