﻿using System;
using System.Collections.Generic;
using System.Text;

namespace TopPushClient
{
    public abstract class MessageHandler
    {
        public abstract void OnMessage(int messageType
            , int bodyFormat
            , byte[] messageBody
            , int offset
            , int length
            , MessageContext context);
    }
}
