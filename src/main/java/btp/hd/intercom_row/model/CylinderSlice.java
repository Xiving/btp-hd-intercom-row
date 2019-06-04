package btp.hd.intercom_row.model;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class CylinderSlice extends HeatChunk {

    private static final double DIRECT_CONST = 0.25 * Math.sqrt(2) / (Math.sqrt(2) + 1.0);
    private static final double DIAGONAL_CONST = 0.25 / (Math.sqrt(2) + 1.0);

    private final int parentOffset;

    private int iteration;
    private double maxDelta;

    private boolean topReady;
    private boolean botReady;

    private CylinderSlice(int parentOffset, double[][] temp, double[][] cond) {
        super(temp, cond);
        this.parentOffset = parentOffset;

        maxDelta = 0;
        iteration = 0;

        topReady = true;
        botReady = true;
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static CylinderSlice of(Cylinder parent) {
        return new CylinderSlice(0, parent.getTemp(), parent.getCond());
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

        return new CylinderSlice(begin, temp, cond);
    }

    public TempResult calcNextResult() {
        double maxDifference = 0;
        int height = height() - 2;
        int width = width() - 2;
        double[][] temp = getTemp();
        double[][] cond = getCond();

        double[][] result = new double[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                result[i][j] = nextTemp(temp, cond, i + 1, j + 1);
                maxDifference = Math.max(maxDifference, Math.abs(temp[i + 1][j + 1] - result[i][j]));
            }
        }

        return TempResult.of(result, parentOffset, iteration + 1, maxDifference);
    }

    public void update(TempResult result) {
        for (int i = 0; i < result.height(); i++) {
            for (int j = 0; j < result.width(); j++) {
                getTemp()[i + 1][j + 1] = result.getTemp()[i][j];
            }
        }

        for (int i = 1; i < height() - 1; i++) {
            getTemp()[i][0] = getTemp()[i][width() - 2];
            getTemp()[i][width() - 1] = getTemp()[i][1];
        }

        iteration = result.getIteration();
        maxDelta = result.getMaxDelta();

        topReady = false;
        botReady = false;
    }

    public void updateTop(TempRow row) {
        if (row.getIteration() != iteration) {
            throw new IllegalArgumentException("Iteration do not match!");
        }

        getTemp()[0] = row.getTemp();
        topReady = true;
    }

    public void updateBot(TempRow row) {
        if (row.getIteration() != iteration) {
            throw new IllegalArgumentException("Iteration do not match!");
        }

        getTemp()[height() - 1] = row.getTemp();
        botReady = true;
    }

    public TempResult getResult() {
        double[][] temp = new double[height() - 2][width() - 2];

        for (int i = 0; i < height() - 2; i++) {
            for (int j = 0; j < width() - 2; j++) {
                temp[i][j] = getTemp()[i + 1][j + 1];
            }
        }

        return TempResult.of(temp, parentOffset, iteration, maxDelta);
    }

    public TempRow getTop() {
        return new TempRow(iteration, getTemp()[1]);
    }

    public TempRow getBot() {
        return new TempRow(iteration, getTemp()[height() - 2]);
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

    public boolean ready() {
        return topReady && botReady;
    }
}
