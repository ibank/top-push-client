using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using NUnit.Framework;
using TopPushClient;

namespace TopPushClientTest
{
    [TestFixture]
    public class ClientTest
    {
        private string uri = "ws://localhost:8889/";
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
            ms.AddRequestHandle(o => { });
            ms.AddResponse(null);
            Client client = new Client("csharp");
            client.Connect(uri);
        }

        [Test]
        [ExpectedException]
        public void ConnectErrorTest()
        {
            Client client = new Client("csharp");
            client.Connect("ws://localhost:8080/");
        }
        [Test]
        [ExpectedException]
        public void ConnectTimeoutTest()
        {
            ms.AddRequestHandle(o => Thread.Sleep(3000));
            Client client = new Client("csharp");
            client.SetConnectTimeout(1000);
            client.Connect(uri);
        }
        public void PingTest() { }
        public void ReconnectTest() { }
        public void SendReceiveTest() { }

        //TODO:mock server
        public class MockServer
        {
            private int _port;
            private Queue<Action<byte[]>> _requestHandles;
            private Queue<byte[]> _response;

            public MockServer(int port)
            {
                this._port = port;
                this._requestHandles = new Queue<Action<byte[]>>();
                this._response = new Queue<byte[]>();
            }

            public void AddRequestHandle(Action<byte[]> handle)
            {
                this._requestHandles.Enqueue(handle);
            }
            public void AddResponse(byte[] data)
            {
                this._response.Enqueue(data);
            }

            public void Start() { }
            public void Stop() { }
        }
    }
}