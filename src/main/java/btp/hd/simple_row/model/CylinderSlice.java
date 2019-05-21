package btp.hd.simple_row.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CylinderSlice extends HeatChunk {

    private static final double DIRECT_CONST = 0.25 * Math.sqrt(2) / (Math.sqrt(2) + 1.0);
    private static final double DIAGONAL_CONST = 0.25 / (Math.sqrt(2) + 1.0);

    private final int parentOffset;
    private final int iteration;

    private CylinderSlice(int parentOffset, int iteration, double[][] temp, double[][] cond) {
        super(temp, cond);
        this.parentOffset = parentOffset;
        this.iteration = iteration;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static CylinderSlice of(Cylinder parent) {
        return new CylinderSlice(0, 1, parent.getTemp(), parent.getCond());
    }

    public static CylinderSlice of(CylinderSlice parent, int begin, int end) {
        if (begin < 0 || begin >= end || end > parent.height()) {
            throw new IllegalArgumentException(
                String.format("Illegal arguments for begin: {} and end: {}", begin, end)
            );
        }

        double[][] temp = new double[end - begin][parent.width()];
        double[][] cond = new double[end - begin][parent.width()];

        for (int i = begin; i < end; i++) {
            for (int j = 0; j < parent.width(); j++) {
                temp[i - begin][j] = parent.getTemp()[i][j];
                cond[i - begin][j] = parent.getCond()[i][j];
            }
        }

        return new CylinderSlice(begin, parent.getIteration(), temp, cond);
    }

    public TempResult result() {
        double maxDifference = 0;
        int height = height() - 2;
        int width = width() - 2;
        double[][] temp = getTemp();
        double[][] cond = getCond();

        double[][] result = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                result[i][j] = nextTemp(temp, cond, i + 1, j + 1);

                double difference = Math.abs(temp[i + 1][j + 1] - result[i][j]);

                if (difference > maxDifference) {
                    maxDifference = difference;
                }
            }
        }

        TempResult resultChunk = TempResult.of(result, parentOffset, iteration, maxDifference);
        return resultChunk;
    }

    private static double nextTemp(double[][] temp, double[][] cond, int i, int j) {
        double w = cond[i][j];
        double restW = 1 - w;

        return temp[i][j] * w +
            (temp[i - 1][j] + temp[i][j - 1] + temp[i][j + 1] + temp[i + 1][j]) * (restW
                * DIRECT_CONST) +
            (temp[i - 1][j - 1] + temp[i - 1][j + 1] + temp[i + 1][j - 1] + temp[i + 1][j + 1]) * (
                restW * DIAGONAL_CONST);
    }
}
