package com.taobao.top.push.client.messages;

public class Message {
	public int messageType;
	
	// message from client id
	// need be filled when receiving
	public String from;
	// target client id
	public String to;
	
	public int remainingLength;
	public Object body;
	
	public int fullMessageSize;

	public void clear() {
		this.messageType = 0;
		this.remainingLength = 0;
		this.body = null;
		
		this.fullMessageSize = 0;
		this.from = null;
		this.to = null;
		
	}
}
