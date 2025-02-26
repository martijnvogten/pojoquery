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

	public void setQuoteStyle(QuoteStyle ansi);

	public QuoteStyle getQuoteStyle();

	public void setQuoteObjectNames(boolean addQuotes);
	
	public String quoteAlias(String alias);

	public FieldMapping getFieldMapping(Field f);
	
	public class DefaultDbContext implements DbContext {
		private QuoteStyle quoteStyle = QuoteStyle.MYSQL;
		private boolean quoteObjects = true;

		@Override
		public void setQuoteStyle(QuoteStyle style) {
			this.quoteStyle = style;
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
		public void setQuoteObjectNames(boolean addQuotes) {
			this.quoteObjects = addQuotes;
		}

		@Override
		public FieldMapping getFieldMapping(Field f) {
			return new SimpleFieldMapping(f);
		}
	}

	public static DbContext getDefault() {
		return DEFAULT;
	}

}
