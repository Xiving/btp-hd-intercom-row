package btp.hd.simple_row.Activity;

import btp.hd.simple_row.model.CylinderSlice;
import btp.hd.simple_row.model.event.LinkEvent;
import ibis.constellation.*;

public class LinkerActivity extends Activity {

    public static final String LABEL = "linkerActivity";

    private static final boolean EXPECT_EVENTS = true;

    private boolean edge;
    private final ActivityIdentifier parent;
    private final ActivityIdentifier topLink;
    private final ActivityIdentifier botLink;

    private CylinderSlice slice;
    private ActivityIdentifier top;
    private ActivityIdentifier bot;

    public LinkerActivity(ActivityIdentifier parent, ActivityIdentifier top, ActivityIdentifier bot, CylinderSlice slice) {
        super(new Context(LABEL), EXPECT_EVENTS);

        this.parent = parent;
        this.topLink = top;
        this.botLink = bot;
        this.slice = slice;

        edge = top == null || bot == null;
    }

    @Override
    public int initialize(Constellation constellation) {
        constellation.send(new Event(identifier(), topLink, new LinkEvent(identifier())));
        constellation.send(new Event(identifier(), botLink, new LinkEvent(identifier())));
        return SUSPEND;
    }

    @Override
    public int process(Constellation constellation, Event event) {
        LinkEvent linkEvent = (LinkEvent) event.getData();

        if (topLink.equals(event.getSource())) {
            top = linkEvent.getIdentifier();
        } else if (botLink.equals(event.getSource())) {
            bot = linkEvent.getIdentifier();
        } else {
            throw new IllegalArgumentException("Unexpected activity identifier!");
        }

        if (edge) {
            return FINISH;
        }

        if (top != null && bot != null) {
            return FINISH;
        }

        return SUSPEND;
    }

    @Override
    public void cleanup(Constellation constellation) {
        // Todo: send event
    }
}
