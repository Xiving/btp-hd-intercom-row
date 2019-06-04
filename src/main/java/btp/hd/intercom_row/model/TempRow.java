package btp.hd.intercom_row.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class TempRow implements Serializable {

    private final int iteration;
    private final double[] temp;

    public int width() {
        return temp.length;
    }
}
