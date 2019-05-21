package btp.hd.simple_row.Activity;

import btp.hd.simple_row.model.CylinderSlice;
import btp.hd.simple_row.model.TempResult;
import btp.hd.simple_row.model.event.LinkEvent;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Context;
import ibis.constellation.Event;
import ibis.constellation.NoSuitableExecutorException;
import ibis.constellation.Timer;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DivideConquerActivity extends Activity {

    public static final String LABEL = "divideAndConquer";

    private static final boolean EXPECT_EVENTS = true;

    private final ActivityIdentifier parent;
    private final ActivityIdentifier topLink;
    private final ActivityIdentifier botLink;
    private final CylinderSlice slice;
    private final int threshold;

    private TempResult result;

    public DivideConquerActivity(
            ActivityIdentifier parent,
            ActivityIdentifier topLink,
            ActivityIdentifier botLink,
            CylinderSlice slice,
            int threshold
    ) {
        super(new Context(LABEL), EXPECT_EVENTS);

        this.parent = parent;
        this.topLink = topLink;
        this.botLink = botLink;
        this.slice = slice;
        this.threshold = threshold;

        log.info("Created '{}' activity with size {} x {}", LABEL, slice.height() - 2, slice.width() - 2);
    }

    @Override
    public int initialize(Constellation cons) {
        if (slice.height() <= threshold) {
            log.debug("Slice with height {} is small enough to be calculated", slice.height());
            submit(cons, slice);
            return FINISH;
        } else {
            log.debug("Slice with height {} is too big. Will be split into smaller slices",
                    slice.height());

            result = TempResult.of(slice);

            int half = (int) Math.ceil((double) slice.height() / 2);
            LinkActivity linkActivity = new LinkActivity();

            sumbit(cons, linkActivity);
            submit(cons, slice, topLink, linkActivity.identifier(), 0, half + 1);
            submit(cons, slice, linkActivity.identifier(), botLink, half - 1, slice.height());

            return SUSPEND;
        }
    }

    private void submit(Constellation cons, CylinderSlice slice, ActivityIdentifier top, ActivityIdentifier bot, int begin, int end) {
        CylinderSlice sliceToSubmit = CylinderSlice.of(slice, begin, end);

        try {
            cons.submit(new DivideConquerActivity(identifier(), top, bot, sliceToSubmit, threshold));
        } catch (NoSuitableExecutorException e) {
            e.printStackTrace();
        }
    }

    private void submit(Constellation cons, CylinderSlice slice) {
        try {
            cons.submit(new StencilOperationActivity(parent, slice));
        } catch (NoSuitableExecutorException e) {
            e.printStackTrace();
        }
    }

    private void sumbit(Constellation cons, LinkActivity linkActivity) {
        try {
            cons.submit(linkActivity);
        } catch (NoSuitableExecutorException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int process(Constellation cons, Event event) {
        if (log.isDebugEnabled()) {
            log.debug("Processing an event");
        }

        log.debug("Adding chunk to result");
        result.add((TempResult) event.getData());

        if (result.finished()) {
            return FINISH;
        }

        return SUSPEND;
    }

    @Override
    public void cleanup(Constellation cons) {
        if (Objects.nonNull(result)) {
            log.debug("Sending result to my parent");
            cons.send(new Event(identifier(), parent, result));
        }
    }
}
