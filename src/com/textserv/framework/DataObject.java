package com.textserv.framework;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import com.textserv.framework.codecs.DataObjectJSONCodec;
import com.textserv.framework.codecs.DataObjectXMLCodec;
import com.textserv.framework.codecs.DataObjectXPath;

public class DataObject extends LinkedHashMap<String, Object> {

	private static final long serialVersionUID = 205004585890679068L;
	private static final String NOT_A_NUMBER = "NAN";
	public static final String DO_NAME = "txtsrv_Name";
	public static final String DO_UNIQUEID = "txtsrv_UniqueId";
    
	//formatters are not thread safe, c'mon guys can't you build this stuff right, so keep in a thread local storage variable to prevent threads stomping on each other
	private static transient ThreadLocal<DecimalFormat> decimalFmttrHolder = new ThreadLocal<DecimalFormat>();
    private static transient ThreadLocal<Map<String, DateFormat>> dateFmttrHolder = new ThreadLocal<Map<String, DateFormat>>();
    private transient long sequence;//useful for our disruptor queueing
    private transient boolean autoGenerateUniqueIdOnSerialization = false;
    private transient boolean generateMetaData = false;
    private transient List<String> dirtyFields = null;
    private transient boolean trackDirtyFields = false;
    private transient HashMap<String, Object> transientCache = null;
    private transient boolean labelStampObjects = false;
    private transient DataObject labelStamp = null;
    
    private boolean store_as_strings = false;
	private boolean immutable = false;
    
//would like to do this, and do not want to have all threads syncronize since that could be slow	
//	private static transient DecimalFormat dcmlFmttr = new DecimalFormat(
//			"#################0.00000000");

	public DataObject() {
	}

	public DataObject(String name) {
		setName(name);
	}

	public DataObject(String keyName, Object value) {
		put(keyName, value);
	}

	public Map<String, Object> getTransientCache() {
		if ( transientCache == null ) {
			transientCache = new HashMap<String, Object>();
		}
		return transientCache;
	}
	
	public void setTransient( String name, Object value ) {
		getTransientCache().put(name, value);
	}

	public Object getTransient( String name ) {
		return getTransientCache().get(name);
	}
	
	public static DataObject fromXMLFile( String fileName ) throws DataObjectException {
    	DataObject returnDO = null;
        try {
        	FileReader reader = new FileReader(fileName);
        	returnDO = DataObjectXMLCodec.createFromXML(reader);
        } catch( Exception e) {
        	throw new DataObjectException(e);
        }
        return returnDO;
	}
	
	public void toXMLFile( String fileName ) throws DataObjectException {
        try {
        	if ( autoGenerateUniqueIdOnSerialization ) {
        		generateMD5Digest();
        	}
            FileWriter writer = new FileWriter(fileName);
            BufferedWriter bufWriter = new BufferedWriter(writer);
            DataObjectXMLCodec.saveAsXML(this, bufWriter);
            bufWriter.close();
        } catch( Exception e) {
        	throw new DataObjectException(e);
        }		
	}

	public String toStringEncoded() throws DataObjectException {
		return toStringEncoded(false);
	}

	public static DataObject fromJsonString( String jsonString ) throws DataObjectException {
		DataObject newObj = new DataObject();
		newObj.fromStringEncoded(jsonString);
		return newObj;
	}
	
	public void toFileAsJson(String filename, boolean pretty) throws DataObjectException {
		String asString = toStringEncoded(pretty);
		
        BufferedWriter writer;
        try { 
        	writer = new BufferedWriter( new FileWriter(filename) );
        	writer.write( asString );
        	writer.close();
        } catch(IOException e) {
        	throw new DataObjectException(e);
        }
	}

	public void fromFileAsJson(String filename) throws DataObjectException {
		
        try { 
        	BufferedReader reader = new BufferedReader( new FileReader (filename) );
            String         line = null;
            StringBuilder  stringBuilder = new StringBuilder();
            String         ls = System.getProperty("line.separator");
            while( ( line = reader.readLine() ) != null ) {
                stringBuilder.append( line );
                stringBuilder.append( ls );
            }

            fromStringEncoded(stringBuilder.toString());      
        } catch(IOException e) {
        	throw new DataObjectException(e);
        }
	}

	//dump to our efficient string encoded format
	public String toStringEncoded(boolean pretty) throws DataObjectException {
    	if ( autoGenerateUniqueIdOnSerialization ) {
    		generateMD5Digest();
    	}
		return DataObjectJSONCodec.toJSONString(this, pretty);
	}
	
	//create from our string encoded format
	public void fromStringEncoded(String encodedString) throws DataObjectException {
		clear();
		DataObjectJSONCodec.fromJSONString(encodedString, this);
	}
	
