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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;

public class BaseReader {

    private final InputStream commIn;
    private final OutputStream commOut;
    private final RXTXPort serial;
    private final PrintWriter pw;
    private final String port;

    public BaseReader(Options opts, PrintWriter pw) {
        this.pw = pw;
        try {
            port = opts.getPort();
            System.setProperty("gnu.io.rxtx.SerialPorts", port);
            CommPortIdentifier ident = CommPortIdentifier.getPortIdentifier(port);

            serial = ident.open("NRSerialPort", 2000);
            serial.enableReceiveTimeout(5000);
            serial.setSerialPortParams(2400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

            commIn = serial.getInputStream();
            commOut = serial.getOutputStream();
        } catch (NoSuchPortException e) {
            throw new RuntimeException(e);
        } catch (PortInUseException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedCommOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public void dumpDiagnostic() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        pw.println("Diagnostic log:");

        commOut.write((byte) (0x50));
        commOut.flush();

        int h = commIn.read();
        if (h != 0xAA) {
            pw.println("Unable to read.");
        }

        byte[] stop = new byte[16];
        for (int i = 0; i < 16; i++) {
            stop[i] = (byte) 0xAA;
        }

        // read prolog
        byte[] buf = readLine(commIn, 16);
        pw.printf("%02d/%02d/%02d %02d:%02d:%02d\n", 2000 + buf[4], buf[5], buf[6], buf[7], buf[8], buf[9]);

        // read all records
        while (!Arrays.equals(buf = readLine(commIn, 16), stop)) {
            ByteBuffer bbb = ByteBuffer.wrap(buf);
            bbb.order(ByteOrder.LITTLE_ENDIAN);
            switch (bbb.get(15)) {
                case 'P':
                    parseP(bbb);
                    break;
                case 'S':
                    parseS(bbb);
                    break;
                case 'A':
                    // omit
                    break;
                case 'B':
                    parseB(bbb);
                    break;
                case 'W':
                    parseW(bbb);
                    break;
                case 'T':
                    parseT(bbb);
                    break;
                default:
                    pw.printf("Unknown operation code '%s': %s\n", (char) buf[15], Arrays.toString(buf));
            }
        }
    }

    public void dumpAlarms() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        pw.println("Alarm log:");

        commOut.write((byte) (0x50));
        commOut.flush();

        int h = commIn.read();
        if (h != 0xAA) {
            pw.println("Unable to read.");
        }

        byte[] stop = new byte[16];
        for (int i = 0; i < 16; i++) {
            stop[i] = (byte) 0xAA;
        }

        // read prolog
        byte[] buf = readLine(commIn, 16);
        pw.printf("%02d/%02d/%02d %02d:%02d:%02d\n", 2000 + buf[4], buf[5], buf[6], buf[7], buf[8], buf[9]);

        // read all records
        while (!Arrays.equals(buf = readLine(commIn, 16), stop)) {
            ByteBuffer bbb = ByteBuffer.wrap(buf);
            bbb.order(ByteOrder.LITTLE_ENDIAN);
            switch (bbb.get(15)) {
                case 'P':
                case 'S':
                case 'B':
                case 'W':
                case 'T':
                    // omit
                    break;
                case 'A':
                    parseA(bbb);
                    break;
                default:
                    pw.printf("Unknown operation code '%s': %s\n", (char) buf[15], Arrays.toString(buf));
            }
        }

    }

    public void dumpDose() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        pw.println("Accumulated dose log:");

        commOut.write((byte) (0x79));
        commOut.flush();

        int h = commIn.read();
        if (h != 0xAA) {
            pw.println("Unable to read.");
        }

        byte[] stop = new byte[16];
        for (int i = 0; i < 16; i++) {
            stop[i] = (byte) 0xAA;
        }

        // read prolog
        byte[] buf = readLine(commIn, 16);
        pw.printf("%02d/%02d/%02d %02d:%02d:%02d\n", 2000 + buf[4], buf[5], buf[6], buf[7], buf[8], buf[9]);

        // read all records
        while (!Arrays.equals(buf = readLine(commIn, 16), stop)) {
            ByteBuffer bbb = ByteBuffer.wrap(buf);
            bbb.order(ByteOrder.LITTLE_ENDIAN);
            switch (bbb.get(15)) {
                case 'D':
                    parseD(bbb);
                    break;
                default:
                    pw.printf("Unknown operation code '%s': %s\n", (char) buf[15], Arrays.toString(buf));
            }
        }

    }

    public void liveStream() throws IOException {
        pw.println("Live counts stream:");
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

    private void parseA(ByteBuffer buf) {
        int maxGamma = buf.getInt(6);
        int maxDose = buf.getInt(10);

        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        pw.printf("%-20s peak %d cps, %d nSv/h", "ALARM", maxGamma, maxDose);
        pw.printf("\n");
    }

    private void parseD(ByteBuffer buf) {
        int dose = buf.getInt(6);
        int time = buf.getShort(10);

        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        pw.printf("%4d nSv, %4d sec, %5.0f nSv/h", dose, time, dose * 3600.0D / time);
        pw.printf("\n");
    }

    private void parseP(ByteBuffer buf) {
        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        int voltage = buf.getShort(6);
        int current = buf.getShort(8);
        pw.printf("%-20s bat=%.2fV drain=%dmA", "POWER-ON RESET", voltage / 100.D, current);
        pw.printf("\n");
    }

    private void parseS(ByteBuffer buf) {
        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        int voltage = buf.getShort(6);
        int current = buf.getShort(8);
        pw.printf("%-20s bat=%.2fV drain=%dmA", "SOFTWARE RESET", voltage / 100.D, current);
        pw.printf("\n");
    }

    private void parseB(ByteBuffer buf) {
        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        int voltage = buf.getShort(6);
        int current = buf.getShort(8);
        pw.printf("%-20s bat=%.2fV drain=%dmA", "NEW BATTERY", voltage / 100.D, current);
        pw.printf("\n");
    }

    private void parseW(ByteBuffer buf) {
        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        int voltage = buf.getShort(6);
        int current = buf.getShort(8);
        pw.printf("%-20s bat=%.2fV drain=%dmA", "WATCHDOG RESET", voltage / 100.D, current);
        pw.printf("\n");
    }

    private void parseT(ByteBuffer buf) {
        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf.get(0), buf.get(1), buf.get(2), buf.get(3), buf.get(4), buf.get(5));
        int voltage = buf.getShort(6);
        int current = buf.getShort(8);
        pw.printf("%-20s bat=%.2fV drain=%dmA", "TIME SET", voltage / 100.D, current);
        pw.printf("\n");
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
