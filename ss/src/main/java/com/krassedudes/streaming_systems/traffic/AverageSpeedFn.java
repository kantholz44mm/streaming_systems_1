package com.krassedudes.streaming_systems.traffic;

import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.coders.CoderRegistry;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.transforms.Combine;

import java.io.Serializable;

public class AverageSpeedFn extends Combine.CombineFn<SpeedEvent, AverageSpeedFn.Accumulator, Double> {

    public static class Accumulator implements Serializable {
        double sumKmh = 0.0;
        long count = 0L;
    }

    @Override
    public Accumulator createAccumulator() {
        return new Accumulator();
    }

    @Override
    public Accumulator addInput(Accumulator acc, SpeedEvent input) {
        acc.sumKmh += input.getSpeedMs() * 3.6;
        acc.count++;
        return acc;
    }

    @Override
    public Accumulator mergeAccumulators(Iterable<Accumulator> accs) {
        Accumulator merged = new Accumulator();
        for (Accumulator a : accs) {
            merged.sumKmh += a.sumKmh;
            merged.count += a.count;
        }
        return merged;
    }

    @Override
    public Double extractOutput(Accumulator acc) {
        return acc.count == 0 ? 0.0 : (acc.sumKmh / acc.count);
    }

    @Override
    public Coder<Accumulator> getAccumulatorCoder(CoderRegistry registry, Coder<SpeedEvent> inputCoder) {
        return SerializableCoder.of(Accumulator.class);
    }
}