/*
 * ome.io.nio.DeltaVision
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ome.conditions.ApiUsageException;
import ome.model.enums.PixelsType;

/**
 * Represents a block of pixel data that needs to be re-ordered in accordance
 * with a DeltaVision file. <b>NOTE:</b> This buffer does not re-order the
 * actual backing buffer so <code>read-only</code> buffers may be used and
 * potential callers of {@link getData()} should be aware of this restriction.
 * 
 * @author Chris Allan &nbsp;&nbsp;&nbsp;&nbsp; <a
 *         href="mailto:chris@glencoesoftware.com">chris@glencoesoftware.com</a>
 * @version $Revision$
 * @since 3.0
 * @see PixelBuffer
 */
public class ReorderedPixelData extends PixelData {
    /** The size of each pixels row in bytes. */
    private final Integer rowSize;

    /**
     * Default constructor.
     * 
     * @param pixelsType
     *            The pixels type.
     * @param data
     *            The raw pixel data.
     * @param rowSize
     *            The size of each pixels row in bytes.
     */
    public ReorderedPixelData(PixelsType pixelsType, ByteBuffer data,
            int rowSize) {
        super(pixelsType, data);
        this.rowSize = rowSize;
        if (data.capacity() % rowSize != 0) {
            throw new ApiUsageException(
                    "Buffer size not divisible by row size.");
        }
    }

    /**
     * Returns the re-ordered pixels offset.
     * 
     * @param size
     *            The size of the array of rows.
     * @param offset
     *            The offset within the array of rows assuming a top left
     *            origin.
     * @param rowSize
     *            The size of each pixels row in bytes.
     * @return
     */
    public static int getReorderedPixelOffset(int size, int offset, int rowSize) {
        int stride = offset / rowSize;
        int remainder = rowSize - (offset % rowSize);
        return size - (stride * rowSize) - remainder;
    }

    /**
     * Returns the pixel intensity value of the pixel at a given offset within
     * the backing buffer. This method does not take into account bytes per
     * pixel.
     * 
     * @param offset
     *            The absolute offset within the backing buffer.
     * @return The intensity value.
     */
    @Override
    public double getPixelValueDirect(int offset) {
        offset = getReorderedPixelOffset(data.capacity(), offset, rowSize);
        return super.getPixelValueDirect(offset);
    }

    /**
     * Returns the backing buffer for the pixel data.
     * 
     * @return See above.
     */
    @Override
    public ByteBuffer getData() {
        return data;
    }

    /**
     * Returns the byte order of the backing buffer.
     * 
     * @return See above.
     */
    @Override
    public ByteOrder getOrder() {
        return data.order();
    }

    /**
     * Set the byte order of the backing buffer.
     * 
     * @param order
     *            The byte order.
     */
    @Override
    public void setOrder(ByteOrder order) {
        data.order(order);
    }
}
