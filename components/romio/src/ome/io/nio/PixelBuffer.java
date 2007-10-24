/*
 * ome.io.nio.PixelBuffer
 *
 *   Copyright 2006 University of Dundee. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * 
 * This interface declares the I/O responsibilities of a buffer, file or
 * otherwise, that contains a 5-dimensional Pixel array (XYZCT).
 * 
 * @author Chris Allan &nbsp;<a
 *         href="mailto:callan@blackcat.ca">callan@blackcat.ca</a>
 * @version $Revision$
 * @since 3.0
 * 
 */
public interface PixelBuffer {
    /**
     * Closes the buffer, cleaning up file state.
     * 
     * @throws IOException
     *             if an I/O error occurs.
     */
    public void close() throws IOException;

    /**
     * Checks to ensure that no one particular axis has an offset out of bounds.
     * <code>null</code> may be passed as the argument to any one of the
     * offsets to ignore it for the purposes of bounds checking.
     * 
     * @param y
     *            offset across the Y-axis of the pixel buffer to check.
     * @param z
     *            offset across the Z-axis of the pixel buffer to check.
     * @param c
     *            offset across the C-axis of the pixel buffer to check.
     * @param t
     *            offset across the T-axis of the pixel buffer to check.
     * @throws DimensionsOutOfBoundsException
     *             if <code>y</code>, <code>z</code>, <code>c</code> or
     *             <code>t</code> is out of bounds.
     */
    public void checkBounds(Integer y, Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException;

    /**
     * Retrieves the in memory size of a 2D image plane in this pixel buffer.
     * 
     * @return 2D image plane size in bytes (sizeX*sizeY*ByteWidth).
     */
    public Integer getPlaneSize();

    /**
     * Retreives the in memory size of a row or scanline of pixels in this pixel
     * buffer.
     * 
     * @return row or scanline size in bytes (sizeX*ByteWidth)
     */
    public Integer getRowSize();

    /**
     * Retreives the in memory size of the entire number of optical sections for
     * a <b>single</b> wavelength or channel at a particular timepoint in this
     * pixel buffer.
     * 
     * @return stack size in bytes (sizeX*sizeY*sizeZ*ByteWidth).
     */
    public Integer getStackSize();

    /**
     * Retrieves the in memory size of the entire number of optical sections for
     * <b>all</b> wavelengths or channels at a particular timepoint in this
     * pixel buffer.
     * 
     * @return timepoint size in bytes (sizeX*sizeY*sizeZ*sizeC*ByteWidth).
     */
    public Integer getTimepointSize();

    /**
     * Retrieves the in memory size of the entire pixel buffer.
     * 
     * @return total size of the pixel size in bytes
     *         (sizeX*sizeY*sizeZ*sizeC*sizeT*ByteWidth).
     */
    public Integer getTotalSize();

    /**
     * Retrieves the in memory size of a hypercube
     * 
     * @return size of hypercube in bytes
     */
    public Integer getHypercubeSize(int sizeX, int sizeY, int sizeZ, int sizeC,
            int sizeT);

    /**
     * Retrieves the offset for a particular row or scanline in this pixel
     * buffer.
     * 
     * @param y
     *            offset across the Y-axis of the pixel buffer.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return offset of the row or scaline.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public Long getRowOffset(Integer y, Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException;

    /**
     * Retrieves the offset for a particular 2D image plane in this pixel
     * buffer.
     * 
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return offset of the 2D image plane.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public Long getPlaneOffset(Integer z, Integer c, Integer t)
            throws DimensionsOutOfBoundsException;

    /**
     * Retreives the offset for the entire number of optical sections for a
     * <b>single</b> wavelength or channel at a particular timepoint in this
     * pixel buffer.
     * 
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return offset of the stack.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public Long getStackOffset(Integer c, Integer t)
            throws DimensionsOutOfBoundsException;

    /**
     * Retrieves the in memory size of the entire number of optical sections for
     * <b>all</b> wavelengths or channels at a particular timepoint in this
     * pixel buffer.
     * 
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return offset of the timepoint.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public Long getTimepointOffset(Integer t)
            throws DimensionsOutOfBoundsException;

    /**
     * Retrieves a region from a given plane directly.
     * 
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @param count
     *            the number of pixels to retrieve.
     * @param offset
     *            the offset at which to retrieve <code>count</code> pixels.
     * @param buffer
     *            pre-allocated buffer, <code>count</code> in size.
     * @return buffer containing the data which comprises the region of the
     *         given 2D image plane. It is guaranteed that this buffer will have
     *         been byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @see getPlaneRegionDirect()
     */
    public byte[] getPlaneRegionDirect(Integer z, Integer c, Integer t,
            Integer count, Integer offset, byte[] buffer) throws IOException,
            DimensionsOutOfBoundsException;

    /**
     * Retrieves a region from this pixel buffer.
     * 
     * @param size
     *            byte width of the region to retrieve.
     * @param offset
     *            offset within the pixel buffer.
     * @return buffer containing the data. It is guaranteed that this buffer
     *         will have its <code>order</code> set correctly but <b>not</b>
     *         that the backing buffer will have been byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @see getRegionDirect()
     */
    public PixelData getRegion(Integer size, Long offset) throws IOException;

    /**
     * Retrieves a region from this pixel buffer directly.
     * 
     * @param size
     *            byte width of the region to retrieve.
     * @param offset
     *            offset within the pixel buffer.
     * @param buffer
     *            pre-allocated buffer of the row's size.
     * @return <code>buffer</code> containing the data which comprises this
     *         region. It is guaranteed that this buffer will have been byte
     *         swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @see getRegion()
     */
    public byte[] getRegionDirect(Integer size, Long offset, byte[] buffer)
            throws IOException;

    /**
     * Retrieves a particular row or scanline from this pixel buffer.
     * 
     * @param y
     *            offset across the Y-axis of the pixel buffer.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return buffer containing the data which comprises this row or scanline.
     *         It is guaranteed that this buffer will have its
     *         <code>order</code> set correctly but <b>not</b> that the
     *         backing buffer will have been byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @see getRowDirect()
     */
    public PixelData getRow(Integer y, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException;

    /**
     * Retrieves a particular row or scanline from this pixel buffer.
     * 
     * @param y
     *            offset across the Y-axis of the pixel buffer.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @param buffer
     *            pre-allocated buffer of the row's size.
     * @return <code>buffer</code> containing the data which comprises this
     *         row or scanline. It is guaranteed that this buffer will have been
     *         byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @see getRowDirect()
     */
    public byte[] getRowDirect(Integer y, Integer z, Integer c, Integer t,
            byte[] buffer) throws IOException, DimensionsOutOfBoundsException;

    /**
     * Retrieves a particular 2D image plane from this pixel buffer.
     * 
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return buffer containing the data which comprises this 2D image plane.
     *         It is guaranteed that this buffer will have its
     *         <code>order</code> set correctly but <b>not</b> that the
     *         backing buffer will have been byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public PixelData getPlane(Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException;

    /**
     * Retrieves a particular 2D image plane from this pixel buffer.
     * 
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @param buffer
     *            pre-allocated buffer of the plane's size.
     * @return <code>buffer</code> containing the data which comprises this 2D
     *         image plane. It is guaranteed that this buffer will have been
     *         byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public byte[] getPlaneDirect(Integer z, Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException;

    /**
     * Retreives the the entire number of optical sections for a <b>single</b>
     * wavelength or channel at a particular timepoint in this pixel buffer.
     * 
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return buffer containing the data which comprises this stack. It is
     *         guaranteed that this buffer will have its <code>order</code>
     *         set correctly but <b>not</b> that the backing buffer will have
     *         been byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public PixelData getStack(Integer c, Integer t) throws IOException,
            DimensionsOutOfBoundsException;

    /**
     * Retreives the the entire number of optical sections for a <b>single</b>
     * wavelength or channel at a particular timepoint in this pixel buffer.
     * 
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @param buffer
     *            pre-allocated buffer of the stack's size.
     * @return <code>buffer</code> containing the data which comprises this
     *         stack. It is guaranteed that this buffer will have been byte
     *         swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public byte[] getStackDirect(Integer c, Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException;

    /**
     * Retrieves the entire number of optical sections for <b>all</b>
     * wavelengths or channels at a particular timepoint in this pixel buffer.
     * 
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @return buffer containing the data which comprises this timepoint. It is
     *         guaranteed that this buffer will have its <code>order</code>
     *         set correctly but <b>not</b> that the backing buffer will have
     *         been byte swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public PixelData getTimepoint(Integer t) throws IOException,
            DimensionsOutOfBoundsException;

    /**
     * Retrieves the entire number of optical sections for <b>all</b>
     * wavelengths or channels at a particular timepoint in this pixel buffer.
     * 
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @param buffer
     *            pre-allocated buffer of the timepoint's size.
     * @return <code>buffer</code> containing the data which comprises this
     *         timepoint. It is guaranteed that this buffer will have been byte
     *         swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public byte[] getTimepointDirect(Integer t, byte[] buffer)
            throws IOException, DimensionsOutOfBoundsException;

    /**
     * Retrieves an entire hypercube for this pixel buffer.
     * 
     * @return <code>buffer</code> containing the data which comprises this
     *         hypercube. It is guaranteed that this buffer will have been byte
     *         swapped.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     */
    public byte[] getHypercubeDirect(int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException;

    /**
     * Sets a region in this pixel buffer.
     * 
     * @param size
     *            byte width of the region to set.
     * @param offset
     *            offset within the pixel buffer.
     * @param buffer
     *            a byte array of the data.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws BufferOverflowException
     *             if <code>buffer.length > size</code>.
     */
    public void setRegion(Integer size, Long offset, byte[] buffer)
            throws IOException, BufferOverflowException;

    /**
     * Sets a region in this pixel buffer.
     * 
     * @param size
     *            byte width of the region to set.
     * @param offset
     *            offset within the pixel buffer.
     * @param buffer
     *            a byte buffer of the data.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws BufferOverflowException
     *             if <code>buffer.length > size</code>.
     */
    public void setRegion(Integer size, Long offset, ByteBuffer buffer)
            throws IOException, BufferOverflowException;

    /**
     * Sets a particular row or scanline in this pixel buffer.
     * 
     * @param buffer
     *            a byte buffer of the data comprising this row or scanline.
     * @param y
     *            offset across the Y-axis of the pixel buffer.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getRowSize()}</code>.
     */
    public void setRow(ByteBuffer buffer, Integer y, Integer z, Integer c,
            Integer t) throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException;

    /**
     * Sets a particular 2D image plane in this pixel buffer.
     * 
     * @param buffer
     *            a byte array of the data comprising this 2D image plane.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getPlaneSize()}</code>.
     */
    public void setPlane(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException;

    /**
     * Sets a particular 2D image plane in this pixel buffer.
     * 
     * @param buffer
     *            a byte buffer of the data comprising this 2D image plane.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getPlaneSize()}</code>.
     */
    public void setPlane(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException;

    /**
     * Sets the entire number of optical sections for a <b>single</b>
     * wavelength or channel at a particular timepoint in this pixel buffer.
     * 
     * @param buffer
     *            a byte buffer of the data comprising this stack.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getStackSize()}</code>.
     */
    public void setStack(ByteBuffer buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException;

    /**
     * Sets the entire number of optical sections for a <b>single</b>
     * wavelength or channel at a particular timepoint in this pixel buffer.
     * 
     * @param buffer
     *            a byte array of the data comprising this stack.
     * @param z
     *            offset across the Z-axis of the pixel buffer.
     * @param c
     *            offset across the C-axis of the pixel buffer.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getStackSize()()}</code>.
     */
    public void setStack(byte[] buffer, Integer z, Integer c, Integer t)
            throws IOException, DimensionsOutOfBoundsException,
            BufferOverflowException;

    /**
     * Sets the entire number of optical sections for <b>all</b> wavelengths or
     * channels at a particular timepoint in this pixel buffer.
     * 
     * @param buffer
     *            a byte buffer of the data comprising this timepoint.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getTimepointSize()}</code>.
     */
    public void setTimepoint(ByteBuffer buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException;

    /**
     * Sets the entire number of optical sections for <b>all</b> wavelengths or
     * channels at a particular timepoint in this pixel buffer.
     * 
     * @param buffer
     *            a byte array of the data comprising this timepoint.
     * @param t
     *            offset across the T-axis of the pixel buffer.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if <code>buffer.length > {@link getTimepointSize()}</code>.
     */
    public void setTimepoint(byte[] buffer, Integer t) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException;

    /**
     * Sets an entire hypercube in this pixel buffer.
     * 
     * @param buffer
     *            a byte array of the data comprising the hypercube.
     * @throws IOException
     *             if there is a problem writing to the pixel buffer.
     * @throws DimensionsOutOfBoundsException
     *             if offsets are out of bounds after checking with
     *             {@link checkBounds()}.
     * @throws BufferOverflowException
     *             if
     *             <code>buffer.length == {@link getByteWidth()} * selected dimensions</code>.
     */
    public void setHypercube(byte[] buffer, int startX, int sizeX, int startY,
            int sizeY, int startZ, int sizeZ, int startC, int sizeC,
            int startT, int sizeT) throws IOException,
            DimensionsOutOfBoundsException, BufferOverflowException;

    /**
     * Calculates a SHA-1 message digest for the entire pixel buffer.
     * 
     * @return byte array containing the message digest.
     * @throws IOException
     *             if there is a problem reading from the pixel buffer.
     */
    public byte[] calculateMessageDigest() throws IOException;

    /**
     * Returns the byte width for the pixel buffer.
     * 
     * @return See above.
     */
    public int getByteWidth();

    /**
     * Returns whether or not the pixel buffer has signed pixels.
     * 
     * @return See above.
     */
    public boolean isSigned();

    /**
     * Returns whether or not the pixel buffer has floating point pixels.
     * 
     * @return
     */
    public boolean isFloat();

    /**
     * Retrieves the full path to this pixel buffer on disk
     * 
     * @return fully qualified path.
     */
    public String getPath();

    /**
     * Delegates to {@link Pixels.getId()}.
     */
    public long getId();

    /**
     * Delegates to {@link Pixels.getSizeX()}.
     */
    public int getSizeX();

    /**
     * Delegates to {@link Pixels.getSizeY()}.
     */
    public int getSizeY();

    /**
     * Delegates to {@link Pixels.getSizeZ()}.
     */
    public int getSizeZ();

    /**
     * Delegates to {@link Pixels.getSizeC()}.
     */
    public int getSizeC();

    /**
     * Delegates to {@link Pixels.getSizeT()}.
     */
    public int getSizeT();
}
