package com.textserv.framework.codecs;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import net.minidev.json.JSONAwareEx;
import net.minidev.json.JSONStyle;
import net.minidev.json.JSONValue;
import net.minidev.json.mapper.AMapper;
import net.minidev.json.parser.JSONParser;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.JavaTypeMapper;

import com.textserv.framework.DataObject;
import com.textserv.framework.DataObjectException;

	
public class DataObjectJSONCodec {

	public static DataObjectJSONCodec instance = new DataObjectJSONCodec();

	public class DataObjectTypeMapper extends JavaTypeMapper {
		private DataObject firstObject = null;

		public DataObjectTypeMapper() {

		}
		public DataObjectTypeMapper(DataObject firstObject) {
			this.firstObject = firstObject;
		}

		public DataObject read(JsonParser jp) throws JsonParseException, IOException {
			return (DataObject)super.read(jp);
		}

		public Map<String, Object> getNewMapImpl() {
			Map<String, Object> returnValue = null;
			if ( firstObject != null ) {
				returnValue = firstObject;
				firstObject = null;
			} else {
				returnValue = new DataObject();
			}				
			return returnValue;
		}

	}
	

	public static void saveAsJSONString (DataObject dO, Writer out) throws Exception {		
		//        JsonGenerator gen = new JsonFactory().createJsonGenerator(out);
		//        new JavaTypeMapper().writeAny(gen, dO);
		//        gen.close();
		String jsonString = toJSONString(dO);
		out.write(jsonString);
	}

	public static String toJSONString(Object obj) throws DataObjectException {
		StringWriter sw = new StringWriter();     
		try {
			JsonGenerator gen = new JsonFactory().createJsonGenerator(sw);
			new JavaTypeMapper().writeAny(gen, obj);
			gen.close();
		} catch ( Exception e ) {
			throw new DataObjectException(e);
		}
		return sw.toString();
	}

	public static String toJSONString(DataObject dO ) throws DataObjectException {
		return toJSONString(dO, false);
	}

	public static String toJSONString(DataObject dO, boolean pretty) throws DataObjectException {
		if ( !pretty ) {
			return toSmartJson(dO);
		} else {
			StringWriter sw = new StringWriter();     
			try {
				JsonGenerator gen = new JsonFactory().createJsonGenerator(sw);
				if ( pretty ) {
					gen.useDefaultPrettyPrinter();
				}
				new JavaTypeMapper().writeAny(gen, dO);
				gen.close();
			} catch ( Exception e ) {
				throw new DataObjectException(e);
			}
			return sw.toString();
		}
	}

	protected static String toSmartJson( DataObject dO) throws DataObjectException {
		StringBuilder sb = new StringBuilder();
		try {
			writeJSON(dO, sb, JSONStyle.NO_COMPRESS);
		} catch (IOException e) {
			new DataObjectException(e);
		}
		return sb.toString();		
	}

	protected static void writeJSONKV(String key, Object value, Appendable out, JSONStyle compression) throws IOException {
		if (key == null)
			out.append("null");
		else if (!compression.mustProtectKey(key))
			out.append(key);
		else {
			out.append('"');
			JSONValue.escape(key, out, compression);
			out.append('"');
		}
		out.append(':');
		if (value instanceof String) {
			if (!compression.mustProtectValue((String) value))
				out.append((String) value);
			else {
				out.append('"');
				JSONValue.escape((String) value, out, compression);
				out.append('"');
			}
		} else
			JSONValue.writeJSONString(value, out, compression);
	}
	
	protected static void writeJSON(DataObject dO, Appendable out, JSONStyle compression) throws IOException {
		if (dO == null) {
			out.append("null");
			return;
		}
		// JSONStyler styler = compression.getStyler();

		boolean first = true;
		// if (styler != null) {
		// styler.objectIn();
		// }

		out.append('{');
		/**
		 * do not use <String, Object> to handle non String key maps
		 */
		for (Map.Entry<?, ?> entry : dO.entrySet()) {
			if (first)
				first = false;
			else
				out.append(',');
			// if (styler != null)
			// out.append(styler.getNewLine());
			writeJSONKV(entry.getKey().toString(), entry.getValue(), out, compression);
		}
		// if (styler != null) {
		// styler.objectOut();
		// }
		out.append('}');
		// if (styler != null) {
		// out.append(styler.getNewLine());
		// }
	}
	
	public static DataObject createFromJSONString (Reader reader) throws Exception {
//		JsonParser jp = new JsonFactory().createJsonParser(reader);
//		DataObject result = instance.new DataObjectTypeMapper().read(jp);
//		jp.close();
//		return result;
		return new JSONParser().parse(reader, DataObjectMapper.DEFAULT);
	}

	public static DataObject fromJSONString(String readString, DataObject dO) throws DataObjectException  {
//		DataObject result = null;
//		try {
//			JsonParser jp = new JsonFactory().createJsonParser(new StringReader(readString));
//			result = instance.new DataObjectTypeMapper(dO).read(jp);
//			jp.close();
//		} catch( Exception e ) {
//			throw new DataObjectException(e);
//		}
		DataObject result = null;
		try {
			result = new JSONParser().parse(readString, new DataObjectMapper(dO));
		} catch( Exception e ) {
			throw new DataObjectException(e);
		}		
		return result;
	}

	public static DataObject fromJSONString(String readString) throws Exception {
//		JsonParser jp = new JsonFactory().createJsonParser(new StringReader(readString));
//		DataObject result = instance.new DataObjectTypeMapper().read(jp);
//		jp.close();
//		return result;
		return new JSONParser().parse(readString, DataObjectMapper.DEFAULT);
	}
}