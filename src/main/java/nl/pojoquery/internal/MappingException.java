package nl.pojoquery.internal;

public class MappingException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public MappingException(String message) {
		super(message);
	}

	public MappingException(String message, Throwable cause) {
		super(message, cause);
	}

	public MappingException(Throwable cause) {
		super(cause);
	}
}

