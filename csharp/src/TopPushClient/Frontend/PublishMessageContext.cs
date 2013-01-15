using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace TopPushClient
{
    public class PublishMessageContext
    {
        private long _messageId;
        private MessageContext _innerContext;

        public PublishMessageContext(long messageId, MessageContext innerContext)
        {
            this._messageId = messageId;
            this._innerContext = innerContext;
        }

        public void confirm()
        {
            byte[] idBytes = new byte[8];
            using (var stream = new MemoryStream(idBytes))
            using (var writer = new BinaryWriter(stream))
            {
                writer.Write(MessageIO.SwapInt64(this._messageId));
                this._innerContext.reply(MessageType.PUBCONFIRM, 0, idBytes, 0, idBytes.Length);
            }
        }
    }
}
