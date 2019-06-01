package btp.hd.simple_row.model.event;

import lombok.Data;

@Data
public class MonitorUpdate {

    private final Status status;
    private final int iteration;

    public enum Status {
        CONTINUE,
        FINISHED
    }

}
