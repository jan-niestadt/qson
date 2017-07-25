
function init() {

	document.getElementById("decoded").value = '';
	unitTests();

	// If we have a query string, try to decode it as an QSON query string and put the JSON in the input box.
	var search = location.search;
	var usingGitHubPreview = search.substr(0, 19) === "?https://github.com";
	if (search && search.length > 1 && !usingGitHubPreview) {
		search = search.substr(1);
		var result = undefined;
		try {
			result = QSON.fromQueryString(search);
		} catch (e) {
			alert("Couldn't parse: " + search);
			throw e;
		}
		if (result) {
			document.getElementById("input").value = JSON.stringify(result, null, 2);
		}

	}
	test(true);
}

function test(appendToDecoded) {

	// See if we can parse the JSON
	var inputEl = document.getElementById("input");
	try {
		var inputJson = inputEl.value;
		var input = JSON.parse(inputJson);
		var compactedLength = JSON.stringify(input).length;
		var optComp = compactedLength != inputJson.length ? " (" + compactedLength + " without whitespace)" : "";
		document.getElementById("inputMsg").innerHTML = inputJson.length + " chars" + optComp;
		inputEl.style.backgroundColor = "#fff";
	} catch (e) {
		inputEl.style.backgroundColor = "#fdd";
		return;
	}
	
	// Encode to QSON
	var encoded = QSON.stringify(input);
	var altEncoded = QSON.toQueryString(input);
	document.getElementById("encoded").value = "stringify():\n" + encoded + "\n\ntoQueryString():\n" + altEncoded;
	var orig = inputJson.length;
	function calcSavings(orig, newVal) { var v = Math.round(100 * (newVal - orig) / orig); if (v > 0) return "+" + v; else return "" + v; }
	var savings = [ calcSavings(compactedLength, encoded.length), calcSavings(compactedLength, altEncoded.length) ];
	document.getElementById("encodedMsg").innerHTML = encoded.length + " / " + altEncoded.length + " chars (" + savings[0] + " / " + savings[1] + "%)";
	
	// Decode and encode to JSON again, and compare with the original input.

	var decoded = QSON.parse(encoded);
	var decodedJson = JSON.stringify(decoded);
	var output = "parse():\n" + decodedJson + "\n";
	var error = false;
	if (decodedJson !== JSON.stringify(input)) {
		output += "(ERROR)";
		error = true;
	} else {
		output += "(OK)";
	}

	var altDecoded = QSON.fromQueryString(altEncoded);
	var altDecodedJson = JSON.stringify(altDecoded);
	output += "\n\nfromQueryString():\n" + altDecodedJson + "\n";
	if (altDecodedJson !== JSON.stringify(input)) {
		output += "(ERROR)";
		error = true;
	} else {
		output += "(OK)";
	}

	document.getElementById("decodedMsg").innerHTML = decodedJson.length + " chars";

	var decodedEl = document.getElementById("decoded");
	if (!appendToDecoded)
		decodedEl.value = '';
	decodedEl.value += output;
	decodedEl.style.backgroundColor = error ? "#fdd" : "#fff";
}

function unitTests() {

	var anyFailed = false;

	var decodedEl = document.getElementById("decoded");

	function output(msg) {
		decodedEl.value += msg + "\n";
	}

	function test(name, value, expected, expectError) {
		var v = JSON.stringify(value);
		var e = JSON.stringify(expected);
		if (v !== e) {
			output("FAIL " + name + ": Expected " + (expectError ? "exception" : e) + ", got " + v);
			anyFailed = true;
		} else {
			//output("SUCCESS " + name + ": " + v);
		}
	}

	function onError(name, expected, expectError, e) {
		if (expectError) {
			//output("SUCCESS " + name + ": errored as expected");
		} else {
			output("FAIL " + name + ": Expected " + expected + ", got exception");
			anyFailed = true;
			console.log("Exception for test '" + name + "':");
			console.log(e);
		}
	}

	function map() {
		var result = {};
		for (var i = 0; i < arguments.length; i += 2) {
			result[arguments[i]] = arguments[i + 1];
		}
		return result;
	}

	function list() {
		var result = [];
		for (var i = 0; i < arguments.length; i++) {
			result.push(arguments[i]);
		}
		return result;
	}

	function testEncode(name, value, expStringified, expQueryString) {
		try {
			var a = QSON.stringify(value);
			test(name + " stringify", a, expStringified);
		} catch(e) {
			output("FAIL " + name + " stringify " + JSON.stringify(value) + ": Expected " + JSON.stringify(expStringified) + ", threw an exception");
			throw e;
		}

		try {
			var b = QSON.parse(a);
			test(name + " parse", b, value);
		} catch(e) {
			output("FAIL " + name + " parse " + a + ": Expected " + JSON.stringify(value) + ", threw an exception");
			throw e;
		}

		try {
			var c = QSON.toQueryString(value);
			test(name + " toQueryString", c, expQueryString);
		} catch(e) {
			output("FAIL " + name + " toQueryString " + JSON.stringify(value) + ": Expected " + JSON.stringify(expQueryString) + ", threw an exception");
			throw e;
		}

		try {
			var d = QSON.fromQueryString(c);
			test(name + " fromQueryString", d, value);
		} catch(e) {
			output("FAIL " + name + " fromQueryString " + c + ": Expected " + JSON.stringify(value) + ", threw an exception");
			throw e;
		}
	}

	function testDecodeQueryString(name, queryString, expected, expectError) {
		try {
			test(name, QSON.fromQueryString(queryString), expected, expectError);
		} catch(e) {
			onError(name, expected, expectError, e);
		}
	}
	
	function testDecodeQsonValue(name, qsonValue, expected, expectError) {
		try {
			test(name, QSON.parse(qsonValue), expected, expectError);
		} catch(e) {
			onError(name, expected, expectError, e);
		}
	}	

	function allowAnyName(b) {
		QSON.setAllowAnyQueryParameterName(b);
	}

	function performTests() {
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
		
		// Below tests values that are never returned from stringify() or toQueryString();
		// some are valid, some invalid Qson values.
		
		testDecodeQueryString("special parameter name", "a=b&_=c", map("a", "b", "_", "c"), false);
		testDecodeQueryString("query string ending in &", "a=b&", map("a", "b"), false);
		testDecodeQueryString("query string starting with ?", "?a=b", map("a", "b"), false);
		testDecodeQueryString("invalid query string starting with &", "&a=b", null, true);
		testDecodeQueryString("invalid query string &&", "a=b&&c=d", null, true);
		testDecodeQueryString("invalid query string no =", "a", null, true);
		testDecodeQueryString("invalid query string empty key", "=1", null, true);

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

	performTests();

	if (anyFailed) {
		decodedEl.style.backgroundColor = "#fdd";
	} else {
		output("All units tests succeeded.");
	}
	output("");
}

