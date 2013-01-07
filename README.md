# top-push-client

===============

top push client, can pub/confirm via [top-push](https://github.com/wsky/top-push)

## Support

- java

```java
Client client = new Client(flag);
client.connect("ws://localhost:8080/server", protocol);
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
backend('yourId', ['ws://localhost/backend']).getTarget(
	'targetId', 
	function(target) {
		target.sendMessage({
			MessageId: "20121221000000001",
			Content: "hello world!"
		});
	}
).on('confirm', function(confirm) {
	//do something
});

var e = frontend('yourId', 'ws://localhost/frontend');
e.on('message', function(context) {
	var msg = context.message;
	//...
	context.confirm();
});
```

## License

	(The MIT License)

	Copyright (C) 2012 wsky (wskyhx at gmail.com) and other contributors

	Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


- WebSocket-Node Apache License Version 2.0
	https://github.com/Worlize/WebSocket-Node
	https://github.com/Worlize/WebSocket-Node/blob/master/LICENSE

- fastjson Java JSON-processor Apache License Version 2.0
	http://code.alibabatech.com/wiki/display/FastJSON/Home

- jp.a840.websocket.websocket-client MIT License
	https://github.com/hashio/websocket-client
	https://github.com/hashio/websocket-client/blob/master/LICENSE
	https://github.com/wsky/websocket-client


