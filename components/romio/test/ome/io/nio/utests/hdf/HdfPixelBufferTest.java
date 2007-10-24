/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio.utests.hdf;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ome.io.nio.BufferFactory;
import ome.io.nio.DeltaVision;
import ome.io.nio.DimensionsOutOfBoundsException;
import ome.io.nio.HdfPixelBuffer;
import ome.io.nio.PixelBuffer;
import ome.io.nio.RomioPixelBuffer;
import ome.io.nio.utests.SkeletonPixelsBuffer;
import ome.model.core.OriginalFile;
import ome.model.core.Pixels;
import ome.model.enums.PixelsType;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.util.ResourceUtils;
import org.testng.annotations.Test;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;
import com.jamonapi.proxy.MonProxyFactory;

/**
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 * @see PixelBuffer
 */
@Test(groups = "manual")
public class HdfPixelBufferTest {

    // Disabling logging in order to have a closer
    // performance comparison
    static {
        Logger.getRootLogger().setLevel(Level.OFF);
        Logger.getLogger(RomioPixelBuffer.class).setLevel(Level.OFF);
    }

    public static String TINY;
    public static String TEST;
    public static String OUTPUT;
    public static String PIXDIR;
    public static String HDF1;
    static {
        try {
            TINY = ResourceUtils.getFile("classpath:tinyTest.d3d.dv")
                    .getAbsolutePath();
            TEST = ResourceUtils.getFile("classpath:test.h5").getAbsolutePath();
            OUTPUT = ResourceUtils.getFile(TEST).getParent() + File.separator;
            PIXDIR = OUTPUT + File.separator + "Pixels" + File.separator;
            HDF1 = PIXDIR + "hdf1.h5";
            // Also make sure PIXDIR exists
            ResourceUtils.getFile(PIXDIR).mkdir();
        } catch (Exception e) {
            PrintWriter pw = new PrintWriter(new StringWriter());
            e.printStackTrace(pw);
            System.err.println(pw.toString());
        }
    }

    @Test
    public void testFixHyperCube() throws Exception {
        Fixture source = new SyntheticFixture(1, 3, 3, 3, 3, 3, "int16");
        Fixture simple = new HdfFixture(source.buffer, PIXDIR,
                HdfPixelBuffer.class, source.pixels);
        simple.open();
        simple.setPlane();
        simple.flush();
        byte[] expected = source.buffer.getHypercubeDirect(0, 3, 0, 3, 1, 2, 1,
                2, 0, 2);
        byte[] actual = simple.buffer.getHypercubeDirect(0, 3, 0, 3, 1, 2, 1,
                2, 0, 2);
        simple.arraysEqual(expected, actual);

    }

    @Test
    public void testCompare() throws Exception {
        List<Fixture> sources = new ArrayList<Fixture>();
        sources.add(new SyntheticFixture(111, 10, 10, 3, 3, 7, "int16"));
        sources.add(new SyntheticFixture(222, 100, 100, 3, 3, 7, "int16"));
        sources.add(new SyntheticFixture(333, 200, 200, 3, 3, 7, "int16"));
        sources.add(new SyntheticFixture(444, 10, 10, 1, 1, 20, "int16"));
        sources.add(new SyntheticFixture(555, 10, 10, 1, 1, 200, "int16"));

        for (Fixture source : sources) {

            System.out.println(source.pixels
                    + "===============================");

            List<Fixture> fixtures = new ArrayList<Fixture>();
            fixtures
                    .add(new RomioFixture(source.buffer, source.pixels, OUTPUT));
            // fixtures.add(new HdfFixture(source.buffer, PIXDIR,
            // ChunkedHdfPixelBuffer.class, source.pixels));
            fixtures.add(new HdfFixture(source.buffer, PIXDIR,
                    HdfPixelBuffer.class, source.pixels));
            // fixtures.add(new DeltaVisionFixture(source.buffer, TINY));

            for (Fixture fixture : fixtures) {
                System.out.println(fixture + "------------");
                // Sizes
                Random r = new Random();

                fixture.run(1L);
                System.gc();
            }

            System.out.println(new Report() + "\n\n\n");
            MonitorFactory.reset();

        }
    }

}

abstract class Fixture {

    PixelBuffer source;
    Pixels pixels;
    PixelBuffer buffer;
    String classMon, readMon, writeMon;
    Monitor primary, read, write;

