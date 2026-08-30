package org.pojoquery.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Re-indents a generated SQL statement so its nesting is visible at a glance.
 *
 * <p>Statements are assembled from fragments - a subquery, a JSON object
 * expression, a CTE body - each of which is built without knowing how deeply it
 * will end up nested. Rather than thread an indentation level through every
 * builder, the assembled statement is indented once, at the end: this class
 * discards the leading whitespace of every line and recomputes it from the
 * structure it finds. That makes indentation a property of the final statement
 * alone, so a fragment reads the same whether it is the whole statement or
 * buried three levels down, and re-indenting an already indented statement is a
 * no-op.</p>
 *
 * <h2>The rules</h2>
 * <ul>
 * <li>Content of a parenthesized group sits one level in from the line that
 * opened it, and the line closing the group returns to the opening line's
 * level - so a derived table's {@code )} lines up under its {@code LEFT JOIN (},
 * and a nested {@code JSON_OBJECT(}'s under the property that introduces it.</li>
 * <li>A clause keyword ({@code SELECT}, {@code FROM}, {@code WHERE}, a join,
 * {@code UNION ALL}, ...) starts a clause at the current level; the lines that
 * follow it - select items, {@code AND} continuations - sit one level in, until
 * the next clause keyword or the end of the group.</li>
 * </ul>
 *
 * <p>Only leading whitespace is rewritten; existing line breaks are kept, since
 * the builders decide what belongs on its own line. Lines are scanned outside
 * of string literals, quoted identifiers and comments, so a parenthesis inside
 * {@code '(not sql)'} does not shift the rest of the statement, and a literal
 * spanning several lines is left exactly as it is.</p>
 */
public final class SqlIndenter {

	/** One level of indentation. */
	private static final String INDENT_UNIT = "  ";

	/**
	 * Words that start a clause: they sit at their group's level and indent
	 * what follows. {@code JOIN} is matched with its optional qualifiers so that
	 * a {@code LEFT JOIN} line is recognized as one clause, not as a line
	 * starting with the word {@code LEFT}.
	 */
	private static final Pattern CLAUSE_KEYWORD = Pattern.compile(
			"^(?:SELECT|FROM|WHERE|GROUP\\s+BY|HAVING|ORDER\\s+BY|LIMIT|OFFSET|FETCH|WINDOW"
					+ "|UNION(?:\\s+ALL)?|INTERSECT(?:\\s+ALL)?|EXCEPT(?:\\s+ALL)?"
					+ "|(?:(?:LEFT|RIGHT|INNER|FULL|CROSS|NATURAL)(?:\\s+OUTER)?\\s+)?JOIN"
					+ "|WITH(?:\\s+RECURSIVE)?|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|SET|VALUES|RETURNING"
					+ "|FOR\\s+UPDATE)\\b",
			Pattern.CASE_INSENSITIVE);

	/** A parenthesized group that is still open. */
	private static final class Group {

		/** Indentation level of the line that opened the group. */
		final int openerLevel;

		/** Indentation level of lines directly inside the group. */
		final int contentLevel;

		/** Whether a clause keyword has been seen at this group's level. */
		boolean inClause;

		Group(int openerLevel) {
			this.openerLevel = openerLevel;
			this.contentLevel = openerLevel + 1;
		}
	}

	/** What the scanner is currently inside of, and so must not read as SQL. */
	private enum Quoting {
		STRING('\''), IDENTIFIER('"'), BACKTICK('`'), BLOCK_COMMENT('\0');

		final char delimiter;

		Quoting(char delimiter) {
			this.delimiter = delimiter;
		}
	}

	/** A parenthesis found outside of any literal or comment. */
	private record Paren(boolean open, boolean leading) {
	}

	private SqlIndenter() {
	}

	/**
	 * Returns the statement with every line's indentation recomputed from its
	 * nesting. Blank lines are dropped and trailing whitespace removed.
	 *
	 * @param sql the assembled statement; may be null or single-line, in which
	 *            case it is returned unchanged
	 */
	public static String indent(String sql) {
		if (sql == null || sql.indexOf('\n') < 0) {
			return sql;
		}
		List<String> result = new ArrayList<>();
		Deque<Group> groups = new ArrayDeque<>();
		// The statement itself is a group: its content sits at level 0, and the
		// root can never be popped by a stray closing parenthesis.
		Group root = new Group(-1);
		groups.push(root);

		Quoting quoting = null;
		int level = 0;
		for (String line : sql.split("\n", -1)) {
			List<Paren> parens = new ArrayList<>();
			boolean continuesLiteral = quoting != null;
			quoting = scan(line, quoting, parens);

			if (continuesLiteral) {
				// Inside a literal that began on an earlier line: its content is
				// data, so it is passed through byte for byte.
				result.add(line);
			} else {
				String trimmed = line.strip();
				if (trimmed.isEmpty()) {
					continue;
				}
				level = levelFor(trimmed, parens, groups);
				result.add(INDENT_UNIT.repeat(level) + trimmed);
			}
			openAndCloseGroups(parens, groups, level, root);
		}
		return String.join("\n", result);
	}

	/**
	 * Determines the level of a line, consuming the groups its leading closing
	 * parentheses close.
	 */
	private static int levelFor(String trimmed, List<Paren> parens, Deque<Group> groups) {
		int closed = 0;
		for (Paren paren : parens) {
			if (paren.leading()) {
				closed++;
			}
		}
		if (closed > 0) {
			// A line that starts by closing groups belongs to the outermost one
			// it closes, so it lines up with that group's opening line.
			int openerLevel = groups.peek().openerLevel;
			for (int i = 0; i < closed && groups.size() > 1; i++) {
				openerLevel = groups.pop().openerLevel;
			}
			return Math.max(openerLevel, 0);
		}
		Group group = groups.peek();
		if (CLAUSE_KEYWORD.matcher(trimmed).find()) {
			group.inClause = true;
			return group.contentLevel;
		}
		return group.inClause ? group.contentLevel + 1 : group.contentLevel;
	}

	/**
	 * Applies the parentheses that remain once the line's level is fixed: the
	 * leading closers are already accounted for by {@link #levelFor}.
	 *
	 * <p>A group opened on this line takes its level from how many of the line's
	 * own groups are still open at that point, so
	 * {@code WITH "cte" ("a", "b") AS (} opens the body at the line's own level,
	 * not one level in for the column list it already closed.</p>
	 */
	private static void openAndCloseGroups(List<Paren> parens, Deque<Group> groups, int level, Group root) {
		int openedHere = 0;
		for (Paren paren : parens) {
			if (paren.leading()) {
				continue;
			}
			if (paren.open()) {
				groups.push(new Group(level + openedHere));
				openedHere++;
			} else if (groups.peek() != root) {
				groups.pop();
				openedHere = Math.max(openedHere - 1, 0);
			}
		}
	}

	/**
	 * Scans one line, collecting the parentheses that are structural - outside
	 * of any literal, quoted identifier or comment - and returns the quoting
	 * still open at the end of the line.
	 *
	 * <p>A parenthesis is {@link Paren#leading()} when nothing but closing
	 * parentheses and whitespace precede it, which is what makes a line "start
	 * by closing a group".</p>
	 */
	private static Quoting scan(String line, Quoting quoting, List<Paren> parens) {
		boolean leading = true;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quoting != null) {
				if (quoting == Quoting.BLOCK_COMMENT) {
					if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
						i++;
						quoting = null;
					}
				} else if (c == quoting.delimiter) {
					// A doubled delimiter is an escaped one, not the end.
					if (i + 1 < line.length() && line.charAt(i + 1) == c) {
						i++;
					} else {
						quoting = null;
					}
				}
				continue;
			}
			switch (c) {
			case '\'' -> quoting = Quoting.STRING;
			case '"' -> quoting = Quoting.IDENTIFIER;
			case '`' -> quoting = Quoting.BACKTICK;
			case '(' -> parens.add(new Paren(true, false));
			case ')' -> parens.add(new Paren(false, leading));
			case '-' -> {
				if (i + 1 < line.length() && line.charAt(i + 1) == '-') {
					return null; // line comment: the rest of the line is prose
				}
			}
			case '/' -> {
				if (i + 1 < line.length() && line.charAt(i + 1) == '*') {
					i++;
					quoting = Quoting.BLOCK_COMMENT;
				}
			}
			default -> {
			}
			}
			// Only closing parentheses keep a line "leading": everything else is
			// content, after which a later ')' no longer starts the line.
			if (c != ')' && !Character.isWhitespace(c)) {
				leading = false;
			}
		}
		return quoting;
	}
}
