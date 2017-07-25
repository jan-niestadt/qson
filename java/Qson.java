/*

Copyright 2017 Jan Niestadt.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * QSON encoder/decoder.
 * 
 * QSON stands for Query String Object Notation. It is an attempt to create a simple but powerful serialization 
 * for JSON-like datastructures that results in shorter, more readable URLs. It provides full support for 
 * any data structure that can be represented in JSON.
 */
public class Qson {
	
	private static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();
	
	private static String encoding = DEFAULT_ENCODING;
	
	// What name to use for the query parameter if we call toQueryString with
    // a non-object value and no other name was specified.
	static final String DEFAULT_PARAM_NAME = "_";

    static final char QS_ENTRY_SEP   = '&';  // regular query string entry separator
    static final char QS_KEY_VAL_SEP = '=';  // regular query string key/value separator
    static final char START_COMPOUND = '(';  // start a QSON compound value (object or array)
    static final char END_COMPOUND   = ')';  // end of QSON compound value (object or array)
    static final char KEY_VAL_SEP    = '~';  // QSON key/value separator
    static final char ENTRY_SEP      = '\'';  // QSON entry separator
    static final char FORCE_STRING   = '_';  // force value to be parsed as a string
    static final char ESCAPE         = '!';  // escape character, similar to \ in many languages

    // When parsing, only KEY_VAL_SEP, ENTRY_SEP and END_COMPOUND will end a 
    // key or value.
    static final String KEY_VAL_ENDING_CHARS = Character.toString(KEY_VAL_SEP) + 
    		Character.toString(ENTRY_SEP) + Character.toString(END_COMPOUND);

    // Escape START_COMPOUND and FORCE_STRING only if they're the first character,
    // because that's the only case where they can interfere with parsing.
    // (START_COMPOUND starts a list and FORCE_STRING indicates the value is explicitly a string)
    // KEY_VAL_ENDING_CHARS and ESCAPE must always be escaped in keys and values.
    static final Pattern KEY_VAL_ESCAPE_REGEX = Pattern.compile("(^[" + START_COMPOUND + FORCE_STRING + "]|[" + KEY_VAL_ENDING_CHARS + ESCAPE + "])");

    // Recognize 4 hexadecimal digits for Unicode escape sequences like !u00E9
    static final Pattern UNICODE_HEX_REGEX = Pattern.compile("^[0-9A-Fa-f]{4}$");

    // What are safe names for regular query parameters?
    static final Pattern QUERY_PARAMETER_NAME_REGEX = Pattern.compile("^\\w+$");
    
    private static class QsonParser {
    	
    	private int pos = 0;
    	
    	private String input;
    	
    	public QsonParser(String input) {
    		this.input = input;
    	}
    	
        private RuntimeException errorMsg(String msg) {
            return new IllegalArgumentException(msg + " at " + pos);
        }

        // Does the current character match this char?
        private boolean accept(char c) {
            if (pos >= input.length() || input.charAt(pos) != c)
                return false;
            pos++;
            return true;
        }

        // If the current character does not match this char, throw an error
        private void expect(char c) {
            if (!accept(c)) {
            	if (pos >= input.length())
            		throw errorMsg("Expected " + c + ", found end of input");
				throw errorMsg("Expected " + c + ", found " + input.charAt(pos));
            }
        }

        // a string value to use as a key
        private String key() {
            StringBuilder str = new StringBuilder();
            while (pos < input.length() && Qson.KEY_VAL_ENDING_CHARS.indexOf(input.charAt(pos)) < 0) {
                if (input.charAt(pos) == Qson.ESCAPE) {
                    if (pos == input.length() - 1)
                        throw errorMsg("Input ends with escape character (" + Qson.ESCAPE + ")");
                    // Escape char, copy next char verbatim
                    pos++;
                }
                str.append(input.charAt(pos));
                pos++;
            }
            return str.toString();
        }
        
        private static class KeyOrValue {
        	
        	public Object value;
        	
        	public String from;

    		public KeyOrValue(Object value) {
    			this(value, null);
    		}
        	
    		public KeyOrValue(Object value, String from) {
    			this.value = value;
    			this.from = from;
    		}
        	
        }

