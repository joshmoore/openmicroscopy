/*
 * Copyright (C) 2014 University of Dundee & Open Microscopy Environment.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package ome.formats.importer.transfers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ome.util.checksum.ChecksumProvider;
import omero.ServerError;
import omero.api.RawFileStorePrx;

import org.apache.commons.lang.ArrayUtils;

/**
 * Traditional file transfer mechanism which uploads
 * files using the API. This is done by reading from
 * {@link TransferState#getFile()} into {@link TransferState#getBuffer()}
 * and then {@link RawFileStorePrx#write(byte[], long, int) writing} to the
 * server. <em>Not thread safe</em>
 *
 * @since 5.0
 */
public class ParallelMMapUploadFileTransfer extends AbstractFileTransfer {

    public String transfer(TransferState state) throws IOException, ServerError {
        Pipe pipe = Pipe.open();
        WritableByteChannel out = pipe.sink();
        ReadableByteChannel in = pipe.source();

        final RawFileStorePrx rawFileStore = start(state);
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        final ExecutorCompletionService<Object> queue = new ExecutorCompletionService<Object>(pool);
        try {
            Producer producer = new Producer(out, state.getFile());
            Consumer consumer = new Consumer(in, state, rawFileStore);
            queue.submit(producer);
            logOnException(consumer.call());
            logOnException(queue.take().get());
            return finish(state, consumer.offset);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            cleanupUpload(rawFileStore, null);
            pool.shutdownNow();
        }
    }

    private void logOnException(Object obj) {
        if (obj instanceof Throwable) {
            Throwable t = (Throwable) obj;
            log.error("Producer/consumers raised exception", t);
        }
    }

    /**
     * Since the {@link RawFileStorePrx} instances are cleaned up after each
     * transfer, there's no need to cleanup per {@link File}.
     */
    public void afterTransfer(int errors, List<String> srcFiles) throws CleanupFailure {
        // no-op
    }

    static class Consumer implements Callable<Object> {

        private final ReadableByteChannel in;

        private final RawFileStorePrx rawFileStore;

        private final ChecksumProvider cp;

        private final TransferState state;

        private long offset = 0;

        public Consumer(ReadableByteChannel in, TransferState state,
                RawFileStorePrx rawFileStore) {
            this.in = in;
            this.state = state;
            this.rawFileStore = rawFileStore;
            this.cp = state.getChecksumProvider();
        }

        public Object call() {

            // "touch" the file otherwise zero-length files
            state.uploadStarted();
            try {
                rawFileStore.write(ArrayUtils.EMPTY_BYTE_ARRAY, offset, 0);
            } catch (ServerError e) {
                return e;
            } finally {
                state.stop();
                state.uploadBytes(offset);
            }

            try {
                ByteBuffer buffer = ByteBuffer.wrap(state.getBuffer());
                int rlen = 0;
                while (buffer.hasRemaining()) {
                    state.start();
                    rlen = in.read(buffer);
                    if (rlen == -1) {
                        break;
                    }

                    buffer.flip();
                    byte[] buf = buffer.array();
                    cp.putBytes(buf, 0, rlen);
                    final byte[] bufferToWrite;
                    if (rlen < buf.length) {
                        bufferToWrite = new byte[rlen];
                        System.arraycopy(buf, 0, bufferToWrite, 0, rlen);
                    } else {
                        bufferToWrite = buf;
                    }
                    rawFileStore.write(bufferToWrite, offset, rlen);
                    offset += rlen;
                    state.stop(rlen);
                    state.uploadBytes(offset);
                    buffer.clear();
                } 
                return Boolean.TRUE;
            } catch (Throwable t) {
                return t;
            }
        }
    }

    class Producer implements Callable<Object> {

        private final WritableByteChannel out;

        private final File file;

        public Producer(WritableByteChannel out, File file) {
              this.out = out;
              this.file = file;
        }

        public Object call() {
            FileChannel ch = null;
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(file, "r");
                ch = raf.getChannel();
                MappedByteBuffer buffer = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());
                buffer.load();
                out.write(buffer);
                buffer.clear();
                return Boolean.TRUE;
            } catch (Throwable t) {
                return t;
            } finally {
                doClose(out);
                doClose(ch);
                doClose(raf);
            }
        }

        private  void doClose(Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}