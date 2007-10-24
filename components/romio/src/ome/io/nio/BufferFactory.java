/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import java.util.Arrays;
import java.util.List;

import ome.model.core.Pixels;

/**
 * Subclass of {@link PixelsService} which makes use of {@link BufferStrategy}
 * to intercept calls to {@link #getPixelBuffer(Pixels)} and possibly use a
 * different implementation than {@link RomioPixelBuffer}.
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see BufferStrategy
 */
public class BufferFactory extends PixelsService {

    private final List<BufferStrategy> strategies;

    public BufferFactory(String dir, BufferStrategy... strategies) {
        super(dir);
        this.strategies = Arrays.asList(strategies);
    }

    @Override
    public PixelBuffer getPixelBuffer(Pixels pixels) {
        for (BufferStrategy strategy : strategies) {
            PixelBuffer buf = strategy.make(pixels);
            if (buf != null) {
                return buf;
            }
        }
        return super.getPixelBuffer(pixels);
    }
}
