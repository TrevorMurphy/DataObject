package com.textserv.framework.measure;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

public class ThroughputBenchmarkPrinter implements Runnable{
	List<ThroughputBenchmark> benchmarks = new ArrayList<ThroughputBenchmark>();
	protected static ThroughputBenchmarkPrinter printer;
	protected long printRate = 10000;
    private static final Logger LOG = Logger.getLogger(ThroughputBenchmarkPrinter.class);
	
	public static ThroughputBenchmarkPrinter getBenchmarkPrinter() {
		if ( ThroughputBenchmarkPrinter.printer == null) {
			ThroughputBenchmarkPrinter.printer = new ThroughputBenchmarkPrinter();
		}
		return ThroughputBenchmarkPrinter.printer;
	}
	
	public void addBenchmark( ThroughputBenchmark benchmark) {
		benchmarks.add(benchmark);
	}
	
	public void setPrintRate( long millis ) {
		printRate = millis;
	}
	@Override
	public void run() {
		while ( true ) {
		for ( ThroughputBenchmark benchmark : benchmarks ) {
			LOG.info(benchmark.getStats());
		}
		LOG.info("****************************************");
		LOG.info("");
		try {
			Thread.sleep(printRate);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
	}

	private Runtime runtime = Runtime.getRuntime();

	public String info() {
	    StringBuilder sb = new StringBuilder();
//	    sb.append(this.OsInfo());
	    sb.append(this.MemInfo());
//	    sb.append(this.DiskInfo());
	    return sb.toString();
	}

	public String OSname() {
	    return System.getProperty("os.name");
	}

	public String OSversion() {
	    return System.getProperty("os.version");
	}

	public String OsArch() {
	    return System.getProperty("os.arch");
	}

	public long totalMem() {
	    return Runtime.getRuntime().totalMemory();
	}

	public long usedMem() {
	    return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	public String MemInfo() {
	    NumberFormat format = NumberFormat.getInstance();
	    StringBuilder sb = new StringBuilder();
	    long maxMemory = runtime.maxMemory();
	    long allocatedMemory = runtime.totalMemory();
	    long freeMemory = runtime.freeMemory();
	    sb.append("Free memory: ");
	    sb.append(format.format(freeMemory / 1024));
	    sb.append("<br/>");
	    sb.append("Allocated memory: ");
	    sb.append(format.format(allocatedMemory / 1024));
	    sb.append("<br/>");
	    sb.append("Max memory: ");
	    sb.append(format.format(maxMemory / 1024));
	    sb.append("<br/>");
	    sb.append("Total free memory: ");
	    sb.append(format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024));
	    sb.append("<br/>");
	    return sb.toString();
	}

	public String OsInfo() {
	    StringBuilder sb = new StringBuilder();
	    sb.append("OS: ");
	    sb.append(this.OSname());
	    sb.append("<br/>");
	    sb.append("Version: ");
	    sb.append(this.OSversion());
	    sb.append("<br/>");
	    sb.append(": ");
	    sb.append(this.OsArch());
	    sb.append("<br/>");
	    sb.append("Available processors (cores): ");
	    sb.append(runtime.availableProcessors());
	    sb.append("<br/>");
	    return sb.toString();
	}

	public String DiskInfo() {
	    /* Get a list of all filesystem roots on this system */
	    File[] roots = File.listRoots();
	    StringBuilder sb = new StringBuilder();

	    /* For each filesystem root, print some info */
	    for (File root : roots) {
	        sb.append("File system root: ");
	        sb.append(root.getAbsolutePath());
	        sb.append("<br/>");
	        sb.append("Total space (bytes): ");
	        sb.append(root.getTotalSpace());
	        sb.append("<br/>");
	        sb.append("Free space (bytes): ");
	        sb.append(root.getFreeSpace());
	        sb.append("<br/>");
	        sb.append("Usable space (bytes): ");
	        sb.append(root.getUsableSpace());
	        sb.append("<br/>");
	    }
	    return sb.toString();
	}
}
