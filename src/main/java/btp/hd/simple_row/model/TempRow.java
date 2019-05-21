package btp.hd.simple_row.model;

import lombok.Data;

@Data
public class TempRow {

    private final int iteration;
    private final double[] temp;

    public int width() {
        return temp.length;
    }
}
