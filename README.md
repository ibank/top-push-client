# top-push-client

===============

top push client, can pub/confirm via [top-push](https://github.com/wsky/top-push)

## Support

- java

extra support MQTT

```java
Client client = new Client(flag);
client.connect("ws://localhost:8080/server", 'mqtt');
client.setMessageHandler(new MessageHandler() {
	@Override
	public void onMessage(int messageType, 
		int bodyFormat, 
		byte[] messageBody, 
		int offset, 
		int length, 
		MessageContext context) {
		if (messageType == MessageType.PUBLISH) {
			// get publish message
			String json = new String(messageBody, offset, length, Charset.forName("UTF-8"));
			PublishMessage pub = JSON.parseObject(json, PublishMessage.class);

			System.out.println(String.format("get publish message: %s | %s", json, pub));
			
			// reply confirm message
			ConfirmMessage confirm = new ConfirmMessage();
			confirm.MessageId = pub.MessageId;
			byte[] body = JSON.toJSONString(confirm).getBytes(Charset.forName("UTF-8"));

			context.reply(MessageType.PUBCONFIRM, MessageBodyFormat.JSON, body, 0, body.length);

		} else if (messageType == MessageType.PUBCONFIRM) {
			// get confirm message
			String json = new String(messageBody, offset, length, Charset.forName("UTF-8"));
			ConfirmMessage confirm = JSON.parseObject(json, ConfirmMessage.class);
	}
});
```

- c#(coming soon)

- javascript(nodejs)

```js
client('your flag', 'ws://localhost:8080/backend')
	.on('connect', function(context) {
		// send to self
		context.sendMessage(
			'target client flag', 
			MessageType.PUBLISH, 
			{ MessageId: "20130104", Content: "hello world! " });
	})
	.on('message', function(context) {
		var msg = context.message;
		
		if(context.messageType == MessageType.PUBLISH) {
			console.log('---- receive publish ----');
			console.log(msg);
			context.reply(MessageType.PUBCONFIRM, { MessageId: msg.MessageId });
		}

		if(context.messageType == MessageType.PUBCONFIRM) {
			console.log('---- receive confirm ----');
			console.log(msg);
			process.exit();
		}
	});
```

## License

	Licensed under the Apache License, Version 2.0 (the "License");

	you may not use this file except in compliance with the License.

	You may obtain a copy of the License at

	     http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, 

	software distributed under the License is distributed on an "AS IS" BASIS, 

	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

	See the License for the specific language governing permissions and limitations under the License.


- WebSocket-Node Apache License Version 2.0

	https://github.com/Worlize/WebSocket-Node

	https://github.com/Worlize/WebSocket-Node/blob/master/LICENSE

- fastjson Java JSON-processor Apache License Version 2.0

	http://code.alibabatech.com/wiki/display/FastJSON/Home

- jp.a840.websocket.websocket-client MIT License

	https://github.com/hashio/websocket-client

	https://github.com/hashio/websocket-client/blob/master/LICENSE

	https://github.com/wsky/websocket-client


