package nl.pojoquery;

import java.lang.reflect.Field;

import nl.pojoquery.pipeline.SimpleFieldMapping;

public interface DbContext {
	
	public enum QuoteStyle {
		ANSI("\""),
		MYSQL("`");

		private final String quote;

		QuoteStyle(String quote) {
			this.quote = quote;
		}

		public String quote(String name) {
			return quote + name + quote;
		}
	}

	static DbContext DEFAULT = new DefaultDbContext();

	public String quoteObjectNames(String... names);
	public QuoteStyle getQuoteStyle();
	public String quoteAlias(String alias);
	public FieldMapping getFieldMapping(Field f);
	
	public class DefaultDbContext implements DbContext {
		private final QuoteStyle quoteStyle;
		private final boolean quoteObjects;

		public DefaultDbContext() {
			this(QuoteStyle.MYSQL, true);
		}

		public DefaultDbContext(QuoteStyle quoteStyle, boolean quoteObjects) {
			this.quoteStyle = quoteStyle;
			this.quoteObjects = quoteObjects;
		}

		@Override
		public String quoteObjectNames(String... names) {
			String ret = "";
			for (int i = 0, nl = names.length; i < nl; i++) {
				String name = names[i];
				if (i > 0) {
					ret += ".";
				}
				ret += quoteObjects ? quoteStyle.quote(name) : name;
			}
			return ret;
		}

		@Override
		public QuoteStyle getQuoteStyle() {
			return quoteStyle;
		}

		@Override
		public String quoteAlias(String alias) {
			// Always quote aliases
			return quoteStyle.quote(alias);
		}

		@Override
		public FieldMapping getFieldMapping(Field f) {
			return new SimpleFieldMapping(f);
		}
	}

	public static DbContext getDefault() {
		return DEFAULT;
	}

	/**
	 * Creates a new DbContextBuilder for configuring a custom DbContext.
	 * @return A new DbContextBuilder instance
	 */
	public static DbContextBuilder builder() {
		return new DbContextBuilder();
	}
}
