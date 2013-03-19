using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using NUnit.Framework;
using TopPushClient;
using WebSocketSharp.Frame;

namespace TopPushClientTest
{
    [TestFixture]
    public class ClientTest
    {
        private string uri = "ws://127.0.0.1:8889/";
        private MockServer ms;
        [SetUp]
        public void StartMock()
        {
            this.ms = new MockServer(8889);
            this.ms.Start();
        }
        [TearDown]
        public void StopMock()
        {
            this.ms.Stop();
        }

        [Test]
        public void ConnectTest()
        {
            AddHandshake();
            Client client = new Client("csharp");
            client.Connect(uri);
        }

        [Test]
        [ExpectedException]
        public void ConnectErrorTest_RemoteDown()
        {
            Client client = new Client("csharp");
            client.Connect("ws://127.0.0.1:8000/");
        }

        [Test]
        [ExpectedException]
        public void ConnectErrorTest_HandshakeError()
        {
            ms.AddRequestHandle(o => { });
            ms.AddResponse(Encoding.ASCII.GetBytes(
                "HTTP/1.1 401 Auth Fail\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: 123\r\n" +
                "Sec-WebSocket-Protocol: chat\r\n\r\n"));
            Client client = new Client("csharp");
            client.Connect(uri);
        }

        //timeout not support in this client impl
        //[Test]
        //[ExpectedException]
        //public void ConnectTimeoutTest()
        //{
        //    ms.AddRequestHandle(o => Thread.Sleep(3000));
        //    Client client = new Client("csharp");
        //    client.SetConnectTimeout(1000);
        //    client.Connect(uri);
        //}

        [Test]
        public void PingTest()
        {
            var handle = new EventWaitHandle(false, EventResetMode.AutoReset);
            AddHandshake();
            ms.AddRequestHandle(o =>
            {
                //got a ping
                var ping = WsFrame.Parse(o);
                Assert.AreEqual(Opcode.PING, ping.Opcode);
                handle.Set();
            });
            Client client = new Client("csharp");
            client.SetMaxIdle(500);
            client.Connect(uri);
            Assert.IsTrue(handle.WaitOne(5000));
        }

        [Test]
        public void ReconnectTest()
        {
            AddHandshake();
            Client client = new Client("csharp");
            client.SetReconnectInterval(500);
            client.Connect(uri);

            StopMock();
            StartMock();
            var handle = new EventWaitHandle(false, EventResetMode.AutoReset);
            AddHandshake(o => handle.Set());
            Assert.IsTrue(handle.WaitOne(3000));
        }

        //send->receive->reply
        [Test]
        public void SendReceiveTest()
        {
            byte[] data = Encoding.ASCII.GetBytes("hello");
            var handle = new EventWaitHandle(false, EventResetMode.AutoReset);
            AddHandshake();
            ms.AddRequestHandle(o =>
            {
                ms.AddResponse(o);
                ms.AddRequestHandle(p =>
                {
                    var frame = WsFrame.Parse(p);
                    Assert.AreEqual(Opcode.BINARY, frame.Opcode);
                    handle.Set();
                });
            });

            Client client = new Client("csharp");
            client.SetMessageHandler(new TestMessageHandler());
            client.Connect(uri);
            client.SendMessage("to", 1, 1, data, 0, data.Length);
            Assert.IsTrue(handle.WaitOne(3000));
        }

        private class TestMessageHandler : MessageHandler
        {
            public override void OnMessage(int messageType,
                int bodyFormat,
                byte[] messageBody,
                int offset,
                int length,
                MessageContext context)
            {
                Console.WriteLine("-------onMessage");
                context.Reply(messageType, bodyFormat, messageBody, offset, length);
            }
        }

        private void AddHandshake() { this.AddHandshake(null); }
        private void AddHandshake(Action<byte[]> func)
        {
            ms.AddRequestHandle(o =>
            {
                if (func != null)
                    func(o);

                var key = string.Empty;
                using (var reader = new StreamReader(new MemoryStream(o)))
                {
                    reader.ReadLine();
                    var httpRequest = reader.ReadToEnd();
                    foreach (var l in httpRequest.Split('\n'))
                    {
                        var arr = l.Split(':');
                        if (arr[0].Trim().Equals("Sec-WebSocket-Key"))
                            key = arr[1].Trim();
                    }
                }

                ms.AddResponse(Encoding.ASCII.GetBytes(
                    "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + createResponseKey(key) + "\r\n" +
                    "Sec-WebSocket-Protocol: chat\r\n\r\n"));
            });
        }
        //refer to websocket-sharp websocket.cs
        private string createResponseKey(string key)
        {
            SHA1 sha1 = new SHA1CryptoServiceProvider();
            var sb = new StringBuilder(key);
            sb.Append("258EAFA5-E914-47DA-95CA-C5AB0DC85B11");
            var src = sha1.ComputeHash(Encoding.UTF8.GetBytes(sb.ToString()));
            return Convert.ToBase64String(src);
        }
    }
}