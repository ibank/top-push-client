package com.taobao.top.push.client;

import java.io.IOException;

import jp.a840.websocket.WebSocket;
import jp.a840.websocket.WebSockets;
import jp.a840.websocket.exception.WebSocketException;
import jp.a840.websocket.handler.WebSocketHandler;
import jp.a840.websocket.impl.WebSocketImpl;
import jp.a840.websocket.frame.Frame;
import jp.a840.websocket.frame.rfc6455.FrameRfc6455;

import org.junit.Test;

public class WebSocketClientTest {
	@Test
	public void jp_a840_websocket_text_test() throws WebSocketException,
			IOException, InterruptedException {
		final Object lockObject = new Object();
		final WebSocket socket = WebSockets.create(
				"ws://localhost:8080/backend", new WebSocketHandler() {
					public void onOpen(WebSocket socket) {
						try {
							FrameRfc6455 frame = (FrameRfc6455) socket
									.createFrame("{\"Command\":\"isOnline\", \"Arguments\":{\"id\":\"test\"}}");
							// client to server always mask
							// https://github.com/wsky/top-push-client/issues/3
							frame.mask();
							socket.send(frame);
						} catch (WebSocketException e) {
							e.printStackTrace();
						}
					}

					public void onMessage(WebSocket socket, Frame frame) {
						System.out.println("onMessage: " + frame);
						synchronized (lockObject) {
							lockObject.notify();
						}
					}

					public void onError(WebSocket socket, WebSocketException e) {
						e.printStackTrace();
					}

					public void onClose(WebSocket socket) {
						System.err.println("Closed");
					}
				});
		((WebSocketImpl) socket).setOrigin("java");
		socket.setBlockingMode(false);
		socket.connect();

		synchronized (lockObject) {
			lockObject.wait();
		}
		socket.close();
	}

	@Test
	public void jp_a840_websocket_binary_test() throws WebSocketException,
			IOException, InterruptedException {
		final Object lockObject = new Object();
		final WebSocket socket = WebSockets.create(
				"ws://localhost:8080/backend", new WebSocketHandler() {
					public void onOpen(WebSocket socket) {
						try {
							FrameRfc6455 frame = (FrameRfc6455) socket
									.createFrame(new byte[] { 0 });
							// client to server always mask
							// https://github.com/wsky/top-push-client/issues/3
							frame.mask();
							socket.send(frame);
						} catch (WebSocketException e) {
							e.printStackTrace();
						}
					}

					public void onMessage(WebSocket socket, Frame frame) {
						System.out.println("onMessage: " + frame);
						synchronized (lockObject) {
							lockObject.notify();
						}
					}

					public void onError(WebSocket socket, WebSocketException e) {
						e.printStackTrace();
					}

					public void onClose(WebSocket socket) {
						System.err.println("Closed");
					}
				});
		((WebSocketImpl) socket).setOrigin("java");
		socket.setBlockingMode(false);
		socket.connect();

		synchronized (lockObject) {
			lockObject.wait();
		}
		socket.close();
	}
}
