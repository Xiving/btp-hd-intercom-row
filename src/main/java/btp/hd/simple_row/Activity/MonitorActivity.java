package btp.hd.simple_row.Activity;

import ibis.constellation.Activity;
import ibis.constellation.Constellation;
import ibis.constellation.Context;
import ibis.constellation.Event;

public class MonitorActivity extends Activity {

    public static final String LABEL = "monitor";

    private static final boolean EXPECT_EVENTS = true;

    public MonitorActivity() {
        super(new Context(LABEL), EXPECT_EVENTS);

    }

    @Override
    public int initialize(Constellation constellation) {
        return 0;
    }

    @Override
    public int process(Constellation constellation, Event event) {
        return 0;
    }

    @Override
    public void cleanup(Constellation constellation) {

    }
}
