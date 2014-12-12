package com.textserv.framework;

import java.util.Comparator;
import java.util.Date;

public class DataObjectComparator implements Comparator<DataObject> {

	private String key_to_use; 
	private static final int sort_asc = 0;
	@SuppressWarnings("unused")
	private static final int sort_desc = 1;
	private int sort_direction = sort_asc;
	
	public DataObjectComparator( String key_to_use, int sort_direction ) {
		this.key_to_use = key_to_use;
		this.sort_direction = sort_direction;
	}
	
	@Override
	public int compare(DataObject in1, DataObject in2) {
		DataObject do1, do2 = null;
		if (sort_direction == sort_asc ) {
			do1 = in1;
			do2 = in2;
		} else {
			do2 = in1;
			do1 = in2;
		}
		DataObject meta = do1.getDataObject("MetaData");
		if ( meta != null ) {
			String type = meta.getString(key_to_use);
			if ( type == "string") {
				return compareStrings(do1.getString(key_to_use), do2.getString(key_to_use));
			} else if ( type == "int") {
				return compareInts(do1.getInt(key_to_use), do2.getInt(key_to_use));
			} else if ( type == "long") {
				return compareLongs(do1.getLong(key_to_use), do2.getLong(key_to_use));
			} else if ( type == "short") {
				return compareShorts(do1.getShort(key_to_use), do2.getShort(key_to_use));
			} else if ( type == "double") {
				return compareDoubles(do1.getDouble(key_to_use), do2.getDouble(key_to_use));
			} else if ( type == "float") {
				return compareFloats(do1.getFloat(key_to_use), do2.getFloat(key_to_use));
			} else if ( type == "boolean") {
				return compareBooleans(do1.getBoolean(key_to_use), do2.getBoolean(key_to_use));
			} else if ( type == "byte") {
				return compareBytes(do1.getByte(key_to_use), do2.getByte(key_to_use));
			} else if ( type == "date") {
				return compareDates(do1.getDate(key_to_use), do2.getDate(key_to_use));
			} else if ( type == "char") {
				return compareChars(do1.getChar(key_to_use), do2.getChar(key_to_use));
			}
		}
		return -1;
	}
		
	private int compareStrings( String val1, String val2) {
		return val1.compareTo(val1); 	
	}

	private int compareInts( Integer val1, Integer val2) {
		return val1.compareTo(val1); 	
	}

	private int compareLongs( Long val1, Long val2) {
		return val1.compareTo(val1); 	
	}

	private int compareShorts( Short val1, Short val2) {
		return val1.compareTo(val1); 	
	}

	private int compareDoubles( Double val1, Double val2) {
		return val1.compareTo(val1); 	
	}

	private int compareFloats( Float val1, Float val2) {
		return val1.compareTo(val1); 	
	}

	private int compareBooleans( Boolean val1, Boolean val2) {
		return val1.compareTo(val1); 	
	}

	private int compareBytes( Byte val1, Byte val2) {
		return val1.compareTo(val1); 	
	}

	private int compareDates( Date val1, Date val2) {
		return val1.compareTo(val1); 	
	}

	private int compareChars( Character val1, Character val2) {
		return val1.compareTo(val1); 	
	}
}
