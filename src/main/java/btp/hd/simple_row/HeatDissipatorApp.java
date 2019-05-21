package btp.hd.simple_row;

import btp.hd.simple_row.Activity.DivideConquerActivity;
import btp.hd.simple_row.Activity.StencilOperationActivity;
import btp.hd.simple_row.model.Cylinder;
import btp.hd.simple_row.model.CylinderSlice;
import btp.hd.simple_row.model.TempResult;
import btp.hd.simple_row.util.HeatValueGenerator;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.Context;
import ibis.constellation.StealStrategy;
import ibis.constellation.Timer;
import ibis.constellation.util.SingleEventCollector;

import java.io.FileNotFoundException;
import java.io.PrintStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeatDissipatorApp {

    public static void writeFile(int it, double min, int w, int h, double ms, double[][] temp) {
        try {
            PrintStream out = new PrintStream("heat-dissipator.out");

            out.println(String.format("Iterations: %d, min temp delta: %f", it, min));
            out.println(String.format("Dimensions: %d x %d, time: %f ms\n", h, w, ms));

            for (int i = 0; i < temp.length; i++) {
                for (int j = 0; j < temp[0].length; j++) {
                    out.print(temp[i][j] + " ");
                }

                out.println();
            }
            out.close();
        } catch (FileNotFoundException e) {
            log.error(e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {

        int divideConquerThreshold = 16;
        int nrExecutorsPerNode = 4;
        double minDifference = 10;
        int height = 10;
        int width = 10;
        int nrNodes = 1;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-nrExecutorsPerNode")) {
                i++;
                nrExecutorsPerNode = Integer.parseInt(args[i]);
            } else if (args[i].equals("-minDifference")) {
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
                        + "[ -nrExecutorsPerNode <num> ] "
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
        ConstellationConfiguration config1 =
                new ConstellationConfiguration(new Context(DivideConquerActivity.LABEL),
                        StealStrategy.SMALLEST, StealStrategy.BIGGEST,
                        StealStrategy.BIGGEST);

        ConstellationConfiguration config2 =
                new ConstellationConfiguration(new Context(StencilOperationActivity.LABEL),
                        StealStrategy.SMALLEST, StealStrategy.BIGGEST,
                        StealStrategy.BIGGEST);

        Constellation constellation =
                ConstellationFactory.createConstellation(config1, config2);

        constellation.activate();

        if (constellation.isMaster()) {
            // This is master specific code.  The rest is going to call
            // Constellation.done(), waiting for Activities to steal.

            HeatValueGenerator heatValueGenerator = new HeatValueGenerator(height, width, 0.2, 100);

            double[][] temp = heatValueGenerator.getTemp();
            double[][] cond = heatValueGenerator.getCond();

            TempResult result = TempResult.of(temp, 0, 0);

            Timer overallTimer = constellation.getOverallTimer();
            int timing = overallTimer.start();

            log.info("Performing stencil operations on:\n{}", result.toString());

            int i = 0;
            do {

                SingleEventCollector sec = new SingleEventCollector(
                        new Context(DivideConquerActivity.LABEL));
                ActivityIdentifier aid = constellation.submit(sec);

                CylinderSlice slice = Cylinder.of(temp, cond).toSlice();
                constellation.submit(new DivideConquerActivity(aid, slice, divideConquerThreshold));

                log.debug(
                        "main(), just submitted, about to waitForEvent() for any event with target "
                                + aid);
                result = (TempResult) sec.waitForEvent().getData();
                log.debug("main(), done with waitForEvent() on identifier " + aid);

                log.info("Performed stencil operation with max temperature delta {}",
                        result.getMaxDifference());

                temp = result.getTemp();
                i++;
                log.debug("Iteration {}:\n{}", i, result.toString());
            } while (result.getMaxDifference() > minDifference);

            overallTimer.stop(timing);

            log.info("Result after {} iteration(s) and {} ms:\n{}", i, overallTimer.totalTimeVal(),
                    result.toString());
            writeFile(i, minDifference, result.width(), result.height(), overallTimer.totalTimeVal() / 1000, result.getTemp());
        }
        log.debug("calling Constellation.done()");
        constellation.done();
        log.debug("called Constellation.done()");
    }

}