    Fixture(PixelBuffer source, Pixels pixels) {
        this.source = source;
        this.pixels = pixels;
    }

    protected void open() throws Exception {
        doOpen();
        init();
    }

    protected void init() throws Exception {
        classMon = buffer.getClass().getName() + ".primary";
        readMon = buffer.getClass().getName() + ".read";
        writeMon = buffer.getClass().getName() + ".write";
        buffer = (PixelBuffer) MonProxyFactory.monitor(buffer);
    }

    abstract void doOpen() throws Exception;

    abstract void flush() throws Exception;

    abstract void close() throws Exception;

    public byte[] getPlane(int z, int c, int t) throws Exception {
        byte[] buffer = new byte[source.getPlaneSize()];
        buffer = source.getPlaneDirect(z, c, t, buffer);
        return buffer;
    }

    public byte[] getRow(int y, int z, int c, int t) throws Exception {
        byte[] buffer = new byte[source.getRowSize()];
        buffer = source.getRowDirect(y, z, c, t, buffer);
        return buffer;
    }

    public byte[] getHypercube(int x1, int x2, int y1, int y2, int z1, int z2,
            int c1, int c2, int t1, int t2) throws Exception {
        byte[] buffer = new byte[source.getHypercubeSize(x2, y2, z2, c2, t2)];
        buffer = source.getHypercubeDirect(x1, x2, y1, y2, z1, z2, c1, c2, t1,
                t2);
        return buffer;
    }

    public void run(long seed) throws Exception {
        open();
        setPlane(); // initializes all
        flush();
        getPlane();
        setRow();
        flush();
        getRow();
        flush();
        // getStack();
        getHypercube(seed);
        close();
    }

    public void start() {
        primary = MonitorFactory.startPrimary(classMon);
    }

    public void startRead() {
        start();
        read = MonitorFactory.start(readMon);
    }

    public void startWrite() {
        start();
        write = MonitorFactory.start(writeMon);
    }

    public void stop() {
        primary.stop();
    }

    public void stopRead() {
        read.stop();
        stop();
    }

    public void stopWrite() {
        write.stop();
        stop();
    }

    void setPlane() throws Exception {
        for (int z = 0; z < buffer.getSizeZ(); z++) {
            for (int c = 0; c < buffer.getSizeC(); c++) {
                for (int t = 0; t < buffer.getSizeT(); t++) {
                    byte[] DATA = getPlane(z, c, t);
                    startWrite();
                    buffer.setPlane(DATA, z, c, t);
                    stopWrite();
                }
            }
        }
    }

    void getPlane() throws Exception {
        for (int z = 0; z < buffer.getSizeZ(); z++) {
            for (int c = 0; c < buffer.getSizeC(); c++) {
                for (int t = 0; t < buffer.getSizeT(); t++) {
                    byte[] DATA = getPlane(z, c, t);
                    byte[] rv = new byte[buffer.getPlaneSize()];
                    startRead();
                    rv = buffer.getPlaneDirect(z, c, t, rv);
                    stopRead();
                    arraysEqual(DATA, rv);
                }
            }
        }
    }

    public void arraysEqual(byte[] DATA, byte[] rv) {
        assertTrue(Arrays.equals(DATA, rv), buffer + ":" + printFirstN(rv)
                + " is not " + printFirstN(DATA));
    }

    private String printFirstN(byte[] rv) {
        int sz = Math.min(rv.length, 100);
        byte[] tmp = new byte[sz];
        System.arraycopy(rv, 0, tmp, 0, sz);
        return Arrays.toString(tmp) + " (+ " + (rv.length - sz) + ") ";
    }

    void setRow() throws Exception {
        startWrite();
        buffer.setRow(ByteBuffer.wrap(getRow(0, 0, 0, 0)), 0, 0, 0, 0);
        stopWrite();
    }

    void getRow() throws Exception {
        byte[] DATA = getRow(0, 0, 0, 0);
        byte[] rv = new byte[buffer.getRowSize()];
        startRead();
        rv = buffer.getRowDirect(0, 0, 0, 0, rv);
        stopRead();
        arraysEqual(DATA, rv);
    }

