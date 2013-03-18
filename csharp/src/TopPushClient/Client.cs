using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Timers;
using Nmqtt;
using WebSocketSharp;

namespace TopPushClient
{
    /// <summary>push client
    /// </summary>
    public class Client
    {
        private static readonly string MQTT = "mqtt";
        private int _maxMessageSize = 1024;
        private int _maxIdle = 60000;
        private int _maxTimeout = 5000;

        private string _uri;
        private string _protocol;
        private string _self;
        private IDictionary<string, string> _headers;
        private MessageHandler _handler;
        private WebSocket _socket;

        private bool _pingFlag;
        private Timer _pingTimer;

        private int _reconnectInterval = 5000;
        private int _reconnectCount = 0;
        private Timer _reconnecTimer;

        public Client(string clientFlag)
        {
            this._self = clientFlag;
            this.DoReconnect();
        }

        public void SetMaxIdle(int maxIdle)
        {
            this._maxIdle = maxIdle;
        }
        public void SetConnectTimeout(int connectTimeoutMillisecond)
        {
            this._maxTimeout = connectTimeoutMillisecond;
        }
        public void SetMaxMessageSize(int maxMessageSize)
        {
            this._maxMessageSize = maxMessageSize;
        }
        public void SetMessageHandler(MessageHandler handler)
        {
            this._handler = handler;
        }

        public void Connect(string uri)
        {
            this.Connect(uri, string.Empty, null);
        }
        public void Connect(string uri, IDictionary<string, string> headers)
        {
            this.Connect(uri, string.Empty, headers);
        }
        public void Connect(string uri, string messageProtocol)
        {
            this.Connect(uri, string.Empty, null);
        }
        public void Connect(string uri, string messageProtocol, IDictionary<string, string> headers)
        {
            var locker = new object();
            var error = string.Empty;
            this._uri = uri;
            this._protocol = messageProtocol;
            this._headers = headers;
            this._socket = new WebSocket(this._uri, this._protocol);
            this.PrepareSocket(this._socket, locker);
            this._socket.OnError += (s, e) =>
            {
                this.StopPing();
                error = e.Message;
                Console.WriteLine("Error: {0}", e.Message);
                System.Threading.Monitor.Pulse(locker);
            };

            this._socket.Origin = this._self;
            this._socket.ExtraHeaders = this._headers;
            //connect -> handshake -> validate respone
            this._socket.Connect();
            // connect maybe fast enough
            if (this._socket.ReadyState == WsState.OPEN)
            {
                this.DoPing();
                return;
            }

            var timeout = !System.Threading.Monitor.Wait(locker, this._maxTimeout);
            if (string.IsNullOrEmpty(error))
                throw new Exception("Connect Error: " + error);
            if (this._socket.ReadyState != WsState.OPEN && timeout)
                throw new Exception("Connect Timeout");

            this.DoPing();
        }

        public void Close()
        {
            this.StopPing();
            this.StopReconnect();
            if (this._socket != null)
                this._socket.Close(WebSocketSharp.Frame.CloseStatusCode.NORMAL);
        }

        public void SendMessage(string to
            , int messageType
            , int messageBodyFormat
            , byte[] messageBody
            , int offset
            , int length)
        {
            using (var stream = new MemoryStream())
            using (var writer = new BinaryWriter(stream))
            {
                MessageIO.WriteMessageType(writer, messageType);
                MessageIO.WriteClientId(writer, to);
                MessageIO.WriteBodyFormat(writer, messageBodyFormat);
                MessageIO.WriteRemainingLength(writer, length);
                writer.Write(messageBody, offset, length);
                stream.Seek(0, SeekOrigin.Begin);
                byte[] data = this.ReadBytes(stream);

                if (MQTT.Equals(this._protocol, StringComparison.InvariantCultureIgnoreCase))
                {
                    var confirm = new MqttPublishMessage();
                    confirm.VariableHeader.TopicName = "";
                    confirm.PublishData(data);
                    using (var mqttStream = new MemoryStream())
                    {
                        confirm.WriteTo(mqttStream);
                        mqttStream.Seek(0, SeekOrigin.Begin);
                        data = this.ReadBytes(mqttStream);
                    }
                }

                this._socket.Send(data);
                this.DelayNextPing();
            }
        }

