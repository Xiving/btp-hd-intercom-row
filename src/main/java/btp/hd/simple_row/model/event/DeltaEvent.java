package btp.hd.simple_row.model.event;

import lombok.Data;

@Data
public class DeltaEvent {

    private final int iteration;
    private final double delta;

}
