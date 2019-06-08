package btp.hd.intercom_row;

import btp.hd.intercom_row.Activity.MonitorActivity;
import btp.hd.intercom_row.Activity.StencilOperationActivity;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
        int nrNodes = poolSize();

        // overwrite defaults with input arguments
        for (int i = 0; i < args.length; i += 2) {
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
        }

        Constellation constellation = activateContellation(nrExecutorsPerNode);

        if (constellation.isMaster()) {
            // This is master specific code.  The rest is going to call
            // Constellation.done(), waiting for Activities to steal.
            CylinderSlice slice = createCylinder(height, width);
            MultiEventCollector mec = createCollector(JobSubmission.getNodes());
            ActivityIdentifier aid = constellation.submit(mec);

            log.info("Performing stencil operations on:\n{}", slice.getResult().toString());

            List<String> nodeNames = JobSubmission.getNodes();
            List<StencilOperationActivity> activities = new ArrayList<>(nrNodes + 2);

            activities.add(null);

            int currentY = 1;
            int rowsLeft = slice.height() - 2;


            //Fixme : this shit

            for (int i = 0; i < nrNodes; i++) {
                int chunksLeft = nrNodes - i;
                int until = currentY + (int) Math.ceil(((double) rowsLeft) / chunksLeft);

                log.info("from: {}", currentY - 1);
                log.info("until: {}", until + 1);

                CylinderSlice nextSlice = CylinderSlice.of(slice, currentY - 1, until + 1);
                StencilOperationActivity activity = new StencilOperationActivity(aid, nextSlice);
                activities.add(i + 1, activity);
                i++;

                currentY = until;
            }

            for (StencilOperationActivity activity : activities) {
                if (Objects.nonNull(activity)) {
                    constellation.submit(activity);
                }
            }

            MonitorActivity monitor = new MonitorActivity(
                maxIterations,
                minDifference,
                activities.stream().filter(Objects::nonNull).map(Activity::identifier)
                    .collect(Collectors.toList())
            );

            constellation.submit(monitor);

            for (int i = 1; i < (nrNodes + 1); i++) {
                ActivityIdentifier upper = (i == 1) ? null : activities.get(i - 1).identifier();
                ActivityIdentifier lower =
                    (i == nrNodes) ? null : activities.get(i + 1).identifier();

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

    private static int poolSize() {
        String ibisPoolSize = System.getProperty("ibis.pool.size");
        return Objects.nonNull(ibisPoolSize)? Integer.parseInt(ibisPoolSize): 1;
    }

    private static Constellation activateContellation(int nrExecutors)
        throws ConstellationCreationException {
        NodeInformation.setHostName();

        log.info("Hostname: {}", NodeInformation.HOSTNAME);

        System.exit(0);

        // Create context for the master node
        OrContext orContext = new OrContext(
            new Context(StencilOperationActivity.LABEL + NodeInformation.HOSTNAME),    // One for every node
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

    private static MultiEventCollector createCollector(List<String> nodes) {
        OrContext orContext = new OrContext(
            nodes.stream()
                .map(e -> new Context(StencilOperationActivity.LABEL + e))
                .toArray(Context[]::new)
        );

        return new MultiEventCollector(new Context(StencilOperationActivity.LABEL), nodes.size());
    }

}
