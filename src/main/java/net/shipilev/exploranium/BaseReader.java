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
    private final PrintWriter pw;
    private final String port;
    private final List<Record> records = new ArrayList<Record>();

    public BaseReader(Options opts, PrintWriter pw) {
        this.pw = pw;
        try {
            port = opts.getPort();
            System.setProperty("gnu.io.rxtx.SerialPorts", port);
            CommPortIdentifier ident = CommPortIdentifier.getPortIdentifier(port);

            serial = ident.open("NRSerialPort", 2000);
            serial.enableReceiveTimeout(100);
            serial.setSerialPortParams(2400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

            commIn = serial.getInputStream();
            commOut = serial.getOutputStream();

            readAll();
        } catch (NoSuchPortException e) {
            throw new RuntimeException(e);
        } catch (PortInUseException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedCommOperationException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void dumpDiagnostic() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
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

        for (int i = 0; i < 100; i++) {
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
            pw.printf("  %s, %4d counts, %4d cps, %6d cpm\n",
                    new Date().toString(),
                    counts, (counts * 1000) / (time2 - time1), 60 * (counts * 1000) / (time2 - time1));
        }
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

        pw.println("OK");
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
                throw new IOException();
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
