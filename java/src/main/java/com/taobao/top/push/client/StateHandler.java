package com.taobao.top.push.client;

public interface StateHandler {
	public void onError(Exception exception);
	public void onClose(int statusCode, String reasonText);
}
