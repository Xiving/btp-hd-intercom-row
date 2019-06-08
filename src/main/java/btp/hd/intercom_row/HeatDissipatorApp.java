package btp.hd.intercom_row;

import btp.hd.intercom_row.Activity.MonitorActivity;
import btp.hd.intercom_row.Activity.StencilActivity;
import btp.hd.intercom_row.model.*;
import btp.hd.intercom_row.model.event.StartEvent;
import btp.hd.intercom_row.util.HeatValueGenerator;
import btp.hd.intercom_row.util.JobSubmission;
import btp.hd.intercom_row.util.NodeInformation;
import ibis.constellation.*;
import ibis.constellation.util.MultiEventCollector;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeatDissipatorApp {

    public static void writeFile(int it, double min, int w, int h, double ms, TempChunk temp) {
        try {
            PrintStream out = new PrintStream("heat-dissipator.out");

            out.println("Performed intercom row heat dissipator sim");
            out.println(String.format("Iterations: %d, min temp delta: %f", it, min));
            out.println(String.format("Dimensions: %d x %d, time: %f ms\n", h, w, ms));
            out.println(temp.toString());
            out.close();
        } catch (FileNotFoundException e) {
            log.error(e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {

        // Default config
        int nrExecutorsPerNode = 1;
        double minDifference = 10;
        int maxIterations = Integer.MAX_VALUE;
        int height = 10;
        int width = 10;

        log.info("Input: {}", Arrays.toString(args));

        // overwrite defaults with input arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-e":
                    nrExecutorsPerNode = Integer.parseInt(args[i + 1]);
                    break;
                case "-d":
                    minDifference = Double.parseDouble(args[i + 1]);
                    break;
                case "-m":
                    maxIterations = Integer.parseInt(args[i + 1]);
                    break;
                case "-h":
                    height = Integer.parseInt(args[i + 1]);
                    break;
                case "-w":
                    width = Integer.parseInt(args[i]);
                    break;
                default:
                    throw new Error("Usage: java HeatDissipatorApp "
                        + "[ -e <nrOfExecutors> ]"
                        + "[ -d <minDelta> ]"
                        + "[ -m <maxIteration> ]"
                        + "[ -h <height> ]"
                        + "[ -w <width> ]");
            }
            i++;
        }

        Constellation constellation = activateContellation(nrExecutorsPerNode);

        if (constellation.isMaster()) {
            // Acquire heat info
            // todo: from file
            CylinderSlice slice = createCylinder(height, width);
            MultiEventCollector mec = createCollector(JobSubmission.getNodes(), nrExecutorsPerNode);
            ActivityIdentifier aid = constellation.submit(mec);

            // create activities
            List<StencilActivity> activities = createActivities(
                aid,
                slice,
                JobSubmission.getNodes(),
                nrExecutorsPerNode
            );

            // submit activities
            for (StencilActivity activity : activities) {
                constellation.submit(activity);
            }

            MonitorActivity monitor = new MonitorActivity(
                maxIterations,
                minDifference,
                activities.stream().map(Activity::identifier).collect(Collectors.toList())
            );

            constellation.submit(monitor);

            for (int i = 0; i < activities.size(); i++) {
                ActivityIdentifier upper = (i == 0) ? null : activities.get(i - 1).identifier();
                ActivityIdentifier lower = (i == activities.size() - 1) ? null : activities.get(i + 1).identifier();
                activities.get(i).init(upper, lower, monitor.identifier());
            }

            Timer overallTimer = constellation.getOverallTimer();
            int timing = overallTimer.start();

            constellation.send(new Event(aid, monitor.identifier(), new StartEvent()));

            log.debug(
                "main(), just submitted, about to waitForEvent() for any event with target "
                    + aid);

            TempResult result = TempResult.of(slice);

            Event[] event = mec.waitForEvents();
            log.info("main(), received results on identifier " + aid);

            Stream.of(event).forEach(e -> {
                    log.debug("Adding chunk {} to result", e);
                    result.add((TempResult) e.getData());
                }
            );

            overallTimer.stop(timing);

            log.info("Result of size {} x {} after {} iteration(s) and {} ms:\n{}", result.height(),
                result.width(), result.getIteration(), overallTimer.totalTimeVal() / 1000,
                result.toString());
            writeFile(result.getIteration(), minDifference, result.width(), result.height(),
                overallTimer.totalTimeVal() / 1000, result);
        }

        constellation.done();
        log.debug("called Constellation.done()");
    }

    private static List<StencilActivity> createActivities(ActivityIdentifier parent, CylinderSlice slice, List<String> nodeNames, int activitiesPerNode) {
        int nrOfActivities = nodeNames.size() * activitiesPerNode;
        List<StencilActivity> activities = new ArrayList<>(nrOfActivities);

        for (int i = 0; i < nrOfActivities; i++) {
            activities.add(null);
        }

        int currentRow = 1;
        int rows = slice.height() - 1;

        for (int i = 0; i < nrOfActivities; i++) {
            String node = nodeNames.get((int) Math.floor(i / activitiesPerNode));
            int activitiesLeft = nrOfActivities - i;
            int until = currentRow + (int) Math.ceil(((double) rows - currentRow) / activitiesLeft);

            CylinderSlice nextSlice = CylinderSlice.of(slice, currentRow - 1, until + 1);
            StencilActivity activity = new StencilActivity(parent, StencilActivity.LABEL + node,
                nextSlice);
            activities.set(i, activity);

            currentRow = until;
        }
        return activities;
    }

    private static Constellation activateContellation(int nrExecutors)
        throws ConstellationCreationException {
        NodeInformation.setHostName();

        // Create context for the master node
        OrContext orContext = new OrContext(
            new Context(StencilActivity.LABEL + NodeInformation.HOSTNAME),    // One for every node
            new Context(MonitorActivity.LABEL)              // Only one on the master node
        );

        // Initialize Constellation with the following configurations
        ConstellationConfiguration config = new ConstellationConfiguration(
            orContext,
            StealStrategy.SMALLEST,
            StealStrategy.BIGGEST,
            StealStrategy.BIGGEST
        );

        Constellation cons = ConstellationFactory.createConstellation(config, nrExecutors);
        cons.activate();

        log.info("Activated Constellation for host: {}", NodeInformation.HOSTNAME);
        return cons;
    }

    private static CylinderSlice createCylinder(int height, int width) {
        HeatValueGenerator heatValueGenerator =
            new HeatValueGenerator(height, width, 0.0001, 100);

        double[][] temp = heatValueGenerator.getTemp();
        double[][] cond = heatValueGenerator.getCond();

        return Cylinder.of(temp, cond).toSlice();
    }

    private static MultiEventCollector createCollector(List<String> nodes, int nrExecutors) {
        Context[] contexts = new Context[nodes.size() * nrExecutors];

        for (int i = 0; i < contexts.length; i++) {
            contexts[i] = new Context(StencilActivity.LABEL + nodes.get(
                (int) Math.floor(i / nrExecutors)));
        }

        OrContext orContext = new OrContext(contexts);
        return new MultiEventCollector(orContext, nodes.size() * nrExecutors);
    }

}
