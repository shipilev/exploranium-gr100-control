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
import java.util.Arrays;

public class Main {

    public static void main(String[] args) throws IOException, NoSuchPortException, UnsupportedCommOperationException, PortInUseException {
        live();
    }

    public static void dump() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        String port = "/dev/ttyUSB0";

        System.setProperty("gnu.io.rxtx.SerialPorts", port);
        CommPortIdentifier ident = CommPortIdentifier.getPortIdentifier(port);

        RXTXPort serial = ident.open("NRSerialPort", 2000);
        serial.enableReceiveTimeout(1000);
        serial.setSerialPortParams(2400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

        InputStream commIn = serial.getInputStream();
        OutputStream commOut = serial.getOutputStream();

        commOut.write((byte) (0x50));
        commOut.flush();

        int h = commIn.read();
        PrintStream out = System.err;
        if (h != 0xAA) {
            out.println("Unable to read.");
        }

        byte[] stop = new byte[16];
        for (int i = 0; i < 16; i++) {
            stop[i] = (byte) 0xAA;
        }

        // read prolog
        byte[] buf = readLine(commIn, 16);
        out.printf("%02d/%02d/%02d %02d:%02d:%02d\n", 2000 + buf[4], buf[5], buf[6], buf[7], buf[8], buf[9]);

        // read all records
        while (!Arrays.equals(buf = readLine(commIn, 16), stop)) {
            switch (buf[15]) {
                case 'P':
                case 'S':
                    parseP(out, buf);
                    break;
                case 'A':
                    parseA(out, buf);
                    break;
                default:
                    out.printf("Unknown operation code '%s': %s\n", (char)buf[15], Arrays.toString(buf));
            }
        }

        // read the rest
        int b;
        while ((b = commIn.read()) != -1) {}

        commIn.close();
        commOut.close();
        serial.close();
    }

    public static void live() throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        String port = "/dev/ttyUSB0";

        System.setProperty("gnu.io.rxtx.SerialPorts", port);
        CommPortIdentifier ident = CommPortIdentifier.getPortIdentifier(port);

        RXTXPort serial = ident.open("NRSerialPort", 2000);
        serial.enableReceiveTimeout(5000);
        serial.setSerialPortParams(2400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

        InputStream commIn = serial.getInputStream();
        OutputStream commOut = serial.getOutputStream();

        PrintStream out = System.err;
        for (int i = 0; i < 100; i++) {
            commOut.write((byte) (0x43));
            commOut.flush();

            int h = commIn.read();
            if (h != 0xAA) {
                out.println("Unable to read.");
                return;
            }

            byte[] buf = readLine(commIn, 8);
            out.println(Arrays.toString(buf));
        }

        commIn.close();
        commOut.close();
        serial.close();
    }

    private static void parseA(PrintStream out, byte[] buf) {
        int maxGamma = (buf[6]) + (buf[7] << 8) + (buf[8] << 16) + (buf[9] << 24);
        int maxDose =  (buf[10]) + (buf[11] << 8) + (buf[12] << 16) + (buf[13] << 24);

        out.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
        out.printf("%-10s %d cps, %d nSv/h", "ALARM", maxGamma, maxDose);
        out.printf("\n");
    }

    private static void parseP(PrintStream out, byte[] buf) {
        out.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
        int voltage = (buf[6] & 0xFF);
        short current = (short) (((short)buf[7] & 0xFF) | (((short)buf[8] & 0xFF) << 8));
        out.printf("%s ", Integer.toBinaryString(current));
        out.printf("%-10s %.2fV %d mA", "POWER-ON", voltage / 100.D, current);
        out.printf("\n");
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

}