        // string, number, boolean or null
        private KeyOrValue simpleValue() {
            StringBuilder str = new StringBuilder();
            boolean explicitString = false;
            if (accept(Qson.FORCE_STRING)) {
            	explicitString = true;
            }
            while (pos < input.length() && Qson.KEY_VAL_ENDING_CHARS.indexOf(input.charAt(pos)) < 0) {
//                if (input.charAt(pos) == Qson.ESCAPE) {
//                    if (pos == input.length() - 1)
//                        throw errorMsg("Input ends with escape character (" + Qson.ESCAPE + ")");
//                    // Escape char, copy next char verbatim
//                    pos++;
//                }
//                str.append(input.charAt(pos));
//                pos++;
                if (input.charAt(pos) == ESCAPE) {
                    if (pos == input.length() - 1)
                        throw errorMsg("Input ends with escape character (" + ESCAPE + ")");
                    // Escape char, copy next char verbatim
                    pos++;
                    switch(input.charAt(pos)) {
                    case START_COMPOUND:  case END_COMPOUND:  case KEY_VAL_SEP: 
                    case ENTRY_SEP:       case FORCE_STRING:  case ESCAPE:
                        str.append(input.charAt(pos)); break;
                    case 't':  str.append('\t'); break;
                    case 'n':  str.append('\n'); break;
                    case 'r':  str.append('\r'); break;
                    case 'f':  str.append('\f'); break;
                    case 'b':  str.append('\b'); break;
                    case 'u':
                        // 4-digit hex Unicode codepoint follows
                        if (pos + 4 >= input.length())
                            throw errorMsg("Malformed unicode escape sequence: " + input);
                        String hexStr = input.substring(pos + 1, pos + 5);
                        if (!UNICODE_HEX_REGEX.matcher(hexStr).matches())
                            throw errorMsg("Malformed unicode escape sequence: " + input);
                        int codePoint = Integer.valueOf(hexStr, 16);
                        str.appendCodePoint(codePoint);
                        pos += 4;
                        break;
                    default:
                        throw errorMsg("Illegal escape sequence !" + input.charAt(pos));
                    }
                } else {
                    str.append(input.charAt(pos));
                }
                pos++;
            	
            }
            String result = str.toString();
            if (explicitString) {
                // Either a key, which must always be a string, or a value starting with Qson.FORCE_STRING (_), meaning:
                // "explicitly interpret this as a string, even though it might look like a number, boolean, or null"
                return new KeyOrValue(result, Qson.FORCE_STRING + result);
            }
            if (result.equals("null"))
                return new KeyOrValue(null, result);
            if (result.equals("true"))
                return new KeyOrValue(Boolean.TRUE, result);
            if (result.equals("false"))
                return new KeyOrValue(Boolean.FALSE, result);
            if (Qson.isNumberString(result))
                return new KeyOrValue(Double.parseDouble(result), result);
            return new KeyOrValue(result);
        }

        private Object object(String firstKey) {
            Map<String, Object> obj = new LinkedHashMap<>();
            if (firstKey.isEmpty() && accept(Qson.KEY_VAL_SEP)) {
                // Empty object.
                return obj;
            }
            obj.put(firstKey, value());
            while (accept(Qson.ENTRY_SEP)) {
                String k = key();
                expect(Qson.KEY_VAL_SEP);
                Object v = value();
                obj.put(k, v);
            }
            return obj;
        }

        private Object array(Object firstValue) {
            List<Object> arr = new ArrayList<>();
            arr.add(firstValue);
            while (accept(Qson.ENTRY_SEP))
                arr.add(value());
            return arr;
        }

        private Object arrayOrObject() {
            KeyOrValue kv = keyOrValue();
            if (accept(Qson.KEY_VAL_SEP)) {
                // It's an object.

                // We read a value or key, and it turned out to be a key.
                // Make sure we use the original string as key, not the 
                // interpreted value (which may be bool, null, number, etc.)
                String key = kv.from != null ? kv.from : kv.value.toString();

                // Pass in the first key.
                return object(key);
            }
    		// It's an array. Pass in the first value.
    		return array(kv.value);
        }

        private Object value() {
            return keyOrValue().value;
        }

        private KeyOrValue keyOrValue() {
            KeyOrValue result;
            if (accept(Qson.START_COMPOUND)) {
                if (accept(Qson.END_COMPOUND)) {
                    // Empty list
                    result = new KeyOrValue(new ArrayList<>());
                } else {
                    // Array or object
                    result = new KeyOrValue(arrayOrObject());
                    expect(Qson.END_COMPOUND);
                }
            } else {
                result = simpleValue();
            }
            return result;
        }

        public Object parse() {
        	Object result = value();
        	if (pos < input.length() && input.charAt(pos) != ENTRY_SEP) // ENTRY_SEP doubles as "end of value"
        		throw errorMsg("Premature end of value found");
        	return result;
        }
    	
    }

    /**
     * Provides Java equivalents to encodeURIComponent and decodeURIcomponent in Javascript.
     * 
     * This is necessary because URLEncoder/Decoder work similarly but escape "subdelimiter" 
     * characters that don't need to be escaped according to RFC 3986.
     */
    private static class EncodingUtil {
    	
    	private static String[] replace = {"+",   "%21", "%27", "%28", "%29", "%7E"};
    	
