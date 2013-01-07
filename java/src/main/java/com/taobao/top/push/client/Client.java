package com.taobao.top.push.client;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.tmall.top.push.messages.MessageIO;
import com.tmall.top.push.messages.MessageType;
import com.tmall.top.push.mqtt.MqttMessageIO;
import com.tmall.top.push.mqtt.publish.MqttPublishMessage;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.WebSockets;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.frame.Frame;
import jp.a840.websocket.frame.rfc6455.BinaryFrame;
import jp.a840.websocket.frame.rfc6455.FrameRfc6455;
import jp.a840.websocket.frame.rfc6455.TextFrame;
import jp.a840.websocket.handler.WebSocketHandler;
import jp.a840.websocket.impl.WebSocketImpl;

public class Client {
	private String self;
	private MessageHandler handler;
	private WebSocket socket;

	public Client(String clientFlag) {
		this.self = clientFlag;
	}

	public void setMessageHandler(MessageHandler handler) {
		this.handler = handler;
	}

	public Client connect(final String uri) throws WebSocketException,
			IOException, InterruptedException {
		WebSocket socket = WebSockets.create(uri, new WebSocketHandler() {
			public void onOpen(WebSocket socket) {
				synchronized (self) {
					socket.notify();
				}

			}

			public void onMessage(WebSocket socket, Frame frame) {
				if (frame instanceof BinaryFrame) {
					if (handler == null)
						return;

					ByteBuffer buffer = frame.getContents();
					int messageType = MessageIO.readMessageType(buffer);

					if (messageType != MessageType.PUBCONFIRM)
						return;

					MessageIO.readClientId(buffer);
					int remainingLength = MessageIO.readRemainingLength(buffer);

					handler.onMessage(messageType, "json", buffer.array(),
							buffer.arrayOffset() + buffer.position(),
							remainingLength);
				} else if (frame instanceof TextFrame) {

				}
			}

			public void onError(WebSocket socket, WebSocketException e) {
				e.printStackTrace();
			}

			public void onClose(WebSocket socket) {
				System.err.println("Closed");
			}
		});
		((WebSocketImpl) socket).setOrigin(this.self);
		socket.setBlockingMode(false);
		socket.connect();

		synchronized (socket) {
			socket.wait(2000);
		}
		this.socket = socket;
		return this;
	}

	public void sendMessage(String target, int messageType, String bodyFormat,
			byte[] messageBody, int offset, int length) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
		MqttPublishMessage pub=new MqttPublishMessage();
		MqttMessageIO.parseClientSending(pub, buffer);
		
		try {
			FrameRfc6455 frame = (FrameRfc6455) socket.createFrame(buffer);
			// client to server always mask
			// https://github.com/wsky/top-push-client/issues/3
			frame.mask();
			this.socket.send(frame);
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}
}
