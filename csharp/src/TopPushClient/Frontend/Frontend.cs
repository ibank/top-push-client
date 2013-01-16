using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace TopPushClient
{
    public class Frontend
    {
        private static readonly String PROTOCOL = "mqtt";
        private Client _client;
        private PublishMessageHandler _handler;
        /// <summary>
        /// </summary>
        /// <param name="clientFlag">client identity string</param>
        public Frontend(string clientFlag)
        {
            this._client = new Client(clientFlag);
        }
        /// <summary>Set Message Handler
        /// </summary>
        /// <param name="handler"></param>
        public void SetPublishMessageHandler(PublishMessageHandler handler)
        {
            this._handler = handler;
            this.SetHandler();
        }
        /// <summary>Connect to push server
        /// </summary>
        /// <param name="uri">push server address, eg: ws://localhost:8080/frontend</param>
        public void Connect(string uri)
        {
            this._client.Connect(uri, PROTOCOL);
        }

        private void SetHandler()
        {
            this._client.SetMessageHandler(new CustomMessageHandler(this._handler));
        }
        private class CustomMessageHandler : MessageHandler
        {
            private PublishMessageHandler _handler;
            public CustomMessageHandler(PublishMessageHandler handler)
            {
                this._handler = handler;
            }
            public override void onMessage(int messageType
                , int bodyFormat
                , byte[] messageBody
                , int offset
                , int length
                , MessageContext context)
            {
                if (messageType != MessageType.PUBLISH) 
                    return;

                using (var stream = new MemoryStream(messageBody, offset, length))
                using (var reader = new BinaryReader(stream))
                {
                    // HACK:messageId will be first from backend
                    long messageId = MessageIO.SwapInt64(reader.ReadInt64());
                    offset += 8;
                    length -= 8;
                    this._handler.onMessage(messageType,
                            bodyFormat,
                            messageBody,
                            offset,
                            length,
                            new PublishMessageContext(messageId, context));
                }
            }
        }
    }
}