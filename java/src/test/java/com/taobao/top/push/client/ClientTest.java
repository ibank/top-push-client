package com.taobao.top.push.client;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

import jp.a840.websocket.exception.WebSocketException;

import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.tmall.top.push.messages.MessageType;

public class ClientTest {
	@Test
	public void connect_test() throws WebSocketException, IOException,
			InterruptedException {
		Client client = new Client("java");
		client.setMaxIdle(100);
		client.connect("ws://localhost:8080/backend");
		client.connect("ws://localhost:8080/backend");
		// Thread.sleep(1000000);
	}

	@Test
	public void pub_confirm_test() throws WebSocketException, IOException,
			InterruptedException {
		pub_confirm_test("java1", "", 100);
	}

	@Test
	public void pub_confirm_mqtt_test() throws WebSocketException, IOException,
			InterruptedException {
		pub_confirm_test("java2", "mqtt", 100);
	}

	private static int count;

	private void pub_confirm_test(final String flag, String protocol,
			final int total) throws WebSocketException, IOException,
			InterruptedException {
		count = 0;
		HashMap<String, String> headers = new HashMap<String, String>();
		headers.put("id", "abc");
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
}