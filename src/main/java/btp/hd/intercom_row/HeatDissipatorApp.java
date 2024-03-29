package btp.hd.intercom_row;

import static btp.hd.intercom_row.util.GeneralUtils.monitorContext;
import static btp.hd.intercom_row.util.GeneralUtils.stencilContext;

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
import btp.hd.intercom_row.util.PgmReader;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.ConstellationConfiguration;
import ibis.constellation.ConstellationCreationException;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.Context;
import ibis.constellation.Event;
import ibis.constellation.NoSuitableExecutorException;
import ibis.constellation.OrContext;
import ibis.constellation.Timer;
import ibis.constellation.util.MultiEventCollector;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeatDissipatorApp {

  public static void writeFile(int it, double min, int w, int h, double ms, TempChunk temp, int nodes, int executors) {
    try {
      PrintStream out = new PrintStream(
          new FileOutputStream(String.format("heat-dissipator-n%d-e%d.out", nodes,executors), true)
      );

      out.println("Performed intercom row heat dissipator sim");
      out.println(String.format("Iterations: %d, max temp delta: %f", it, min));
      out.println(String.format("Dimensions: %d x %d, time: %f ms\n", h, w, ms));
      out.close();
    } catch (FileNotFoundException e) {
      log.error(e.getMessage());
    }
  }

  public static void main(String[] args) throws Exception {

    // Default config
    String fileDir = null;
    int nrExecutorsPerNode = 1;
    double minDifference = 0.1;
    int maxIterations = Integer.MAX_VALUE;
    int height = 0;
    int width = 0;

    // overwrite defaults with input arguments
    for (int i = 0; i < args.length; i += 2) {
      switch (args[i]) {
        case "-f":
          fileDir = args[i + 1];
          break;
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
              + " -f fileDir "
              + "[ -e <nrOfExecutors> ]"
              + "[ -d <minDelta> ]"
              + "[ -m <maxIteration> ]"
              + "[ -h <height> ]"
              + "[ -w <width> ]");
      }
    }

    if (Objects.isNull(fileDir) || height < 1 || width < 1) {
      throw new Error("Usage: java HeatDissipatorApp "
          + " -f fileDir "
          + "[ -e <nrOfExecutors> ]"
          + "[ -d <minDelta> ]"
          + "[ -m <maxIteration> ]"
          + "[ -h <height> ]"
          + "[ -w <width> ]");
    }

    Constellation cons = activateContellation(nrExecutorsPerNode);

    if (cons.isMaster()) {
      log.info(
          "Running heat dissipator app with conifg:\n"
              + "\tHeight: {}\n"
              + "\tWidth:  {}\n"
              + "\tMinimum difference: {}\n"
              + "\tMaximum iterations: {}\n"
              + "\tNumber of executors per node: {}\n",
          height,
          width,
          minDifference,
          maxIterations,
          nrExecutorsPerNode
      );

      // Acquire heat info
      CylinderSlice slice = createCylinder(fileDir, height, width);
      List<String> nodes = JobSubmission.getNodes();
      log.info("Nodes: {}", nodes);
      MultiEventCollector mec = createCollector(nodes, nrExecutorsPerNode);
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

      // link up activities
      for (int i = 0; i < identifiers.size(); i++) {
        ActivityIdentifier upper = (i == 0) ? null : identifiers.get(i - 1);
        ActivityIdentifier lower = (i == identifiers.size() - 1) ? null : identifiers.get(i + 1);
        InitEvent event = new InitEvent(upper, lower, monitor);
        log.debug("Sending init event: {}, to: {}", event.toString(), identifiers.get(i));
        cons.send(new Event(aid, identifiers.get(i), event));
      }

      Timer overallTimer = cons.getOverallTimer();
      int timing = overallTimer.start();

      log.info("Sending start event");
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
      writeFile(result.getIteration(), result.getMaxDelta(), result.width(), result.height(),
          overallTimer.totalTimeVal() / 1000, result, nodes.size(), nrExecutorsPerNode);
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
      activities.add(new StencilActivity(parent, node, i % activitiesPerNode, slices.get(i)));
    }

    return activities;
  }

  private static Constellation activateContellation(int nrExecutors)
      throws ConstellationCreationException {
    NodeInformation.setHostName();
    String host = NodeInformation.HOSTNAME;

    Context[] contexts = new Context[nrExecutors + 1];

    IntStream.range(0, nrExecutors).forEach(
        i -> contexts[i + 1] = stencilContext(host, i)
    );

    contexts[0] = monitorContext(host);

    Constellation cons;

    if (contexts.length - 1 == nrExecutors) {
      cons = createOrContextConstellation(contexts); //nrExecutors);
    } else {
      cons = createVarArgConstellation(contexts);
    }

    if (!cons.activate()) {
      log.error("Constellation could not be activated!");
      System.exit(1);
    }

    log.info("Activated Constellation for host: {}", NodeInformation.HOSTNAME);
    return cons;
  }

  private static Constellation createOrContextConstellation(Context[] contexts)
      throws ConstellationCreationException {
    OrContext orContext = new OrContext(contexts);//contexts);
    ConstellationConfiguration config = new ConstellationConfiguration(orContext);
    return ConstellationFactory.createConstellation(config, contexts.length - 1);
  }

  private static Constellation createVarArgConstellation(Context[] contexts)
      throws ConstellationCreationException {
    ConstellationConfiguration[] configs = Stream.of(contexts)
        .map(ConstellationConfiguration::new)
        .toArray(ConstellationConfiguration[]::new);

    return ConstellationFactory.createConstellation(configs);
  }

  private static CylinderSlice createCylinder(String fileDir, int height, int width) {
    double[][] temp = PgmReader.getTempValues(fileDir, height, width);
    double[][] cond = PgmReader.getCondValues(fileDir, height, width);

    return Cylinder.of(temp, cond).toSlice();
  }

  private static MultiEventCollector createCollector(List<String> nodes, int nrExecutors) {
    Context[] contexts = new Context[nodes.size() * nrExecutors];
    String node = null;

    for (int i = 0; i < contexts.length; i++) {
      if (i % nrExecutors == 0) {
        node = nodes.get((int) Math.floor((double) i / nrExecutors));
      }

      contexts[i] = stencilContext(node, i % nrExecutors);
    }

    return new MultiEventCollector((contexts.length == 1) ? contexts[0] : new OrContext(contexts),
        nodes.size() * nrExecutors);
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
        NodeInformation.HOSTNAME,
        maxIterations,
        minDifference,
        identifiers
    );

    ActivityIdentifier identifier = cons.submit(monitor);
    log.debug("Submitted monitor with id: {}", identifier);
    return identifier;
  }
}
