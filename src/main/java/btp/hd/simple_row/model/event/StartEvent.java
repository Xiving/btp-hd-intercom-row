package btp.hd.simple_row.model.event;

import java.io.Serializable;

public class StartEvent implements Serializable {
    private boolean start;

    public StartEvent() {
        start = true;
    }
}
