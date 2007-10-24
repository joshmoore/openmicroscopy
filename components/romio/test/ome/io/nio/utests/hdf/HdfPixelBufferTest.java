/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.io.nio.utests.hdf;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ome.io.nio.BufferFactory;
import ome.io.nio.ChunkedHdfPixelBuffer;
import ome.io.nio.HdfPixelBuffer;
import ome.io.nio.PixelBuffer;
import ome.io.nio.RomioPixelBuffer;
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

    public static String TEST;
    public static String OUTPUT;
    public static String PIXDIR;
    public static String HDF1;
    static {
        try {
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

    final static Pixels[] PIX = new Pixels[5];
    static {
        PIX[0] = new Pixels(111L);
        PIX[0].setSizeX(10);
        PIX[0].setSizeY(10);
        PIX[0].setSizeZ(3);
        PIX[0].setSizeC(3);
        PIX[0].setSizeT(7);
        PIX[0].setPixelsType(new PixelsType("int8"));
        PIX[1] = new Pixels(222L);
        PIX[1].setSizeX(100);
        PIX[1].setSizeY(100);
        PIX[1].setSizeZ(3);
        PIX[1].setSizeC(3);
        PIX[1].setSizeT(7);
        PIX[1].setPixelsType(new PixelsType("int8"));
        PIX[2] = new Pixels(333L);
        PIX[2].setSizeX(500);
        PIX[2].setSizeY(500);
        PIX[2].setSizeZ(3);
        PIX[2].setSizeC(3);
        PIX[2].setSizeT(7);
        PIX[2].setPixelsType(new PixelsType("int8"));
        PIX[3] = new Pixels(444L);
        PIX[3].setSizeX(10);
        PIX[3].setSizeY(10);
        PIX[3].setSizeZ(1);
        PIX[3].setSizeC(1);
        PIX[3].setSizeT(20);
        PIX[3].setPixelsType(new PixelsType("int8"));
        PIX[4] = new Pixels(555L);
        PIX[4].setSizeX(10);
        PIX[4].setSizeY(10);
        PIX[4].setSizeZ(1);
        PIX[4].setSizeC(1);
        PIX[4].setSizeT(200);
        PIX[4].setPixelsType(new PixelsType("int8"));
        Arrays.sort(PIX, new Comparator<Pixels>() {
            public int compare(Pixels o1, Pixels o2) {
                return o2.getId().compareTo(o1.getId());
            }

        });
    }

    @Test
    public void testCompare() throws Exception {
        for (Pixels pix : PIX) {

            System.out.println(pix + "===============================");

            Fixture romio = new RomioFixture(pix, OUTPUT);
            Fixture hdf1 = new HdfFixture(PIXDIR, HdfPixelBuffer.class, pix);
            Fixture hdf2 = new HdfFixture(PIXDIR, ChunkedHdfPixelBuffer.class,
                    pix);
            romio.run();
            hdf1.run();
            hdf2.run();
            System.gc();
            hdf2.run();
            hdf1.run();
            romio.run();

            System.out.println(new Report() + "\n\n\n");
            MonitorFactory.reset();

        }
    }

}

abstract class Fixture {

    Pixels pixels;
    PixelBuffer buffer;
    String classMon, readMon, writeMon;
    Monitor primary, read, write;

    Fixture(Pixels pixels) {
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

    public void run() throws Exception {
        open();
        setPlane();
        flush();
        getPlane();
        setRow();
        flush();
        getRow();
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
                    byte[] DATA = DATA(z, c, t);
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
                    byte[] DATA = DATA(z, c, t);
                    byte[] rv = new byte[buffer.getPlaneSize()];
                    startRead();
                    rv = buffer.getPlaneDirect(z, c, t, rv);
                    stopRead();
                    assertTrue(Arrays.equals(DATA, rv));
                }
            }
        }
    }

    private String printFirstN(byte[] rv) {
        byte[] tmp = new byte[10];
        System.arraycopy(rv, 0, tmp, 0, 10);
        return Arrays.toString(tmp);
    }

    void setRow() throws Exception {
        startWrite();
        buffer.setRow(ByteBuffer.wrap(DATA(0, 0, 0, 0)), 0, 0, 0, 0);
        stopWrite();
    }

    void getRow() throws Exception {
        byte[] DATA = DATA(0, 0, 0, 0);
        byte[] rv = new byte[buffer.getRowSize()];
        startRead();
        rv = buffer.getRowDirect(0, 0, 0, 0, rv);
        stopRead();
        assertTrue(Arrays.equals(DATA, rv));
    }

    void FILL(byte[] rv, int... is) {
        byte value = 0;
        for (int i : is) {
            value += i;
            value = (byte) (value % 127);
        }
        Arrays.fill(rv, value);
    }

    byte[] DATA(int z, int c, int t) {
        byte[] rv = new byte[buffer.getPlaneSize()];
        FILL(rv, z, c, t);
        return rv;
    }

    byte[] DATA(int y, int z, int c, int t) {
        byte[] rv = new byte[buffer.getRowSize()];
        FILL(rv, y, z, c, t);
        return rv;
    }
}

class HdfFixture extends Fixture {

    String dir;
    File hdf;
    Class<? extends PixelBuffer> type;
    Constructor<? extends PixelBuffer> ctor;

    HdfFixture(String dir, Class<? extends PixelBuffer> type, Pixels pixels)
            throws Exception {
        super(pixels);
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

    RomioFixture(Pixels pixels, String dir) throws Exception {
        super(pixels);
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