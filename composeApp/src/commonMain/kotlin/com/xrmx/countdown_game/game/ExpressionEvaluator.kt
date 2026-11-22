package com.xrmx.countdown_game.game

/**
 * Checks a player's working such as "(75 + 5) × 8 − 3" against the Countdown rules:
 * only the dealt numbers (each at most once), + − × ÷ and brackets, and every intermediate
 * result must be a positive whole number.
 */
object ExpressionEvaluator {

    sealed interface Result {
        data class Ok(val value: Int, val used: List<Int>) : Result
        data class Error(val message: String) : Result
    }

    fun evaluate(input: String, available: List<Int>): Result {
        val tokens = try {
            tokenize(input)
        } catch (e: IllegalArgumentException) {
            return Result.Error(e.message ?: "Invalid working")
        }
        if (tokens.isEmpty()) return Result.Error("Enter your working")
        val parser = Parser(tokens, available.toMutableList())
        return try {
            val value = parser.parseExpression()
            if (parser.pos != tokens.size) throw EvalException("Unexpected '${tokens[parser.pos]}'")
            Result.Ok(value.toInt(), parser.used)
        } catch (e: EvalException) {
            Result.Error(e.message ?: "Invalid working")
        }
    }

    /** Numbers typed so far, used to grey out tiles while the player builds their working. */
    fun numbersIn(input: String): List<Int> =
        Regex("\\d+").findAll(input).mapNotNull { it.value.toIntOrNull() }.toList()

    private class EvalException(message: String) : Exception(message)

    private fun tokenize(input: String): List<String> {
        val tokens = ArrayList<String>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() -> {
                    val start = i
                    while (i < input.length && input[i].isDigit()) i++
                    tokens.add(input.substring(start, i))
                }
                c == '+' -> { tokens.add("+"); i++ }
                c == '-' || c == '−' || c == '–' -> { tokens.add("-"); i++ }
                c == '*' || c == '×' || c == 'x' || c == 'X' -> { tokens.add("*"); i++ }
                c == '/' || c == '÷' -> { tokens.add("/"); i++ }
                c == '(' || c == ')' -> { tokens.add(c.toString()); i++ }
                c == '=' -> i = input.length // ignore anything after '='
                else -> throw IllegalArgumentException("'$c' isn't allowed")
            }
        }
        return tokens
    }

    private class Parser(val tokens: List<String>, val remaining: MutableList<Int>) {
        var pos = 0
        val used = ArrayList<Int>()

        fun parseExpression(): Long {
            var value = parseTerm()
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
                val op = tokens[pos++]
                val right = parseTerm()
                value = if (op == "+") value + right else {
                    val r = value - right
                    if (r <= 0) throw EvalException("$value − $right isn't positive")
                    r
                }
            }
            return value
        }

        fun parseTerm(): Long {
            var value = parseFactor()
            while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
                val op = tokens[pos++]
                val right = parseFactor()
                value = if (op == "*") value * right else {
                    if (value % right != 0L) throw EvalException("$value ÷ $right isn't a whole number")
                    value / right
                }
                if (value > Int.MAX_VALUE) throw EvalException("That number is too big")
            }
            return value
        }

        fun parseFactor(): Long {
            if (pos >= tokens.size) throw EvalException("Your working ends too soon")
            val token = tokens[pos++]
            return when {
                token == "(" -> {
                    val value = parseExpression()
                    if (pos >= tokens.size || tokens[pos] != ")") throw EvalException("Missing ')'")
                    pos++
                    value
                }
                token[0].isDigit() -> {
                    val n = token.toIntOrNull() ?: throw EvalException("$token is too big")
                    if (!remaining.remove(n)) {
                        throw EvalException(
                            if (n in used) "$n has already been used" else "$n isn't one of your numbers",
                        )
                    }
                    used.add(n)
                    n.toLong()
                }
                else -> throw EvalException("Unexpected '$token'")
            }
        }
    }
}
