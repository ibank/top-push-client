/*
    (The MIT License)

    Copyright (C) 2012 wsky (wskyhx at gmail.com) and other contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

var top 		= require('./lib/top-push-client'),
	client 		= top.client,
	flag		= 'sender',
	MessageType = { PUBLISH: 1, PUBCONFIRM: 2 };


client(flag, 'ws://localhost:8080/backend')
	.on('connect', function(context) {
		setTimeout(function(){
			setInterval(function(){
				// send to self
				for(var i = 0; i < 100; i++) {
					context.sendMessage(
						'receiver', 
						MessageType.PUBLISH, 
						{ MessageId: "20130104" + i });
				}
			}, 10);
			
		}, 2000);
	})
	.on('message', function(context) {
		var msg = context.message;

		if(context.messageType == MessageType.PUBCONFIRM) {
			//console.log('---- receive confirm ----');
			//console.log(msg);
			//process.exit();
		}
	});