    void getHypercube(long seed) throws Exception {
        Random r = new Random(seed);
        int x2 = r.nextInt(Math.min(10, pixels.getSizeX() - 1)) + 1;
        int y2 = r.nextInt(Math.min(10, pixels.getSizeY() - 1)) + 1;
        int z2 = r.nextInt(Math.min(10, pixels.getSizeZ() - 1)) + 1;
        int c2 = r.nextInt(Math.min(10, pixels.getSizeC() - 1)) + 1;
        int t2 = r.nextInt(Math.min(10, pixels.getSizeT() - 1)) + 1;

        for (int x1 = 0; x1 < pixels.getSizeX() - x2; x1++) {
            for (int y1 = 0; y1 < buffer.getSizeY() - y2; y1++) {
                for (int z1 = 0; z1 < buffer.getSizeZ() - z2; z1++) {
                    for (int c1 = 0; c1 < buffer.getSizeC() - c2; c1++) {
                        for (int t1 = 0; t1 < buffer.getSizeT() - t2; t1++) {
                            byte[] DATA = getHypercube(x1, x2, y1, y2, z1, z2,
                                    c1, c2, t1, t2);
                            byte[] rv = new byte[buffer.getHypercubeSize(x2,
                                    y2, z2, c2, t2)];
                            startRead();
                            rv = buffer.getHypercubeDirect(x1, x2, y1, y2, z1,
                                    z2, c1, c2, t1, t2);
                            stopRead();
                            arraysEqual(DATA, rv);
                        }
                    }
                }
            }
        }
    }
    // void getStack() throws Exception {
    // for (int c1 = 0; c1 < buffer.getSizeC() - c2; c1++) {
    // for (int t1 = 0; t1 < buffer.getSizeT() - t2; t1++) {
    // byte[] DATA = getHypercube(x1, x2, y1, y2, z1, z2, c1, c2, t1,
    // t2);
    // byte[] rv = new byte[buffer
    // .getHypercubeSize(x2, y2, z2, c2, t2)];
    // startRead();
    // rv = buffer.getHypercubeDirect(x1, x2, y1, y2, z1, z2, c1, c2,
    // t1, t2);
    // stopRead();
    // arraysEqual(DATA, rv);
    // }
    // }
    // }

}

class HdfFixture extends Fixture {

    String dir;
    File hdf;
    Class<? extends PixelBuffer> type;
    Constructor<? extends PixelBuffer> ctor;

    HdfFixture(PixelBuffer source, String dir,
            Class<? extends PixelBuffer> type, Pixels pixels) throws Exception {
        super(source, pixels);
        this.dir = dir;
        this.type = type;
        ctor = this.type.getConstructor(String.class, Pixels.class);
        hdf = ResourceUtils.getFile(dir + this.type.getName() + "."
                + pixels.getId() + ".h5");

    }

    void makeBuffer() throws Exception {
        buffer = ctor.newInstance(hdf.getAbsolutePath(), this.pixels);
    }

    @Override
    void doOpen() throws Exception {
        if (hdf.exists()) {
            hdf.delete();
        }
        hdf.createNewFile();
        makeBuffer();
    }

    @Override
    void flush() throws Exception {
        buffer.close();
        makeBuffer();
        init();
    }

    @Override
    void close() throws Exception {
        if (buffer != null) {
            try {
                buffer.close();
            } catch (Exception e) {
                System.err.println("error closing: " + e.getMessage());
                buffer = null;
            }

        }
    }
}

class RomioFixture extends Fixture {

    final String dir;
    final BufferFactory factory;

    RomioFixture(PixelBuffer source, Pixels pixels, String dir)
            throws Exception {
        super(source, pixels);
        this.dir = dir;
        factory = new BufferFactory(dir);
    }

    @Override
    void doOpen() throws Exception {
        File file = ResourceUtils
                .getFile(factory.getPixelsPath(pixels.getId()));
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();
        buffer = factory.createPixelBuffer(this.pixels);
    }

    @Override
    void flush() throws Exception {
        buffer.close();
        buffer = factory.getPixelBuffer(pixels);
        init();
    }

    @Override
    void close() throws Exception {
        buffer.close();
    }

}

class DeltaVisionFixture extends Fixture {
    final String file;

    DeltaVisionFixture(PixelBuffer source, String file) {
        super(source, null);
        this.file = file;
    }

    @Override
    void doOpen() throws Exception {
        buffer = new DeltaVision(file, new OriginalFile());
    }

    @Override
    void flush() throws Exception {
        // noop
    }

