package com.taobao.top.push.client;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.taobao.top.push.DefaultLoggerFactory;
import com.taobao.top.push.Logger;
import com.taobao.top.push.LoggerFactory;
import com.taobao.top.push.messages.MessageIO;
import com.taobao.top.push.mqtt.MqttMessageIO;
import com.taobao.top.push.mqtt.publish.MqttPublishMessage;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.WebSockets;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.frame.rfc6455.FrameRfc6455;
import jp.a840.websocket.frame.rfc6455.PingFrame;
import jp.a840.websocket.impl.WebSocketImpl;

public class Client {
	private final static String MQTT = "mqtt";
	private int maxMessageSize = 1024;
	// heartbeat ping idle
	private int maxIdle = 60000;
	private LoggerFactory loggerFactory;
	private Logger logger;

	private String uri;
	private String protocol;
	private String self;
	private HashMap<String, String> headers;
	private WebSocket socket;
	private MessageHandler messageHandler;
	private StateHandler stateHandler;

	private ConcurrentLinkedQueue<byte[]> bufferQueue;

	private boolean pingFlag;
	private Timer pingTimer;
	private TimerTask pingTimerTask;

	private Exception failure;
	private int reconnectInterval = 5000;
	private int reconnectCount;
	private Timer reconnecTimer;
	private TimerTask reconnecTimerTask;

	public Client(String clientFlag) {
		this(new DefaultLoggerFactory(), clientFlag);
	}

	public Client(LoggerFactory loggerFactory, String clientFlag) {
		this.loggerFactory = loggerFactory;
		this.logger = this.loggerFactory.create(this);

		this.self = clientFlag;
		this.bufferQueue = new ConcurrentLinkedQueue<byte[]>();
		// necessary?
		this.doReconnect();
	}

	protected MessageHandler getMessageHandler() {
		return this.messageHandler;
	}

	protected StateHandler getStateHandler() {
		return this.stateHandler;
	}

	protected void setFailure(Exception failure) {
		this.failure = failure;
	}

	public void setMaxIdle(int maxIdle) {
		this.maxIdle = maxIdle;
	}

	public void setMaxMessageSize(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	public void setMessageHandler(MessageHandler handler) {
		this.messageHandler = handler;
	}

	public void setStateHandler() {

	}

	public Client connect(String uri) throws ClientException {
		return this.connect(uri, "", null);
	}

	public Client connect(String uri, HashMap<String, String> headers) throws ClientException {
		return this.connect(uri, "", headers);
	}

	public Client connect(String uri, String messageProtocol) throws ClientException {
		return this.connect(uri, messageProtocol, null);
	}

	public Client connect(String uri, String messageProtocol, HashMap<String, String> headers) throws ClientException {
		this.stopPing();
		this.failure = null;
		this.uri = uri;
		this.protocol = messageProtocol;
		this.headers = headers;

		WebSocket startSocket = null;
		try {
			startSocket = WebSockets.create(uri,
					new WebSocketClientHandler(this.loggerFactory, this, this.protocol),
					this.protocol);
			((WebSocketImpl) startSocket).setOrigin(this.self);
			if (this.headers != null) {
				for (String h : this.headers.keySet()) {
					((WebSocketImpl) startSocket).getRequestHeader().addHeader(h, this.headers.get(h));
				}
			}
			startSocket.setBlockingMode(false);
			// startSocket.connect(); is sync
			// https://github.com/wsky/top-push-client/issues/20
			startSocket.connect();
		} catch (Exception e) {
			throw new ClientException("error while connecting", e);
		}
		
		if (this.failure != null)
			throw new ClientException("connect fail", this.failure);

		this.socket = startSocket;
		this.reconnectCount++;

		this.doPing();
		this.logger.info("connected to server %s", uri);

		return this;
	}

	public void sendMessage(String to, int messageType, 
			int messageBodyFormat, byte[] messageBody, int offset, int length) throws ClientException {
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
			throw new ClientException("error while sending", e);
		} finally {
			this.returnBuffer(back);
		}

		this.delayNextPing();
	}

	protected void stopPing() {
		if (this.pingTimer != null) {
			this.pingTimer.cancel();
			this.pingTimer = null;
		}
	}

	protected void delayNextPing() {
		pingFlag = true;
	}

	protected void doPing() {
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

	protected void ping() {
		if (this.socket == null || !this.socket.isConnected())
			return;
		try {
			PingFrame pingFrame = new PingFrame();
			pingFrame.mask();
			this.socket.send(pingFrame);
			if (this.logger.isDebugEnable())
				this.logger.debug("ping#" + this.reconnectCount);
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
					} catch (ClientException e) {
						logger.error("error while reconnecting", e);
					}
				}
			}
		};
		this.reconnecTimer = new Timer(true);
		this.reconnecTimer.schedule(
				this.reconnecTimerTask, new Date(), this.reconnectInterval);
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
