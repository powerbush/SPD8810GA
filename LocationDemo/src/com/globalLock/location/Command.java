package com.globalLock.location;


import org.apache.mina.core.buffer.IoBuffer;

public class Command {

     
     private int seq;
     private long deviceNo;
     private short cmd;
     private IoBuffer content;
     private boolean  bad = false;
     private byte sign;


     public byte getSign()
     {
         return sign;
     }

     public void setSign(byte sign)
     {
         this.sign = sign;
     }


     public boolean  isBad()
     {
         return bad;
     }

     public void setBad(boolean  bad)
     {
         this.bad = bad;
     }

     public short getCmd()
     {
         return  cmd ;
     }

     public void setCmd(short cmd)
     {
         this.cmd = cmd;
     }

     public IoBuffer getContent()
     {
         return content;
     }

     public void setContent(IoBuffer content)
     {
         this.content = content;
     }

     

     public int getSeq()
     {
         return seq;
     }

     public void setSeq(int seq)
     {
         this.seq = seq;
     }

     public long getDeviceNo()
     {
         return deviceNo;
     }
     public byte[] getDeviceNoBuffer()
     {
    	 IoBuffer buffer=IoBuffer.allocate(8).putLong(deviceNo).flip();
    	 buffer.getShort();
    	 byte[] bs=new byte[6];
    	 buffer.get(bs);
    	 return bs;
    	 
     }
     public void setDeviceNo(long deviceNo)
     {
         this.deviceNo = deviceNo;
     }

     



     public String getContentAsString()
     {
         byte[] b = new byte[content.remaining()];
         content.get(b);
         content.rewind();
         return new String(b);
     }

     public String toString()
     {
         //操 作	包头（3B）	序号（2B）	终端编号（8B）	命令字（2B）	数据长度(2B)	状态码(2B)	数据(xB)	效验(1B)	包尾(3B)
         return (bad ? "BAD PACKET " : "")
                 + " seq: "
                 + seq
                 + " deviceNo:"
                 + deviceNo
                 + " cmd: "
                 + cmd
                 + (content == null ? "" : " data: " + content.getHexDump()
                      +"txt:"+getContentAsString()    );
     }
     public String toString2()
     {
         //操 作	包头（3B）	序号（2B）	终端编号（8B）	命令字（2B）	数据长度(2B)	状态码(2B)	数据(xB)	效验(1B)	包尾(3B)
         return (bad ? "BAD PACKET " : "")
                 + " seq: "
                 + seq
                 + " deviceNo:"
                 + deviceNo
                 + " cmd: "
                 + cmd
                 + (content == null ? "" : " data: " + content.getHexDump()
                          );
     }


     //public void releaseContent()
     //{
     //    if (this.Content != null)
     //        this.Content.free();
     //}

     public boolean isSuccess()
     {
         if(this.content==null)
             return true ;
         return this.content.getUnsignedShort() == 0x9000;
     }

     public boolean isSetOK()
     {
         if (this.content == null)
             return true;
         if (isSuccess())// && content.remaining()>5 && content.getString(6, Encoding.ASCII).Equals("SET_OK"))
             return true;
         else
             return false;
     }

     public Command(int seq, long deviceNo, short cmd)
     {
         this.deviceNo = deviceNo;
         this.cmd = cmd;
         this.seq = seq;

     }

     public Command(int seq, long deviceNo, short cmd, byte sign)
     {
         this.deviceNo = deviceNo;
         this.cmd = cmd;
         this.seq = seq;
         this.sign = sign;
     }

     public Command(int seq, long deviceNo, short cmd, byte sign, IoBuffer content)
     {
         this.deviceNo = deviceNo;
         this.seq = seq;
         this.cmd = cmd;
         this.sign = sign;
         this.content = content;
     }


     private static byte checksum(IoBuffer buffer, int startPos, int endPos)
     {

         int sum = 0;
         for (int i = startPos; i <= endPos; i++)
             sum += buffer.get(i);
         return (byte)((0x100 - sum & 0xff) & 0xff);
     }

	 

	
	
	
	
}
