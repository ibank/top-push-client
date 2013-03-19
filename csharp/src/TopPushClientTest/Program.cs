using System;
using System.Collections.Generic;
using System.Text;
using TopPushClient;

namespace TopPushClientTest
{
    class Program
    {
        static void ClientTest()
        { 
            
        }

        static void Main(string[] args)
        {
            var frontend = new Frontend("4272");
            frontend.SetSecret("0ebbcccfee18d7ad1aebc5b135ffa906");
            frontend.SetMessageHandler(new MessageHandler());
            frontend.Connect("ws://localhost:8080/frontend");

            Console.Read();
        }

        class MessageHandler : FrontendMessageHandler
        {
            public override void OnMessage(int messageType
                , int bodyFormat
                , byte[] messageBody
                , int offset
                , int length
                , FrontendMessageContext context)
            {
                Console.WriteLine("receive publish message: {0}"
                    , Encoding.ASCII.GetString(messageBody, offset, length));
                context.Confirm();
            }
        }
    }
}