    	private static String[] with =    {"%20", "!",   "'",   "(",   ")",   "~"};
    	
    	public static String decodeURIComponent(String s) {
    		try {
        		if (s == null)
        			return null;
    			return URLDecoder.decode(s, encoding);
    		} catch (UnsupportedEncodingException e) {
    			throw new RuntimeException(e);
    		}
    	}

    	public static String encodeURIComponent(String s) {
    		try {
    			String encoded = URLEncoder.encode(s, encoding);
    			for (int i = 0; i < replace.length; i++) {
    				encoded = encoded.replace(replace[i], with[i]);
    			}
    			return encoded;
    		} catch (UnsupportedEncodingException e) {
    			throw new RuntimeException(e);
    		}
    	}

    	/**
    	 * Private constructor to prevent this class from being instantiated.
    	 */
    	private EncodingUtil() {
    		super();
    	}
    }
    
    /**
     * Get the encoding that will be used to URL-encode characters.
     * @return encoding that will be used
     */
    public static String getEncoding() {
		return encoding;
	}

    /**
     * Set the encoding that will be used to URL-encode characters.
     * @param encoding encoding to use
     */
	public static void setEncoding(String encoding) {
		Qson.encoding = encoding;
	}

    // Check if the input appears to be a number.
    static boolean isNumberString(String input) {
        return input != null && input.matches("[\\-]?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");
    }

    // Escape characters with special meaning in QSON with a !
    private static String escapeSpecialChars(String str) {
    	return KEY_VAL_ESCAPE_REGEX.matcher(str).replaceAll(Character.toString(ESCAPE) + "$1");
    }
    
    private static String join(List<String> parts, char sep) {
    	StringBuilder b = new StringBuilder();
    	for (String part: parts) {
    		if (b.length() > 0)
    			b.append(sep);
    		b.append(part);
    	}
    	return b.toString();
    }

    /**
     * Serialize the value to a QSON string. 
     * @param value value to serialize
     * @return QSON representation
     */
    public static String stringify(Object value) {

        StringBuilder output = new StringBuilder();
        if (value instanceof List) {
            // Array. Join entries with ENTRY_SEP.
			List<?> array = (List<?>)value;
            List<String> parts = new ArrayList<>();
            for (Object entry: array) {
                parts.add(stringify(entry));
            }
            output.append(START_COMPOUND).append(join(parts, ENTRY_SEP)).append(END_COMPOUND);
        } else if (value instanceof Map) {
            // Object. Join key/value with ~ and entries with ENTRY_SEP.
			Map<?, ?> object = (Map<?, ?>)value;
        	List<String> parts = new ArrayList<>();
            for (Entry<?, ?> entry: object.entrySet()) {
            	Object key = entry.getKey();
            	if (!(key instanceof String))
            		throw new UnsupportedOperationException("Only string keys are supported in maps");
            	Object entryVal = entry.getValue();
                parts.add(escapeSpecialChars(key.toString()) + KEY_VAL_SEP + stringify(entryVal));
            }
            if (parts.size() > 0) {
                output.append(START_COMPOUND).append(join(parts, ENTRY_SEP)).append(END_COMPOUND);
            } else {
                // Empty object has a special notation (to distinguish from empty array)
                output.append(START_COMPOUND).append(KEY_VAL_SEP).append(KEY_VAL_SEP).append(END_COMPOUND);
            }
        } else if (value == null) {
            output.append("null");
        } else if (value instanceof Boolean) {
            output.append((Boolean)value ? "true" : "false");
        } else if (value instanceof Integer || value instanceof Float || value instanceof Double) {
            output.append(stringifyNumber(value));
        } else if (value instanceof String) {
        	String str = (String)value;
            if (str.equals("null") || str.equals("true") || str.equals("false") || isNumberString(str)) {
                return FORCE_STRING + str; // FORCE_STRING (_) means: parse this as a string even if it looks like a number, etc.
            }

            // String value. Escape QSON special characters.
            output.append(escapeSpecialChars(str));
        }
        return output.toString();
    }

    private static Object stringifyNumber(Object value) {
    	if (value instanceof Integer)
    		return value.toString();
		return value.toString().toLowerCase().replaceAll("\\.0(e|$)", "$1"); // 1.0E-2 -> 1e-2
	}

	/**
	 * Deserialize the input string to the original value.
	 * @param input QSON string to deserialize
	 * @return corresponding value
	 */
    public static Object parse(String input) {
    	QsonParser parser = new QsonParser(input);
    	return parser.parse();
    }

    /** Convert the value to a query parameter object suitable for passing to e.g. jQuery.
     * 
     * @param value value to serialize
     * @return Map of QSON-encoded parameters 
     */
    public static Map<String, String> toParamObject(Object value) {
    	return toParamObject(value, null);
    }
    
