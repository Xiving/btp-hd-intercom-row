package btp.hd.intercom_row.model.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeltaUpdate implements Serializable {

    private final int iteration;
    private final double delta;

}
