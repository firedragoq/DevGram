"""Text parsing helpers with Telegram-compatible UTF-16 entity offsets."""

from dataclasses import dataclass
from html import escape as _html_escape
from html.parser import HTMLParser
from java import jclass


@dataclass(frozen=True)
class Entity:
    type: str
    offset: int
    length: int
    url: str = None
    language: str = None


_ENTITY_CLASSES = {
    "bold": "org.telegram.tgnet.TLRPC$TL_messageEntityBold",
    "italic": "org.telegram.tgnet.TLRPC$TL_messageEntityItalic",
    "underline": "org.telegram.tgnet.TLRPC$TL_messageEntityUnderline",
    "strikethrough": "org.telegram.tgnet.TLRPC$TL_messageEntityStrike",
    "spoiler": "org.telegram.tgnet.TLRPC$TL_messageEntitySpoiler",
    "code": "org.telegram.tgnet.TLRPC$TL_messageEntityCode",
    "pre": "org.telegram.tgnet.TLRPC$TL_messageEntityPre",
    "blockquote": "org.telegram.tgnet.TLRPC$TL_messageEntityBlockquote",
    "text_url": "org.telegram.tgnet.TLRPC$TL_messageEntityTextUrl",
}


def to_tlrpc_entities(entities):
    """Convert Entity objects/dicts to Java ArrayList<TLRPC.MessageEntity>."""
    ArrayList = jclass("java.util.ArrayList")
    result = ArrayList()
    for item in entities or []:
        kind = getattr(item, "type", None) or item.get("type")
        cls_name = _ENTITY_CLASSES.get(str(kind))
        if not cls_name:
            continue
        entity = jclass(cls_name)()
        if isinstance(item, dict):
            offset, length = item.get("offset", 0), item.get("length", 0)
        else:
            offset, length = getattr(item, "offset", 0), getattr(item, "length", 0)
        entity.offset = int(offset)
        entity.length = int(length)
        url = getattr(item, "url", None) if not isinstance(item, dict) else item.get("url")
        language = getattr(item, "language", None) if not isinstance(item, dict) else item.get("language")
        if url is not None and hasattr(entity, "url"):
            entity.url = str(url)
        if language is not None and hasattr(entity, "language"):
            entity.language = str(language)
        result.add(entity)
    return result


def add_surrogates(text):
    result = []
    for char in str(text):
        code = ord(char)
        if code < 0x10000:
            result.append(char)
        else:
            code -= 0x10000
            result.extend((chr(0xD800 + (code >> 10)), chr(0xDC00 + (code & 0x3FF))))
    return "".join(result)


def remove_surrogates(text):
    return str(text).encode("utf-16-le", "surrogatepass").decode("utf-16-le", "replace")


def utf16_length(text):
    return len(str(text).encode("utf-16-le", "surrogatepass")) // 2


class _HTMLToEntities(HTMLParser):
    TYPES = {"b": "bold", "strong": "bold", "i": "italic", "em": "italic",
             "u": "underline", "s": "strikethrough", "strike": "strikethrough",
             "del": "strikethrough", "code": "code", "pre": "pre", "blockquote": "blockquote"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts, self.entities, self.stack = [], [], []

    @property
    def length(self):
        return utf16_length("".join(self.parts))

    def handle_data(self, data):
        self.parts.append(data)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        entity_type = "text_url" if tag == "a" else self.TYPES.get(tag)
        if entity_type:
            self.stack.append((tag, entity_type, self.length, attrs))
        elif tag == "br":
            self.parts.append("\n")

    def handle_endtag(self, tag):
        for index in range(len(self.stack) - 1, -1, -1):
            opened_tag, entity_type, start, attrs = self.stack[index]
            if opened_tag != tag:
                continue
            del self.stack[index]
            length = self.length - start
            if length:
                self.entities.append(Entity(entity_type, start, length,
                                            url=attrs.get("href"), language=attrs.get("class")))
            break


def parse_html(text):
    parser = _HTMLToEntities()
    parser.feed(str(text))
    parser.close()
    return "".join(parser.parts), sorted(parser.entities, key=lambda e: (e.offset, -e.length))


_MARKERS = (("**", "bold"), ("__", "underline"), ("~~", "strikethrough"),
            ("||", "spoiler"), ("`", "code"), ("_", "italic"))


def parse_markdown(text):
    source, output, entities, stack = str(text), [], [], []
    i = 0
    while i < len(source):
        if source[i] == "\\" and i + 1 < len(source):
            output.append(source[i + 1]); i += 2; continue
        marker_info = next(((m, t) for m, t in _MARKERS if source.startswith(m, i)), None)
        if marker_info is None:
            output.append(source[i]); i += 1; continue
        marker, entity_type = marker_info
        if stack and stack[-1][0] == marker:
            _, _, start = stack.pop()
            length = utf16_length("".join(output)) - start
            if length:
                entities.append(Entity(entity_type, start, length))
        else:
            stack.append((marker, entity_type, utf16_length("".join(output))))
        i += len(marker)
    for marker, _, start in reversed(stack):
        current = utf16_length("".join(output))
        output.insert(_python_index_for_utf16("".join(output), start), marker)
    return "".join(output), sorted(entities, key=lambda e: (e.offset, -e.length))


def _python_index_for_utf16(text, offset):
    count = 0
    for index, char in enumerate(text):
        if count >= offset:
            return index
        count += utf16_length(char)
    return len(text)


def parse_text(text, parse_mode=None):
    mode = str(parse_mode or "").lower()
    if mode in ("html", "htm"):
        return parse_html(text)
    if mode in ("markdown", "md", "markdownv2"):
        return parse_markdown(text)
    return str(text), []


def escape_markdown(text):
    special = "_*[]()~`>#+-=|{}.!\\"
    return "".join("\\" + c if c in special else c for c in str(text))


def escape_html(text):
    return _html_escape(str(text), quote=False)
