package btp.hd.intercom_row.model;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class TempResult extends TempChunk {

    private final int parentOffset;

    private int iteration;
    private double maxDelta;
    private int rowsAdded;

    private TempResult(double[][] temp, int parentOffset, int iteration, double maxDelta) {
        super(temp);
        this.parentOffset = parentOffset;
        this.iteration = iteration;
        this.maxDelta = maxDelta;
        this.rowsAdded = temp.length;
    }

    private TempResult(int height, int width, int parentOffset, int iteration) {
        super(new double[height][width]);
        this.parentOffset = parentOffset;
        this.iteration = iteration;
        this.maxDelta = 0;
        this.rowsAdded = 0;
    }

    public static TempResult of(double[][] temp, int offsetFromParent, int iteration, double maxDifference) {
        return new TempResult(temp, offsetFromParent, iteration, maxDifference);
    }

    public static TempResult of(CylinderSlice slice) {
        return new TempResult(slice.height() - 2, slice.width() - 2, slice.getParentOffset(), slice.getIteration());
    }

    public void add(TempResult result) {
        System.arraycopy(result.getTemp(), 0, getTemp(), result.getParentOffset(), result.height());
        rowsAdded += result.height();
        maxDelta = Math.max(maxDelta, result.getMaxDelta());
        iteration = result.getIteration();
    }

    public boolean finished() {
        return rowsAdded >= getTemp().length;
    }

    public TempRow getTopRow() {
        return new TempRow(iteration, getTemp()[0]);
    }

    public TempRow getBotRow() {
        return new TempRow(iteration, getTemp()[height() - 1]);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
