package onebrc.java;

public class Result {
    private int min;
    private int max;
    private int sum;
    private int cnt;

    public Result(int temp) {
        min = temp;
        max = temp;
        sum = temp;
        cnt = 1;
    }

    public void add(final int temp) {
        cnt++;
        sum += temp;
        min = BitwiseHelpers.branchlessMin(min, temp);
        max = BitwiseHelpers.branchlessMax(max, temp);
    }

    public void merge(final Result other) {
        sum += other.sum;
        cnt += other.cnt;
        min = BitwiseHelpers.branchlessMin(min, other.min);
        max = BitwiseHelpers.branchlessMax(max, other.max);
    }

    private double round(final double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @Override
    public String toString() {
        double min = round(this.min / 10.0);
        double max = round(this.max / 10.0);
        double avg = round(sum / 10.0 / cnt);
        return min + "/" + avg + "/" + max;
    }
}
