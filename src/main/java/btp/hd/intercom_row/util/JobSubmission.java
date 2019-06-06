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

import java.util.List;
import java.util.ArrayList;

public class JobSubmission {

    /*
     * The following methods that fill the nodes list with the hostnames of the
     * nodes in the cluster using the Slurm job submission system.
     */

    static void parseRangeSlurm(List<String> nodes, String prefix, String inputNodes) {
        String[] nodeNumbers = inputNodes.split("-");

        if (nodeNumbers.length == 1) {
            nodes.add(prefix + nodeNumbers[0]);
        } else {
            for (int i = 0; i < nodeNumbers.length; i += 2) {
                int start = Integer.parseInt(nodeNumbers[i]);
                int end = Integer.parseInt(nodeNumbers[i + 1]);

                for (int j = start; j <= end; j++) {
                    nodes.add(String.format("%s%03d", prefix, j));
                }
            }
        }
    }

    static void parseListSlurm(List<String> nodes, String prefix, String inputNodes) {
        String[] ranges = inputNodes.split(",");

        for (String range : ranges) {
            parseRangeSlurm(nodes, prefix, range);
        }
    }

    static void parseSuffixSlurm(List<String> nodes, String prefix, String inputNodes) {
        if (inputNodes.charAt(0) == '[') {
            parseListSlurm(nodes, prefix, inputNodes.substring(1, inputNodes.length() - 1));
        } else {
            nodes.add(prefix + inputNodes);
        }
    }

    static void parseNodesSlurm(List<String> nodes, String inputNodes) {
        if (inputNodes.startsWith("node")) {
            parseSuffixSlurm(nodes, "node", inputNodes.substring(4));
        }
    }

    /*
     * The following methods fill the nodes list with the hostnames of the nodes
     * in the cluster using the SGE job submission system.
     */

    static void parseNodesSGE(List<String> nodes) {
        String inputNodes = System.getenv("PRUN_HOSTNAMES");
        for (String s : inputNodes.split(" ")) {
            nodes.add(s.substring(0, 7));
        }
    }

    public static List<String> getNodes() {
        List<String> nodes = new ArrayList<String>();

        String inputNodes = System.getenv("SLURM_JOB_NODELIST");
        if (inputNodes == null) {
            parseNodesSGE(nodes);
        } else {
            parseNodesSlurm(nodes, inputNodes);
        }
        if (nodes.isEmpty()) {
            nodes.add(NodeInformation.HOSTNAME);
        }

        return nodes;
    }
}

