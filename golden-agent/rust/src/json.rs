//! A minimal JSON value, parser and writer, so the crate needs no dependencies.
//!
//! Objects keep insertion order. Numbers without a fraction or exponent become [`Json::Int`],
//! everything else becomes [`Json::Float`].

use std::fmt;

const MAX_DEPTH: usize = 128;

/// A JSON value.
#[derive(Debug, Clone, PartialEq)]
pub enum Json {
    /// `null`
    Null,
    /// `true` or `false`
    Bool(bool),
    /// A whole number
    Int(i64),
    /// A number with a fraction or exponent
    Float(f64),
    /// A string
    String(String),
    /// An array
    Array(Vec<Json>),
    /// An object, in insertion order
    Object(Vec<(String, Json)>),
}

/// A JSON syntax error with the character position where it was found.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JsonError {
    /// What went wrong.
    pub message: String,
    /// Character offset in the input.
    pub position: usize,
}

impl fmt::Display for JsonError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} at position {}", self.message, self.position)
    }
}

impl std::error::Error for JsonError {}

impl Json {
    /// Build an object from key and value pairs.
    pub fn object<K, I>(pairs: I) -> Json
    where
        K: Into<String>,
        I: IntoIterator<Item = (K, Json)>,
    {
        Json::Object(pairs.into_iter().map(|(k, v)| (k.into(), v)).collect())
    }

    /// Build an array from values.
    pub fn array<I: IntoIterator<Item = Json>>(items: I) -> Json {
        Json::Array(items.into_iter().collect())
    }

    /// Look up a key in an object. Returns `None` for other kinds of value.
    pub fn get(&self, key: &str) -> Option<&Json> {
        match self {
            Json::Object(pairs) => pairs.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }

    /// The string, if this is a string.
    pub fn as_str(&self) -> Option<&str> {
        match self {
            Json::String(s) => Some(s),
            _ => None,
        }
    }

    /// The integer, if this is a whole number.
    pub fn as_i64(&self) -> Option<i64> {
        match self {
            Json::Int(i) => Some(*i),
            _ => None,
        }
    }

    /// The number as a float, if this is any number.
    pub fn as_f64(&self) -> Option<f64> {
        match self {
            Json::Int(i) => Some(*i as f64),
            Json::Float(f) => Some(*f),
            _ => None,
        }
    }

    /// The boolean, if this is a boolean.
    pub fn as_bool(&self) -> Option<bool> {
        match self {
            Json::Bool(b) => Some(*b),
            _ => None,
        }
    }

    /// The items, if this is an array.
    pub fn as_array(&self) -> Option<&[Json]> {
        match self {
            Json::Array(items) => Some(items),
            _ => None,
        }
    }

    /// The key and value pairs, if this is an object.
    pub fn as_object(&self) -> Option<&[(String, Json)]> {
        match self {
            Json::Object(pairs) => Some(pairs),
            _ => None,
        }
    }

    /// True for `null`.
    pub fn is_null(&self) -> bool {
        matches!(self, Json::Null)
    }

    /// Parse a complete JSON document.
    pub fn parse(text: &str) -> Result<Json, JsonError> {
        let mut parser = Parser {
            chars: text.chars().collect(),
            pos: 0,
            depth: 0,
        };
        let value = parser.value()?;
        parser.skip_whitespace();
        if parser.pos < parser.chars.len() {
            return Err(parser.error("Unexpected trailing characters"));
        }
        Ok(value)
    }

    /// Write compact JSON. A float that is not finite is written as `null`, because JSON cannot represent it.
    pub fn write(&self) -> String {
        let mut out = String::new();
        write_value(self, &mut out);
        out
    }
}

impl fmt::Display for Json {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.write())
    }
}

impl From<&str> for Json {
    fn from(value: &str) -> Self {
        Json::String(value.to_string())
    }
}

impl From<String> for Json {
    fn from(value: String) -> Self {
        Json::String(value)
    }
}

impl From<bool> for Json {
    fn from(value: bool) -> Self {
        Json::Bool(value)
    }
}

impl From<i64> for Json {
    fn from(value: i64) -> Self {
        Json::Int(value)
    }
}

