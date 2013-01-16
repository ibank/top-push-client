using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace TopPushClient
{
    /// <summary>custom top-push client for special usage
    /// </summary>
    public class Frontend
    {
        private static readonly String PROTOCOL = "mqtt";
        private Client _client;
        private PublishMessageHandler _handler;

        private string _appKey;
        private int _userId = -1;
        private int _groupId = 1;

        public Frontend(string appKey) : this(appKey, -1, 1) { }
        public Frontend(string appKey, int userId) : this(appKey, userId, 1) { }
        public Frontend(string appKey, int userId, int groupId)
        {
            this._appKey = appKey;
            this._userId = userId;
            this._groupId = groupId;
            this._client = new Client(string.Format("{0}{1}{2}"
                , this._appKey
                , this._userId
                , this._groupId));
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
            var headers = new Dictionary<string, string>();
            headers.Add("appkey", this._appKey);
            headers.Add("userId", this._userId.ToString());
            headers.Add("groupId", this._groupId.ToString());
            this._client.Connect(uri, PROTOCOL, headers);
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