package com.taobao.top.push.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.taobao.top.push.messages.MessageIO;
import com.taobao.top.push.mqtt.MqttMessageIO;
import com.taobao.top.push.mqtt.MqttMessageType;
import com.taobao.top.push.mqtt.publish.MqttPublishMessage;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.WebSockets;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.frame.Frame;
import jp.a840.websocket.frame.rfc6455.BinaryFrame;
import jp.a840.websocket.frame.rfc6455.FrameRfc6455;
import jp.a840.websocket.frame.rfc6455.PingFrame;
import jp.a840.websocket.frame.rfc6455.TextFrame;
import jp.a840.websocket.handler.WebSocketHandler;
import jp.a840.websocket.impl.WebSocketImpl;

public class Client {
	private final static String MQTT = "mqtt";
	private int maxMessageSize = 1024;
	private int maxIdle = 60000;

	private String uri;
	private String protocol;
	private String self;
	private HashMap<String, String> headers;
	private MessageHandler handler;
	private WebSocket socket;
	private ConcurrentLinkedQueue<byte[]> bufferQueue;

	private boolean pingFlag;
	private Timer pingTimer;
	private TimerTask pingTimerTask;

	private WebSocketException exception;
	private int reconnectInterval = 5000;
	private int reconnectCount;
	private Timer reconnecTimer;
	private TimerTask reconnecTimerTask;

	public Client(String clientFlag) {
		this.self = clientFlag;
		this.bufferQueue = new ConcurrentLinkedQueue<byte[]>();
		// necessary?
		this.doReconnect();
	}

	public void setMaxIdle(int maxIdle) {
		this.maxIdle = maxIdle;
	}

	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	public void setMessageHandler(MessageHandler handler) {
		this.handler = handler;
	}

	public Client connect(String uri) throws WebSocketException, IOException,
			InterruptedException {
		return this.connect(uri, "", null);
	}

	public Client connect(String uri, HashMap<String, String> headers) throws WebSocketException, IOException,
			InterruptedException {
		return this.connect(uri, "", headers);
	}

	public Client connect(String uri, String messageProtocol) throws WebSocketException, IOException,
			InterruptedException {
		return this.connect(uri, messageProtocol, null);
	}

	public Client connect(String uri, String messageProtocol, HashMap<String, String> headers)
			throws WebSocketException, IOException, InterruptedException {
		this.uri = uri;
		// message protocol to cover top-push protocol
		this.protocol = messageProtocol;
		this.headers = headers;
		final Client base = this;

		WebSocket startSocket = WebSockets.create(uri, new WebSocketHandler() {
			public void onOpen(WebSocket socket) {
				base.socket = socket;
				// TODO:after open, send CONNECT if mqtt
				synchronized (base) {
					base.notify();
				}
			}

			public void onMessage(WebSocket socket, Frame frame) {
				delayNextPing();

				if (frame instanceof BinaryFrame) {
					if (handler == null)
						return;

					ByteBuffer buffer = frame.getContents();
					String messageFrom;
					int messageType = 0;
					int messageBodyFormat = 0;
					int remainingLength = 0;

					if (MQTT.equalsIgnoreCase(protocol)) {
						int mqttMessageType = MqttMessageIO
								.parseMessageType(buffer.get(0));
						if (mqttMessageType == MqttMessageType.ConnectAck) {
							// TODO:deal with CONNACK
							return;
						} else if (mqttMessageType != MqttMessageType.Publish) {
							System.err.println("Not Implement MqttMessageType:"
									+ mqttMessageType);
							return;
						}
						MqttPublishMessage message = new MqttPublishMessage();
						MqttMessageIO.parseClientReceiving(message, buffer);
						messageType = message.messageType;
						messageFrom = message.from;
						messageBodyFormat = message.bodyFormat;
						remainingLength = message.remainingLength;
					} else {
						messageType = MessageIO.readMessageType(buffer);
						messageFrom = MessageIO.readClientId(buffer);
						messageBodyFormat = MessageIO.readBodyFormat(buffer);
						remainingLength = MessageIO.readRemainingLength(buffer);
					}

					MessageContext context = new MessageContext(base,
							messageFrom);
					handler.onMessage(messageType, messageBodyFormat,
							buffer.array(),
							buffer.arrayOffset() + buffer.position(),
							remainingLength, context);

				} else if (frame instanceof TextFrame) {
					System.out.println(String.format("text message:%s", frame));
				}
			}

			public void onError(WebSocket socket, WebSocketException e) {
				stopPing();
				base.exception = e;
				e.printStackTrace();
			}

			public void onClose(WebSocket socket) {
				stopPing();
				socket.close();
				System.err.println("Closed");
			}
		}, this.protocol);

		((WebSocketImpl) startSocket).setOrigin(this.self);

		if (this.headers != null) {
			for (String h : this.headers.keySet()) {
				((WebSocketImpl) startSocket).getRequestHeader().addHeader(h, this.headers.get(h));
			}
		}
		startSocket.setBlockingMode(false);
		startSocket.connect();

		synchronized (base) {
			base.wait(2000);
		}

		if (!startSocket.isConnected())
			throw this.exception;
		this.socket = startSocket;
		this.reconnectCount++;
		this.doPing();
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
			MessageIO.writeBodyFormat(buffer, messageBodyFormat);
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

		this.delayNextPing();
	}

	private void stopPing() {
		if (this.pingTimer != null) {
			this.pingTimer.cancel();
			this.pingTimer = null;
		}
	}

	private void delayNextPing() {
		pingFlag = true;
	}

	private void doPing() {
		this.stopPing();
		this.pingTimerTask = new TimerTask() {
			public void run() {
				if (!pingFlag)
					ping();
				pingFlag = false;
			}
		};
		Date begin = new Date();
		begin.setTime(new Date().getTime() + maxIdle);
		this.pingTimer = new Timer(true);
		this.pingTimer.schedule(this.pingTimerTask, begin, maxIdle);
	}

	private void ping() {
		if (this.socket == null || !this.socket.isConnected())
			return;
		try {
			PingFrame pingFrame = new PingFrame();
			pingFrame.mask();
			this.socket.send(pingFrame);
			System.out.println("ping#" + this.reconnectCount);
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}

	private void doReconnect() {
		this.reconnecTimerTask = new TimerTask() {
			@Override
			public void run() {
				if (socket != null && !socket.isConnected()) {
					try {
						connect(uri, protocol, headers);
					} catch (WebSocketException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		this.reconnecTimer = new Timer(true);
		this.reconnecTimer.schedule(this.reconnecTimerTask, new Date(),
				this.reconnectInterval);
	}

	// easy buffer pool
	private byte[] getBuffer() {
		byte[] buffer = this.bufferQueue.poll();
		if (buffer == null)
			buffer = new byte[maxMessageSize];
		return buffer;
	}

	private void returnBuffer(byte[] buffer) {
		this.bufferQueue.add(buffer);
	}
}
