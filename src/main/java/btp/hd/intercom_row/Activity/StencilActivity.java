package btp.hd.intercom_row.Activity;

import static btp.hd.intercom_row.util.GeneralUtils.stencilContext;

import btp.hd.intercom_row.model.CylinderSlice;
import btp.hd.intercom_row.model.TempRow;
import btp.hd.intercom_row.model.event.InitEvent;
import btp.hd.intercom_row.model.event.DeltaUpdate;
import btp.hd.intercom_row.model.event.MonitorUpdate;
import btp.hd.intercom_row.model.event.MonitorUpdate.Status;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Event;
import ibis.constellation.Timer;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StencilActivity extends Activity implements Serializable {

    public static final String LABEL = "stencilOperation";

    private static final boolean EXPECT_EVENTS = true;

    private final ActivityIdentifier parent;
    private final CylinderSlice slice;

    private ActivityIdentifier upperActivity;
    private ActivityIdentifier lowerActivity;
    private ActivityIdentifier monitorActivity;

    private int calcUntilIndex;
    private boolean finished;

    private HashMap<Integer, TempRow> topRows;
    private HashMap<Integer, TempRow> botRows;

    private Timer waitTimer;
    private int timerId;

    public StencilActivity(
        ActivityIdentifier parent,
        String host,
        int executor,
        CylinderSlice slice
    ) {
        super(stencilContext(host, executor), EXPECT_EVENTS);

        this.parent = parent;
        this.slice = slice;

        this.calcUntilIndex = 0;
        this.finished = false;

        this.topRows = new HashMap<>();
        this.botRows = new HashMap<>();

        log.info("Created '{}' activity for host: {}, executor: {} and with size {} x {}", LABEL, host, executor, slice.height() - 2, slice.width() - 2);
    }

    private void init(ActivityIdentifier upper, ActivityIdentifier lower, ActivityIdentifier monitor) {
        upperActivity = upper;
        lowerActivity = lower;
        monitorActivity = monitor;
    }

    @Override
    public int initialize(Constellation cons) {
        log.info("Initialized '{}' activity with id: {}", LABEL, identifier());
        startTimer(cons, "Waiting");
        return SUSPEND;
    }

    @Override
    public int process(Constellation cons, Event event) {
        log.debug("Process event: {}", event.toString());
        Object o = event.getData();

        if (o instanceof InitEvent) {
            InitEvent e = (InitEvent) o;
            init(e.getUpper(), e.getLower(), e.getMonitor());
            return SUSPEND;
        } else if (o instanceof TempRow) {
            TempRow row = (TempRow) o;

            if (event.getSource().equals(upperActivity)) {
                topRows.put(row.getIteration(), row);
            } else {
                botRows.put(row.getIteration(), row);
            }
        } else if (o instanceof MonitorUpdate) {
            MonitorUpdate update = (MonitorUpdate) o;

            if (update.getStatus() == Status.FINISHED) {
                calcUntilIndex = update.getIteration();
                finished = true;
            } else {
                calcUntilIndex = update.getIteration();
            }
        }

        return calcNext(cons);
    }

    @Override
    public void cleanup(Constellation cons) {
        stopTimer();
        log.debug("Sending results to: {}", parent);
        cons.send(new Event(identifier(), parent, slice.getResult()));
    }

    private int calcNext(Constellation cons) {
        if (!slice.isTopReady() && Objects.nonNull(topRows.get(slice.getIteration()))) {
            slice.updateTop(topRows.get(slice.getIteration()));
        }

        if (!slice.isBotReady() && Objects.nonNull(botRows.get(slice.getIteration()))) {
            slice.updateBot(botRows.get(slice.getIteration()));
        }

        if (slice.ready() && slice.getIteration() < calcUntilIndex) {
            stopTimer();
            calc(cons);
            sendUpdates(cons);
            startTimer(cons, "Waiting");
        }

        if (slice.getIteration() == calcUntilIndex && finished) {
            log.info("Met stop condition!");
            return FINISH;
        }

        return SUSPEND;
    }

    private void calc(Constellation cons) {
        String executor = cons.identifier().toString();
        Timer timer = cons.getTimer("java", executor, "Stencil operation");

        // time the stencil operation
        int timing = timer.start();
        slice.nextIteration();
        timer.stop(timing);

        log.info("Performed  a stencil operation of size {} x {} in {} ms, iteration: {}",
                slice.height(), slice.width(), timer.totalTimeVal() / 1000, slice.getIteration());

        if (Objects.isNull(upperActivity)) {
            slice.setTopReady(true);
        }

        if (Objects.isNull(lowerActivity)) {
            slice.setBotReady(true);
        }
    }

    private void sendUpdates(Constellation cons) {
        startTimer(cons, "Send updates");

        if (Objects.nonNull(upperActivity)) {
            log.debug("Sending top row from: {} to: {}", identifier(), upperActivity);
            cons.send(new Event(identifier(), upperActivity, slice.getTop()));
        }

        if (Objects.nonNull(lowerActivity)) {
            log.debug("Sending bot row from: {} to: {}", identifier(), lowerActivity);
            cons.send(new Event(identifier(), lowerActivity, slice.getBot()));
        }

        if (!finished) {
            cons.send(new Event(identifier(), monitorActivity, new DeltaUpdate(slice.getIteration(), slice.getMaxDelta())));
        }

        stopTimer();
    }

    private void startTimer(Constellation cons, String id) {
        String executor = cons.identifier().toString();
        waitTimer = cons.getTimer("java", executor, id);
        timerId = waitTimer.start();
    }

    private void stopTimer() {
        waitTimer.stop(timerId);
        log.debug("Waited {} ms", waitTimer.totalTimeVal());
    }
}