    @Override
    void close() throws Exception {
        buffer.close();
    }
}

class SyntheticFixture extends Fixture {

    static Pixels pixels(long id, int x, int y, int z, int c, int t, String type) {
        Pixels p = new Pixels(id);
        p.setSizeX(x);
        p.setSizeY(y);
        p.setSizeZ(z);
        p.setSizeC(c);
        p.setSizeT(t);
        p.setPixelsType(new PixelsType(type));
        return p;
    }

    /** Calls {@link #open()} */
    public SyntheticFixture(long id, int x, int y, int z, int c, int t,
            String type) throws Exception {
        super(null, pixels(id, x, y, z, c, t, type));
        open();
    }

    byte[] buffer(int size, byte value) {
        byte[] buf = new byte[size];
        Arrays.fill(buf, value);
        return buf;
    }

    byte fillValue(int z, int c, int t) {
        return (byte) ((z + 1) * 100 + (c + 1) * 10 + t + 1);
    }

    @Override
    void doOpen() throws Exception {
        buffer = new SkeletonPixelsBuffer("", this.pixels) {
            @Override
            public byte[] getPlaneDirect(Integer z, Integer c, Integer t,
                    byte[] buffer) throws IOException,
                    DimensionsOutOfBoundsException {
                return buffer(getPlaneSize(), fillValue(z, c, t));
            }

            @Override
            public byte[] getRowDirect(Integer y, Integer z, Integer c,
                    Integer t, byte[] buffer) throws IOException,
                    DimensionsOutOfBoundsException {
                return buffer(getRowSize(), fillValue(z, c, t));
            }

            @Override
            public byte[] getHypercubeDirect(int startX, int sizeX, int startY,
                    int sizeY, int startZ, int sizeZ, int startC, int sizeC,
                    int startT, int sizeT) throws IOException,
                    DimensionsOutOfBoundsException, BufferOverflowException {

                byte[] buf = buffer(getHypercubeSize(sizeX, sizeY, sizeZ,
                        sizeC, sizeT), (byte) 0);
                int buf_offset = 0;
                for (int t = startT; t < startT + sizeT; t++) {
                    for (int c = startC; c < startC + sizeC; c++) {
                        for (int z = startZ; z < startZ + sizeZ; z++) {
                            for (int y = startY; y < startY + sizeY; y++) {
                                byte[] row = getRowDirect(y, z, c, t, null);
                                System.arraycopy(row, 0, buf, buf_offset, sizeX
                                        * buffer.getByteWidth());
                                buf_offset += sizeX * buffer.getByteWidth();
                            }
                        }
                    }
                }
                return buf;
            }
        };

    }

    @Override
    void flush() throws Exception {
        // noop
    }

    @Override
    void close() throws Exception {
        // noop
    }
}

class Report {

    static int LABEL = 0;
    static int HITS = 1;
    static int AVG = 2;
    static int TOTAL = 3;
    static int STDDEV = 4;
    static int LASTVALUE = 5;
    static int MIN = 6;
    static int MAX = 7;
    static int ACTIVE = 8;
    static int AVGACTIVE = 9;
    static int MAXACTIVE = 10;
    static int FIRSTACCESS = 11;
    static int LASTACCESS = 12;

    String[] header;
    Object[][] data;

    public Report() {
        header = MonitorFactory.getHeader();
        data = MonitorFactory.getData();
    }

    @Override
    public String toString() {
        int[] labels = new int[] { AVG, TOTAL, MIN, MAX, LABEL, HITS };
        StringBuilder sb = new StringBuilder();
        for (int l : labels) {
            sb.append(header[l]);
            for (int i = 0; i < 8 - header[l].length(); i++) {
                sb.append(" ");
            }
            sb.append("\t");
        }
        sb.append("\n");

        Map<String, String> ordering = new HashMap<String, String>();
        for (int i = 0; i < data.length; i++) {
            StringBuilder line = new StringBuilder();
            for (int l : labels) {
                Object d = data[i][l];
                if (d instanceof Double) {
                    line.append(String.format("%3.2e\t", (Double) d));
                } else {
                    line.append(d + "\t");
                }
            }
            line.append("\n");
            ordering.put((String) data[i][LABEL], line.toString());
        }
        List<String> keys = new ArrayList<String>(ordering.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            sb.append(ordering.get(key));
        }

        return sb.toString();
    }

}