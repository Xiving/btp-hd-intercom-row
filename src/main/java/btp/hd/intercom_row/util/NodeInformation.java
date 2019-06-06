package btp.hd.intercom_row.util;

/*
 * Copyright 2018 Vrije Universiteit Amsterdam, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Objects;

public class NodeInformation {

    // constants for setting up Constellation (some are package private)
    public static String HOSTNAME = "localhost";
    public static int ID = 0;
    public static String STEALPOOL = "stealpool";
    public static String LABEL = "commonSourceIdentification";

    /*
     * All kinds of bookkeeping methods
     */

    public static void setNodeID(List<String> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).equals(HOSTNAME)) {
                ID = i;
                return;
            }
        }
    }

    public static int getNrExecutors(String property, int defaultValue) {
        String prop = System.getProperties().getProperty(property);
        return Objects.nonNull(prop)? Integer.parseInt(prop): defaultValue;
    }

    public static void setHostName() {
        try {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec("hostname");
            p.waitFor();
            BufferedReader b = new BufferedReader(new InputStreamReader(p.getInputStream()));
            HOSTNAME = b.readLine();
            b.close();
        } catch (IOException | InterruptedException e) {
        }
    }

    public static String getProcessId(final String fallback) {
        // Note: may fail in some JVM implementations
        // therefore fallback has to be provided

        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
        final int index = jvmName.indexOf('@');

        if (index < 1) {
            // part before '@' empty (index = 0) / '@' not found (index = -1)
            return fallback;
        }

        try {
            return Long.toString(Long.parseLong(jvmName.substring(0, index)));
        } catch (NumberFormatException e) {
            // ignore
        }
        return fallback;
    }

}
