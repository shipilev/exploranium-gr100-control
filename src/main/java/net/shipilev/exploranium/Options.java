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
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;

public class Options {
    private final String[] args;
    private final PrintWriter pw;
    private String port;
    private boolean liveStream;
    private boolean dumpInfo;
    private boolean dumpDose;
    private boolean dumpAlarm;
    private boolean dumpSettings;
    private boolean gatherSpectrum;
    private int spectrumDuration;
    private int spectrumSecsPerChannel;

    public Options(String[] args, PrintWriter pw) {
        this.args = args;
        this.pw = pw;
    }

    public static String selectPort() {
        Enumeration identifiers = CommPortIdentifier.getPortIdentifiers();
        if (identifiers.hasMoreElements()) {
            CommPortIdentifier o = (CommPortIdentifier)identifiers.nextElement();
            return o.getName();
        }
        return null;
    }

    public boolean parse() throws IOException {
        OptionParser parser = new OptionParser();
        parser.formatHelpWith(new OptFormatter());

        OptionSpec<String> port = parser.accepts("p", "Communication port (e.g. COM1, /dev/ttyUSB0, etc).")
                .withRequiredArg().ofType(String.class).describedAs("PORT").required();

        OptionSpec<String> spectrum = parser.accepts("s", "Gather gamma-spectrum for a given time.")
                .withOptionalArg().ofType(String.class).describedAs("(seconds, seconds-per-channel)").defaultsTo("3600,5");

        parser.accepts("l", "Live data streaming.");
        parser.accepts("d", "Dump accumulated dose log.");
        parser.accepts("i", "Dump diagnostic info.");
        parser.accepts("a", "Dump registered alarms.");
        parser.accepts("u", "Dump user settings");

        parser.accepts("v", "Be verbose.");
        parser.accepts("h", "Print this help.");

        OptionSet set;
        try {
            set = parser.parse(args);
        } catch (OptionException e) {
            pw.println("ERROR: " + e.getMessage());
            pw.println();
            parser.printHelpOn(pw);
            return false;
        }

        if (set.has("h")) {
            parser.printHelpOn(pw);
            return false;
        }

        this.port = set.valueOf(port);
        this.liveStream = set.has("l");
        this.dumpInfo = set.has("i");
        this.dumpAlarm = set.has("a");
        this.dumpDose = set.has("d");
        this.dumpSettings = set.has("u");

        if (set.has("s")) {
            this.gatherSpectrum = true;
            String s = set.valueOf(spectrum);
            String[] split = s.split(",");
            if (split.length == 1) {
                this.spectrumDuration = Integer.valueOf(split[0]);
                this.spectrumSecsPerChannel = 5;
            }
            if (split.length == 2) {
                this.spectrumDuration = Integer.valueOf(split[0]);
                this.spectrumSecsPerChannel = Integer.valueOf(split[1]);
            }
        }

        return true;
    }

    public String getPort() {
        return port;
    }

    public boolean shouldLiveStream() {
        return liveStream;
    }

    public boolean shouldGatherSpectrum() {
        return gatherSpectrum;
    }

    public int getSpectrumDuration() {
        return spectrumDuration;
    }

    public int getSpectrumSecsPerChannel() {
        return spectrumSecsPerChannel;
    }

    public boolean shouldDumpInfo() {
        return dumpInfo;
    }

    public boolean shouldDumpDose() {
        return dumpDose;
    }

    public boolean shouldDumpAlarms() {
        return dumpAlarm;
    }

    public boolean shouldDumpSettings() {
        return dumpSettings;
    }


}
