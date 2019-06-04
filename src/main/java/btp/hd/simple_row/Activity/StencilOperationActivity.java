package btp.hd.simple_row.Activity;

import btp.hd.simple_row.model.CylinderSlice;
import btp.hd.simple_row.model.TempResult;
import btp.hd.simple_row.model.TempRow;
import btp.hd.simple_row.model.event.MonitorDelta;
import btp.hd.simple_row.model.event.MonitorUpdate;
import btp.hd.simple_row.model.event.MonitorUpdate.Status;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Context;
import ibis.constellation.Event;
import ibis.constellation.Timer;
import java.util.HashMap;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StencilOperationActivity extends Activity {

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

    public StencilOperationActivity(
        ActivityIdentifier parent,
        CylinderSlice slice
    ) {
        super(new Context(LABEL), EXPECT_EVENTS);

        this.parent = parent;
        this.slice = slice;

        this.calcUntilIndex = 0;
        this.finished = false;

        this.topRows = new HashMap<>();
        this.botRows = new HashMap<>();

        log.info("Created '{}' activity with size {} x {}", LABEL, slice.height() - 2, slice.width() - 2);
    }

    public void init(ActivityIdentifier upper, ActivityIdentifier lower, ActivityIdentifier monitor) {
        upperActivity = upper;
        lowerActivity = lower;
        monitorActivity = monitor;
    }

    @Override
    public int initialize(Constellation cons) {
        return SUSPEND;
    }

    @Override
    public int process(Constellation cons, Event event) {
        log.debug("Process event: {}", event.toString());
        Object o = event.getData();

        if (o instanceof TempRow) {
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
        log.debug("Sending and event to my parent");
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
            calc(cons);
            sendUpdates(cons);
        }

        if (slice.getIteration() == calcUntilIndex && finished) {
            return FINISH;
        }

        return SUSPEND;
    }

    private void calc(Constellation cons) {
        String executor = cons.identifier().toString();
        Timer timer = cons.getTimer("java", executor, "stencil operation");
        int timing = timer.start();

        log.debug("Performing stencil operation on:\n{}", slice.toString());
        TempResult result = slice.calcNextResult();
        log.debug("Result of stencil operation:\n{}", result.toString());
        slice.update(result);

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
        if (Objects.nonNull(upperActivity)) {
            cons.send(new Event(identifier(), upperActivity, slice.getTop()));
        }

        if (Objects.nonNull(lowerActivity)) {
            cons.send(new Event(identifier(), lowerActivity, slice.getBot()));
        }

        if (!finished) {
            cons.send(new Event(identifier(), monitorActivity, new MonitorDelta(slice.getIteration(), slice.getMaxDelta())));
        }
    }
}
