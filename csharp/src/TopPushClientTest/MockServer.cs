using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace TopPushClientTest
{
    /// <summary>TCP Mock Server for testing
    /// </summary>
    public class MockServer
    {
        private int _port;
        private TcpListener _listener;

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

        public void Start()
        {
            this._listener = new TcpListener(IPAddress.Parse("127.0.0.1"), this._port);
            this._listener.Start();
            this._listener.BeginAcceptTcpClient(o =>
            {
                TcpClient client = null;
                try
                {
                    client = this._listener.EndAcceptTcpClient(o);
                }
                catch (Exception e)
                {
                    Console.WriteLine(e.Message);
                }

                if (client == null) return;

                using (var stream = client.GetStream())
                {
                    while (true)
                    {
                        var requestHandle = this._requestHandles.Count > 0
                            ? this._requestHandles.Dequeue()
                            : null;

                        if (requestHandle == null)
                            break;

                        byte[] request = new byte[1024 * 4];
                        int read = stream.Read(request, 0, request.Length);

                        //TODO:DUMP
                        Console.WriteLine("[MockServer] Packet DUMP:" + read);

                        if (requestHandle != null)
                            requestHandle(request);

                        var response = this._response.Count > 0
                            ? this._response.Dequeue()
                            : null;
                        if (response != null)
                            stream.Write(response, 0, response.Length);
                    }
                }
            }, null);
        }
        public void Stop()
        {
            this._listener.Stop();
        }
    }
}