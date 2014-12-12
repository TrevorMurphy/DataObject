package com.textserv.framework;

public class DataObjectException extends RuntimeException {

	private static final long serialVersionUID = -6893243871758595540L;

	public DataObjectException() {
	}

	public DataObjectException(String message) {
		super(message);
	}

	public DataObjectException(Throwable cause) {
		super(cause);
	}

	public DataObjectException(String message, Throwable cause) {
		super(message, cause);
	}
}
