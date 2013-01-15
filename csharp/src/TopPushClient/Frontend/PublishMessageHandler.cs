using System;
using System.Collections.Generic;
using System.Text;

namespace TopPushClient
{
    public abstract class PublishMessageHandler
    {
        public abstract void onMessage(int messageType
            , int bodyFormat
            , byte[] messageBody
            , int offset
            , int length
            , PublishMessageContext context);
    }
}
