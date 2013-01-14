using System;
using System.Collections.Generic;
using System.IO;
using System.Text;

namespace TopPushClient
{
    // parse message using default protocol
    static class MessageIO
    {
        /*
         * 1byte MessageType, or extend to 8bit usage, DUP Flag, Body Formatter,
         * RETAIN
         * 
         * 8byte from(receiving)/to(sending) id, support front<-->forward<-->back.
         * 
         * 1byte Message body format flag
         * 
         * 4byte remainingLength int32, do not need support longer message.
         * 
         * ... body/content, maybe serialized by json/protobuf/msgpack/...
         * 
         * body example, just resolve/parse by client: - publish message: {
         * MessageId:"20121221000000001", Content:"hello world!" } - confirm
         * message: ["20121221000000001", "20121221000000002"]
         */

        // server send: server -> client, write "from"
        // server receive: server <- client, read "to"

        // client send: client -> server, write "to"
        // client receive: client <- server, read "from"

        public static int ParseMessageType(byte headerByte)
        {
            return headerByte;
        }

        public static int ReadMessageType(BinaryReader buffer)
        {
            return buffer.ReadByte();
        }

        public static void WriteMessageType(BinaryWriter buffer, int messageType)
        {
            buffer.Write((byte)messageType);
        }

        public static String ReadClientId(BinaryReader buffer)
        {
            return ReadString(buffer, 8).Trim();
        }

        public static void WriteClientId(BinaryWriter buffer, String id)
        {
            WriteString(buffer, PadClientId(id));
        }

        public static int ReadBodyFormat(BinaryReader buffer)
        {
            return buffer.ReadByte();
        }

        public static void WriteBodyFormat(BinaryWriter buffer, int bodyFormat)
        {
            buffer.Write((byte)bodyFormat);
        }

        public static int ReadRemainingLength(BinaryReader buffer)
        {
            return buffer.ReadInt32();
        }

        public static void WriteRemainingLength(BinaryWriter buffer, int remainingLength)
        {
            buffer.Write(remainingLength);
        }

        // HACK:string encoding? a-zA-Z0-9 not necessary
        public static String ReadString(BinaryReader buffer, int length)
        {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++)
                sb.Append((char)buffer.ReadByte());
            return sb.ToString();
        }

        public static void WriteString(BinaryWriter buffer, String value)
        {
            for (int i = 0; i < value.Length; i++)
                buffer.Write((byte)value[i]);
        }

        public static String PadClientId(String id)
        {
            // HACK:8 is faster!
            if (id != null && id.Length == 8)
                return id;
            return id.PadLeft(8, ' ');// bad perf
        }

        public static int GetFullMessageSize(int remainingLength)
        {
            return remainingLength + 1 + 8 + 1 + 4;
        }

        public static short SwapInt16(short v)
        {
            return (short)(((v & 0xff) << 8) | ((v >> 8) & 0xff));
        }
        public static int SwapInt32(int v)
        {
            return (int)(((SwapInt16((short)v) & 0xffff) << 0x10) | (SwapInt16((short)(v >> 0x10)) & 0xffff));
        }
        public static long SwapInt64(long v)
        {
            return (long)(((SwapInt32((int)v) & 0xffffffffL) << 0x20) | (SwapInt32((int)(v >> 0x20)) & 0xffffffffL));
        }
    }
}