/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import ncsa.hdf.object.Dataset;
import ome.model.core.Pixels;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see PixelBuffer
 */
public class ChunkedHdfPixelBuffer extends HdfPixelBuffer {

    public final static Log log = LogFactory
            .getLog(ChunkedHdfPixelBuffer.class);

    public ChunkedHdfPixelBuffer(String path, Pixels pixel) {
        super(path, pixel);
    }

    @Override
    protected Dataset createOrOpenDataset(Pixels pixels) {
        long[] values = makeChunks(pixels, 1024 * 1024);
        chunks = new long[5];
        mapFromToWithPtr(values, chunks);
        return super.createOrOpenDataset(pixels);
    }

    public static long[] makeChunks(Pixels pixels, long sizePerChunk) {

        int x0 = pixels.getSizeX(); // e.g. 10000
        int y0 = pixels.getSizeY(); // e.g. 10000

        int b = PixelsService.getBitDepth(pixels.getPixelsType()) / 8;
        double p = sizePerChunk / b; // pixPerChunk, e.g. for int8 & 1M, 1

        double x = Math.pow((x0 * p / y0), 0.5); // e.g. 361
        long x1 = Math.round(x);
        long y1 = Math.round(p / x1); // e.g.361

        if (x1 < 1) {
            x1 = 1;
        }

        if (y1 < 1) {
            y1 = 1;
        }

        long[] values = { x1, y1, 1L, 1L, 1L };
        return values;
    }
}