impl From<i32> for Json {
    fn from(value: i32) -> Self {
        Json::Int(i64::from(value))
    }
}

impl From<u32> for Json {
    fn from(value: u32) -> Self {
        Json::Int(i64::from(value))
    }
}

impl From<f64> for Json {
    fn from(value: f64) -> Self {
        Json::Float(value)
    }
}

fn write_value(value: &Json, out: &mut String) {
    match value {
        Json::Null => out.push_str("null"),
        Json::Bool(b) => out.push_str(if *b { "true" } else { "false" }),
        Json::Int(i) => out.push_str(&i.to_string()),
        Json::Float(f) => {
            if f.is_finite() {
                let text = f.to_string();
                out.push_str(&text);
                if !text.contains(['.', 'e', 'E']) {
                    out.push_str(".0");
                }
            } else {
                out.push_str("null");
            }
        }
        Json::String(s) => write_string(s, out),
        Json::Array(items) => {
            out.push('[');
            for (i, item) in items.iter().enumerate() {
                if i > 0 {
                    out.push(',');
                }
                write_value(item, out);
            }
            out.push(']');
        }
        Json::Object(pairs) => {
            out.push('{');
            for (i, (key, item)) in pairs.iter().enumerate() {
                if i > 0 {
                    out.push(',');
                }
                write_string(key, out);
                out.push(':');
                write_value(item, out);
            }
            out.push('}');
        }
    }
}

