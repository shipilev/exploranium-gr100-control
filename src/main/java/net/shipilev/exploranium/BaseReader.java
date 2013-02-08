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
import java.util.Arrays;

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
            serial.enableReceiveTimeout(1000);
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

    public void dump() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
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
            switch (buf[15]) {
                case 'P':
                case 'S':
                    parseP(buf);
                    break;
                case 'A':
                    parseA(buf);
                    break;
                default:
                    pw.printf("Unknown operation code '%s': %s\n", (char) buf[15], Arrays.toString(buf));
            }
        }

    }

    public void live() throws IOException {
        for (int i = 0; i < 100; i++) {
            commOut.write((byte) (0x43));
            commOut.flush();

            int h = commIn.read();
            if (h != 0xAA) {
                pw.println("Unable to read from " + port);
                return;
            }

            byte[] buf = readLine(commIn, 8);
            pw.println(Arrays.toString(buf));
        }
    }

    private void parseA(byte[] buf) {
        int maxGamma = (buf[6]) + (buf[7] << 8) + (buf[8] << 16) + (buf[9] << 24);
        int maxDose =  (buf[10]) + (buf[11] << 8) + (buf[12] << 16) + (buf[13] << 24);

        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
        pw.printf("%-10s %d cps, %d nSv/h", "ALARM", maxGamma, maxDose);
        pw.printf("\n");
    }

    private void parseP(byte[] buf) {
        pw.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
        int voltage = (buf[6] & 0xFF);
        short current = (short) (((short)buf[7] & 0xFF) | (((short)buf[8] & 0xFF) << 8));
        pw.printf("%s ", Integer.toBinaryString(current));
        pw.printf("%-10s %.2fV %d mA", "POWER-ON", voltage / 100.D, current);
        pw.printf("\n");
    }

    private static void parseS(PrintStream out, byte[] buf) {
//        out.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
//        out.printf("%-10s %d cps, %d nSv/h", "ALARM", maxGamma, maxDose);
//        out.printf("\n");
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
