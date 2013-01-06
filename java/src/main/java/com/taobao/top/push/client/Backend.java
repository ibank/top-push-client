package com.taobao.top.push.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.alibaba.fastjson.JSON;
import com.taobao.top.push.client.messages.MessageIO;
import com.taobao.top.push.client.messages.MessageType;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.WebSockets;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.frame.Frame;
import jp.a840.websocket.frame.rfc6455.BinaryFrame;
import jp.a840.websocket.frame.rfc6455.FrameRfc6455;
import jp.a840.websocket.frame.rfc6455.TextFrame;
import jp.a840.websocket.handler.WebSocketHandler;
import jp.a840.websocket.impl.WebSocketImpl;

public class Backend {
	private String self;
	private HashMap<String, Server> servers;
	private MessageHandler handler;

	public Backend(String selfTarget) {
		this.self = selfTarget;
		this.servers = new HashMap<String, Server>();
	}

	public void setMessageHandler(MessageHandler handler) {
		this.handler = handler;
	}

	public void AddServer(String uri) {
		servers.put(uri, null);
	}

	public void getTarget(String target, TargetHandler handler) {
		Iterator<Map.Entry<String, Server>> iter = this.servers.entrySet()
				.iterator();
		while (iter.hasNext()) {
			Server server = iter.next().getValue();
			if (server != null) {
				server.onResponse(new ResponseHandler() {
					@Override
					public void onResponse(Response response) {
						if(response.Result == "true") {
							//onGetServer(conn);
						}
					}
				});
				getTargetOnWhichServer(server.Socket, target, handler);
			}
		}
		// TargetContext context = new TargetContext();
		// context.setTarget(target);
		// context.setSocket(socket);
	}

	private void getTargetOnWhichServer(WebSocket socket, String target,
			TargetHandler handler) {
		FrameRfc6455 frame;
		try {
			frame = (FrameRfc6455) socket
					.createFrame("{\"Command\":\"isOnline\", \"Arguments\":{\"id\":\""
							+ target + "\"}}");
			frame.mask();
			socket.send(frame);
		} catch (WebSocketException e) {
			e.printStackTrace();
		}
	}

	private void connect(final String uri) throws WebSocketException,
			IOException {
		final WebSocket socket = WebSockets.create(uri, new WebSocketHandler() {
			public void onOpen(WebSocket socket) {
				servers.put(uri, new Server(socket));
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
					// easy rpc
					Response response = JSON.parseObject(frame.toString(),
							Response.class);
					Server server = servers.get(uri);
					server.raiseResponse(response);
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
	}

	public class Server {
		private ConcurrentLinkedQueue<ResponseHandler> handlers;
		public WebSocket Socket;

		public Server(WebSocket socket) {
			this.Socket = socket;
			this.handlers = new ConcurrentLinkedQueue<Backend.ResponseHandler>();
		}

		public void onResponse(ResponseHandler handler) {
			this.handlers.add(handler);
		}

		public void raiseResponse(Response response) {
			ResponseHandler handler = this.handlers.poll();
			if (handler != null)
				handler.onResponse(response);
		}

	}

	public class Response {
		public boolean IsError;
		public String ErrorPhrase;
		public String Result;
	}

	public abstract class ResponseHandler {
		public abstract void onResponse(Response response);
	}

}
