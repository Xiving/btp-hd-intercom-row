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

        NodeInformation.setHostName();
        JobSubmission.getNodes();

        int nrExecutorsPerNode = 1;
        double minDifference = 10;
        int maxIterations = Integer.MAX_VALUE;
        int height = 10;
        int width = 10;
        int nrNodes = 4;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-d")) {
                i++;
                minDifference = Double.parseDouble(args[i]);
            } else if (args[i].equals("-m")) {
                i++;
                maxIterations = Integer.parseInt(args[i]);
            } else if (args[i].equals("-h")) {
                i++;
                height = Integer.parseInt(args[i]);
            } else if (args[i].equals("-w")) {
                i++;
                width = Integer.parseInt(args[i]);
            } else {
                throw new Error("Usage: java HeatDissipatorApp "
                        + "[ -d <num> ] minimum temp delta"
                        + "[ -m <num> ] maximum iterations"
                        + "[ -h <height> ]"
                        + "[ -w <width> ]");
            }
        }

        String ibisPoolSize = System.getProperty("ibis.pool.size");
        if (ibisPoolSize != null) {
            nrNodes = Integer.parseInt(ibisPoolSize);
        }

        log.info("HeatDissipatorApp, running with dimensions {} x {}:", height, width);

        // Initialize Constellation with the following configurations
        OrContext orContext = new OrContext(new Context(StencilOperationActivity.LABEL),
                new Context(MonitorActivity.LABEL));

        // Initialize Constellation with the following configurations
        ConstellationConfiguration config =
                new ConstellationConfiguration(orContext,
                        StealStrategy.SMALLEST, StealStrategy.BIGGEST,
                        StealStrategy.BIGGEST);

        Constellation constellation =
                ConstellationFactory.createConstellation(config, nrExecutorsPerNode);

        constellation.activate();

        if (constellation.isMaster()) {
            // This is master specific code.  The rest is going to call
            // Constellation.done(), waiting for Activities to steal.

            HeatValueGenerator heatValueGenerator = new HeatValueGenerator(height, width, 0.0001,
                    100);

            double[][] temp = heatValueGenerator.getTemp();
            double[][] cond = heatValueGenerator.getCond();

            Timer overallTimer = constellation.getOverallTimer();
            int timing = overallTimer.start();

            MultiEventCollector sec = new MultiEventCollector(
                    new Context(StencilOperationActivity.LABEL), nrNodes);
            ActivityIdentifier aid = constellation.submit(sec);

            CylinderSlice slice = Cylinder.of(temp, cond).toSlice();

            log.info("Performing stencil operations on:\n{}", slice.getResult().toString());

            List<StencilOperationActivity> activities = new ArrayList<>(nrNodes + 2);
            for (int i = 0; i < nrNodes + 2; i++) {
                activities.add(null);
            }
            int currentIndex = 0;
            int currentY = 1;

            while (currentIndex < nrNodes) {
                int rowsLeft = slice.height() - 1 - currentY;
                int chunksLeft = nrNodes - currentIndex;
                int until = currentY + (int) Math.ceil(((double) rowsLeft) / chunksLeft);

                log.info("from: {}", currentY - 1);
                log.info("until: {}", until + 1);

                CylinderSlice nextSlice = CylinderSlice.of(slice, currentY - 1, until + 1);
                StencilOperationActivity activity = new StencilOperationActivity(aid, nextSlice);
                activities.add(currentIndex + 1, activity);
                currentIndex++;
                currentY = until;
            }

            for (StencilOperationActivity activity : activities) {
                if (Objects.nonNull(activity)) constellation.submit(activity);
            }

            MonitorActivity monitor = new MonitorActivity(
                    maxIterations,
                    minDifference,
                    activities.stream().filter(Objects::nonNull).map(Activity::identifier).collect(Collectors.toList())
            );

            constellation.submit(monitor);

            for (int i = 1; i < (nrNodes + 1); i++) {
                ActivityIdentifier upper = (i == 1) ? null : activities.get(i - 1).identifier();
                ActivityIdentifier lower = (i == nrNodes) ? null : activities.get(i + 1).identifier();

                activities.get(i).init(upper, lower, monitor.identifier());
            }

            constellation.send(new Event(aid, monitor.identifier(), new StartEvent()));

            log.debug(
                    "main(), just submitted, about to waitForEvent() for any event with target "
                            + aid);

            TempResult result = TempResult.of(slice);


            Event[] event = sec.waitForEvents();
            log.info("main(), received results on identifier " + aid);

            Stream.of(event).forEach(e -> {
                        log.debug("Adding chunk {} to result", e);
                        result.add((TempResult) e.getData());
                    }
            );

            overallTimer.stop(timing);

            log.info("Result of size {} x {} after {} iteration(s) and {} ms:\n{}", result.height(), result.width(), result.getIteration(), overallTimer.totalTimeVal() / 1000, result.toString());
            writeFile(result.getIteration(), minDifference, result.width(), result.height(),
                    overallTimer.totalTimeVal() / 1000, result);
        }
        log.debug("calling Constellation.done()");
        constellation.done();
        log.debug("called Constellation.done()");
    }

}
