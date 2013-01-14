﻿using System;
using System.Collections.Generic;
using System.Text;

namespace TopPushClient
{
    public class MessageContext
    {
        private String messageFrom;
        private Client client;

        public MessageContext(Client client, String messageFrom)
        {
            this.messageFrom = messageFrom;
            this.client = client;
        }

        public void reply(int messageType
            , int bodyFormat
            , byte[] messageBody
            , int offset, int length)
        {
            this.client.SendMessage(this.messageFrom
                , messageType
                , bodyFormat
                , messageBody
                , offset
                , length);
        }
    }
}