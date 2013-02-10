/*
 * #%L
 * Exploranium GR-100 Control
 * %%
 * Copyright (C) 2013 Aleksey Shipilev, and other contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package net.shipilev.exploranium;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.RXTXPort;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class BaseReader {

    public static final byte[] STOP = new byte[16];
    static {
        for (int i = 0; i < 16; i++) {
            STOP[i] = (byte) 0xAA;
        }
    }

    private final InputStream commIn;
    private final OutputStream commOut;
    private final RXTXPort serial;
    private final Options opts;
    private final PrintWriter pw;
    private final String port;
    private final List<Record> records = new ArrayList<Record>();
    private boolean read;

    public BaseReader(Options opts, PrintWriter pw) {
        this.opts = opts;
        this.pw = pw;
        try {
            port = opts.getPort();
            System.setProperty("gnu.io.rxtx.SerialPorts", port);
            CommPortIdentifier ident = CommPortIdentifier.getPortIdentifier(port);

            serial = ident.open("NRSerialPort", 2000);
            serial.enableReceiveTimeout(1000);
            serial.setSerialPortParams(2400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

            commIn = serial.getInputStream();
            commOut = serial.getOutputStream();

            tryReadOutStale();
        } catch (NoSuchPortException e) {
            throw new RuntimeException(e);
        } catch (PortInUseException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedCommOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void tryReadOutStale() {
        try {
            while (true) {
                byte[] bytes = readLine(commIn, 16);
                pw.println("Warning: stale data read: " + Arrays.toString(bytes));
            }
        } catch (IOException e) {
            // expected
        }
    }

    public void dumpDiagnostic() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        if (!read) {
            read = true;
            readAll();
        }

        pw.println("Diagnostic log:");

        for (Record r : records) {
            if (!(r instanceof AlarmRecord) && !(r instanceof DoseRecord)) {
                pw.print("  ");
                pw.println(r);
            }
        }
        pw.println();
    }

    public void dumpAlarms() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        if (!read) {
            read = true;
            readAll();
        }

        pw.println("Alarm log:");

        for (Record r : records) {
            if (r instanceof PrologueRecord || r instanceof AlarmRecord) {
                pw.print("  ");
                pw.println(r);
            }
        }
        pw.println();
    }

    public void dumpDose() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        if (!read) {
            read = true;
            readAll();
        }

        pw.println("Accumulated dose log:");

        for (Record r : records) {
            if (r instanceof PrologueRecord || r instanceof DoseRecord) {
                pw.print("  ");
                pw.println(r);
            }
        }
        pw.println();
    }

    public void liveStream() throws IOException {
        pw.println("Live counts stream:");

        serial.enableReceiveTimeout(5000); // beef up for measurement

        CircularBuffer<Timing> average5 = new CircularBuffer<Timing>(5);
        CircularBuffer<Timing> average15 = new CircularBuffer<Timing>(15);
        CircularBuffer<Timing> average60 = new CircularBuffer<Timing>(60);

        while(true) {
            commOut.write((byte) (0x43));
            commOut.flush();

            int h = commIn.read();
            if (h != 0xAA) {
                pw.println("Unable to read from " + port);
                return;
            }

            long time1 = System.currentTimeMillis();
            byte[] buf = readLine(commIn, 8);
            long time2 = System.currentTimeMillis();

            int c1 = buf[0] + (buf[1] << 8);
            int c2 = buf[2] + (buf[3] << 8);
            int c3 = buf[4] + (buf[5] << 8);
            int counts = c1 + c2 + c3;
            long duration = time2 - time1;

            Timing t = new Timing(duration, 60 * 1000 * counts);
            average5.add(t);
            average15.add(t);
            average60.add(t);

            pw.printf("  %s, %4d counts, %4d cps, %6d cpm, %6d cpm (5s), %6d cpm (15s), %6d cpm (60s)\n",
                    new Date().toString(),
                    counts, (counts * 1000) / duration, 60 * (counts * 1000) / duration,
                    cpm(average5),
                    cpm(average15),
                    cpm(average60)
                    );
        }
    }

    public long cpm(CircularBuffer<Timing> buf) {
        long totalCounts = 0;
        long totalDuration = 0;
        for (Timing t : buf.getAll()) {
            totalCounts += t.counts;
            totalDuration += t.duration;
        }

        return totalCounts / totalDuration;
    }

    private void readAll() throws IOException {
        pw.print("Reading data... ");
        pw.flush();

        pw.print(" (diagnostic) ");
        pw.flush();

        commOut.write((byte) (0x50));
        commOut.flush();

        int h = commIn.read();
        if (h != 0xAA) {
            throw new IOException("Unable to read.");
        }

        // read all records
        byte[] buf;
        while (!Arrays.equals(buf = readLine(commIn, 16), STOP)) {
            pw.print(".");
            pw.flush();
            records.add(parse(buf));
        }

        pw.print(" (dose) ");
        pw.flush();

        commOut.write((byte) (0x79));
        commOut.flush();

        h = commIn.read();
        if (h != 0xAA) {
            throw new IOException("Unable to read.");
        }

        // read all records
        while (!Arrays.equals(buf = readLine(commIn, 16), STOP)) {
            pw.print(".");
            pw.flush();
            records.add(parse(buf));
        }

        pw.println(" OK");
        pw.println();
    }

    final Record parse(byte[] buf) {
        switch (buf[15]) {
            case 0x30:
                return new PrologueRecord(buf);
            case 'P':
                return new PowerResetRecord(buf);
            case 'S':
                return new SoftResetRecord(buf);
            case 'T':
                return new TimeSetRecord(buf);
            case 'W':
                return new WatchdogResetRecord(buf);
            case 'B':
                return new NewBatteryRecord(buf);
            case 'A':
                return new AlarmRecord(buf);
            case 'D':
                return new DoseRecord(buf);
            default:
                return new UnknownRecord(buf);
        }
    }

    public void gatherSpectrum() throws IOException {
        final int CHANNELS = 41;

        int secsPerChannel = opts.getSpectrumDuration();
        serial.enableReceiveTimeout(secsPerChannel * 2 * 1000); // beef up for measurement

        pw.printf("Gathering gamma spectrum (%d seconds per channel)\n", secsPerChannel);

        commOut.write((byte) 0x53);
        commOut.flush();

        int h = commIn.read();
        if (h != 0xAA) {
            pw.println("Unable to read from " + port);
            return;
        }

        commOut.write((byte)(secsPerChannel & 0xFF));
        commOut.write((byte)((secsPerChannel >> 8) & 0xFF));
        commOut.flush();

        for (int i = 1; i <= CHANNELS; i++) {
            byte[] buf = readLine(commIn, 4);

            int c1 = (buf[0] & 0xFF) + ((buf[1] & 0xFF) << 8);
            pw.printf("  Channel %2d/%d: %d counts\n", i, CHANNELS, c1);
        }

        commOut.write((byte)0x5A);
        commOut.flush();
    }

    public void dumpSettings() throws IOException {
        commOut.write((byte)0x4A);
        commOut.flush();

        int h = commIn.read();
        if (h != 0xAA) {
            pw.println("Unable to read from " + port);
            return;
        }

        byte[] buf = readLine(commIn, 31);
        ByteBuffer b = ByteBuffer.wrap(buf);
        b.order(ByteOrder.LITTLE_ENDIAN);

        pw.println("Device settings:");

        pw.printf("  Firmware Rev.: %s\n", (char)b.get(20) + "V" + (char)b.get(21) + (char)b.get(22));
        pw.format("  Date/Time: %4d/%02d/%02d %02d:%02d:%02d\n", 2000 + b.get(24), b.get(25), b.get(26), b.get(27), b.get(28), b.get(29));

        pw.printf("  Battery voltage: %2.1fV\n", b.getShort(14) / 100f);
        pw.printf("  Temperature: %2.1fC\n", b.getShort(12) / 100f);
        pw.printf("  Screen contrast: %d\n", b.get(3));

        byte status = b.get(0);
        if (((status & ~0x10) != status)) {
            pw.println("  Units: Gy/h");
        }

        if (((status & ~0x20) != status)) {
            pw.println("  Units: R/h");
        }

        if (((status & ~0x40) != status)) {
            pw.println("  Units: Sv/h");
        }

        pw.printf("  Gamma alarm set at %2.1f sigma over background.\n", b.get(8) / 10f);
        pw.printf("  Gamma danger alarm set at %d uSv/h.\n", b.get(10));

        pw.printf("  Neutron alarm set at %d counts per 6 seconds.\n", b.get(9));
        pw.printf("  Neutron danger alarm set at %d counts per 6 seconds.\n", b.get(11));

        pw.println("  Visuals:");
        pw.printf("    vibrator %s\n", ((status & ~0x01) != status) ? "ON" : "OFF");
        pw.printf("    buzzer %s\n", ((status & ~0x02) != status) ? "ON" : "OFF");
        pw.printf("    beep %s\n", ((status & ~0x08) != status) ? "ON" : "OFF");
        pw.printf("    backlight %s\n", ((status & ~0x04) != status) ? "ON" : "OFF");

//        pw.printf("  gamma discriminator: %d\n", b.getShort(4));
//        pw.printf("  neutron discriminator: %d\n", b.getShort(6));
    }

    public static class Record {
        protected final String meta;
        protected final ByteBuffer b;

        public Record(byte[] buf, String m) {
            b = ByteBuffer.wrap(buf);
            b.order(ByteOrder.LITTLE_ENDIAN);
            meta = m;
        }

        public String toString() { return ""; }
    }

    public static class TimedRecord extends Record {
        private final String time;
        public TimedRecord(byte[] buf, String m) {
            super(buf, m);
            time = String.format("%4d/%02d/%02d %02d:%02d:%02d   %-15s ", 2000 + b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5), meta);
        }
        public String toString() {
            return super.toString() + time;
        }
    }

    public static class UnknownRecord extends Record {
        public UnknownRecord(byte[] buf) {
            super(buf, "UNKNOWN");
        }
        public String toString() {
            return super.toString() + String.format("Unknown operation code '%s': %s", (char) b.get(15), Arrays.toString(b.array()));
        }
    }

    public static class VoltageTimedRecord extends TimedRecord {
        private final int current;
        private final int voltage;
        public VoltageTimedRecord(byte[] buf, String meta) {
            super(buf, meta);
            voltage = b.getShort(6);
            current = b.getShort(8);
        }

        public String toString() {
            return super.toString() + String.format("bat=%.2fV, current=%dmA", voltage / 100.0D, current);
        }
    }

    public static class PowerResetRecord extends VoltageTimedRecord {
        public PowerResetRecord(byte[] buf) {
            super(buf, "POWER RESET");
        }
    }

    public static class SoftResetRecord extends VoltageTimedRecord {
        public SoftResetRecord(byte[] buf) {
            super(buf, "SOFT RESET");
        }
    }

    public static class TimeSetRecord extends VoltageTimedRecord {
        public TimeSetRecord(byte[] buf) {
            super(buf, "TIME SET");
        }
    }

    public static class WatchdogResetRecord extends VoltageTimedRecord {
        public WatchdogResetRecord(byte[] buf) {
            super(buf, "WATCHDOG RESET");
        }
    }

    public static class NewBatteryRecord extends VoltageTimedRecord {
        public NewBatteryRecord(byte[] buf) {
            super(buf, "NEW BATTERY");
        }
    }

    public static class DoseRecord extends TimedRecord {
        private final int dose;
        private final int time;
        public DoseRecord(byte[] buf) {
            super(buf, "DOSE");
            dose = b.getInt(6);
            time = b.getShort(10);
        }

        public String toString() {
            return super.toString() + String.format("%4d nSv, %4d sec, %5.0f nSv/h", dose, time, dose * 3600.0D / time);
        }
    }

    public static class AlarmRecord extends TimedRecord {
        private final int maxGamma;
        private final int maxDose;
        public AlarmRecord(byte[] buf) {
            super(buf, "ALARM");
            maxGamma = b.getInt(6);
            maxDose = b.getInt(10);
        }

        public String toString() {
            return super.toString() + String.format("%d cps, %d nSv/h", maxGamma, maxDose);
        }
    }

    public static class PrologueRecord extends Record {
        private final String time;
        private final String serial;
        private final String firmware;

        public PrologueRecord(byte[] buf) {
            super(buf, "");
            time = String.format("%4d/%02d/%02d %02d:%02d:%02d", 2000 + b.get(4), b.get(5), b.get(6), b.get(7), b.get(8), b.get(9));
            serial = String.valueOf(b.getShort(10));
            firmware = "" + (char) b.get(12) + "V" + (char) b.get(13) +(char) b.get(14);
        }
        public String toString() {
            return super.toString() + String.format("Log starts %s, Serial No: %5s, Firmware Rev. %5s", time, serial, firmware);
        }
    }

    private static byte[] readLine(InputStream in, int count) throws IOException {
        byte[] buf = new byte[count];
        for (int i = 0; i < count; i++) {
            int read = in.read();
            if (read == -1) {
                throw new IOException("Unable to read byte " + (i+1) + " of " + count);
            }
            buf[i] = (byte) read;
        }
        return buf;
    }


    public void close() {
        // read the rest
        try {
            int b;
            while ((b = commIn.read()) != -1) {}
        } catch (IOException e) {
            // swallow
        }

        try {
            commIn.close();
        } catch (IOException e) {
        }

        try {
            commOut.close();
        } catch (IOException e) {
        }

        serial.close();
    }

}
