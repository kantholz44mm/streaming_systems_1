package hob;

import java.util.ArrayList;
import java.util.List;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Sum;
import org.apache.beam.sdk.transforms.windowing.AfterPane;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.GlobalWindow;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.PCollection;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.Duration;

public class WindowInfos {

	static int j = 0;

	public static void main(String[] args) {

		List<Integer> ints = new ArrayList<Integer>();
		// Create Collection with n elements
		final int n = 10;
		for (int i = 0; i < n; i++) {
			ints.add(i);
		}

		System.out.println(ints + "\n");
		// Create the pipeline
		PipelineOptions options = PipelineOptionsFactory.create();
		Pipeline p = Pipeline.create(options);

		// Apply Create, passing the list and the coder, to create the PCollection
		PCollection<Integer> pc = p.apply(Create.of(ints));

		// Assign timestamps
		PCollection<Integer> pcWithTimestamp = pc.apply(ParDo.of(new DoFn<Integer, Integer>() {
			@ProcessElement
			public void processElement(@Element Integer element, OutputReceiver<Integer> out) {

				// Extract the timestamp from current entry in the PCollection pc
				Instant i = Instant.now();

				// Set the new timestamp by adding one second each 
				out.outputWithTimestamp(element, i.plus(j++ * 1000));
			}
		}));
	
		
		// Show the entries with their timestamps
		pcWithTimestamp.apply(ParDo.of(new PrintTimeStamp()));


		
		final boolean showDetails = false;
		
		if (!showDetails){
		// Define and fixed window und compute the sum of the values contained in each window
 		PCollection<Integer> pcWindowed = pcWithTimestamp
 				.apply(Window.<Integer>into(FixedWindows.of(Duration.standardSeconds(3))))
 				.apply(Sum.integersGlobally().withoutDefaults())
 				.apply(ParDo.of(new PrintIntegerWithWindowInfo()));
		}
		
		
		if (showDetails){
 		// Update the sum each time, when a new value enters the current window
 		PCollection<Integer> pcWindowed = pcWithTimestamp
				.apply(Window.<Integer>into(FixedWindows.of(Duration.standardSeconds(3)))
					.triggering(Repeatedly.forever(AfterPane.elementCountAtLeast(1)))
					.withAllowedLateness(Duration.standardMinutes(1))
					.accumulatingFiredPanes())
				.apply(Sum.integersGlobally().withoutDefaults())
				.apply(ParDo.of(new PrintIntegerWithWindowInfo()));
		}
		
		// start the pipeline 
		p.run().waitUntilFinish();

	}

	static class PrintIntegerWithWindowInfo extends DoFn<Integer, Integer> {
		@ProcessElement
		public void processElement(@Element Integer intValue, ProcessContext c, BoundedWindow window,
				OutputReceiver<Integer> out) {
			if (window instanceof GlobalWindow) {
				System.out.println("[global window]");
			} else if (window instanceof IntervalWindow) {
				IntervalWindow interval = (IntervalWindow) window;
				System.out.println("\n==================================");
				System.out.println("[" + formatTime(interval.start()) + ", " + formatTime(interval.end()) + "): " + intValue);
				System.out.println("==================================");
			} else {
				System.out.println("..., " + formatTime(window.maxTimestamp()) + "]");
			}			
		}
	}

	static class PrintTimeStamp extends DoFn<Integer, Integer> {
		@ProcessElement
		public void processElement(@Element Integer input, @Timestamp Instant timestamp,
				OutputReceiver<Integer> output) {
			System.out.println(timestamp + ": " + input);
		}
	}

	static class PrintValue extends DoFn<Integer, Integer> {
		@ProcessElement
		public void processElement(@Element Integer input, @Timestamp Instant timestamp,
				OutputReceiver<Integer> output) {
			System.out.println(input);
			output.output(input);
		}
	}


	public static String formatTime(Instant timestamp) {
		if (timestamp == null) {
			return "null";
		} else if (timestamp.equals(BoundedWindow.TIMESTAMP_MIN_VALUE)) {
			return "TIMESTAMP_MIN_VALUE";
		} else if (timestamp.equals(BoundedWindow.TIMESTAMP_MAX_VALUE)) {
			return "TIMESTAMP_MAX_VALUE";
		} else if (timestamp.equals(GlobalWindow.INSTANCE.maxTimestamp())) {
			return "END_OF_GLOBAL_WINDOW";
		} else {
			return timestamp.toString(TIME_FMT);
		}
	}
	
	public static DateTimeFormatter TIME_FMT = DateTimeFormat.forPattern("HH:mm:ss");


}
