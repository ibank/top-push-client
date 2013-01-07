package com.taobao.top.push.client;

import java.io.IOException;
import java.nio.charset.Charset;

import jp.a840.websocket.exception.WebSocketException;

import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.tmall.top.push.messages.MessageType;

public class ClientTest {
	@Test
	public void get_target_and_send_test() throws WebSocketException,
			IOException, InterruptedException {
		// TODO:run server for test

		Client client = new Client("javaback");
		client.connect("ws://localhost:8080/backend");

		PublishMessage pub = new PublishMessage();
		pub.MessageId = "20130104";
		pub.Content = "hello world!";
		byte[] body = JSON.toJSONString(pub).getBytes(Charset.forName("UTF-8"));

		// send message to target client
		client.sendMessage("front", MessageType.PUBLISH, "json", body, 0,
				body.length);
	}

	// present business layer message
	class PublishMessage {
		public String MessageId;
		public String Content;
	}
}