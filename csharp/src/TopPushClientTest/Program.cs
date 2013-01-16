using System;
using System.Collections.Generic;
using System.Text;
using TopPushClient;

namespace TopPushClientTest
{
    class Program
    {
        static void Main(string[] args)
        {
            var frontend = new Frontend("csharp");
            frontend.SetPublishMessageHandler(new MessageHandler());
            frontend.Connect("ws://localhost:8080/frontend");

            Console.Read();
        }

        class MessageHandler : PublishMessageHandler
        {
            public override void onMessage(int messageType
                , int bodyFormat
                , byte[] messageBody
                , int offset
                , int length
                , PublishMessageContext context)
            {
                Console.WriteLine("receive publish message: {0}"
                    , Encoding.ASCII.GetString(messageBody, offset, length));
                context.confirm();
            }
        }
    }
}
