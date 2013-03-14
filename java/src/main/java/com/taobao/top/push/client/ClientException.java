package com.taobao.top.push.client;

public class ClientException extends Exception {

	private static final long serialVersionUID = 454609188763498119L;

	public ClientException(String message) {
		super(message);
	}

	public ClientException(String message, Exception innerException) {
		super(message, innerException);
	}

	public ClientException(String message, Throwable innerException) {
		super(message, innerException);
	}
}
