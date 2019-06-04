package btp.hd.simple_row.model.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class MonitorUpdate implements Serializable {

    private final Status status;
    private final int iteration;

    public enum Status {
        CONTINUE,
        FINISHED
    }

}
