package net.coderbot.iris.shaderpack.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Evaluates the small boolean expressions shader packs write in shaders.properties values such as
 * {@code program.world0/gbuffers_entities_translucent.enabled = TRANSLUCENT_ENTITIES && IS_IRIS}.
 *
 * Grammar: {@code or := and ('||' and)*}, {@code and := unary ('&&' unary)*},
 * {@code unary := '!' unary | '(' or ')' | 'defined' '(' name ')' | 'true' | 'false' | number | name}.
 * A number is true when it is not zero. Names are resolved by the caller; {@code defined(name)} asks the caller
 * whether a macro with that name exists.
 */
public final class BooleanExpression {
	private final List<String> tokens;
	private final Function<String, Boolean> resolveName;
	private final Predicate<String> isDefined;
	private int position;

	private BooleanExpression(List<String> tokens, Function<String, Boolean> resolveName, Predicate<String> isDefined) {
		this.tokens = tokens;
		this.resolveName = resolveName;
		this.isDefined = isDefined;
	}

	/** True if the value uses any operator, i.e. is more than a single option name. */
	public static boolean isExpression(String value) {
		return value.contains("&&") || value.contains("||") || value.indexOf('!') >= 0
			|| value.indexOf('(') >= 0 || value.indexOf(')') >= 0;
	}

	/**
	 * @return the value of the expression, or empty if it cannot be parsed.
	 */
	public static Optional<Boolean> evaluate(String expression, Function<String, Boolean> resolveName, Predicate<String> isDefined) {
		List<String> tokens = tokenize(expression);
		if (tokens == null || tokens.isEmpty()) {
			return Optional.empty();
		}

		BooleanExpression parser = new BooleanExpression(tokens, resolveName, isDefined);
		Boolean result = parser.parseOr();
		if (result == null || parser.position != tokens.size()) {
			return Optional.empty();
		}
		return Optional.of(result);
	}

	private static List<String> tokenize(String expression) {
		List<String> tokens = new ArrayList<>();
		int i = 0;
		int length = expression.length();
		while (i < length) {
			char c = expression.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '&' || c == '|') {
				if (i + 1 >= length || expression.charAt(i + 1) != c) {
					return null;
				}
				tokens.add(c == '&' ? "&&" : "||");
				i += 2;
			} else if (c == '!' || c == '(' || c == ')') {
				tokens.add(String.valueOf(c));
				i++;
			} else if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
				int start = i;
				while (i < length && (Character.isLetterOrDigit(expression.charAt(i)) || expression.charAt(i) == '_'
						|| expression.charAt(i) == '.')) {
					i++;
				}
				tokens.add(expression.substring(start, i));
			} else {
				return null;
			}
		}
		return tokens;
	}

	private String peek() {
		return position < tokens.size() ? tokens.get(position) : null;
	}

	private Boolean parseOr() {
		Boolean left = parseAnd();
		while (left != null && "||".equals(peek())) {
			position++;
			Boolean right = parseAnd();
			if (right == null) {
				return null;
			}
			left = left || right;
		}
		return left;
	}

	private Boolean parseAnd() {
		Boolean left = parseUnary();
		while (left != null && "&&".equals(peek())) {
			position++;
			Boolean right = parseUnary();
			if (right == null) {
				return null;
			}
			left = left && right;
		}
		return left;
	}

	private Boolean parseUnary() {
		String token = peek();
		if (token == null) {
			return null;
		}
		position++;

		switch (token) {
			case "!": {
				Boolean operand = parseUnary();
				return operand == null ? null : !operand;
			}
			case "(": {
				Boolean inner = parseOr();
				if (inner == null || !")".equals(peek())) {
					return null;
				}
				position++;
				return inner;
			}
			case ")":
			case "&&":
			case "||":
				return null;
			case "true":
				return true;
			case "false":
				return false;
			case "defined": {
				if (!"(".equals(peek())) {
					return null;
				}
				position++;
				String name = peek();
				if (name == null || !isName(name)) {
					return null;
				}
				position++;
				if (!")".equals(peek())) {
					return null;
				}
				position++;
				return isDefined.test(name);
			}
			default:
				if (Character.isDigit(token.charAt(0))) {
					try {
						return Double.parseDouble(token) != 0.0;
					} catch (NumberFormatException e) {
						return null;
					}
				}
				return resolveName.apply(token);
		}
	}

	private static boolean isName(String token) {
		char first = token.charAt(0);
		return Character.isLetter(first) || first == '_';
	}
}
