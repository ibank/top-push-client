package com.taobao.top.push.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.tmall.top.push.messages.MessageIO;
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
	private final static String MQTT = "mqtt";
	private final static int MAXSIZE = 1024;
	private String protocol;
	private String self;
	private MessageHandler handler;
	private WebSocket socket;
	private ConcurrentLinkedQueue<byte[]> bufferQueue;

	public Client(String clientFlag) {
		this.self = clientFlag;
		this.bufferQueue = new ConcurrentLinkedQueue<byte[]>();
	}

	public void setMessageHandler(MessageHandler handler) {
		this.handler = handler;
	}

	public Client connect(String uri) throws WebSocketException, IOException,
			InterruptedException {
		return this.connect(uri, "");
	}

	public Client connect(String uri, String messageProtocol)
			throws WebSocketException, IOException, InterruptedException {
		// message protocol to cover top-push protocol
		this.protocol = messageProtocol;
		final Client base = this;

		WebSocket startSocket = WebSockets.create(uri, new WebSocketHandler() {
			public void onOpen(WebSocket socket) {
				synchronized (base) {
					base.notify();
				}
			}

			public void onMessage(WebSocket socket, Frame frame) {
				if (frame instanceof BinaryFrame) {
					if (handler == null)
						return;

					ByteBuffer buffer = frame.getContents();
					String messageFrom;
					int messageType = 0;
					int remainingLength = 0;

					if (MQTT.equalsIgnoreCase(protocol)) {
						MqttPublishMessage message = new MqttPublishMessage();
						MqttMessageIO.parseClientReceiving(message, buffer);
						messageType = message.messageType;
						messageFrom = message.from;
						remainingLength = message.remainingLength;
					} else {
						messageType = MessageIO.readMessageType(buffer);
						messageFrom = MessageIO.readClientId(buffer);
						remainingLength = MessageIO.readRemainingLength(buffer);
					}

					MessageContext context = new MessageContext(base,
							messageFrom);
					handler.onMessage(messageType, MessageBodyFormat.JSON,
							buffer.array(),
							buffer.arrayOffset() + buffer.position(),
							remainingLength, context);

				} else if (frame instanceof TextFrame) {
					System.out.println(String.format("text message:%s", frame));
				}
			}

			public void onError(WebSocket socket, WebSocketException e) {
				e.printStackTrace();
			}

			public void onClose(WebSocket socket) {
				System.err.println("Closed");
			}
		}, this.protocol);

		((WebSocketImpl) startSocket).setOrigin(this.self);
		startSocket.setBlockingMode(false);
		startSocket.connect();

		synchronized (base) {
			base.wait(2000);
		}
		this.socket = startSocket;
		System.out.println(String.format("connected to server %s", uri));
		return this;
	}

	public void sendMessage(String to, int messageType, int messageBodyFormat,
			byte[] messageBody, int offset, int length) {
		byte[] back = this.getBuffer();
		ByteBuffer buffer = ByteBuffer.wrap(back);

		if (MQTT.equalsIgnoreCase(this.protocol)) {
			MqttPublishMessage msg = new MqttPublishMessage();
			msg.messageType = messageType;
			msg.to = to;
			msg.remainingLength = length;
			buffer = ByteBuffer.wrap(back);
			MqttMessageIO.parseClientSending(msg, buffer);
			buffer.put(messageBody, offset, length);
		} else {
			MessageIO.writeMessageType(buffer, messageType);
			MessageIO.writeClientId(buffer, to);
			MessageIO.writeRemainingLength(buffer, length);
			buffer.put(messageBody, offset, length);
		}
		// empty field will cause websocket loop and frame wont be send
		buffer = ByteBuffer.wrap(back, 0, buffer.position() + 1).slice();
		buffer.position(0);

		try {
			FrameRfc6455 frame = (FrameRfc6455) socket.createFrame(buffer);
			// client to server always mask
			// https://github.com/wsky/top-push-client/issues/3
			frame.mask();
			this.socket.send(frame);
		} catch (WebSocketException e) {
			e.printStackTrace();
		} finally {
			this.returnBuffer(back);
		}
	}

	// easy buffer pool
	private byte[] getBuffer() {
		byte[] buffer = this.bufferQueue.poll();
		if (buffer == null)
			buffer = new byte[MAXSIZE];
		return buffer;
	}

	private void returnBuffer(byte[] buffer) {
		this.bufferQueue.add(buffer);
	}
}
