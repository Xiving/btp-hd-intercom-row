package btp.hd.intercom_row.model;

import lombok.Getter;

@Getter
public class Cylinder extends HeatChunk {

    private Cylinder(double[][] temp, double[][] cond) {
        super(temp, cond);
    }

    public static Cylinder of(double[][] temp, double[][] cond) {
        int height = temp.length;
        int width = temp[0].length;

        double[][] cylinderTemp = new double[height + 2][width + 2];
        double[][] cylinderCond = new double[height + 2][width + 2];

        for (int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                cylinderTemp[i + 1][j + 1] = temp[i][j];
                cylinderCond[i + 1][j + 1] = cond[i][j];
            }

            cylinderTemp[i + 1][0] = temp[i][width - 1];
            cylinderCond[i + 1][0] = cond[i][width - 1];

            cylinderTemp[i + 1][width + 1] = temp[i][1];
            cylinderCond[i + 1][width + 1] = cond[i][1];
        }

        return new Cylinder(cylinderTemp, cylinderCond);
    }

    public CylinderSlice toSlice() {
        return CylinderSlice.of(this);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
