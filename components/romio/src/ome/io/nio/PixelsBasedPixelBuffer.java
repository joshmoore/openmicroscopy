/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import java.nio.MappedByteBuffer;

import ome.model.core.Pixels;

/**
 * Basic methods for {@link PixelBuffer} implementations. Contains calculations
 * for byte array sizes.
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see PixelBuffer
 */
public abstract class PixelsBasedPixelBuffer extends AbstractPixelBuffer {

    protected final Pixels pixels;

    public PixelsBasedPixelBuffer(String path, Pixels pixels) {
        super(path);
        if (pixels == null) {
            throw new NullPointerException(
                    "Expecting a not-null pixels element.");
        }

        this.pixels = pixels;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ome.io.nio.PixelBuffer#getByteWidth()
     */
    public int getByteWidth() {
        return PixelsService.getBitDepth(pixels.getPixelsType()) / 8;
    }

    /*
     * (non-Javadoc)
     * 
     * @see ome.io.nio.PixelBuffer#isSigned()
     */
    public boolean isSigned() {
        MappedByteBuffer b = null;
        PixelData d = new PixelData(pixels.getPixelsType(), b);
        return d.isSigned();
    }

    /*
     * (non-Javadoc)
     * 
     * @see ome.io.nio.PixelBuffer#isFloat()
     */
    public boolean isFloat() {
        MappedByteBuffer b = null;
        PixelData d = new PixelData(pixels.getPixelsType(), b);
        return d.isFloat();
    }

    //
    // Delegate methods to ease work with pixels
    //

    public int getSizeC() {
        return pixels.getSizeC();
    }

    public int getSizeT() {
        return pixels.getSizeT();
    }

    public int getSizeX() {
        return pixels.getSizeX();
    }

    public int getSizeY() {
        return pixels.getSizeY();
    }

    public int getSizeZ() {
        return pixels.getSizeZ();
    }

    public long getId() {
        return pixels.getId();
    }

    public String getSha1() {
        return pixels.getSha1();
    }
}
