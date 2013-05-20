package nl.pojoquery;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryStructure {
	
	public static class Alias {
		public final String alias;
		public final Alias parent;
		public final Class<?> resultClass;
		public final Field linkField;
		public final Alias superAlias;
		public List<Field> idFields = new ArrayList<Field>();
		
		public Alias(String linkName, Alias superClass, Class<?> resultClass) {
			this.alias = combinedAlias(linkName, superClass);
			this.superAlias = superClass;
			this.resultClass = resultClass;
			this.linkField = null;
			this.parent = null;
		}
		
		public Alias(String linkName, Alias parent, Class<?> resultClass, Field linkField) {
			this.linkField = linkField;
			this.alias = combinedAlias(linkName, parent);
			this.parent = parent;
			this.resultClass = resultClass;
			this.superAlias = null;
		}

		private static String combinedAlias(String linkName, Alias parent) {
			if (parent == null || parent.parent == null) {
				return linkName;
			}
			return parent.alias + "." + linkName;
		}
		
		public boolean isPrincipalAlias() {
			return parent == null;
		}
		
		public boolean isSubClass() {
			return superAlias != null;
		}
		
		public String toString() {
			return alias + "[" + resultClass.getName() + "]";
		}
	}
	
	public Map<String, Alias> aliases = new HashMap<String,Alias>();
	public Map<String, Field> classFields = new HashMap<String,Field>();
	public List<Alias> subClasses = new ArrayList<Alias>();
	
	public Alias createAlias(String linkName, Alias parent, Class<?> resultClass, Field linkField) {
		Alias alias = new Alias(linkName, parent, resultClass, linkField);
		aliases.put(alias.alias, alias);
		return alias;
	}

	public Alias addSubClass(String linkName, Alias superClass, Class<?> subClass) {
		Alias subClassAlias = new Alias(linkName, superClass, subClass);
		subClasses.add(subClassAlias);
		aliases.put(subClassAlias.alias, subClassAlias);
		return subClassAlias;
	}
}
