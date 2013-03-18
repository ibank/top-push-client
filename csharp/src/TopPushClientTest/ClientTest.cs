using System;
using System.Collections.Generic;
using System.Text;
using NUnit.Framework;
using TopPushClient;

namespace TopPushClientTest
{
    [TestFixture]
    public class ClientTest
    {
        public void ConnectTest()
        {
            Client client = new Client("csharp");
            client.Connect("ws://localhost:8080/");
        }

        [Test]
        [ExpectedException]
        public void ConnectErrorTest()
        {
            Client client = new Client("csharp");
            client.Connect("ws://localhost:8080/");
        }
        public void ConnectTimeoutTest() { }
        public void PingTest() { }
        public void ReconnectTest() { }
        public void SendReceiveTest() { }
    }
}