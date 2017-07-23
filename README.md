# QSON: Query String Object Notation

## Introduction

There exist several ways of encoding more complex [JSON](http://json.org/)-like datastructures into URL query strings (e.g. [jQuery.param](http://api.jquery.com/jquery.param/), [jsonuri-js](https://github.com/guidj/jsonuri-js)). Unfortunately, there's no universally accepted standard and existing approaches seem to have drawbacks in terms of compactness and readability (because of how arrays and objects are represented and URL encoding).

QSON is an attempt to create a simple but powerful serialization for JSON-like datastructures that results in shorter, more readable URLs. It provides full support for any data structure that can be represented in JSON. Javascript and Java versions are included, with unit tests for both.

You can test QSON out yourself using the provided [test page](https://htmlpreview.github.io/?https://github.com/jan-niestadt/qson/blob/master/js/test.html) (just input JSON data in the left textarea).

## Usage

To generate a query string that can be concatenated to a URL (after the "?"), simply call `toQueryString(value)`. To convert a query string back to the original datastructure, call `fromQueryString(string)`.

To generate a parameter object (e.g. for use with `jQuery.ajax()`), call `toParamObject(value)`. To convert a parameter object back to the original datastructure, call `fromParamObject(object)`.

To skip the query string functionality and simply encode the entire value in the QSON syntax, simply call `stringify(value)`. To convert a plain QSON string back to the original value, call `parse(string)`.

## Examples
These examples all use the `toQueryString()` function. See the [unit tests](https://github.com/jan-niestadt/qson/blob/master/js/test.js#L180) for more examples.

### Simple object
A simple value like this
```json
{
    "id": 3,
    "search": "test",
    "highlight": true
}
```
will be converted by `toQueryString()` to

    id=3&search=test&highlight=true

### Object containing arrays and objects
The previous example is no different from normal URLs. QSON becomes useful if you have nested datastructures like
```json
{
    "favcol": ["blue", "green"],
    "pets": {
        "cats": 3,
        "dogs": 2
    }
}
```
This will be converted into the query string

    favcol=(blue'green)&pets=(cats~3'dogs~2)

### Complex object
A more complex object like
```json
{
    "a": [1, 2, 3],
    "b": {
        "d": {},
        "f": ["g", "h"]
    },
    "c": "true",
    "d": 4e-20,
    "e": ":-)"
}
```
becomes the query string

    a=(1'2'3)&b=(d~(~~)'f~(g'h))&c=_true&d=4e-20&e=%3A-!)

Notes:
- Array elements and objects entries are `'`-separated; object keys and values are separated using `~`.
- An empty object has a special notation: `(~~)`.
- The string `"true"` is encoded as `_true`, while the boolean value `true` simply remains `true`. The same applies to `false` and `null`. It also applies to numbers, so a string that looks like a number will be decoded into a number unless it is prefixed with `_` to force it to be decoded to a string.
- The escape character is `!`. This is used to escape characters that have special meaning in QSON. Any character following `!` is considered part of the value, so for example `(a!~2)` describes an array with one string element: `["a~2"]`.
- The delimiters `(`, `'`, `~` and `)` the the escape character `!` were all chosen because they don't need to be %-encoded in query strings. This makes QSON query strings more readable.

    
### Non-object value
If you pass a value that is not an object but, for example, an array like
```json
[
    "a",
    {
        "b": "c",
        "d": "e"
    },
    -3.3
]
```
`toQueryString()` will use a default query parameter name. Unless you specify your own, `_` is used:

    _=(a'(b~c'd~e)'-3.3)

Of course, `fromQueryString()` recognizes the special parameter name and correctly returns the original array.

### Objects with unusual keys
If an object has a key that's not a simple identifier, this will automatically trigger the same behaviour as for non-objects. So this object
```json
{
    "a b": 3
}
```
will be encoded as the query string

    _=(a%20b~3)

The same is true for an object containing a key that matches the default query parameter name (`_` unless you specify otherwise).

## Specification
For the EBNF grammar and diagrams, see [the doc/ directory](https://github.com/jan-niestadt/qson/tree/master/doc).

## License

This work is licensed under the [Apache license 2.0](https://www.apache.org/licenses/LICENSE-2.0).
