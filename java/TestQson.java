import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestQson {
	
	boolean anyFailed = false;

	void output(String msg) {
		System.out.println(msg);
	}
	
	void test(String name, Object value, Object expected) {
		if ((value == null && expected == null) || value.equals(expected)) {
			//output("SUCCESS " + name + ": " + value);
		} else {
			output("FAIL " + name + ": Expected " + expected + ", got " + value);
			anyFailed = true;
		}
	}

	void onError(String name, Object expected, boolean expectError, Exception e) {
		if (expectError) {
			//output("SUCCESS " + name + ": errored as expected");
		} else {
			output("FAIL " + name + ": Expected " + expected + ", got exception");
			e.printStackTrace();
			anyFailed = true;
		}
	}

	void testEncode(String name, Object value, String expStringified, String expQueryString) {
		String a, c;
		Object b, d;
		
		try {
			a = Qson.stringify(value);
			test(name + " stringify", a, expStringified);
		} catch(Exception e) {
			output(name + " stringify " + value + ": Expected " + expStringified + ", threw an exception");
			throw e;
		}

		try {
			b = Qson.parse(a);
			test(name + " parse", b, value);
		} catch(Exception e) {
			output(name + " parse " + a + ": Expected " + value + ", threw an exception");
			throw e;
		}

		try {
			c = Qson.toQueryString(value);
			test(name + " toQueryString", c, expQueryString);
		} catch(Exception e) {
			output(name + " toQueryString " + value + ": Expected " + expQueryString + ", threw an exception");
			throw e;
		}

		try {
			d = Qson.fromQueryString(c);
			test(name + " fromQueryString", d, value);
		} catch(Exception e) {
			output(name + " fromQueryString " + c + ": Expected " + value + ", threw an exception");
			throw e;
		}
	}
	
	void testDecodeQueryString(String name, String queryString, Object expected, boolean expectError) {
		try {
			test(name, Qson.fromQueryString(queryString, null, Arrays.asList("ignoreThis")), expected);
		} catch (Exception e) {
			onError(name, expected, expectError, e);
		}
	}
	
	void testDecodeQsonValue(String name, String qsonValue, Object expected, boolean expectError) {
		try {
			test(name, Qson.parse(qsonValue), expected);
		} catch (Exception e) {
			onError(name, expected, expectError, e);
		}
	}
	
	Map<Object, Object> map(Object... values) {
		Map<Object, Object> result = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			result.put(values[i], values[i + 1]);
		}
		return result;
	}
	
	List<Object> list(Object... values) {
		return Arrays.asList(values);
	}

	private static void allowAnyName(boolean b) {
		Qson.setAllowAnyQueryParameterName(b);
	}

	public static void introduceEscapes(boolean b) {
		Qson.setEscapeLowAsciiAndUnicode(b);
	}

	void performTests() {
		// NOTE: this test code is directly portable between Java and JavaScript!

		// Special values
		testEncode("empty string", "", "", "_=");
		testEncode("string", "a", "a", "_=a");
		testEncode("null", null, "null", "_=null");
		testEncode("true", true, "true", "_=true");
		testEncode("false", false, "false", "_=false");
		testEncode("1", 1.0, "1", "_=1");
		testEncode("1.2", 1.2, "1.2", "_=1.2");
		testEncode("1e3", 1e3, "1000", "_=1000");
		testEncode("1e-20", 1e-20, "1e-20", "_=1e-20");
		testEncode("null string", "null", "_null", "_=_null");
		testEncode("1 string", "1", "_1", "_=_1");
		testEncode("1.0 string", "1", "_1", "_=_1");
		
		// URL encoding
		testEncode("URL-encoding 1", "a\nb", "a\nb", "_=a%0Ab");
		testEncode("URL-encoding 2", "a & b", "a & b", "_=a%20%26%20b");
		testEncode("URL-encoding 3", map("a&b", 3.0), "(a&b~3)", "_=(a%26b~3)");
		testEncode("URL-encoding 4", "a + b", "a + b", "_=a%20%2B%20b");

		// Arrays
		testEncode("simple array 1", list(1.0), "(1)", "_=(1)");
		testEncode("simple array 2", list(1.0, 2.0, 3.0), "(1'2'3)", "_=(1'2'3)");
		testEncode("empty array", list(), "()", "_=()");

		// Objects
		testEncode("simple object 1", map("a", 3.0), "(a~3)", "a=3");
		testEncode("simple object 2", map("a", 3.0, "b", "c"), "(a~3'b~c)", "a=3&b=c");
		testEncode("empty key", map("", 3.0), "(~3)", "_=(~3)");
		testEncode("empty key and value", map("", ""), "(~)", "_=(~)");
		testEncode("empty object", map(), "(~~)", "");
		testEncode("whitespace 1", map(" a ", " b "), "( a ~ b )", "_=(%20a%20~%20b%20)");
		testEncode("whitespace 2", map("a", " b "), "(a~ b )", "a=%20b%20");
		testEncode("special char keys", map("(", 1.0, "!", 2.0, "_", 3.0), "(!(~1'!!~2'!_~3)", "_=(!(~1'!!~2'!_~3)");
		testEncode("key starts with _", map("_a", "b"), "(!_a~b)", "_a=b");
		testEncode("key contains _", map("a_b", "c"), "(a_b~c)", "a_b=c");
		testEncode("key containing number", map("a1", "b"), "(a1~b)", "a1=b");
		testEncode("key starts with number", map("1a", "b"), "(1a~b)", "_=(1a~b)");
		testEncode("key containing dash and dot", map("a.b", "c", "a-b", "d"), "(a.b~c'a-b~d)", "a.b=c&a-b=d");
		testEncode("key starting with dot", map(".a", "b"), "(.a~b)", "_=(.a~b)");
		testEncode("keys that look like values 1", map("null", 3.0), "(null~3)", "null=3");
		testEncode("keys that look like values 2", map("1.2", 3.4), "(1.2~3.4)", "_=(1.2~3.4)");
		testEncode("nested structures in object", map("a", list(1.0,2.0), "b", map("c", map("d", "e")), "f", list(list(list(3.0)))), "(a~(1'2)'b~(c~(d~e))'f~(((3))))", "a=(1'2)&b=(c~(d~e))&f=(((3)))");

		allowAnyName(true);
		testEncode("allow any query parameter name", map(" ", 1.0, "|", 2.0, "\u00e9", 3.0), "( ~1'|~2'\u00e9~3)", "%20=1&%7C=2&%C3%A9=3");
		allowAnyName(false);
		
		introduceEscapes(true);
		testEncode("introduce escapes low ascii", "\n\r\f\b\t", "!n!r!f!b!t", "_=!n!r!f!b!t");
		testEncode("introduce escapes unicode", "\u00e9", "!u00e9", "_=!u00e9");
		introduceEscapes(false);
		
		// Below tests values that are never returned from stringify() or toQueryString();
		// some are valid, some invalid Qson values.
		
		testDecodeQueryString("special parameter name", "a=b&_=c", map("a", "b", "_", "c"), false);
		testDecodeQueryString("query string ending in &", "a=b&", map("a", "b"), false);
		testDecodeQueryString("query string starting with ?", "?a=b", map("a", "b"), false);
		testDecodeQueryString("invalid query string starting with &", "&a=b", null, true);
		testDecodeQueryString("invalid query string &&", "a=b&&c=d", null, true);
		testDecodeQueryString("invalid query string no =", "a", null, true);
		testDecodeQueryString("invalid query string empty key", "=1", null, true);
		testDecodeQueryString("ignore parameter", "a=b&ignoreThis=2", map("a", "b"), false);

		testDecodeQsonValue("key starting with underscore 1", "(_1~3)", map("_1", 3.0), false);
		testDecodeQsonValue("key starting with underscore 2", "(a~b'_1~3)", map("a", "b", "_1", 3.0), false);
		testDecodeQsonValue("end of value character", "1'2", 1.0, false);
		testDecodeQsonValue("illegal string 2", "1~2", null, true);
		testDecodeQsonValue("illegal string 3", "1)2", null, true);
		testDecodeQsonValue("illegal string 4", "(1", null, true);
		testDecodeQsonValue("forced empty string", "_", "", false);
		testDecodeQsonValue("malformed object 1", "(a~b')", null, true);
		testDecodeQsonValue("malformed object 2", "('a~b)", null, true);
		testDecodeQsonValue("malformed object 3", "(a~b'c)", null, true);

		// Escapes
		testDecodeQsonValue("character escapes", "!n!r!f!b", "\n\r\f\b", false);
		testDecodeQsonValue("unicode escapes", "!u0041!u00e9!u03A3!u306C", "\u0041\u00e9\u03A3\u306C", false);
		testDecodeQsonValue("illegal escape 1", "Test!", null, true);
		testDecodeQsonValue("illegal escape 2", "!q", null, true);
		testDecodeQsonValue("illegal unicode escape 1", "!u007", null, true);
		testDecodeQsonValue("illegal unicode escape 2", "!uBABY", null, true);
	}
	
	private void unitTests() {
		performTests();
		if (!anyFailed) {
			output("All units tests succeeded!");
		}
	}
	
	public static void main(String[] args) {
		new TestQson().unitTests();
	}

}
