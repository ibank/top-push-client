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

        private string _uri;
        private string _protocol;
        private string _self;
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
        public void SetMaxMessageSize(int maxMessageSize)
        {
            this._maxMessageSize = maxMessageSize;
        }
        public void SetMessageHandler(MessageHandler handler)
        {
            this._handler = handler;
        }

        public void Connect(String uri)
        {
            this.Connect(uri, null);
        }
        public void Connect(String uri, String messageProtocol)
        {
            this._uri = uri;
            this._protocol = messageProtocol;//message protocol to cover top-push protocol
            this._socket = new WebSocket(this._uri, this._protocol);
            this._socket.OnOpen += (s, e) => { this._reconnectCount++; Console.WriteLine("connected to server {0}", this._uri); };
            this._socket.OnClose += (s, e) => { this.StopPing(); Console.WriteLine("Closed: {0}|{1}", e.Code, e.Reason); };
            this._socket.OnError += (s, e) => { this.StopPing(); Console.WriteLine("Error: {0}", e.Message); };
            this._socket.OnMessage += (s, e) =>
            {
                this.DelayNextPing();

                using (var stream = new MemoryStream(e.RawData))
                {
                    if (MQTT.Equals(this._protocol, StringComparison.InvariantCultureIgnoreCase))
                        MqttMessage.CreateFrom(stream);

                    using (var reader = new BinaryReader(stream))
                    {
                        int messageType = MessageIO.ReadMessageType(reader);
                        string messageFrom = MessageIO.ReadClientId(reader);
                        int messageBodyFormat = MessageIO.ReadBodyFormat(reader);
                        int remainingLength = MessageIO.ReadRemainingLength(reader);

                        MessageContext context = new MessageContext(this, messageFrom);
                        this._handler.onMessage(messageType
                            , messageBodyFormat
                            , e.RawData
                            , (int)stream.Position
                            , remainingLength
                            , context);
                    }
                }
            };
            this._socket.Connect();
            this.DoPing();
        }

        public void SendMessage(String to
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
            if (this._socket == null || !this._socket.IsAlive)
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
        private void DoReconnect()
        {
            this._reconnecTimer = new Timer(this._reconnectInterval);
            this._reconnecTimer.Elapsed += reconnecTimer_Elapsed;
            this._reconnecTimer.Start();
        }
        void reconnecTimer_Elapsed(object sender, ElapsedEventArgs e)
        {
            if (_socket != null && !_socket.IsAlive)
            {
                try
                {
                    this.Connect(this._uri, this._protocol);
                }
                catch (Exception ex)
                {
                    Console.WriteLine(ex);
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