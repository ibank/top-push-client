package com.taobao.top.push.client;

import java.nio.ByteBuffer;

import com.taobao.top.push.client.messages.MessageIO;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.frame.rfc6455.FrameRfc6455;

// backend will get target frontend context before sending
public class TargetContext {
	private WebSocket socket;
	private String target;

	public void setTarget(String target) {
		this.target = target;
	}

	public void setSocket(WebSocket socket) {
		this.socket = socket;
	}

	public void sendMessage(int messageType, String bodyFormat,
			byte[] messageBody, int offset, int length) {
		// TODO:impl mqtt parse
		ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
		MessageIO.writeMessageType(buffer, messageType);
		MessageIO.writeClientId(buffer, this.target);
		MessageIO.writeRemainingLength(buffer, length);
		buffer.put(messageBody, offset, length);

		try {
			FrameRfc6455 frame = (FrameRfc6455) socket.createFrame(buffer);
			// client to server always mask
			// https://github.com/wsky/top-push-client/issues/3
			frame.mask();
			socket.send(frame);
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}
}
