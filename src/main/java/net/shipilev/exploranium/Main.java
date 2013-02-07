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
import java.util.Arrays;

public class Main {

    public static void main(String[] args) throws IOException, NoSuchPortException, UnsupportedCommOperationException, PortInUseException {
        String port = "/dev/ttyUSB0";

        System.setProperty("gnu.io.rxtx.SerialPorts", port);
        CommPortIdentifier ident = CommPortIdentifier.getPortIdentifier(port);

        RXTXPort serial = ident.open("NRSerialPort", 2000);
        serial.enableReceiveTimeout(1000);
        serial.setSerialPortParams(2400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

        InputStream in = serial.getInputStream();
        OutputStream out = serial.getOutputStream();

        out.write((byte) (0x50));
        out.flush();

        int h = in.read();
        if (h != 0xAA) {
            System.err.println("Unable to read.");
        }

        byte[] stop = new byte[16];
        for (int i = 0; i < 16; i++) {
            stop[i] = (byte) 0xAA;
        }

        // read prolog
        byte[] buf = readLine(in);
        System.err.printf("%02d/%02d/%02d %02d:%02d:%02d\n", 2000 + buf[4], buf[5], buf[6], buf[7], buf[8], buf[9]);

        // read all records
        while (!Arrays.equals(buf = readLine(in), stop)) {
            System.err.printf("%4d/%02d/%02d %02d:%02d:%02d ", 2000 + buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]);
            System.err.printf("%.2f", buf[6] / 100.0);
            System.err.printf("\n");
        }

        // read the rest
        int b;
        while ((b = in.read()) != -1) {}

        serial.getInputStream().close();
        serial.getOutputStream().close();
        serial.close();
    }

    private static byte[] readLine(InputStream in) throws IOException {
        byte[] buf = new byte[16];
        for (int i = 0; i < 16; i++) {
            int read = in.read();
            if (read == -1) {
                throw new IOException();
            }
            buf[i] = (byte) read;
        }
        return buf;
    }

}
