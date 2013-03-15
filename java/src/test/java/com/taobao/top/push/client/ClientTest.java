package com.taobao.top.push.client;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;

import jp.a840.websocket.MockServer;
import jp.a840.websocket.frame.draft06.PingFrame;
import jp.a840.websocket.frame.rfc6455.CloseFrame;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.taobao.top.push.messages.MessageType;

public class ClientTest {

	private MockServer ms;
	private String uri = "ws://localhost:9999/frontend";

	@Before
	public void startMockServer() {
		ms = new MockServer(9999, 13);
	}

	@After
	public void stopMockServer() throws Exception {
		ms.join(5000);
		Assert.assertFalse(ms.isAlive());
	}

	@Test
	public void connect_test() throws ClientException {
		emptyRequestMock();
		handshakeMock();
		emptyRequestMock();
		ms.start();

		Client client = new Client("java");
		client.connect(uri);
		client.close();
	}

	@Test
	public void header_test() throws ClientException {
		ms.addRequest(new MockServer.VerifyRequest() {
			public void verify(ByteBuffer request) {
				// assert httpheader
			}
		});
		handshakeMock();
		emptyRequestMock();
		ms.start();

		Client client = new Client("java");
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("appkey", "javatest");
		client.connect(uri, "", headers);
		client.close();
	}

	@Test
	public void ping_test() throws ClientException, InterruptedException {
		emptyRequestMock();
		handshakeMock();
		ms.addRequest(new MockServer.VerifyRequest() {
			public void verify(ByteBuffer request) {
				Assert.assertEquals(new PingFrame().toByteBuffer().slice(), request.slice());
			}
		});
		ms.start();

		Client client = new Client("java");
		client.setMaxIdle(500);
		client.connect(uri);
		Thread.sleep(1000);
		client.close();
	}

	@Test(expected = ClientException.class)
	public void connect_fail_error_uri_test() throws ClientException {
		Client client = new Client("java");
		try {
			client.connect("ws://localhost:8889/frontend");
		} catch (ClientException e) {
			e.printStackTrace();
			assertEquals("connect fail", e.getMessage());
			throw e;
		}
	}

	@Test(expected = ClientException.class)
	public void connect_refused_test() throws ClientException {
		emptyRequestMock();
		ms.addResponse(toByteBuffer("HTTP/1.1 401 Not Auth\r\n"));
		ms.start();

		Client client = new Client(null);
		try {
			client.connect(uri);
		} catch (ClientException e) {
			e.printStackTrace();
			assertEquals("connect fail", e.getMessage());
			throw e;
		}
	}

	// @Test(expected = ClientException.class)
	public void connect_timeout_test() throws ClientException {
		ms.addRequest(new MockServer.VerifyRequest() {
			public void verify(ByteBuffer request) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
				}
			}
		});
		ms.start();

		Client client = new Client(null);
		// not well support timeout for websocket handshake, just tcp connect
		// timeout
		client.setConnectTimeout(2);
		try {
			client.connect(uri);
		} catch (ClientException e) {
			e.printStackTrace();
			assertEquals("connect fail", e.getMessage());
			throw e;
		}
	}

	@Test
	public void reconnect_test() throws Exception {
		final Object obj = new Object();
		emptyRequestMock();
		handshakeMock();
		ms.addResponse(new CloseFrame().toByteBuffer());
		ms.addRequest(new MockServer.VerifyRequest() {
			public void verify(ByteBuffer request) {
				System.out.println("reconnect");
				synchronized (obj) {
					obj.notify();
				}
			}
		});
		ms.start();

		final Client client = new Client("java");
		client.enableReconnect(500);
		client.connect(uri);

		// restart server
		// stopMockServer();
		// startMockServer();
		//
		// ms.addRequest(new MockServer.VerifyRequest() {
		// public void verify(ByteBuffer request) {
		// synchronized (client) {
		// client.notify();
		// }
		// }
		// });
		// handshakeMock();
		// ms.start();

		synchronized (obj) {
			obj.wait();
		}
	}

	@Test
	public void pub_confirm_test() throws ClientException, InterruptedException {
		pub_confirm_test("java1", "", 100);
	}

	@Test
	public void pub_confirm_mqtt_test() throws ClientException, InterruptedException {
		pub_confirm_test("java2", "mqtt", 100);
	}

	private static int count;

	private void pub_confirm_test(final String flag, String protocol,
			final int total) throws ClientException, InterruptedException {
		count = 0;
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("id", flag);
		Client client = new Client(flag);
		client.connect("ws://localhost:8080/backend", protocol, headers);
		client.setMessageHandler(new MessageHandler() {
			@Override
			public void onMessage(int messageType, int bodyFormat,
					byte[] messageBody, int offset, int length,
					MessageContext context) {
				if (messageType == MessageType.PUBLISH) {
					// get publish message
					String json = new String(messageBody, offset, length,
							Charset.forName("UTF-8"));
					PublishMessage pub = JSON.parseObject(json,
							PublishMessage.class);
					System.out.println(String.format(
							"get publish message: %s | %s", json, pub));
					// reply confirm message
					ConfirmMessage confirm = new ConfirmMessage();
					confirm.MessageId = pub.MessageId;
					byte[] body = JSON.toJSONString(confirm).getBytes(
							Charset.forName("UTF-8"));
					context.reply(MessageType.PUBCONFIRM,
							MessageBodyFormat.JSON, body, 0, body.length);
				} else if (messageType == MessageType.PUBCONFIRM) {
					// get confirm message
					String json = new String(messageBody, offset, length,
							Charset.forName("UTF-8"));
					ConfirmMessage confirm = JSON.parseObject(json,
							ConfirmMessage.class);
					System.out.println(String.format(
							"get confirm message: %s | %s", json, confirm));

					if (++ClientTest.count == total) {
						synchronized (flag) {
							flag.notify();
						}
					}
				} else {
					System.err
							.println("NotSupport MessageType: " + messageType);
				}
			}
		});

		Thread.sleep(2000);

		for (int i = 0; i < total; i++) {
			PublishMessage pub = new PublishMessage();
			pub.MessageId = "20130104-" + i;
			pub.Content = "hello world! " + i;
			byte[] body = JSON.toJSONString(pub).getBytes(
					Charset.forName("UTF-8"));
			// send message to self
			client.sendMessage(flag, MessageType.PUBLISH,
					MessageBodyFormat.JSON, body, 0, body.length);
		}

		synchronized (flag) {
			flag.wait();
		}
	}

	private void emptyRequestMock() {
		ms.addRequest(new MockServer.VerifyRequest() {
			public void verify(ByteBuffer request) {
			}
		});
	}

	private void handshakeMock() {
		ms.addResponse(toByteBuffer(
				"HTTP/1.1 101 Switching Protocols\r\n" +
						"Upgrade: websocket\r\n" +
						"Connection: Upgrade\r\n" +
						"Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n" +
						"Sec-WebSocket-Protocol: \r\n\r\n"));
	}

	private ByteBuffer toByteBuffer(String str) {
		return ByteBuffer.wrap(str.getBytes());
	}
}