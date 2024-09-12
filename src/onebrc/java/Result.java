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
        min = branchlessMin(min, temp);
        max = branchlessMax(max, temp);
    }

    public void merge(final Result other) {
        sum += other.sum;
        cnt += other.cnt;
        min = branchlessMin(min, other.min);
        max = branchlessMax(max, other.max);
    }

    private int branchlessMin(final int a, final int b) {
        return b + ((a - b) & ((a - b) >> 31));
    }

    private int branchlessMax(final int a, final int b) {
        return a - ((a - b) & ((a - b) >> 31));
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
