package btp.hd.simple_row.util;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class HeatValueGenerator {

    private final int height;
    private final int width;
    private final double condConst;
    private final double initTemp;

    public double[][] getTemp() {
        double[][] temp = new double[height][width];

        for (int i = 0; i < temp.length; i++) {
            for(int j = 0; j < temp[0].length; j++) {
                temp[i][j] = (i == 0 || i == temp.length - 1)? initTemp: 0;
            }
        }

        return temp;
    }

    public double[][] getCond() {
        double[][] cond = new double[height][width];

        for (int i = 0; i < cond.length; i++) {
            for(int j = 0; j < cond[0].length; j++) {
                cond[i][j] = (i == 0 || i == cond.length - 1)? 1: condConst;
            }
        }

        return cond;
    }

}
