package org.pojoquery.parser;



public class StatementParameterMatcher {

	private enum State {
		IDENTIFIERS, QUOTED_STRING, PARAMETERNAME
	}
	private State state = State.IDENTIFIERS;
	
	private final char[] sql;
	
	private int index = 0;

	private int tailStart = -1;
	private int parameterNameStart = -1;
	
	public StatementParameterMatcher(String sql) {
		this.sql = sql.toCharArray();
	}
	
	public boolean find() {
		parameterNameStart = -1;
		tailStart = index;
		
		while (index < sql.length) {
			char c = sql[index];
			switch (state) {
			case PARAMETERNAME:
				if (!Character.isUnicodeIdentifierPart(c)) {
					state = State.IDENTIFIERS;
					return true;
				}
				break;
			case QUOTED_STRING:
				if (c == '\\') {
					index++;
					break;
				}
				if (c == '\'') {
					state = State.IDENTIFIERS;
				}
				break;
			default:
				if (c == '\'') {
					state = State.QUOTED_STRING;
				}
				if (c == '?') {
					parameterNameStart = index;
					index++;
					return true;
				}
				if (c == ':') {
					if ((index + 1) < sql.length && Character.isUnicodeIdentifierStart(sql[index + 1])) {
						state = State.PARAMETERNAME;
						parameterNameStart = index;
					}
				}
				break;
			}
			index++;
		}
		switch(state) {
		case PARAMETERNAME:
			state = State.IDENTIFIERS;
			return true;
		case QUOTED_STRING:
			throw new RuntimeException("Syntax error: Unclosed quoted string at end of expression");
		case IDENTIFIERS:
			break;
		}
		return false;
	}

	public String getParameterName() {
		return new String(sql, parameterNameStart, index - parameterNameStart);
	}

	public void appendReplacement(StringBuilder target, String string) {
		target.append(sql, tailStart, parameterNameStart - tailStart);
		target.append(string);
	}

	public void appendTail(StringBuilder target) {
		target.append(sql, tailStart, index - tailStart);
	}

}
