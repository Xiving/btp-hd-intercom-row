package btp.hd.intercom_row;

import btp.hd.intercom_row.Activity.MonitorActivity;
import btp.hd.intercom_row.Activity.StencilActivity;
import btp.hd.intercom_row.model.Cylinder;
import btp.hd.intercom_row.model.CylinderSlice;
import btp.hd.intercom_row.model.TempChunk;
import btp.hd.intercom_row.model.TempResult;
import btp.hd.intercom_row.model.event.InitEvent;
import btp.hd.intercom_row.model.event.StartEvent;
import btp.hd.intercom_row.util.HeatValueGenerator;
import btp.hd.intercom_row.util.JobSubmission;
import btp.hd.intercom_row.util.NodeInformation;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.ConstellationCreationException;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.Context;
import ibis.constellation.Event;
import ibis.constellation.NoSuitableExecutorException;
import ibis.constellation.OrContext;
import ibis.constellation.StealStrategy;
import ibis.constellation.Timer;
import ibis.constellation.util.MultiEventCollector;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
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
          width = Integer.parseInt(args[i + 1]);
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

    Constellation cons = activateContellation(nrExecutorsPerNode);

    if (cons.isMaster()) {
      // Acquire heat info
      // todo: from file
      CylinderSlice slice = createCylinder(height, width);
      MultiEventCollector mec = createCollector(JobSubmission.getNodes(), nrExecutorsPerNode);
      ActivityIdentifier aid = cons.submit(mec);

      // create activities
      List<StencilActivity> activities = createActivities(
          aid,
          slice,
          JobSubmission.getNodes(),
          nrExecutorsPerNode
      );

      // submit activities and create monitor
      List<ActivityIdentifier> identifiers = submitActivities(cons, activities);
      ActivityIdentifier monitor = createMonitor(cons, identifiers, maxIterations, minDifference);

      log.info(identifiers.toString());

      // link up activities
      for (int i = 0; i < identifiers.size(); i++) {
        ActivityIdentifier upper = (i == 0) ? null : identifiers.get(i - 1);
        ActivityIdentifier lower = (i == identifiers.size() - 1) ? null : identifiers.get(i + 1);
        cons.send(new Event(aid, identifiers.get(i), new InitEvent(upper, lower, monitor)));
      }

      Timer overallTimer = cons.getOverallTimer();
      int timing = overallTimer.start();

      cons.send(new Event(aid, monitor, new StartEvent()));

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

      log.info("Done with stencil of size {} x {} after {} iteration(s) and {} ms", result.height(),
          result.width(), result.getIteration(), overallTimer.totalTimeVal() / 1000);
      writeFile(result.getIteration(), minDifference, result.width(), result.height(),
          overallTimer.totalTimeVal() / 1000, result);
    }

    cons.done();
    log.debug("called Constellation.done()");
  }

  private static List<StencilActivity> createActivities(ActivityIdentifier parent,
      CylinderSlice slice, List<String> nodeNames, int activitiesPerNode) {
    int nrOfActivities = nodeNames.size() * activitiesPerNode;
    List<CylinderSlice> slices = split(slice, nrOfActivities);
    List<StencilActivity> activities = new ArrayList<>();

    for (int i = 0; i < nrOfActivities; i++) {
      String node = nodeNames.get((int) Math.floor((double) i / activitiesPerNode));
      activities.add(new StencilActivity(parent, StencilActivity.LABEL + node, slices.get(i)));
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
    Context[] contexts = new Context[nodes.size()];

    for (int i = 0; i < contexts.length; i++) {
      contexts[i] = new Context(StencilActivity.LABEL + nodes.get(i));
    }

    return new MultiEventCollector(new OrContext(contexts), nodes.size() * nrExecutors);
  }

  private static List<ActivityIdentifier> submitActivities(Constellation cons,
      List<StencilActivity> activities)
      throws NoSuitableExecutorException {
    List<ActivityIdentifier> identifiers = new ArrayList<>();

    for (StencilActivity activity : activities) {
      ActivityIdentifier submittedActivity = cons.submit(activity);
      identifiers.add(submittedActivity);
      log.info("Submitted activity with id: {}", submittedActivity);
    }

    return identifiers;
  }

  private static List<CylinderSlice> split(CylinderSlice slice, int amount) {
    List<CylinderSlice> slices = new ArrayList<>();
    int currentRow = 1;
    int rows = slice.height() - 1;

    for (int i = 0; i < amount; i++) {
      int until = currentRow + (int) Math.ceil(((double) rows - currentRow) / (amount - i));
      CylinderSlice nextSlice = CylinderSlice.of(slice, currentRow - 1, until + 1);
      slices.add(nextSlice);
      currentRow = until;
    }

    return slices;
  }

  private static ActivityIdentifier createMonitor(
      Constellation cons,
      List<ActivityIdentifier> identifiers,
      int maxIterations,
      double minDifference
  ) throws NoSuitableExecutorException {
    MonitorActivity monitor = new MonitorActivity(
        maxIterations,
        minDifference,
        identifiers
    );

    ActivityIdentifier identifier = cons.submit(monitor);
    log.info("Submitted monitor with id: {}", identifier);
    return identifier;
  }
}
