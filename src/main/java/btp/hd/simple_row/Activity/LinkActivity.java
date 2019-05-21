package btp.hd.simple_row.Activity;

import btp.hd.simple_row.model.event.LinkEvent;
import ibis.constellation.*;

import java.util.Objects;

public class LinkActivity extends Activity {

    public static final String LABEL = "link";

    private static final boolean EXPECT_EVENTS = true;

    private ActivityIdentifier ping;
    private ActivityIdentifier pong;

    public LinkActivity() {
        super(new Context(LABEL), EXPECT_EVENTS);
    }

    @Override
    public int initialize(Constellation constellation) {
        return SUSPEND;
    }

    @Override
    public int process(Constellation cons, Event event) {
        return link((LinkEvent) event.getData());
    }

    private synchronized int link(LinkEvent event) {
        if (Objects.isNull(ping)) {
            ping = event.getIdentifier();
            return SUSPEND;
        }

        pong = event.getIdentifier();
        return FINISH;
    }

    @Override
    public void cleanup(Constellation cons) {
        cons.send(new Event(identifier(), ping, new LinkEvent(pong)));
        cons.send(new Event(identifier(), pong, new LinkEvent(ping)));
    }
}
