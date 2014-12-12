package com.textserv.framework.measure;

import java.text.NumberFormat;

import org.apache.log4j.Logger;

import com.textserv.framework.DataObject;

public class ThroughputBenchmark {
	static Logger log = Logger.getLogger(ThroughputBenchmark.class.getName());

	protected long firstMessageTime = 0;
	protected int intermediateCount = 0;
	protected long numberOfMessages = 0;

	protected boolean measurePeak = false;
	protected boolean logEachInterval = true;
	protected int intermediateLogInterval = 100;
	protected boolean logToFile = false;
	protected String name;
	protected String logMessage;

	private long messagesPerSecondLog = 0;
	protected long numberOfMessagesLog = 0;
	protected long totalNumberOfMessagesLog = 0;

	public ThroughputBenchmark(String name) {
		this(name, true, false, 100, false);
	}

	public ThroughputBenchmark(String name, boolean logEachInterval) {
		this(name, true, logEachInterval, 100, false);
	}

	public ThroughputBenchmark(String name, boolean measurePeak, boolean logEachInterval, int intermediateLogInterval,  boolean logToFile) {
		this.name = name;
		this.measurePeak = measurePeak;
		this.logEachInterval = logEachInterval;
		this.intermediateLogInterval = intermediateLogInterval;
		this.logToFile = logToFile;
		logMessage = (name + " Data throughput: [0/s]. [0] processed.");
		ThroughputBenchmarkPrinter.getBenchmarkPrinter().addBenchmark(this);
	}

	public String getStats() {
		logThroughPut();
		return (name + " Data throughput: [" + NumberFormat.getInstance().format(messagesPerSecondLog) + "/s]. [" + NumberFormat.getInstance().format(numberOfMessagesLog) + " / " + NumberFormat.getInstance().format(totalNumberOfMessagesLog) + " ] processed.");
	}

	public DataObject getStats(DataObject dO) {
		dO.setDouble(name + "_msgs_per_sec", messagesPerSecondLog);
		dO.setDouble(name + "_num_msgs", numberOfMessagesLog);
		dO.setDouble(name + "_total_msgs", totalNumberOfMessagesLog);
		return dO;
	}

	public void incrementCount( ) {
		incrementCount(1);
	}
	public void incrementCount(int count ) {
		// No starttime set is marked by a zero value.
		if (firstMessageTime == 0) {
			setFirstCountedMessage();
		}

		numberOfMessages += count;
		intermediateCount += count;
		totalNumberOfMessagesLog += count;
		if (intermediateCount >= intermediateLogInterval) {
			logEachInterval();
		}
	}

	/**
	 * Set start time and reset message count
	 */
	protected void setFirstCountedMessage() {
		firstMessageTime = System.currentTimeMillis();
		numberOfMessages = 0;
	}

	protected void logEachInterval() {
		if (logEachInterval) {
			logThroughPut();
		}
		intermediateCount = 0;
	}

	/**
	 * calculate throughput in messages per second and log it.
	 */
	protected void logThroughPut() {
		long cleanupTime = System.currentTimeMillis();
		double n = (cleanupTime - firstMessageTime) / 1000.0;
		long numMessages = numberOfMessages;

		if (measurePeak) {
			// If measuring peak throughput then we reset all startpoints.
			setFirstCountedMessage();
		}

		messagesPerSecondLog = Math.round(numMessages / n);
		numberOfMessagesLog = numMessages;
		if ( logToFile ) {
			log.info(getStats());
		}
	}

	/**
	 * Cleanup calculating final throughput if not measuring peak throughput.
	 **/
	public void cleanUp() {
		if (!measurePeak) {
			logThroughPut();
		}
	}
}
