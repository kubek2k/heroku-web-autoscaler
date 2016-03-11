package plan3.ner.brute.model;

public class RatioEntry {

    private final long epochTimestamp;

    private final double ratio;

    public RatioEntry(final long epochTimestamp, final double ratio) {
        this.epochTimestamp = epochTimestamp;
        this.ratio = ratio;
    }

    public long getEpochTimestamp() {
        return this.epochTimestamp;
    }

    public double getRatio() {
        return this.ratio;
    }
}

