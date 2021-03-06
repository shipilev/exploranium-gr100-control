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

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.PrintWriter;

public class Main {

    public static void main(String[] args) throws IOException, NoSuchPortException, UnsupportedCommOperationException, PortInUseException {
        PrintWriter pw = new PrintWriter(System.out, true);

        pw.println("Exploranium GR-100 Control Software.");
        pw.println("  This is the free software. Bug reports, feedbacks and suggestions");
        pw.println("  are always welcome at:");
        pw.println("      https://github.com/shipilev/exploranium-gr100-control");
        pw.println();
        pw.println("  No warranty is provided for the item whatsoever, whether express");
        pw.println("  implied, or statutory, including, but not limited to, any warranty");
        pw.println("  of merchantability or fitness for a particular purpose or any warranty");
        pw.println("  that the contents of the item will be error-free.");
        pw.println("  You are using this software on your own risk.");
        pw.println();

        Options opts = new Options(args, pw);
        if (!opts.parse()) {
            pw.close();
            System.exit(1);
        }

        BaseReader reader = new BaseReader(opts, pw);
        try {
            if (opts.shouldGatherSpectrum()) {
                reader.gatherSpectrum();
                return;
            }
            if (opts.shouldLiveStream()) {
                reader.liveStream();
                return;
            }
            if (opts.shouldDumpSettings()) {
                reader.dumpSettings();
            }
            if (opts.shouldDumpInfo()) {
                reader.dumpDiagnostic();
            }
            if (opts.shouldDumpAlarms()) {
                reader.dumpAlarms();
            }
            if (opts.shouldDumpDose()) {
                reader.dumpDose();
            }
        } finally {
            reader.close();
            pw.close();
        }

    }


}
