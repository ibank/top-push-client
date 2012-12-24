/*
    (The MIT License)

    Copyright (C) 2012 wsky (wskyhx at gmail.com) and other contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
var	EventEmitter = require('events').EventEmitter,
	websocket = require('websocket').client,
	PING_INTERVAL = 60000,
	SIZE_MSG = 1024,
	PROTOCOL = 'mqtt',

module.export.backend = function(origin, uris) {
	var e = {};

	e.getTarget = function(target, onFind) {
		getTargetOnWhichServer(target, function(connection) {
			onFind({
				sendMessage: function(message){
					//TODO: parse to buffer
					var buffer;
					connection.sendBytes(buffer);
				}
			});
		});
	};
	
	//multi-server
	var connections = [uris.length];//TODO:design LRU
	function getTargetOnWhichServer(target, onGetServer) {
		for(var i = 0; i< connections.length; i++) {
			var conn = connections[i];
			conn.once('response', function(response) {
				if(response.result == 'true') {
					onGetServer(conn);
				}
			});
			conn.sendUTF(JSON.stringify(createIsOnlineRequest(target)));
		}
	}
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
						//easy rpc
						this.emit('response', JSON.parse(message.utf8Data));
					} else if(message.type == 'binary') {
						var buffer = message.binaryData;
						//TODO:parse message from buffer
						var confirm = {};
						this.emit('confirm', confirm);
					}
				}
			).connect(uris[i], PROTOCOL, origin);
		})(i);
	}
	return e;
}

module.export.frontend = function() {
	var context = {};
	context.confirm = function(message) {};
	var e = {};
	//e.onMessage = function(message, context) {}
	//e.onError = function(error){}
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

        if(onConnect)
        	onConnect(connection);

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