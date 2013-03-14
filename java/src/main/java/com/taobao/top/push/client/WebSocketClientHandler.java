package com.taobao.top.push.client;

import java.nio.ByteBuffer;

import com.taobao.top.push.Logger;
import com.taobao.top.push.LoggerFactory;
import com.taobao.top.push.messages.MessageIO;
import com.taobao.top.push.mqtt.MqttMessageIO;
import com.taobao.top.push.mqtt.MqttMessageType;
import com.taobao.top.push.mqtt.publish.MqttPublishMessage;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.frame.Frame;
import jp.a840.websocket.frame.rfc6455.BinaryFrame;
import jp.a840.websocket.frame.rfc6455.TextFrame;
import jp.a840.websocket.handler.WebSocketHandler;

public class WebSocketClientHandler implements WebSocketHandler {
	private LoggerFactory loggerFactory;
	private Logger logger;
	private Client client;
	private String protocol;

	public WebSocketClientHandler(LoggerFactory loggerFactory, Client client, String protocol) {
		this.loggerFactory = loggerFactory;
		this.logger = this.loggerFactory.create(this);
		this.client = client;
		this.protocol = protocol;
	}

	public void onOpen(WebSocket socket) {
		this.logger.info("websocket open");
		this.client.setSocket(socket);
	}

	public void onError(WebSocket socket, WebSocketException e) {
		this.client.stopPing();
		this.client.setFailure(e);
		this.logger.error("websocket error", e);

		if (this.client.getStateHandler() != null)
			this.client.getStateHandler().exceptionCaught(e);
	}

	public void onClose(WebSocket socket) {
		this.client.stopPing();
		socket.close();
		this.logger.warn("websocket closed");
	}

	public void onMessage(WebSocket socket, Frame frame) {
		this.client.delayNextPing();

		if (this.client.getMessageHandler() == null)
			return;

		if (frame instanceof BinaryFrame) {
			ByteBuffer buffer = frame.getContents();
			String messageFrom;
			int messageType = 0;
			int messageBodyFormat = 0;
			int remainingLength = 0;

			if ("mqtt".equalsIgnoreCase(protocol)) {
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

			this.client.getMessageHandler().onMessage(messageType,
					messageBodyFormat,
					buffer.array(),
					buffer.arrayOffset() + buffer.position(),
					remainingLength,
					new MessageContext(this.loggerFactory, this.client, messageFrom));

		} else if (frame instanceof TextFrame) {
			this.logger.info("text message: %s", frame);
		}
	}
}