	public String toXMLEncoded() throws Exception {
    	if ( autoGenerateUniqueIdOnSerialization ) {
    		generateMD5Digest();
    	}
		StringWriter writer = new StringWriter();
		DataObjectXMLCodec.saveAsXML(this, writer);
		return writer.toString();
	}
	
	public void fromXMLEncoded(String xmlString ) throws DataObjectException {
		StringReader reader = new StringReader(xmlString);
		DataObjectXMLCodec.createFromXML(reader, this);
	}
	
	//xpath like access to dataobject values
	// "|" allows map access, ":" allows list access
	public void setValueByPath(String path, String value)throws DataObjectException {
		DataObjectXPath.setValueByPath(this, path, value);
	}
	
	public void setObjectByPath(String path, Object value)throws DataObjectException {
		DataObjectXPath.setObjectByPath(this, path, value);
	}

	//xpath like access to dataobject values
	// "|" allows map access, ":" allows list access
	public String getValueByPath(String path) throws DataObjectException{
		return DataObjectXPath.getValueByPath(this, path);
	}

	public String getValueByPath(String path, boolean tolerateBadPath) throws DataObjectException{
		return DataObjectXPath.getValueByPath(this, path, tolerateBadPath);
	}

	public void removeObjectByPath(String path) throws DataObjectException{
		 setObjectByPath(path, null);
	}
	
	public Object getObjectByPath(String path) throws DataObjectException{
		return DataObjectXPath.getObjectByPath(this, path);
	}

	public boolean getBooleanByPath(String path) throws DataObjectException{
		Object object = DataObjectXPath.getObjectByPath(this, path, true);
		boolean returnVal = false;
		if ( object != null) {
			if ( object instanceof Boolean ) {
				returnVal = (Boolean)object;
			} else if ( object instanceof String ) {
				returnVal = Boolean.valueOf(object.toString()).booleanValue();				
			}
		}
		return returnVal;
	}

