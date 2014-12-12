package com.textserv.framework.codecs;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import net.minidev.json.mapper.AMapper;

import com.textserv.framework.DataObject;

public class DataObjectMapper extends AMapper<DataObject> {

	private boolean firstObj = true;
	private DataObject firstDo = null;
    private static ThreadLocal<DateTimeFormatter> dateFmttrHolder = new ThreadLocal<DateTimeFormatter>();
	private static final Logger LOG = Logger.getLogger(DataObjectMapper.class);
	private static Pattern isoRegEx = Pattern.compile("(\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\.\\d+([+-][0-2]\\d:[0-5]\\d|Z))|(\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d([+-][0-2]\\d:[0-5]\\d|Z))|(\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d([+-][0-2]\\d:[0-5]\\d|Z))");

	public DataObjectMapper() {
	}

	public DataObjectMapper(DataObject firstDo) {
		this.firstDo = firstDo;
	}

	public static AMapper<DataObject> DEFAULT = new DataObjectMapper();

	@Override
	public AMapper<DataObject> startObject(String key) {
		return DEFAULT;
	}

	@Override
	public AMapper<DataObject> startArray(String key) {
		return DEFAULT;
	}

	@Override
	public Object createObject() {
		if ( firstDo != null && firstObj) {
			firstObj = false;
			return firstDo;
		} else {
			return new DataObject();
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Object createArray() {
		return new ArrayList();
	}

	   private DateTimeFormatter getDateFormater() {		   
	    	DateTimeFormatter formatter = dateFmttrHolder.get();
	        if ( formatter == null ) {
	        	formatter = ISODateTimeFormat.dateTime();
	        	dateFmttrHolder.set(formatter);
	        }
	        return formatter;
	    }
	   
	@SuppressWarnings("deprecation")
	@Override
	public void setValue(Object current, String key, Object value) {
		//hack to handle mongodb $types
		if ( value instanceof DataObject ) {
			DataObject doValue = (DataObject)value;
			if ( doValue.containsKey("$date")) {
				((DataObject) current).put(key, doValue.getDate("$date"));
			} else {
				((DataObject) current).put(key, value);
			}
		} else if ( value instanceof String ) {
			Matcher isoMatcher = isoRegEx.matcher((String)value);
			if ( isoMatcher.matches() ) {
				DateTimeFormatter formatter = getDateFormater();
				try {
					DateTime parsedDateTime = formatter.parseDateTime((String)value);
					((DataObject) current).put(key, parsedDateTime.toDate());
				} catch (Exception e ) {
					((DataObject) current).put(key, value);
				}
			} else {
				((DataObject) current).put(key, value);
			}
		} else {
			((DataObject) current).put(key, value);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void addValue(Object current, Object value) {
		((ArrayList) current).add(value);
	}

}
