using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;

namespace TopPushClient
{
    /// <summary>custom top-push client for special usage
    /// </summary>
    public class Frontend
    {
        private static readonly String PROTOCOL = "mqtt";
        private CustomClient _client;
        private FrontendMessageHandler _handler;

        public Frontend(string appKey) : this(appKey, -1, 1) { }
        public Frontend(string appKey, int userId) : this(appKey, userId, 1) { }
        public Frontend(string appKey, int userId, int groupId)
        {
            this._client = new CustomClient(string.Format(
                "{0}{1}{2}"
                , appKey
                , userId
                , groupId));
            this._client.AppKey = appKey;
            this._client.UserId = userId;
            this._client.GroupId = groupId;
        }

        /// <summary>set app secret for auth
        /// </summary>
        /// <param name="secret"></param>
        public void SetSecret(string secret)
        {
            this._client.Secret = secret;
        }
        /// <summary>Set Message Handler
        /// </summary>
        /// <param name="handler"></param>
        public void SetMessageHandler(FrontendMessageHandler handler)
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
            private FrontendMessageHandler _handler;
            public CustomMessageHandler(FrontendMessageHandler handler)
            {
                this._handler = handler;
            }
            public override void OnMessage(int messageType
                , int bodyFormat
                , byte[] messageBody
                , int offset
                , int length
                , MessageContext context)
            {
                using (var stream = new MemoryStream(messageBody, offset, length))
                using (var reader = new BinaryReader(stream))
                {
                    // HACK:messageId will be first from backend
                    long messageId = MessageIO.SwapInt64(reader.ReadInt64());
                    offset += 8;
                    length -= 8;
                    this._handler.OnMessage(messageType,
                            bodyFormat,
                            messageBody,
                            offset,
                            length,
                            new FrontendMessageContext(messageId, context));
                }
            }
        }

        class CustomClient : Client
        {
            public string AppKey { get; set; }
            public int UserId { get; set; }
            public int GroupId { get; set; }

            public string Secret { get; set; }

            public CustomClient(string clientFlag) : base(clientFlag) { }

            public override void Connect(string uri, string messageProtocol, IDictionary<string, string> headers)
            {
                headers = new Dictionary<string, string>();
                headers.Add("timestamp", DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
                headers.Add("appkey", this.AppKey);
                headers.Add("userid", this.UserId.ToString());
                headers.Add("groupid", this.GroupId.ToString());
                headers.Add("sign", this.signature(headers, this.Secret));
                base.Connect(uri, messageProtocol, headers);
            }

            private string signature(IDictionary<string, string> dict, string secret)
            {
                var names = new string[dict.Count];
                dict.Keys.CopyTo(names, 0);
                Array.Sort(names);
                StringBuilder sb = new StringBuilder();
                sb.Append(secret);
                for (int i = 0; i < names.Length; i++)
                {
                    String name = names[i];
                    sb.Append(name);
                    sb.Append(dict[name]);
                }
                sb.Append(secret);
                return toMD5(sb.ToString()).ToUpper();
            }
            private string toMD5(string originalString)
            {
                var bytes = MD5.Create().ComputeHash(Encoding.UTF8.GetBytes(originalString));
                var strb = new StringBuilder();
                for (int i = 0; i < bytes.Length; i++)
                    strb.Append(bytes[i].ToString("X2"));
                return strb.ToString();
            }
        }
    }
}