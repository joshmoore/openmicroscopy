/*
 * #%L
 * OME SCIFIO package for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package ome.io.bioformats;

import java.io.IOException;
import java.io.Serializable;

import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ReaderWrapper;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ReaderWrapper} implementation which caches the return value
 * of each {@link #openBytes(int)} like methods.
 *
 * Note: this class follows the Bio-Formats code style for possible future
 * inclusion in Bio-Formats.
 */
public class BytesCacher extends ReaderWrapper {

  // -- Constants --

  private static final Logger LOGGER =
    LoggerFactory.getLogger(BytesCacher.class);

  // -- Fields --

  /**
   * {@link Ehcache} instance which has been configured externally.
   */
  private final Ehcache cache;

  // -- Constructors --

  /** Constructs a cacher around the given reader. */
  public BytesCacher(IFormatReader r, Ehcache cache) {
    super(r);
    this.cache = cache;
  }

  // -- ReaderWrapper API methods --

  @Override
  public byte[] openBytes(int no) throws FormatException, IOException {
    Key k = new Key(getCurrentFile(), no);
    byte[] buf = get(k);
    if (buf == null) {
        long start = System.currentTimeMillis();
        buf = super.openBytes(no);
        set(k, buf, start);
    }
    return buf;
  }

@Override
  public byte[] openBytes(int no, byte[] buf) throws FormatException,
    IOException {
    Key k = new Key(getCurrentFile(), no);
    byte[] rv = get(k);
    if (rv == null) {
        long start = System.currentTimeMillis();
        rv = super.openBytes(no, buf);
        set(k, rv, start);
    }
    return rv;
  }

  @Override
  public Object openPlane(int no, int x, int y, int w, int h)
    throws FormatException, IOException {
    Key k = new Key(getCurrentFile(), no, x, y, w, h);
    byte[] buf = get(k);
    if (buf == null) {
        long start = System.currentTimeMillis();
        buf = super.openBytes(no, x, y, w, h);
        set(k, buf, start);
    }
    return buf;
  }

  @Override
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException {
    Key k = new Key(getCurrentFile(), no, x, y, w, h);
    byte[] buf = get(k);
    if (buf == null) {
        long start = System.currentTimeMillis();
        buf = super.openBytes(no, x, y, w, h);
        set(k, buf, start);
    }
    return buf;
  }

  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException {
    Key k = new Key(getCurrentFile(), no, x, y, w, h);
    byte[] rv = get(k);
    if (rv == null) {
        long start = System.currentTimeMillis();
        rv = super.openBytes(no, buf, x, y, w, h);
        set(k, rv, start);
    }
    return rv;
  }

//-- Helper methods --

  private static class Key implements Serializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Key.class);

    private static final long serialVersionUID = 1L;

    final String file;
    final String lookup;

    public Key(String file, Integer...indexes) {
      this.file = file;
      this.lookup = parse(indexes);
    }

    private String parse(Integer[] indexes) {
      return StringUtils.join(indexes, "-");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(file);
        sb.append("[");
        sb.append(lookup);
        sb.append("]");
        return sb.toString();
    }
  }

  protected byte[] get(Key k) {
    final String key = k.toString();
    final long start = System.currentTimeMillis();
    final Element elt = cache.get(key);
    byte[] rv = null;
    try {
      if (elt != null) {
        rv = (byte[]) elt.getValue();
      }
    } finally {
      final long stop = System.currentTimeMillis();
      if (elt == null) {
        BytesCacher.LOGGER.warn("miss: {} ms.", (stop-start));
      } else {
        BytesCacher.LOGGER.warn("hit: {} bytes in {} ms. - {}",
        rv.length, (stop-start), key);
        Key.LOGGER.warn("hit-details: accessed {} time(s). last {} ms ago.",
          elt.getHitCount(), elt.getLatestOfCreationAndUpdateTime() - start);
        
      }
    }
    return rv;
  }

  protected void set(Key k, byte[] buf, long start) {
    String key = k.toString();
    cache.put(new Element(key, buf));
    final long stop = System.currentTimeMillis();
    BytesCacher.LOGGER.warn("put: {} bytes in {} ms. - {}",
      buf.length, (stop-start), key);
  }

}