	public boolean getBoolean(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if ( object instanceof Boolean ) {
				return (Boolean)object;
			} else {
				return Boolean.valueOf(object.toString()).booleanValue();				
			}
		} catch (Exception e) {
			return false;
		}
	}

	public byte getByte(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if ( object instanceof Byte ) {
				return (Byte)object;
			} else {
				return Byte.valueOf((String) object.toString()).byteValue();
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public char getChar(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if ( object instanceof Character ) {
				return (Character)object;
			} else {
				return ((String) object.toString()).charAt(0);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public double getDouble(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if (object == null )
				 return Double.NaN; 
			else if ( object instanceof Double ) {
				return (Double)object;
			} else {
				String stringValue = (String) object.toString();
				if (NOT_A_NUMBER.equals(stringValue)) {
					return Double.NaN;
				}
				return Double.valueOf(stringValue).doubleValue();
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public float getFloat(String key) throws DataObjectException {
		try {
				Object object = super.get(key);
				if ( object instanceof Float ) {
					return (Float)object;
				} else {
					return Float.valueOf((String) object.toString()).floatValue();
				}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public int getInt(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if ( object instanceof Integer ) {
				return (Integer)object;
			} else {
				return Integer.parseInt((String) object.toString());
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public long getLong(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if ( object instanceof Long ) {
				return (Long)object;
			} else {
			return Long.valueOf((String) object.toString()).longValue();
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}
    
    public Date getDate(String key) throws DataObjectException {
        try {
        	Object object = super.get(key);
        	if(object != null) {
        		if ( object instanceof Date) {
        			return (Date)object;
        		} else {
        			DateFormat dateFormater = getDateFormater();
        			return dateFormater.parse((String)object.toString());
        		}
            }
            return null;
        } catch (ParseException e) {
            return tryAlternativeDateParse(key);
        } catch (Exception e) {
            throw new DataObjectException(e);
        }
    }
    
    private Date tryAlternativeDateParse(String key) throws DataObjectException {
        try {
        	Object value = super.get(key);
        	if(value != null) {
        		DateFormat dateFormater = getSecondaryDateFormater();
            	return dateFormater.parse((String)value.toString());
            }
            return null;
        } catch (Exception e) {
            throw new DataObjectException(e);
        }    	
    }

    /**
	 * Get a List value from the DataObject. If there is no such List, it
	 * returns an empty one
	 */

	
	public List<DataObject> getDataObjectList(String key) throws DataObjectException {
		try {
			@SuppressWarnings("unchecked")
			List<DataObject> value = (List<DataObject>) super.get(key);
			if (value == null) {
				value = Collections.emptyList();
			}
			return value;
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	
	public List<DataObject> getDataObjectList(String key, boolean createIfNotFound) throws DataObjectException {
		try {
			Object objValue = super.get(key);
			if ( objValue instanceof StringEncodedDataObjectList) {
				return toDataObjectList((StringEncodedDataObjectList)objValue);
			} else {
				@SuppressWarnings("unchecked")
				List<DataObject> value = (List<DataObject>) super.get(key);
				if (value == null) {
					if ( createIfNotFound ) {
						value = new ArrayList<DataObject>();
						setDataObjectList(key, value);
					} else {
						value = Collections.emptyList();					
					}
				}
				return value;
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	private List<DataObject> toDataObjectList( StringEncodedDataObjectList objValue) throws DataObjectException {
		List<DataObject> dataObjects = new ArrayList<DataObject>();
		if ( objValue.encodedDataObjects != null ) {
			for ( String encodedDataObject : objValue.encodedDataObjects ) {
				DataObject newObj = new DataObject();
				newObj.fromStringEncoded(encodedDataObject);
				dataObjects.add(newObj);
			}
		}
		return dataObjects;
	}

	
	public List<String> getStringList(String key) throws DataObjectException {
		try {
			@SuppressWarnings("unchecked")
			List<String> value = (List<String>) super.get(key);
			if (value == null) {
				value = new ArrayList<String>();
			}
			return value;
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	
	@SuppressWarnings("unchecked")
	public List<String> getStringList(String key, boolean createIfNotFound) throws DataObjectException {
		if ( !createIfNotFound ) {
			return getStringList(key);
		}
		try {
			if ( !itemExists(key)) {
				List<String> value = new ArrayList<String>();
				put(key, value);
				return value;
			} else {
				return (List<String>) super.get(key);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	
    public List<Integer> getIntegerList(String key) throws DataObjectException {
        try {
            @SuppressWarnings("unchecked")
			List<String> raw = (List<String>) super.get(key);
            List<Integer> value;
            if (raw == null) {
                value = Collections.emptyList();
            } else {
                value = new ArrayList<Integer>();
                for (String intString : raw) {
                    value.add(Integer.parseInt(intString));
                }
            }
            return value;
        } catch (Exception e) {
            throw new DataObjectException(e);
        }
    }

	public short getShort(String key) throws DataObjectException {
		try {
			Object object = super.get(key);
			if ( object instanceof Short) {
				return (Short)object;
			}
			return Short.valueOf((String) object.toString()).shortValue();
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	//return as a simple string or a json string for more complex types
	public String getAsString( String key ) throws DataObjectException {
		Object value = super.get(key);
		if ( value instanceof String ) {
			return (String)value;
		} else {
			return DataObjectJSONCodec.toJSONString(value);
		}
	}

	public void setFromString( String key, String value ) throws DataObjectException {
		if ( !value.startsWith("{") ) {
			trackMetaData(key, "string");
			super.put(key, value);
		} else {
			try {
				trackMetaData(key, "do");
				super.put(key, DataObjectJSONCodec.fromJSONString(value));
			} catch (Exception e) {
				throw new DataObjectException(e);
			}
		}
	}
	
	/**
	 * Get a DataObject value from the DataObject.
	 */

	public DataObject getDataObject(String key) throws DataObjectException {
		try {
			return (DataObject) super.get(key);
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public DataObject getDataObject(String key, boolean createIfNeeded) throws DataObjectException {
		try {
			DataObject returnVal = (DataObject) super.get(key);
			if ( returnVal == null) {
				returnVal = new DataObject();
				setDataObject(key, returnVal);
			}
			return returnVal;
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public Object getObject(Object key) {
		return super.get(key);
	}

	/**
	 * Get the DataObject object's name.
	 */

	public String getName() {
		String name = (String) super.get(DO_NAME);
		return name == null ? "" : name;
	}

	/**
	 * Get a String value from the DataObject.
	 */

	public String getString(String key) throws DataObjectException {
		try {
			return (String) super.get(key);
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	/**
	 * Determines whether a value exists for a specific key.
	 */

	public boolean itemExists(String key) {
		return containsKey(key);
	}

	// Mutator methods for Java primitives

	public void setUsingType( String key, String value, String type) throws DataObjectException {
		if (type.equals("string") ) {
			setString(key, value);
		} else if (type.equals("boolean") ) {
			setBoolean(key, Boolean.parseBoolean(value));
		} else if (type.equals("byte" )) {
			setByte(key, Byte.parseByte(value));
		} else if (type.equals("char" )) {
			setChar(key, value.charAt(0));
		} else if (type.equals("double" )) {
			setDouble(key, Double.parseDouble(value));
		} else if (type.equals("float" )) {
			setFloat(key, Float.parseFloat(value));
		} else if (type.equals("int" )) {
			setInt(key, Integer.parseInt(value));
		} else if (type.equals("long" )) {
			setLong(key, Long.parseLong(value));
		} else if (type.equals("short" )) {
			setShort(key, Short.parseShort(value));
		} else if (type.equals("date" )) {
			DateFormat dateFormater = getDateFormater(); 
			try {
				setDate(key, dateFormater.parse(value));
			} catch ( ParseException pe) {
				throw new DataObjectException(pe);
			}
		} else if (type.equals("datetime" )) {
			setByte(key, Byte.parseByte(value));
		} else if (type.equals("json" )) {
			setByte(key, Byte.parseByte(value));
		} else if (type.equals("email" )) {
			setString(key, value);
		} else if (type.equals("email_md5" )) {
			setString(key, value);
		} else if (type.equals("list_name" )) {
			setString(key, value);
		} else if (type.equals("time_zone" )) {
			setString(key, value);
		} else if (type.equals("msisdn" )) {
			setString(key, value);
		} else if (type.equals("sarray" )) {
			List<String> sList = Arrays.asList(value.split("\\|"));
			setStringList(key, sList);
		} else if (type.equals("iarray" )) {
			List<Integer> iList = new ArrayList<Integer>();
			String[] iValues = value.split("\\|");
			for( String iValue : iValues ) {
				iList.add(Integer.parseInt(iValue));
			}
			setIntegerList(key, iList);
		} else if (type.equals("farray" )) {
			List<Float> fList = new ArrayList<Float>();
			String[] fValues = value.split("\\|");
			for( String fValue : fValues ) {
				fList.add(Float.parseFloat(fValue));
			}
			setFloatList(key, fList);
		} else if (type.equals("dtarray" )) {
			DateFormat dateFormater = getDateFormater(); 
			try {
				setDate(key, dateFormater.parse(value));
				List<Date> dtList = new ArrayList<Date>();
				String[] dtValues = value.split("\\|");
				for( String dtValue : dtValues ) {
					dtList.add(dateFormater.parse(dtValue));
				}
				setDateList(key, dtList);			
			} catch ( ParseException pe) {
				throw new DataObjectException(pe);
			}
		} else if (type.equals("shash" )) {
		}
	}

	public void setBoolean(String key, Boolean value) throws DataObjectException {
		try {
			trackMetaData(key, "boolean");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);				
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setBoolean(String key, boolean value) throws DataObjectException {
		try {
			trackMetaData(key, "boolean");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Boolean(value));				
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setByte(String key, Byte value) throws DataObjectException {
		try {
			trackMetaData(key, "byte");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}
	
	public void setByte(String key, byte value) throws DataObjectException {
		try {
			trackMetaData(key, "byte");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Byte(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setByte(String key, byte value, byte defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			setByte(key, value);
		}
	}

	public void setChar(String key, Character value) throws DataObjectException {
		try {
			trackMetaData(key, "char");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}
	
	public void setChar(String key, char value) throws DataObjectException {
		try {
			trackMetaData(key, "char");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Character(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setChar(String key, char value, char defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			setChar(key, value);
		}
	}


	public void setDouble(String key, Double value) throws DataObjectException {
		try {
			trackMetaData(key, "double");				
			if ( store_as_strings ) {
				DecimalFormat dcmlFmttr = decimalFmttrHolder.get();
				if ( dcmlFmttr == null ) {
					dcmlFmttr = new DecimalFormat("#################0.00000000");
					decimalFmttrHolder.set(dcmlFmttr);
				}
				if (Double.isNaN(value)) {
					super.put(key, NOT_A_NUMBER);
				} else {
					super.put(key, dcmlFmttr.format(value));
				}
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setDouble(String key, double value) throws DataObjectException {
		try {
			trackMetaData(key, "double");				
			if ( store_as_strings ) {
				DecimalFormat dcmlFmttr = decimalFmttrHolder.get();
				if ( dcmlFmttr == null ) {
					dcmlFmttr = new DecimalFormat("#################0.00000000");
					decimalFmttrHolder.set(dcmlFmttr);
				}
				if (Double.isNaN(value)) {
					super.put(key, NOT_A_NUMBER);
				} else {
					super.put(key, dcmlFmttr.format(value));
				}
			} else {
				super.put(key, new Double(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setDouble(String key, double value, double defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			setDouble(key, value);
		}
	}

	public void setFloat(String key, Float value) throws DataObjectException {
		try {
			trackMetaData(key, "float");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}
	
	public void setFloat(String key, float value) throws DataObjectException {
		try {
			trackMetaData(key, "float");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Float(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setFloat(String key, float value, float defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			setFloat(key, value);
		}
	}


	public void setInt(String key, Integer value) throws DataObjectException {
		try {
			trackMetaData(key, "int");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setInt(String key, int value) throws DataObjectException {
		try {
			trackMetaData(key, "int");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Integer(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setInt(String key, int value, int defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			setInt(key, value);
		}
	}

	public void setImmutable() {
		this.immutable  = true;
	}
	
	boolean isImmutable() {
		return immutable;
	}
	
	public void setLong(String key, Long value) throws DataObjectException {
		try {
			trackMetaData(key, "long");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setLong(String key, long value) throws DataObjectException {
		try {
			trackMetaData(key, "long");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Long(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setLong(String key, long value, long defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			setLong(key, value);
		}
	}

	
	public void setShort(String key, Short value) throws DataObjectException {
		try {
			trackMetaData(key, "short");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, value);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}
	
	public void setShort(String key, short value) throws DataObjectException {
		try {
			trackMetaData(key, "short");				
			if ( store_as_strings ) {
				super.put(key, String.valueOf(value));
			} else {
				super.put(key, new Short(value));
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	public void setShort(String key, short value, short defaultValue)
			throws DataObjectException {
		if (value != defaultValue) {
			trackMetaData(key, "short");				
			setShort(key, value);
		}
	}

    public void setBinaryStream(String key, InputStream is) throws DataObjectException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 4);
			byte[] raw = new byte[1024 * 4];
			while (is.read(raw) != -1) {
				out.write(raw);
			}
		String sb = new String(Base64.encodeBase64(out.toByteArray(), true));
			
//			StringBuilder sb = new StringBuilder(1024 * 4);
//			byte[] raw = new byte[1024 * 4];
//		int count = -1;
//			StringBuilder temp = new StringBuilder(1024 * 4);
//			while ((count = is.read(raw)) != -1) {
//				byte[] converted = null;
//				if (count < raw.length) {
//					byte[] revised = new byte[count];
//					System.arraycopy(raw, 0, revised, 0, count);
//					converted = Base64.encodeBase64(revised, true);
//				} else {
//					converted = Base64.encodeBase64(raw, true);
//				}
//				sb.append(new String(converted));
//			}
			trackMetaData(key, "binary");				
			setString(key, sb.toString());
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}
	
	public InputStream getBinaryStream(String key) throws DataObjectException {
		ByteArrayInputStream baos = null;
		try {
			String value = getString(key);
			if (value != null) {
				byte[] converted = Base64.decodeBase64(value.getBytes());
				baos = new ByteArrayInputStream(converted);
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
		return baos;
	}

    
	public void setDate(String key, Date value) throws DataObjectException {
    	if ( value != null ) {
    		try {
				trackMetaData(key, "date");				
				if ( store_as_strings ) {
					DateFormat dateFmttr = getDateFormater();
					super.put(key, dateFmttr.format(value));
				} else {
					super.put(key, value);
				}
    		} catch (Exception e) {
    			throw new DataObjectException(e);
    		}
    	} else {
    		super.remove(key);				
    	}
    }

	@SuppressWarnings("deprecation")
	public void setExpandedDate(String key, Date value) throws DataObjectException {
    	if ( value != null ) {
    		try {
    			setInt(key+"Year", value.getYear() + 1900);
    			setInt(key+"Month", value.getMonth());
    			setInt(key+"Date", value.getDate());
    			setInt(key+"Hour", value.getHours());
    			setInt(key+"Min", value.getMinutes());
    			setInt(key+"Sec", value.getSeconds());
    		} catch (Exception e) {
    			throw new DataObjectException(e);
    		}
    	} else {
    		super.remove(key);				
    	}
    }

	private DateFormat getDateFormater() {
    	Map<String, DateFormat> formatters = getDateFormaters();
    	return formatters.get("Default");
    }

    private Map<String, DateFormat> getDateFormaters() {
    	Map<String, DateFormat> formatters = dateFmttrHolder.get();
        if ( formatters == null ) {
        	formatters = new HashMap<String, DateFormat>();
        	formatters.put("Default", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        	formatters.put("Secondary", new SimpleDateFormat("MM/dd/yyyy"));
        	dateFmttrHolder.set(formatters);
        }
        return formatters;
    }
    private DateFormat getSecondaryDateFormater() {
    	Map<String, DateFormat> formatters = getDateFormaters();
    	return formatters.get("Secondary");
    }

    /**
	 * Set a List value in a DataObject. Calls <code>setObject()</code>.
	 */

	public void setDataObjectList(String key, List<DataObject> value) throws DataObjectException {
		try {
			if ( value != null ) {
				trackMetaData(key, "doList");				
				setObject_inner(key, value);
			} else {
				super.remove(key);				
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

    public void setIntegerList(String key, List<Integer> value) throws DataObjectException {
        try {
			if ( value != null ) {
				ArrayList<String> realList = new ArrayList<String>();
				for (int i : value) {
					realList.add(""+i);
				}
				trackMetaData(key, "iList");				
				setObject(key, realList);
			} else {
				super.remove(key);				
			}
        } catch (Exception e) {
            throw new DataObjectException(e);
        }
    }

    public void setFloatList(String key, List<Float> value) throws DataObjectException {
        try {
			if ( value != null ) {
				ArrayList<String> realList = new ArrayList<String>();
				for (float i : value) {
					realList.add(""+i);
				}
				trackMetaData(key, "fList");				
				setObject(key, realList);
			} else {
				super.remove(key);				
			}
        } catch (Exception e) {
            throw new DataObjectException(e);
        }
    }

    public void setDateList(String key, List<Date> value) throws DataObjectException {
        try {
			if ( value != null ) {
				ArrayList<String> realList = new ArrayList<String>();
    			DateFormat dateFmttr = getDateFormater();
				for (Date i : value) {
					realList.add(dateFmttr.format(i));
				}
				trackMetaData(key, "dtList");				
				setObject(key, realList);
			} else {
				super.remove(key);				
			}
        } catch (Exception e) {
            throw new DataObjectException(e);
        }
    }
   
    public void setStringList(String key, List<String> value) throws DataObjectException {
		try {
			if ( value != null ) {
				trackMetaData(key, "sList");				
				setObject(key, value);
			} else {
				super.remove(key);				
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	/**
	 * Set a DataObject value in a DataObject. Calls <code>setObject()</code>.
	 */

	public void setDataObject(String key, DataObject value) throws DataObjectException {
		setDataObject(key, value, true);
	}
	
	public void move(String key, String newObjectName, String stripPrefix) {
		String[] keysToMove = {key};
		move(keysToMove, newObjectName, stripPrefix);
	}
	
	public void move( String[] keysToMove, String newObectName, String stripPrefix) {
		DataObject newObject = null;
		if ( newObectName.indexOf(".") > -1 ) {
			String[] splitStr = newObectName.split("\\.");
			newObject = this;
			for ( String s : splitStr ) {
				newObject = newObject.getDataObject(s, true);
			}
		} else {
			newObject = getDataObject(newObectName, true);
		}		
		for ( String key :keysToMove) {
			if ( stripPrefix != null ) {
				String keyA = key.replace(stripPrefix, "");
				newObject.put(keyA, get(key));
			} else {
				newObject.put(key, get(key));
			}
			remove(key);
		}
	}
	
	public void moveAll(String prefix, String newObectName) throws DataObjectException {
		DataObject newObject = null;
		if ( newObectName.indexOf(".") > -1 ) {
			String[] splitStr = newObectName.split("\\.");
			newObject = this;
			for ( String s : splitStr ) {
				newObject = newObject.getDataObject(s, true);
			}
		} else {
			newObject = getDataObject(newObectName, true);
		}
		List<String> keysToRemove = new ArrayList<String>();
		for ( String key : keySet() ) {
			if ( key.startsWith(prefix)) {
				String keyA = key.replace(prefix, "");
				newObject.put(keyA, get(key));
				keysToRemove.add(key);
			}
		}
		for ( String key : keysToRemove ) {
			remove(key);
		}
	}
	
	public DataObject stampIfNeeded( DataObject object ) {
		if (labelStampObjects && !object.isImmutable()) {
			for ( String lskey : labelStamp.keySet() ) {
				object.put(lskey, labelStamp.get(lskey));
			}
		}
		return object;
	}
	
	public boolean isStampNeeded() {
		return labelStampObjects;
	}
	
	public void setDataObject(String key, DataObject value, boolean optrackMeta) throws DataObjectException {
		try {
			if ( value != null ) {
				if ( optrackMeta ) {
					trackMetaData(key, "do");
				}
				stampIfNeeded(value);
				setObject_inner(key, value);
			} else {
				super.remove(key);				
			}
		} catch (Exception e) {
			throw new DataObjectException(e);
		}
	}

	/**
	 * Set the DataObject object's name.
	 */

	
	public void setName(String name) {
		if (!containsKey(DO_NAME) && name != null) {
			super.put(DO_NAME, name);
		}
	}

	// Service method used by setList() and setDataObject(). Performs validations
	// on List content.

	private void setObject_inner(String key, Object value) throws DataObjectException {
		if ( value == null ) {
			super.remove(key);
			return;
		}
		super.put(key, value);
	}
	
	
	protected void setObject(String key, Object value) throws DataObjectException {
		if (key.equals(DO_NAME)) {
			setName(value.toString());
			return;
		}
		if ( value instanceof String ) {
			setString(key, (String)value);
			return;
		}
		if ( value instanceof DataObject ) {
			setDataObject(key, (DataObject)value);
			return;
		}
		if ( value instanceof Integer ) {
			setInt(key, (Integer)value);
			return;
		}
		if ( value instanceof Long ) {
			setLong(key, (Long)value);
			return;
		}
		if ( value instanceof Float ) {
			setFloat(key, (Float)value);
			return;
		}
		if ( value instanceof Double ) {
			setDouble(key, (Double)value);
			return;
		}
		if ( value instanceof Short ) {
			setShort(key, (Short)value);
			return;
		}
		if ( value instanceof Boolean ) {
			setBoolean(key, (Boolean)value);
			return;
		}
		if ( value instanceof Character ) {
			setChar(key, (Character)value);
			return;
		}
		if ( value instanceof Byte ) {
			setByte(key, (Byte)value);
			return;
		}
		if ( value instanceof Date ) {
			setDate(key, (Date)value);
			return;
		}
		if (value instanceof List) {
			@SuppressWarnings("rawtypes")
			List list = (List) value;
			for (int i = 0, size = list.size(); i < size; i++) {
				Object obj = list.get(i);
				if (!(obj instanceof DataObject) && !(obj instanceof String) && !(obj instanceof Integer)) {
					throw new DataObjectException(obj.getClass().getName()
							+ " cannot be used with setObject()");
				}
			}
			setObject_inner(key, value);
		} else {
			throw new DataObjectException(value.getClass().getName() + " cannot be used with setObject()");
		}
	}

	/**
	 * Sets a String value in a DataObject.
	 */

	
	public void setString(String key, String value, boolean andReverseMap) {
		if ( value != null ) {
			if (key.equals(DO_NAME)) {
				setName(value);
				return;
			}
			trackMetaData(key, "string");
			super.put(key, value);
			if ( andReverseMap ) {
				super.put(value, key);				
			}
		} else {
			super.remove(key);
		}
	}

	public void setString(String key, String value) {
		setString(key, value, false);
	}

	// Only sets the string value if it exists
	public void setOptionalString(String key, String value) {
		if ((value != null) && (value.trim().length() > 0)) {
			setString(key, value);
		}
	}

	public DataObject append(String key, Object value) {
		try {
			put(key, value);
		} catch( Exception e ) {
			
		}
		return this;
	}
	
	public Object put(String key, Object value) {
		if ( value == null ) {
			super.remove(key);
			return get(key);
		}
		Object retVal = null;
		try {
			retVal = get(key);
			setObject((String) key, value);
		} catch (DataObjectException e) {
			throw new RuntimeException("DataObjectException occurred", e);
		}

		return retVal;
	}
	
	//handy way to create a DO from a map
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void setFromMap(Map m) {
		if (this != m) {
			clear();
			putAll(m);
		}
	}
	/**
	* essentially a deep clone of the dataobject
	* throws DataObjectException problems occur
	* copy is created by serializing to the string encoding and back 
	*/
	public DataObject createCopy() throws DataObjectException {
		DataObject copy = new DataObject();
		String thisString = toStringEncoded();
		copy.fromStringEncoded(thisString);
		if ( isStampNeeded() ) {
			copy.setLabelStampObjects(getLabelStamp());
		}
		return copy;
	}

	public DataObject createShallowCopy() throws DataObjectException {
		DataObject copy = new DataObject();
		for ( String key : keySet() ) {
			copy.put(key, get(key));
		}
		return copy;
	}

	public DataObject createShallowCopy(DataObject copy) throws DataObjectException {
		copy.clear();
		for ( String key : keySet() ) {
			copy.put(key, get(key));
		}
		return copy;
	}

	public class StringEncodedDataObjectList  {
		public List<String> encodedDataObjects = null;
		
		public StringEncodedDataObjectList(List<String> encodedDataObjects) {
			this.encodedDataObjects = encodedDataObjects;
		}
		
		public String toString() {
			return encodedDataObjects.toString();
		}
	}
	
	public void setStringEncodedDataObjectList(String key, List<String> stringEncodedDataObjects) throws DataObjectException {
		try {
			trackMetaData(key, "doList");
			setObject(key, new StringEncodedDataObjectList(stringEncodedDataObjects));
		} catch (Exception e) {
			throw new DataObjectException(e);
		}		
	}
	
	public boolean equals( Object o ) {
		boolean isEqual = false;
		if ( o instanceof DataObject ) {
			DataObject that = (DataObject)o;
			try {
				if ( this.toStringEncoded().equals(that.toStringEncoded())) {
					isEqual = true;
				}
			} catch( DataObjectException e ) {
				throw new RuntimeException("DataObjectException occurred", e);
			}
		}
		return isEqual;
	}
	
	   //sequences are useful for our disruptor service, you can ignore
	public long getSequence() {
		return sequence;
	}

	public void setSequence(long sequence) {
		this.sequence = sequence;
	}

	public void autoGenerateUniqueId(boolean autoGenerateUniqueIdOnSerialization) {
		this.autoGenerateUniqueIdOnSerialization = autoGenerateUniqueIdOnSerialization;
	}

	public void setTrackDirtyFields( boolean trackDirty ) {
		this.trackDirtyFields = trackDirty;
		if ( trackDirtyFields == true ) {
			dirtyFields = new ArrayList<String>();
		}
	}
	
	public void setLabelStampObjects( DataObject stamp ) {
		this.labelStampObjects = true;
		this.labelStamp = stamp;
	}
	
 	public void setGenerateMetaData( boolean generateMetaData ) {
		this.generateMetaData = generateMetaData;
		if ( this.generateMetaData ) {
			if ( this.getDataObject("MetaData") == null ) {
				this.setDataObject("MetaData", new DataObject() );
			} else {
				this.getDataObject("MetaData").clear();
			}
		} else {
			this.setDataObject("MetaData", null);
		}
	}
	
	public String getUniqueId() {
		return (String) super.get(DO_UNIQUEID);
	}

	public void setUniqueId(String uniqueID) {
		setString(DO_UNIQUEID, uniqueID);
	}

	public void clear() {
		sequence = -1L;
		autoGenerateUniqueIdOnSerialization = false;
		transientCache = null;
		super.clear();
	}
	
	public Object getUsingMetaData(String name) {
		DataObject meta = getDataObject("MetaData");
		if ( meta != null ) {
			String type = meta.getString(name);
			if ( type.equals("string")) {
				return getString(name);
			} else if ( type.equals("int")) {
				return getInt(name);
			} else if ( type.equals("long")) {
				return getLong(name);
			} else if ( type.equals("short")) {
				return getShort(name);
			} else if ( type.equals("double")) {
				return getDouble(name);
			} else if ( type.equals("float")) {
				return getFloat(name);
			} else if ( type.equals("do")) {
				return getDataObject(name);
			} else if ( type.equals("doList")) {
				return getDataObjectList(name);
			} else if ( type.equals("iList")) {
				return getIntegerList(name);
			} else if ( type.equals("sList")) {
				return getStringList(name);
			} else if ( type.equals("boolean")) {
				return getBoolean(name);
			} else if ( type.equals("byte")) {
				return getByte(name);
			} else if ( type.equals("binary")) {
				return getString(name);
			} else if ( type.equals("date")) {
				return getDate(name);
			} else if ( type.equals("char")) {
				return getChar(name);
			}
		}
		return get(name);
	}

	public void merge( DataObject mergeObj, boolean mergeMeta, String prefix) {
		if ( mergeMeta ) {
			DataObject meta = getDataObject("MetaData");
			DataObject mergeObjMeta = mergeObj.getDataObject("MetaData");
			if ( meta != null ) {
				meta.merge(mergeObjMeta, false, prefix);
			}
		}
		if ( mergeObj != null) {
			for ( String key : mergeObj.keySet() ) {
				if ( !containsKey(prefix+key)) {
					put(prefix+key, mergeObj.get(key));
				}
			}
		}
	}

	public void merge( DataObject mergeObj, boolean mergeMeta) {
		merge(mergeObj, mergeMeta, "");
	}

	protected void trackMetaData(String key, String type) throws DataObjectException{
		if ( immutable ) {
			throw new DataObjectException("Immutable Cannot be set");
		}
		DataObject meta = getDataObject("MetaData");
		if ( meta != null ) {
			meta.setString(key, type);
		}
		if ( trackDirtyFields == true ) {
			dirtyFields.add(key);
		}
	}

	public List<String> getDirtyFields() {
		if ( trackDirtyFields ) {
			return dirtyFields;
		} else {
			return Collections.emptyList();
		}
	}
	
	public String generateMD5Digest() {
		String currentRep = DataObjectJSONCodec.toJSONString(this);
		String digest = DigestUtils.md5Hex(currentRep);
		setUniqueId(digest);
		return digest;
	}

	public void setStore_as_strings(boolean store_as_strings) {
		this.store_as_strings = store_as_strings;
	}

	public boolean isStore_as_strings() {
		return store_as_strings;
	}

	public DataObject getLabelStamp() {
		return labelStamp;
	}
}
