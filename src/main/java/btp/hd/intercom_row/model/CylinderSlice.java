package btp.hd.intercom_row.model;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CylinderSlice extends HeatChunk implements Serializable {

    private static final double DIRECT_CONST = Math.sqrt(2) / (Math.sqrt(2) + 1.0) / 4;
    private static final double DIAGONAL_CONST = 1 / (Math.sqrt(2) + 1.0) / 4;

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

    public void nextIteration() {
        double maxDifference = 0;
        double[][] result = new double[height()][width()];

        // calculate next temperatures
        for (int i = 1; i < height() - 1; i++) {
            for (int j = 1; j < width() - 1; j++) {
                result[i][j] = nextTemp(i, j);
            }
        }

        // find maximum delta between old and new temperatures
        for (int i = 1; i < height() - 1; i++) {
            for (int j = 1; j < width() - 1; j++) {
                maxDifference = Math.max(maxDifference, Math.abs(getTemp()[i][j] - result[i][j]));
            }
        }

        update(result, maxDifference);
    }

    public void update(double[][] nextTemperatures, double maxDelta) {
        setTemp(nextTemperatures);

        for (int i = 1; i < height() - 1; i++) {
            getTemp()[i][0] = getTemp()[i][width() - 2];
            getTemp()[i][width() - 1] = getTemp()[i][1];
        }

        this.iteration++;
        this.maxDelta = maxDelta;

        this.topReady = false;
        this.botReady = false;
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

    private double nextTemp(int i, int j) {
        double w = getCond()[i][j];
        double restW = 1 - w;

        return
            // Current temperature
            w * getTemp()[i][j] +
            // Direct neighbours
            restW * DIRECT_CONST * getDirectTemps(i, j)+
            // Diagonal neighbours
            restW * DIAGONAL_CONST * getDiagonalTemps(i, j);
    }

    private double getDirectTemps(int i, int j) {
        return getTemp()[i - 1][j] + getTemp()[i][j - 1] + getTemp()[i][j + 1] + getTemp()[i + 1][j];
    }

    private double getDiagonalTemps(int i, int j) {
        return getTemp()[i - 1][j - 1] + getTemp()[i - 1][j + 1] + getTemp()[i + 1][j - 1] + getTemp()[i + 1][j + 1];
    }

    public boolean ready() {
        return topReady && botReady;
    }
}