        private void PrepareSocket(WebSocket socket, object locker)
        {
            socket.OnOpen += (s, e) =>
            {
                this._reconnectCount++;
                Console.WriteLine("connected to server {0}", this._uri);
                System.Threading.Monitor.Pulse(locker);
            };
            socket.OnClose += (s, e) =>
            {
                this.StopPing();
                Console.WriteLine("Closed: {0}|{1}", e.Code, e.Reason);
            };
            socket.OnMessage += (s, e) =>
            {
                this.DelayNextPing();

                using (var stream = new MemoryStream(e.RawData))
                {
                    if (MQTT.Equals(this._protocol, StringComparison.InvariantCultureIgnoreCase))
                    {
                        var msg = MqttMessage.CreateFrom(stream);

                        if (!(msg is MqttPublishMessage))
                            return;

                        using (var payload = new MemoryStream())
                        {
                            (msg as MqttPublishMessage).Payload.WriteTo(payload);
                            payload.Seek(0, SeekOrigin.Begin);
                            this.OnMessage(payload);
                        }

                        return;
                    }
                    this.OnMessage(stream);
                }
            };
        }
        private void OnMessage(Stream stream)
        {
            using (var reader = new BinaryReader(stream))
            {
                int messageType = MessageIO.ReadMessageType(reader);
                string messageFrom = MessageIO.ReadClientId(reader);
                int messageBodyFormat = MessageIO.ReadBodyFormat(reader);
                int remainingLength = MessageIO.ReadRemainingLength(reader);

                MessageContext context = new MessageContext(this, messageFrom);
                this._handler.onMessage(messageType
                    , messageBodyFormat
                    , Ext.ReadBytes(stream, stream.Length)
                    , 0
                    , remainingLength
                    , context);
            }
        }
        private void StopPing()
        {
            if (this._pingTimer != null)
                this._pingTimer.Stop();
        }
        private void DelayNextPing()
        {
            _pingFlag = true;
        }
        private void DoPing()
        {
            this.StopPing();
            if (this._pingTimer == null)
            {
                this._pingTimer = new Timer(this._maxIdle);
                this._pingTimer.Elapsed += this.pingTimer_Elapsed;
            }
            this._pingTimer.Start();
        }
        private void Ping()
        {
            if (this._socket == null || this._socket.ReadyState != WsState.OPEN)
                return;
            try
            {
                this._socket.Ping();
                Console.WriteLine("ping#" + this._reconnectCount);
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        }
        private void pingTimer_Elapsed(object sender, ElapsedEventArgs e)
        {
            if (!this._pingFlag)
                this.Ping();
            this._pingFlag = false;
        }
        private void StopReconnect()
        {
            if (this._reconnecTimer != null)
                this._reconnecTimer.Stop();
        }
        private void DoReconnect()
        {
            this.StopReconnect();
            this._reconnecTimer = new Timer(this._reconnectInterval);
            this._reconnecTimer.Elapsed += reconnecTimer_Elapsed;
            this._reconnecTimer.Start();
        }
        void reconnecTimer_Elapsed(object sender, ElapsedEventArgs e)
        {
            if (this._socket != null && this._socket.ReadyState == WsState.CLOSED)
            {
                try
                {
                    this.Connect(this._uri, this._protocol, this._headers);
                }
                catch (Exception ex)
                {
                    Console.WriteLine("reconnect error: {0}", ex);
                }
            }
        }
        // easy buffer pool
        private byte[] GetBuffer()
        {
            return new byte[_maxMessageSize];
        }
        private void ReturnBuffer(byte[] buffer)
        {
        }
        private byte[] ReadBytes(Stream stream)
        {
            var bytes = new byte[stream.Length];
            for (var i = 0; i < stream.Length; i++)
                bytes[i] = (byte)stream.ReadByte();
            return bytes;
        }
    }
}