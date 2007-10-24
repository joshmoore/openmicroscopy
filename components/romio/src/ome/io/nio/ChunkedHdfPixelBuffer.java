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
        chunks = new long[] { 5, 5, 1, 1, 2 };
        return super.createOrOpenDataset(pixels);
    }

}
