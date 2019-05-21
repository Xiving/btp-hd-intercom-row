package btp.hd.simple_row.Activity;

import btp.hd.simple_row.model.event.DeltaEvent;
import btp.hd.simple_row.model.TempRow;
import btp.hd.simple_row.model.CylinderSlice;
import btp.hd.simple_row.model.TempResult;
import btp.hd.simple_row.model.event.MonitorEvent;
import ibis.constellation.*;

import java.util.HashMap;
import java.util.Map;

public class RowMonitorActivity extends Activity {

    public static final String LABEL = "row-monitor";

    private static final boolean EXPECT_EVENTS = true;

    private final ActivityIdentifier parent;
    private final ActivityIdentifier top;
    private final ActivityIdentifier bot;
    private final ActivityIdentifier monitor;

    private CylinderSlice slice;

    private TempResult result;
    private Map<Integer, TempRow> topRows;
    private Map<Integer, TempRow> botRows;

    private int calcUntilIteration;
    private int currentIteration;

    public RowMonitorActivity(
            ActivityIdentifier parent,
            ActivityIdentifier top,
            ActivityIdentifier bot,
            ActivityIdentifier monitor,
            CylinderSlice slice
    ) {
        super(new Context(LABEL), EXPECT_EVENTS);
        this.parent = parent;
        this.top = top;
        this.bot = bot;
        this.monitor = monitor;
        this.slice = slice;

        this.calcUntilIteration = 1;
        this.currentIteration = 0;

        this.topRows = new HashMap<>();
        this.botRows = new HashMap<>();
    }

    @Override
    public int initialize(Constellation cons) {
        submit(cons, slice);
        return SUSPEND;
    }

    @Override
    public int process(Constellation cons, Event event) {
        Object o = event.getData();

        if (o instanceof TempResult) {
            result = (TempResult) o;
            handleResult(cons);
        } else if (o instanceof TempRow) {
            handleRow((TempRow) o, event.getSource());
        } else if (o instanceof MonitorEvent) {
            return handleMonitor((MonitorEvent) o);
        }
    }

    @Override
    public void cleanup(Constellation constellation) {

    }

    private int handleMonitor(MonitorEvent event) {
        if (event.getStatus() == MonitorEvent.Status.CONTINUE) {
            calcUntilIteration = Math.max(calcUntilIteration, event.getIteration());
            return SUSPEND;
        } else {
            return FINISH;
        }
    }

    private void handleResult(Constellation cons) {
        if (result.getIteration() == calcUntilIteration) {
            cons.send(new Event(identifier(), monitor, new DeltaEvent(result.getIteration(), result.getMaxDelta())));
        }

    }

    private void handleRow(TempRow row, ActivityIdentifier source) {
        if (source.equals(top)) {
            topRows.put(row.getIteration(), row);
        } else {
            botRows.put(row.getIteration(), row);
        }
    }

    private void submit(Constellation cons, CylinderSlice slice) {
        try {
            cons.submit(new StencilOperationActivity(identifier(), slice));
        } catch (NoSuitableExecutorException e) {
            e.printStackTrace();
        }
    }
}
