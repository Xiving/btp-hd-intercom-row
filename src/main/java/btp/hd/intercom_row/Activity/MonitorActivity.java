package btp.hd.intercom_row.Activity;

import btp.hd.intercom_row.model.event.MonitorDelta;
import btp.hd.intercom_row.model.event.MonitorUpdate;
import btp.hd.intercom_row.model.event.MonitorUpdate.Status;
import btp.hd.intercom_row.model.event.StartEvent;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Constellation;
import ibis.constellation.Context;
import ibis.constellation.Event;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonitorActivity extends Activity {

    public static final String LABEL = "monitor";

    private static final boolean EXPECT_EVENTS = true;

    private final int maxIterations;
    private final double minDelta;
    private final List<ActivityIdentifier> recipients;

    private int currentIteration;
    private int deltasReceived;
    private double maxDelta;

    public MonitorActivity(
        int maxIterations,
        double minDelta,
        List<ActivityIdentifier> recipients
    ) {
        super(new Context(LABEL), EXPECT_EVENTS);
        this.maxIterations = maxIterations;
        this.minDelta = minDelta;
        this.recipients = recipients;

        currentIteration = 0;
        deltasReceived = 0;
        maxDelta = Double.MAX_VALUE;
    }

    @Override
    public int initialize(Constellation cons) {
        return SUSPEND;
    }

    @Override
    public int process(Constellation cons, Event event) {
        Object o = event.getData();

        if (o instanceof StartEvent) {
            return broadcastWhenNeeded(cons);
        }

        if (!recipients.contains(event.getSource())) {
            log.warn("Received event from unknown source: {}", event.getSource());
            return SUSPEND;
        }

        MonitorDelta delta = (MonitorDelta) event.getData();
        log.debug("Received event: {}", delta);

        if (delta.getIteration() < currentIteration) {
            return SUSPEND;
        }

        deltasReceived++;
        maxDelta = Math.max(maxDelta, delta.getDelta());
        return broadcastWhenNeeded(cons);
    }

    @Override
    public void cleanup(Constellation cons) {
        recipients.forEach(
            r -> cons.send(
                new Event(identifier(), r, new MonitorUpdate(Status.FINISHED, currentIteration)))
        );

        log.info("Broadcast stop signal after {} iterations", currentIteration);
    }

    private int broadcastWhenNeeded(Constellation cons) {
        if (maxDelta >= minDelta) {
            broadcastNextIteration(cons);

            if (currentIteration >= maxIterations) {
                log.info("Maximum amount of iterations met!");
                return FINISH;
            }

            deltasReceived = 0;
            maxDelta = 0;
        } else if (deltasReceived >= recipients.size()) {
            log.info("Max delta is below minimum!");
            return FINISH;
        }

        return SUSPEND;
    }

    private void broadcastNextIteration(Constellation cons) {
        currentIteration++;
        recipients.forEach(
            r -> cons.send(
                new Event(identifier(), r, new MonitorUpdate(Status.CONTINUE, currentIteration)))
        );

        log.info("Broadcast next iteration: {}", currentIteration);
    }
}
