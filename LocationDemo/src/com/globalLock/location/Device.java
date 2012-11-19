package com.globalLock.location;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

import com.globalLock.location.Client;
import com.globalLock.location.Command;

import android.util.Log;

public class Device {

	public Device(String imei, int radix, String phone, String fatherPhone) {
		IMEI = imei;
		IMSI = "460030912121001";
		setDeviceNo(getDeviceNo(imei, radix));
		setPhone(phone);
		setFatherPhone(fatherPhone);

	}

	public static long getDeviceNo(String imei, int radix) {
		long no = 0;
		if (radix == 10)
			no = Long.parseLong(imei, radix);
		else
			no = Long.parseLong(imei.substring(0, imei.length() - 1), radix);
		return no & 0xffffffffffffL;
	}

	String IMEI;

	String IMSI;

	long DeviceNo;

	String phone;

	String fatherPhone;

	public String getIMEI() {
		return IMEI;
	}

	public void setIMEI(String iMEI) {
		IMEI = iMEI;
	}

	public String getIMSI() {
		return IMSI;
	}

	public void setIMSI(String iMSI) {
		IMSI = iMSI;
	}

	void setDeviceNo(long deviceNo) {
		DeviceNo = deviceNo;
	}

	public long getDeviceNo() {
		return DeviceNo;
	}

	public String getFatherPhone() {
		return fatherPhone;
	}

	public void setFatherPhone(String fatherPhone) {
		this.fatherPhone = fatherPhone;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	private static IoBuffer fromHexdump(String hex) {
		String[] arr = hex.split("\\s");
		IoBuffer buff = IoBuffer.allocate(arr.length);
		for (String a : arr) {
			buff.put((byte) Integer.parseInt(a, 16));
		}
		return buff.flip();
	}

	SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");

	SimpleDateFormat shortFormat = new SimpleDateFormat("yyMMddHHmmss");

	DecimalFormat decimalFormat = new DecimalFormat("0.000000");

	public String QueryLocation(double lon, double lat)
			throws CharacterCodingException {

		final Command cmd = new Command(0, getDeviceNo(), (short) 0x9001,
				(byte) 0x10);

		IoBuffer buffer = IoBuffer.allocate(1024);
		String s = shortFormat.format(new Date());
		for (int i = 0; i < s.length(); i += 2) {
			buffer.put(Byte.parseByte(s.substring(i, i + 2)));
		}

		buffer.put((byte) lon);

		s = decimalFormat.format(lon);
		int start = s.indexOf(".") + 1;
		for (int i = start; i < s.length(); i += 2) {
			if (i > start + 5)
				break;
			buffer.put(Byte.parseByte(s.substring(i, i + 2)));
		}
		buffer.put((byte) lat);
		s = decimalFormat.format(lat);
		start = s.indexOf(".") + 1;
		for (int i = start; i < s.length(); i += 2) {
			if (i > start + 5)
				break;
			buffer.put(Byte.parseByte(s.substring(i, i + 2)));
		}
		 
		buffer.put((byte)0).put((byte)0).putShort((short)0)
		.put((byte)0x12).put((byte)0).put((byte)1);
        buffer.put((byte)0x45);
        buffer.put((byte)0x4e);
		buffer.flip();
		cmd.setContent(buffer);
		final Object lockObj = new Object();
		final StringBuffer sb = new StringBuffer();

		Client.Send(new IoHandler() {

			@Override
			public void sessionOpened(IoSession session) throws Exception {
				session.write(cmd);
				Log.i("", "send:" + cmd);
			}

			@Override
			public void sessionIdle(IoSession session, IdleStatus status)
					throws Exception {

			}

			@Override
			public void sessionCreated(IoSession session) throws Exception {
				// session.write(cmd);
			}

			@Override
			public void sessionClosed(IoSession session) throws Exception {

			}

			@Override
			public void messageSent(IoSession session, Object message)
					throws Exception {

			}

			@Override
			public void messageReceived(IoSession session, Object message)
					throws Exception {
				Log.i("", "recv:" + message);
				Command result = (Command) message;
				if ((result.getCmd() & 0xffff) == 0x0991) {

					sb.append(result.getContent().getString(
							result.getContent().remaining(),
							Charset.forName("UTF-16BE").newDecoder()));

				}
				synchronized (lockObj) {
					lockObj.notify();
				}

			}

			@Override
			public void exceptionCaught(IoSession session, Throwable cause)
					throws Exception {

			}
		});
		synchronized (lockObj) {
			try {
				lockObj.wait(10 * 1000);
				if (sb.length() > 0)
					return sb.toString();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}
		return "timeout";

	}

	public String QueryLocation(long cellid, long lac,short mcc,short mnc )
			throws CharacterCodingException {

		final Command cmd = new Command(0, getDeviceNo(), (short) 0x9001,
				(byte) 0x10);

		IoBuffer buffer = IoBuffer.allocate(1024);
		String s = shortFormat.format(new Date());
		for (int i = 0; i < s.length(); i += 2) {
			buffer.put(Byte.parseByte(s.substring(i, i + 2)));
		}

		buffer.put((byte) 0);

		s = decimalFormat.format(0);
		int start = s.indexOf(".") + 1;
		for (int i = start; i < s.length(); i += 2) {
			if (i > start + 5)
				break;
			buffer.put(Byte.parseByte(s.substring(i, i + 2)));
		}
		buffer.put((byte) 0);
		s = decimalFormat.format(0);
		start = s.indexOf(".") + 1;
		for (int i = start; i < s.length(); i += 2) {
			if (i > start + 5)
				break;
			buffer.put(Byte.parseByte(s.substring(i, i + 2)));
		}

		buffer.put((byte)0).put((byte)0).putShort((short)0)
		.put((byte)0xf2).put((byte)0).put((byte)1);
		buffer.put((byte)0x45);
        buffer.put((byte)0x4e);
		buffer.putLong(cellid).putLong(lac).putShort(mcc).putShort(mnc);
		
		
		
		buffer.flip();
		cmd.setContent(buffer);
		final Object lockObj = new Object();
		final StringBuffer sb = new StringBuffer();

		Client.Send(new IoHandler() {

			@Override
			public void sessionOpened(IoSession session) throws Exception {
				session.write(cmd);
				Log.i("", "send:" + cmd);
			}

			@Override
			public void sessionIdle(IoSession session, IdleStatus status)
					throws Exception {

			}

			@Override
			public void sessionCreated(IoSession session) throws Exception {
				// session.write(cmd);
			}

			@Override
			public void sessionClosed(IoSession session) throws Exception {

			}

			@Override
			public void messageSent(IoSession session, Object message)
					throws Exception {

			}

			@Override
			public void messageReceived(IoSession session, Object message)
					throws Exception {
				Log.i("", "recv:" + message);
				Command result = (Command) message;
				if ((result.getCmd() & 0xffff) == 0x0991) {

					sb.append(result.getContent().getString(
							result.getContent().remaining(),
							Charset.forName("UTF-16BE").newDecoder()));

				}
				synchronized (lockObj) {
					lockObj.notify();
				}

			}

			@Override
			public void exceptionCaught(IoSession session, Throwable cause)
					throws Exception {

			}
		});
		synchronized (lockObj) {
			try {
				lockObj.wait(10 * 1000);
				if (sb.length() > 0)
					return sb.toString();
			} catch (InterruptedException e) {

				e.printStackTrace();
			}
		}
		return "";

	}

}