fn write_string(text: &str, out: &mut String) {
    out.push('"');
    for c in text.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{08}' => out.push_str("\\b"),
            '\u{0c}' => out.push_str("\\f"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
}

struct Parser {
    chars: Vec<char>,
    pos: usize,
    depth: usize,
}

impl Parser {
    fn error(&self, message: &str) -> JsonError {
        JsonError {
            message: message.to_string(),
            position: self.pos,
        }
    }

    fn skip_whitespace(&mut self) {
        while self.pos < self.chars.len() && self.chars[self.pos].is_whitespace() {
            self.pos += 1;
        }
    }

    fn peek(&self) -> Result<char, JsonError> {
        self.chars
            .get(self.pos)
            .copied()
            .ok_or_else(|| self.error("Unexpected end of input"))
    }

    fn next(&mut self) -> Result<char, JsonError> {
        let c = self.peek()?;
        self.pos += 1;
        Ok(c)
    }

    fn expect(&mut self, wanted: char) -> Result<(), JsonError> {
        if self.peek()? == wanted {
            self.pos += 1;
            Ok(())
        } else {
            Err(self.error(&format!("Expected '{wanted}'")))
        }
    }

    fn value(&mut self) -> Result<Json, JsonError> {
        self.skip_whitespace();
        match self.peek()? {
            '{' => self.nested(Parser::object),
            '[' => self.nested(Parser::array),
            '"' => self.string().map(Json::String),
            't' => self.literal("true", Json::Bool(true)),
            'f' => self.literal("false", Json::Bool(false)),
            'n' => self.literal("null", Json::Null),
            _ => self.number(),
        }
    }

    fn nested(
        &mut self,
        parse: fn(&mut Parser) -> Result<Json, JsonError>,
    ) -> Result<Json, JsonError> {
        if self.depth >= MAX_DEPTH {
            return Err(self.error("Nesting is too deep"));
        }
        self.depth += 1;
        let result = parse(self);
        self.depth -= 1;
        result
    }

    fn literal(&mut self, word: &str, result: Json) -> Result<Json, JsonError> {
        let end = self.pos + word.chars().count();
        let matches = self
            .chars
            .get(self.pos..end)
            .is_some_and(|slice| slice.iter().copied().eq(word.chars()));
        if !matches {
            return Err(self.error("Invalid literal"));
        }
        self.pos = end;
        Ok(result)
    }

    fn object(&mut self) -> Result<Json, JsonError> {
        self.pos += 1;
        let mut pairs = Vec::new();
        self.skip_whitespace();
        if self.peek()? == '}' {
            self.pos += 1;
            return Ok(Json::Object(pairs));
        }
        loop {
            self.skip_whitespace();
            if self.peek()? != '"' {
                return Err(self.error("Expected a string key"));
            }
            let key = self.string()?;
            self.skip_whitespace();
            self.expect(':')?;
            let value = self.value()?;
            pairs.push((key, value));
            self.skip_whitespace();
            match self.next()? {
                '}' => return Ok(Json::Object(pairs)),
                ',' => {}
                _ => {
                    self.pos -= 1;
                    return Err(self.error("Expected ',' or '}'"));
                }
            }
        }
    }

    fn array(&mut self) -> Result<Json, JsonError> {
        self.pos += 1;
        let mut items = Vec::new();
        self.skip_whitespace();
        if self.peek()? == ']' {
            self.pos += 1;
            return Ok(Json::Array(items));
        }
        loop {
            items.push(self.value()?);
            self.skip_whitespace();
            match self.next()? {
                ']' => return Ok(Json::Array(items)),
                ',' => {}
                _ => {
                    self.pos -= 1;
                    return Err(self.error("Expected ',' or ']'"));
                }
            }
        }
    }

    fn string(&mut self) -> Result<String, JsonError> {
        self.expect('"')?;
        let mut out = String::new();
        loop {
            let c = self
                .chars
                .get(self.pos)
                .copied()
                .ok_or_else(|| self.error("Unterminated string"))?;
            self.pos += 1;
            match c {
                '"' => return Ok(out),
                '\\' => out.push(self.escape()?),
                c => out.push(c),
            }
        }
    }

    fn escape(&mut self) -> Result<char, JsonError> {
        let e = self
            .chars
            .get(self.pos)
            .copied()
            .ok_or_else(|| self.error("Unterminated escape"))?;
        self.pos += 1;
        Ok(match e {
            '"' | '\\' | '/' => e,
            'b' => '\u{08}',
            'f' => '\u{0c}',
            'n' => '\n',
            'r' => '\r',
            't' => '\t',
            'u' => return self.unicode_escape(),
            _ => return Err(self.error("Bad escape")),
        })
    }

    fn hex4(&mut self) -> Result<u32, JsonError> {
        let mut value = 0u32;
        for _ in 0..4 {
            let c = self
                .chars
                .get(self.pos)
                .copied()
                .ok_or_else(|| self.error("Bad unicode escape"))?;
            let digit = c
                .to_digit(16)
                .ok_or_else(|| self.error("Bad unicode escape"))?;
            value = value * 16 + digit;
            self.pos += 1;
        }
        Ok(value)
    }

    fn unicode_escape(&mut self) -> Result<char, JsonError> {
        let first = self.hex4()?;
        let code = if (0xD800..0xDC00).contains(&first) {
            if self.chars.get(self.pos) != Some(&'\\') || self.chars.get(self.pos + 1) != Some(&'u')
            {
                return Err(self.error("Expected a low surrogate"));
            }
            self.pos += 2;
            let second = self.hex4()?;
            if !(0xDC00..0xE000).contains(&second) {
                return Err(self.error("Invalid low surrogate"));
            }
            0x10000 + ((first - 0xD800) << 10) + (second - 0xDC00)
        } else if (0xDC00..0xE000).contains(&first) {
            return Err(self.error("Unexpected low surrogate"));
        } else {
            first
        };
        char::from_u32(code).ok_or_else(|| self.error("Invalid unicode code point"))
    }

    fn number(&mut self) -> Result<Json, JsonError> {
        let start = self.pos;
        while self.pos < self.chars.len() && "+-0123456789.eE".contains(self.chars[self.pos]) {
            self.pos += 1;
        }
        if start == self.pos {
            return Err(self.error(&format!("Unexpected character '{}'", self.chars[start])));
        }
        let text: String = self.chars[start..self.pos].iter().collect();
        let is_float = text.contains(['.', 'e', 'E']);
        let parsed = if is_float {
            text.parse::<f64>().ok().map(Json::Float)
        } else {
            text.parse::<i64>().ok().map(Json::Int)
        };
        parsed.ok_or_else(|| JsonError {
            message: "Invalid number".to_string(),
            position: start,
        })
    }
}
