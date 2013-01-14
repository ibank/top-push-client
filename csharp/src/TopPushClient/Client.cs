using System;
using System.Collections.Generic;
using System.Text;
using System.Timers;
using WebSocketSharp;

namespace TopPushClient
{
    /// <summary>push client
    /// </summary>
    public class Client
    {
        private static readonly String MQTT = "mqtt";
        private int maxMessageSize = 1024;
        private int maxIdle = 60000;

        private String uri;
        private String protocol;
        private String self;
        private MessageHandler handler;
        private WebSocket socket;

        private bool pingFlag;
        private Timer pingTimer;
        //private TimerTask pingTimerTask;

        private int reconnectInterval = 5000;
        private int reconnectCount;
        private Timer reconnecTimer;
        //private TimerTask reconnecTimerTask;

        public Client(String clientFlag)
        {
            this.self = clientFlag;
            this.DoReconnect();
        }

        public void SetMaxIdle(int maxIdle)
        {
            this.maxIdle = maxIdle;
        }
        public void SetMaxMessageSize(int maxMessageSize)
        {
            this.maxMessageSize = maxMessageSize;
        }
        public void SetMessageHandler(MessageHandler handler)
        {
            this.handler = handler;
        }

        public Client Connect(String uri)
        {
            return this.Connect(uri, "");
        }
        public Client Connect(String uri, String messageProtocol)
        {
            this.uri = uri;
            // message protocol to cover top-push protocol
            this.protocol = messageProtocol;
            return this;
        }

        public void SendMessage(String to
            , int messageType
            , int messageBodyFormat
            , byte[] messageBody
            , int offset
            , int length)
        {
            this.DelayNextPing();
        }

        private void StopPing()
        {
            if (this.pingTimer != null)
            {
                //this.pingTimer.cancel();
                this.pingTimer = null;
            }
        }
        private void DelayNextPing()
        {
            pingFlag = true;
        }
        private void DoPing()
        {
            this.StopPing();
        }
        private void Ping()
        {
        }
        private void DoReconnect()
        {

        }
        // easy buffer pool
        private byte[] GetBuffer()
        {
            return new byte[maxMessageSize];
        }
        private void ReturnBuffer(byte[] buffer)
        {
        }
    }
}