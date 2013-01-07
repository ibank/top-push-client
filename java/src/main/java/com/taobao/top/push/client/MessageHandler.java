package com.taobao.top.push.client;

public abstract class MessageHandler {
	public abstract void onMessage(int messageType, int bodyFormat,
			byte[] messageBody, int offset, int length, MessageContext context);
}