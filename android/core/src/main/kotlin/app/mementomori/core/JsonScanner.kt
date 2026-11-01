package app.mementomori.core

/**
 * Быстрое чтение больших JSON-файлов с картой и границами стран: сотни тысяч чисел
 * сразу складываются в массивы, без промежуточных объектов.
 */
class JsonScanner(private val text: String) {
    private var position = 0

    private fun skipWhitespace() {
        while (position < text.length && text[position].isWhitespace()) position++
    }

    fun peek(): Char {
        skipWhitespace()
        return if (position < text.length) text[position] else '\u0000'
    }

    fun expect(char: Char) {
        skipWhitespace()
        require(position < text.length && text[position] == char) { "JSON: ожидался «$char» в позиции $position" }
        position++
    }

    /** Пропускает запятую, если она есть. */
    private fun skipComma(): Boolean {
        skipWhitespace()
        if (position < text.length && text[position] == ',') {
            position++
            return true
        }
        return false
    }

    fun readString(): String {
        expect('"')
        val builder = StringBuilder()
        while (position < text.length) {
            val char = text[position++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> {
                    val escaped = text[position++]
                    when (escaped) {
                        'n' -> builder.append('\n')
                        't' -> builder.append('\t')
                        'u' -> {
                            builder.append(text.substring(position, position + 4).toInt(16).toChar())
                            position += 4
                        }
                        else -> builder.append(escaped)
                    }
                }
                else -> builder.append(char)
            }
        }
        error("JSON: незакрытая строка")
    }

    fun readDouble(): Double {
        skipWhitespace()
        val start = position
        while (position < text.length && (text[position].isDigit() || text[position] in "+-.eE")) position++
        return text.substring(start, position).toDouble()
    }

    /** Целое число без промежуточных строк. */
    fun readInt(): Int {
        skipWhitespace()
        var negative = false
        if (text[position] == '-') {
            negative = true
            position++
        }
        var value = 0
        while (position < text.length && text[position].isDigit()) {
            value = value * 10 + (text[position] - '0')
            position++
        }
        return if (negative) -value else value
    }

    /** Обходит объект: для каждого ключа вызывает onKey, который обязан прочитать значение. */
    fun readObject(onKey: (String) -> Unit) {
        expect('{')
        if (peek() == '}') {
            position++
            return
        }
        do {
            val key = readString()
            expect(':')
            onKey(key)
        } while (skipComma())
        expect('}')
    }

    /** Обходит массив: для каждого элемента вызывает onItem, который обязан прочитать элемент. */
    fun readArray(onItem: () -> Unit) {
        expect('[')
        if (peek() == ']') {
            position++
            return
        }
        do {
            onItem()
        } while (skipComma())
        expect(']')
    }

    fun readIntArray(): IntArray {
        var buffer = IntArray(64)
        var size = 0
        readArray {
            if (size == buffer.size) buffer = buffer.copyOf(size * 2)
            buffer[size++] = readInt()
        }
        return buffer.copyOf(size)
    }

    fun readFloatArray(): FloatArray {
        var buffer = FloatArray(64)
        var size = 0
        readArray {
            if (size == buffer.size) buffer = buffer.copyOf(size * 2)
            buffer[size++] = readDouble().toFloat()
        }
        return buffer.copyOf(size)
    }

    /** Пропускает любое значение. */
    fun skipValue() {
        when (peek()) {
            '{' -> readObject { skipValue() }
            '[' -> readArray { skipValue() }
            '"' -> readString()
            't' -> position += 4
            'f' -> position += 5
            'n' -> position += 4
            else -> readDouble()
        }
    }
}
