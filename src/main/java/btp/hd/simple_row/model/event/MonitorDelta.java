package btp.hd.simple_row.model.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class MonitorDelta implements Serializable {

    private final int iteration;
    private final double delta;

}
