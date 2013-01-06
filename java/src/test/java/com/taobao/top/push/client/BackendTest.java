package com.taobao.top.push.client;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.junit.Test;

import com.alibaba.fastjson.JSON;
import com.taobao.top.push.client.messages.MessageType;

public class BackendTest {
	@Test
	public void get_target_and_send_test() throws UnsupportedEncodingException {
		Backend backend = new Backend("javaback");
		backend.AddServer("ws://localhost:8080/backend");

		backend.getTarget("javafront", new TargetHandler() {
			@Override
			public void onFind(TargetContext target) {
				// present business layer message
				PublishMessage pub = new PublishMessage();
				pub.MessageId = "20130104";
				pub.Content = "hello world!";
				
				byte[] body = JSON.toJSONString(pub).getBytes(
						Charset.forName("UTF-8"));
				
				target.sendMessage(MessageType.PUBLISH, "json", body, 0,
						body.length);
			}
		});
	}

	class PublishMessage {
		public String MessageId;
		public String Content;
	}
}