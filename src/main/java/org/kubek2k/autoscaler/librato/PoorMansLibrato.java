package org.kubek2k.autoscaler.librato;

import java.util.Optional;

public class PoorMansLibrato {

    private final String commonPrefix;

    public PoorMansLibrato(final String commonPrefix) {
        this.commonPrefix = commonPrefix;
    }

    public void reportMeasure(final String name, final double value, final String unit, final Optional<String> source) {
        report(name, Double.toString(value), unit, source);
    }

    public void reportMeasure(final String name, final int value, final String unit, final Optional<String> source) {
        report(name, Integer.toString(value), unit, source);
    }

    private void report(final String name, final String val, final String unit, final Optional<String> source) {
        final String sourceString = source.map(s -> "source=" + s).orElse("");
        System.out.println(sourceString + " measure#" + this.commonPrefix + "." + name + "=" + val + unit);
    }

    public MeasureReporter measureReporter(final String name, final String unit, final Optional<String> source) {
        return new MeasureReporter(name, unit, source);
    }

    public class MeasureReporter {
        private final String name;
        private final String unit;
        private final Optional<String> source;

        public MeasureReporter(final String name, final String unit, final Optional<String> source) {
            this.name = name;
            this.unit = unit;
            this.source = source;
        }

        public int report(final int val) {
            reportMeasure(this.name, val, this.unit, this.source);
            return val;
        }

        public double report(final double val) {
            reportMeasure(this.name, val, this.unit, this.source);
            return val;
        }

    }
}
