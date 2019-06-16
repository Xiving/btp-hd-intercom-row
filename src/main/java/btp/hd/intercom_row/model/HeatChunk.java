package btp.hd.intercom_row.model;

import java.io.Serializable;
import lombok.Data;

@Data
public abstract class HeatChunk extends TempChunk implements Serializable {
    private final double[][] cond;

    public HeatChunk(double[][] temp, double[][] cond) {
        super(temp);

        if (temp.length != cond.length || temp[0].length != cond[0].length) {
            throw new IllegalArgumentException("Dimension of temp and cond have to match");
        }

        this.cond = cond;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < cond.length; i++) {
            for (int j = 0; j < cond[0].length; j++) {
                str.append(String.format("%11.4f (%1.3f) ", getTemp()[i][j], cond[i][j]));
            }

            str.append('\n');
        }

        return str.deleteCharAt(str.length() - 1).toString();
    }

}