    /** Convert the value to a query parameter object suitable for passing to e.g. jQuery.
     * 
     * @param value value to serialize
     * @param defaultParamName parameter name used if the value is not an object
     * @return Map of QSON-encoded parameters 
     */
    public static Map<String, String> toParamObject(Object value, String defaultParamName) {
        Map<String, String> obj = new LinkedHashMap<>();
        if (defaultParamName == null)
            defaultParamName = DEFAULT_PARAM_NAME;
        if (value instanceof Map) {
            // Top-level object. Encode as regular query string.
			Map<?, ?> object = (Map<?, ?>)value;
            for (Entry<?, ?> entry: object.entrySet()) {
            	Object key = entry.getKey();
            	if (!(key instanceof String))
            		throw new UnsupportedOperationException("Only string keys are supported in maps");
                if (key.equals(defaultParamName) || !QUERY_PARAMETER_NAME_REGEX.matcher((String)key).matches()) {
                    // We found a key we can't use as a regular query parameter name
                    // (either not a valid name, or equal to default param name)
                    // Just use the default param name and stringify the whole value.
                    obj.clear();
                    obj.put(defaultParamName, Qson.stringify(value));
                    return obj;
                }
                obj.put((String)key, stringify(entry.getValue()));
            }
        } else {
            // Top-level is not an object; return regular encoding.
            obj.put(defaultParamName, stringify(value));
        }
        return obj;
    }

    /** Convert the value from a query parameter object back to the original value.
     * @param obj parameter object to decode
     * @return decoded original value
     */
    public static Object fromParamObject(Map<String, String> obj) {
    	return fromParamObject(obj, null);
    }
    
    /** Convert the value from a query parameter object back to the original value.
     * @param obj parameter object to decode
     * @param defaultParamName parameter name that was used if the value was not an object
     * @return decoded original value
     */
    public static Object fromParamObject(Map<String, String> obj, String defaultParamName) {
    	Map<String, Object> result = new LinkedHashMap<>();
        if (defaultParamName == null)
            defaultParamName = DEFAULT_PARAM_NAME;
        for (Entry<String, String> entry: obj.entrySet()) {
        	String key = entry.getKey();
            Object value = parse(entry.getValue());
            result.put(key, value);
        }
        if (result.size() == 1 && result.containsKey(defaultParamName))
        	return result.get(defaultParamName);
        return result;
    }

    /** Convert the value to a query string that can be appended to a URL directly.
     * NOTE: the resulting query string does not start with a "?".
     * @param value value to convert to a query string
     * @return QSON-encoded query string
     */
    public static String toQueryString(Object value) {
    	return toQueryString(value, null);
    }
    
    /** Convert the value to a query string that can be appended to a URL directly.
     * NOTE: the resulting query string does not start with a "?".
     * @param value value to convert to a query string
     * @param defaultParamName parameter name used if the value is not an object
     * @return QSON-encoded query string
     */
    public static String toQueryString(Object value, String defaultParamName) {
        Map<String, String> param = toParamObject(value, defaultParamName);
        List<String> parts = new ArrayList<>();
        for (Entry<String, String> entry: param.entrySet()) {
            parts.add(EncodingUtil.encodeURIComponent(entry.getKey()) + QS_KEY_VAL_SEP + EncodingUtil.encodeURIComponent(entry.getValue()));
        }
        return join(parts, QS_ENTRY_SEP);
    }

    /** Convert a query string back to the original value.
     * NOTE: the query string should not start with a "?".
     * @param input query string to decode
     * @return decoded original value
     */
    public static Object fromQueryString(String input) {
    	return fromQueryString(input, null);
    }
    
    /** Convert a query string back to the original value.
     * NOTE: the query string should not start with a "?".
     * @param input query string to decode
     * @param defaultParamName parameter name used if the value is not an object
     * @return decoded original value
     */
    public static Object fromQueryString(String input, String defaultParamName) {
        if (input.isEmpty()) {
            // Empty object
            return new LinkedHashMap<>();
        }
        String[] entries = input.split("&");
        Map<String, String> paramObj = new LinkedHashMap<>();
        for (String entry: entries) {
            String[] keyValue = entry.split("=", -1);
            if (keyValue.length != 2)
            	throw new UnsupportedOperationException("Malformed parameter in query string: " + input);
            String key = EncodingUtil.decodeURIComponent(keyValue[0]);
            if (key.length() == 0)
            	throw new UnsupportedOperationException("Malformed parameter in query string: " + input);
            String value = EncodingUtil.decodeURIComponent(keyValue[1]);
            paramObj.put(key, value);
        }
        return fromParamObject(paramObj, defaultParamName);
    }

}
