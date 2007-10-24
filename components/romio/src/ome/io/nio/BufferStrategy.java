/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio;

import ome.model.core.Pixels;

/**
 * Simple strategy interface which can be used along with {@link BufferFactory}
 * to have configurable {@link PixelBuffer} types created at runtime.
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 */
public interface BufferStrategy {

    PixelBuffer make(Pixels p);

    public final static BufferStrategy HDF = new BufferStrategy() {
        public PixelBuffer make(Pixels p) {
            return null;
        }
    };

    public final static BufferStrategy DV = new BufferStrategy() {
        public PixelBuffer make(Pixels p) {
            return null;
        }
    };
}
