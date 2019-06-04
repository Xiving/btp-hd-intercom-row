package btp.hd.simple_row;

import btp.hd.simple_row.Activity.MonitorActivity;
import btp.hd.simple_row.Activity.StencilOperationActivity;
import btp.hd.simple_row.model.*;
import btp.hd.simple_row.util.HeatValueGenerator;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.Context;
import ibis.constellation.OrContext;
import ibis.constellation.StealStrategy;
import ibis.constellation.Timer;
import ibis.constellation.util.SingleEventCollector;

import java.io.FileNotFoundException;
import java.io.PrintStream;

import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeatDissipatorApp {

    public static void writeFile(int it, double min, int w, int h, double ms, TempChunk temp) {
        try {
            PrintStream out = new PrintStream("heat-dissipator.out");

            out.println(String.format("Iterations: %d, min temp delta: %f", it, min));
            out.println(String.format("Dimensions: %d x %d, time: %f ms\n", h, w, ms));
            out.println(temp.toString());
            out.close();
        } catch (FileNotFoundException e) {
            log.error(e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {

        int nrExecutorsPerNode = 4;
        double minDifference = 10;
        int height = 10;
        int width = 10;
        int nrNodes = 4;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-minDifference")) {
                i++;
                minDifference = Double.parseDouble(args[i]);
            } else if (args[i].equals("-h")) {
                i++;
                height = Integer.parseInt(args[i]);
            } else if (args[i].equals("-w")) {
                i++;
                width = Integer.parseInt(args[i]);
            } else {
                throw new Error("Usage: java HeatDissipatorApp "
                    + "[ -threshold <num> ] "
                    + "[ -minDifference <num> ] "
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
            new Context(
                MonitorActivity.LABEL));

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

            HeatValueGenerator heatValueGenerator = new HeatValueGenerator(height, width, 0.05,
                10000);

            double[][] temp = heatValueGenerator.getTemp();
            double[][] cond = heatValueGenerator.getCond();

            TempResult result = TempResult.of(temp, 0, 0, 0);

            Timer overallTimer = constellation.getOverallTimer();
            int timing = overallTimer.start();

            log.info("Performing stencil operations on:\n{}", result.toString());

            SingleEventCollector sec = new SingleEventCollector(
                new Context(StencilOperationActivity.LABEL));
            ActivityIdentifier aid = constellation.submit(sec);

            CylinderSlice slice = Cylinder.of(temp, cond).toSlice();

            ArrayList<StencilOperationActivity> activities = new ArrayList<>(nrNodes);
            int currentIndex = 0;
            int currentY = 0;

            while (currentIndex < nrNodes) {
                int until = currentY + (int) Math.ceil((((double) slice.height()) - currentY)/ (nrNodes - currentIndex));
                CylinderSlice nextSlice = CylinderSlice.of(slice, currentY, until);
                StencilOperationActivity activity = new StencilOperationActivity(aid, nextSlice);

            }

            log.debug(
                "main(), just submitted, about to waitForEvent() for any event with target "
                    + aid);
            result = (TempResult) sec.waitForEvent().getData();
            log.debug("main(), done with waitForEvent() on identifier " + aid);

            log.info("Performed stencil operation with max temperature delta {}",
                result.getMaxDelta());

            overallTimer.stop(timing);

            log.info("Result after {} iteration(s) and {} ms:\n{}", result.getIteration(), overallTimer.totalTimeVal(),
                result.toString());
            writeFile(result.getIteration(), minDifference, result.width(), result.height(),
                overallTimer.totalTimeVal() / 1000, result);
        }
        log.debug("calling Constellation.done()");
        constellation.done();
        log.debug("called Constellation.done()");
    }

}
