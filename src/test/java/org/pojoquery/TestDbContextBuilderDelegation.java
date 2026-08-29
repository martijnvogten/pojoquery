package org.pojoquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.pojoquery.DbContext.Dialect;

/**
 * A context from {@link DbContextBuilder} must behave as its dialect does,
 * except for the few things the builder exists to customize.
 *
 * <p>{@link DbContext} declares most of its dialect behaviour as {@code default}
 * methods, so a wrapper that forgets to forward one does not fail to compile -
 * it silently answers with the interface default. That is how a POSTGRES
 * context built here came to emit MySQL's {@code CAST(x AS CHAR)}, which in
 * PostgreSQL means {@code char(1)} and truncates every value to a single
 * character with no error anywhere.</p>
 */
public class TestDbContextBuilderDelegation {

	/** Methods the builder deliberately overrides, or that carry no dialect behaviour. */
	private static final List<String> CUSTOMIZED = List.of(
			"getFieldMapping",     // the builder's own factory
			"getQuoteStyle",       // overridable through the builder
			"quoteObjectNames",    // ditto
			"quoteAlias");         // ditto

	@Test
	public void testBuiltContextAnswersLikeItsDialect() {
		for (Dialect dialect : Dialect.values()) {
			DbContext expected = DbContext.forDialect(dialect);
			DbContext actual = new DbContextBuilder().dialect(dialect).build();
			for (Method method : DbContext.class.getMethods()) {
				if (Modifier.isStatic(method.getModifiers()) || CUSTOMIZED.contains(method.getName())) {
					continue;
				}
				Object[] args = argumentsFor(method);
				if (args == null) {
					continue;
				}
				assertEquals(describe(invoke(expected, method, args)), describe(invoke(actual, method, args)),
						dialect + "." + method.getName() + " must come from the dialect, not the interface default");
			}
		}
	}

	/** Sample arguments for a method, or null when we cannot call it meaningfully. */
	private static Object[] argumentsFor(Method method) {
		Object[] args = new Object[method.getParameterCount()];
		for (int i = 0; i < args.length; i++) {
			Class<?> type = method.getParameterTypes()[i];
			if (type == SqlExpression.class) {
				args[i] = SqlExpression.sql("x");
			} else if (type == List.class) {
				args[i] = List.of();
			} else if (type == String.class) {
				args[i] = "x";
			} else if (type == String[].class) {
				args[i] = new String[] { "x" };
			} else if (type == Object.class) {
				args[i] = BigDecimal.ONE;
			} else if (type == Class.class) {
				args[i] = String.class;
			} else {
				return null;   // nothing sensible to pass
			}
		}
		return args;
	}

	private static Object invoke(DbContext context, Method method, Object[] args) {
		try {
			return method.invoke(context, args);
		} catch (Exception e) {
			// Some methods reject the sample arguments; that is fine as long as
			// both contexts reject them the same way.
			return e.getCause() == null ? e.toString() : e.getCause().toString();
		}
	}

	private static String describe(Object value) {
		if (value instanceof SqlExpression expression) {
			return expression.getSql();
		}
		return String.valueOf(value);
	}

	/**
	 * The specific breakage that prompted this test: values travel through JSON
	 * as text, so a wrong cast silently truncates them.
	 */
	@Test
	public void testCastToStringMatchesDialect() {
		assertEquals("CAST(x AS CHAR)", builtCast(Dialect.MYSQL));
		// PostgreSQL's CHAR means char(1): the wrong cast here truncated every
		// value in a JSON document to its first character.
		assertEquals("CAST(x AS TEXT)", builtCast(Dialect.POSTGRES));
		// CHAR would pad, which this codebase has been bitten by before.
		assertEquals("CAST(x AS VARCHAR)", builtCast(Dialect.HSQLDB));
	}

	private static String builtCast(Dialect dialect) {
		return new DbContextBuilder().dialect(dialect).build()
				.castToStringExpression(SqlExpression.sql("x")).getSql();
	}
}
