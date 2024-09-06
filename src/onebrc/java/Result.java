package onebrc.java;

public class Result {
    public double min;
    public double max;
    public double sum;
    public int cnt;

    public Result(final double temp) {
        min = temp;
        max = temp;
        sum = temp;
        cnt = 1;
    }

    public void add(final double temp) {
        cnt++;
        sum += temp;

        if (temp < min) {
            min = temp;
        }

        if (temp > max) {
            max = temp;
        }
    }

    public void merge(final Result other) {
        sum += other.sum;
        cnt += other.cnt;

        if (other.min < min) {
            min = other.min;
        }

        if (other.max > max) {
            max = other.max;
        }
    }

    public double mean() {
        return Math.round(sum / cnt * 10) / 10.0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("sum: ");
        sb.append(sum);
        sb.append(" | cnt: ");
        sb.append(cnt);
        sb.append(" | min: ");
        sb.append(min);
        sb.append(" | max: ");
        sb.append(max);
        sb.append(" | avg: ");
        sb.append(mean());
        return sb.toString();
    }
}
