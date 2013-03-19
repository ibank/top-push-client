using System;
using System.Collections.Generic;
using System.Text;

namespace TopPushClient
{
    public abstract class FrontendMessageHandler
    {
        public abstract void OnMessage(int messageType
            , int bodyFormat
            , byte[] messageBody
            , int offset
            , int length
            , FrontendMessageContext context);
    }
}
