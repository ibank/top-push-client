/*
    (The MIT License)

    Copyright (C) 2012 wsky (wskyhx at gmail.com) and other contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

/*

//message format
var message={
	messageType: 1,
	from: '', //receiving
	to: '', //sending
	remainingLength: 100,
	body: {}
}

//easy-rpc format
var request = { Command: 'isOnline', Arguments: { id: 'targetId' }};
var response = { IsError: false, ErrorPhase: '', Result: '' };


//usage

backend('yourId', 'ws://localhost/backend').getTarget(
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
*/

var	EventEmitter = require('events').EventEmitter,
	websocket = require('websocket').client,
	PING_INTERVAL = 60000,
	SIZE_MSG = 1024,
	PROTOCOL = 'mqtt',
	ENCODING = 'utf-8',
	MessageType = { PUBLISH: 1, PUBCONFIRM: 2 };

module.export.backend = function(origin, uris) {
	var e = endpoint(origin);
	e.getTarget = function(target, onFind) {
		getTargetOnWhichServer(target, function(connection) {
			//you can sendMessage, after server found
			onFind({
				sendMessage: function(messageBody){
					connection.sendMessage(writeMessage({
						messageType: MessageType.PUBLISH,
						to: target
						body: messageBody
					}, getBuffer()));
				}
			});
		});
	};
	
	//multi-server for polling
	var connections = [uris.length];//TODO:design LRU
	function getTargetOnWhichServer(target, onGetServer) {
		for(var i = 0; i< connections.length; i++) {
			var conn = connections[i];
			e.once('response', function(response) {
				if(response.Result == 'true') {
					onGetServer(conn);
				}
			});
			conn.sendUTF(JSON.stringify(createIsOnlineRequest(target)));
		}
	}
	//easy rpc request
	function createIsOnlineRequest(target) {
		return { Command: 'isOnline', Arguments: { id: target }};
	}

	for(var i = 0; i< uris.length; i++) {
		(function(j){
			ws(
				function(connection) {
					connections[j] = connection;
				},
				function(message) {
					if(message.type == 'utf8') {
						//easy rpc response
						e.emit('response', JSON.parse(message.utf8Data));
					} else if(message.type == 'binary') {
						var msg = readMessage(message.binaryData);
						if(msg.messageType == MessageType.PUBCONFIRM)
							e.emit('confirm', msg.body);
					}
				}
			).connect(uris[j], PROTOCOL, origin);
		})(i);
	}
	return e;
}

module.export.frontend = function(origin, uri) {
	var e = endpoint(origin);
	var conn;
	var queue = {
		offset: 0,
		size: 50,
		array: new Array(50),
		add: function(i) { this.array[this.offset++] = i; },
		clear: function() { this.offset = 0; },
		isFull: function() { return this.offset == this.size; }
	};

	ws(
		function(connection) {
			conn = connection;
			doConfirm();
		},
		function(message) {
			if(message.type == 'binary') {
				var msg = readMessage(message.binaryData);

				if(msg.messageType != MessageType.PUBLISH) 
					return;

				e.emit('message', {
					context: {
						message: msg.body,
						confirm: function() {
							doConfirm();
							if(queue.isFull())
								batchConfirm();

							queue.add({
								to: msg.from
								id: msg.body.MessageId
							});
						}
					}
				});
			}
		}
	).connect(uri, PROTOCOL, origin);

	//TODO:do reconnect in timer loop?
	var timer;
	function doConfirm() {
		if(timer != null)
			clearTimeout(timer);
		timer = setTimeout(function() {
			batchConfirm();
			doConfirm();
		}, 1000);
	}
	function batchConfirm() {
		var temp = {};
		for(var i = 0; i <= queue.offset; i++) {
			var item = queue.array[i];
			if(item == null) continue;
			if(!temp[item.to])
				temp[item.to] = [];
			temp[item.to] = temp[item.to].concat([ item.id ]);
		}
		queue.clear();

		for(var to in temp) {
			conn.sendMessage(writeMessage({
				messageType: MessageType.PUBCONFIRM,
				to: to
				body: temp[to];
			}));
		}
	}

	return e;
}

function endpoint(id) {
	var e = { 
		id: id, 
		emiter: new EventEmitter() 
	};
	e.emit = function(event, argument) { this.emiter.emit(event, argument); };
	e.on = function(event, callback) { this.emiter.on(event, callback); };
	e.once = function(event, callback) { this.emiter.once(event, callback); };
	return e;
}

function ws(onConnect, onMessage) {
	var client = new websocket({
    	//https://github.com/Worlize/WebSocket-Node/blob/master/lib/WebSocketClient.js
    	//default is 16k
		fragmentationThreshold: SIZE_MSG
		//websocketVersion: 8
	});

    client.on('connectFailed', function(error) { 
        console.log('Connect Failed: %s', error.toString()); 
    });

    client.on('connect', function(connection) {
        console.log('WebSocket client connected');

        connection.on('error', function(error) {
        	stopPing();
        	console.log("Connection Error: %s", error.toString()); 
        });
        
        connection.on('close', function() { stopPing(); });

        connection.on('message', function(message) {
        	doPing();

    		if(onMessage)
    			onMessage(message);
        });

        if(onConnect) {
        	connection.sendMessage = function(data) {
        		doPing();
        		connection.sendBytes(data);
        	}
        	onConnect(connection);
        }

        doPing();
    });

    var timer;
    function doPing() {
    	stopPing();
    	timer = setTimeout(function() {
    		try {
				connection.ping();
    		} catch(e) {
				console.log(e);
    		}
        	doPing();
    	}, PING_INTERVAL);
    }
    function stopPing() {
		if(timer)
			clearTimeout(timer);
    }

    return client;
}

//TODO:buffer pool
function getBuffer() {
	return new Buffer(SIZE_MSG);
}
//message protocol refer to
//https://github.com/wsky/top-push/issues/13
//0,1-8,9-12,13-N
function writeMessage(message, buffer) {
	var type = new Buffer([message.messageType]);
	var to = new Buffer(message.to, ENCODING);
	var body = new Buffer(JSON.stringify(message.body), ENCODING);
	message.remainingLength = body.length;
	buffer.fill(type, 0, 1);
	buffer.fill(to, 1, 9);
	buffer.writeInt32LE(message.remainingLength, 9);
	buffer.fill(body, 13, 13 + message.remainingLength);
	return buffer;
}
function readMessage(buffer) {
	var msg = {};
	msg.messageType = buffer[0];
	msg.from = buffer.toString(ENCODING, 1, 9);
	msg.remainingLength = buffer.readInt32LE(9);
	msg.body = JSON.parse(buffer.toString(ENCODING, 13, 13 + msg.remainingLength));
	return msg;
}