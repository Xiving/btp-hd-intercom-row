package btp.hd.intercom_row.model;

import lombok.Data;

@Data
public abstract class TempChunk implements java.io.Serializable {

    private final double[][] temp;

    public int height() {
        return temp.length;
    }

    public int width() {
        return temp[0].length;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < temp.length; i++) {
            for (int j = 0; j < temp[0].length; j++) {
                str.append(String.format("%11.4f ", getTemp()[i][j]));
            }

            str.append('\n');
        }

        return str.deleteCharAt(str.length() - 1).toString();
    }

}
