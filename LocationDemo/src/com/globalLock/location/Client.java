package com.globalLock.location;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.CharacterCodingException;
import java.util.Properties;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.proxy.utils.ByteUtilities;
import org.apache.mina.transport.socket.nio.NioDatagramConnector;

import android.util.Log;

public class Client {

	static {
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
		java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
	}

	public static void setProperty(Properties properties) {
		 commAddress = new InetSocketAddress(properties.getProperty("server"),
		 Integer.parseInt(properties
		 .getProperty("port")));

		// gateAddress = new
		// InetSocketAddress(properties.getProperty("server_gate"),
		// Integer.parseInt(properties
		// .getProperty("port_gate")));
		//
		// commAddress = new InetSocketAddress("58.251.70.201", 2277);
	}

	static InetSocketAddress commAddress = null;

	static InetSocketAddress gateAddress = null;

	public static boolean Send(IoHandler handler) {
		 
		final NioDatagramConnector connector = new NioDatagramConnector();
		connector.setConnectTimeoutMillis(60000L);
		connector.setConnectTimeoutCheckInterval(10000);
		connector.getFilterChain().addLast("uniguard",
				new UniguardCodecFilter());
		connector.setHandler(handler);
		Log.i("", commAddress.toString());
		connector.connect(commAddress);
		// future.awaitUninterruptibly();
		// future.getSession().getCloseFuture().awaitUninterruptibly();
		// connector.dispose();
		// future.wait(20000);
		return true;

	}

	public static void main(String[] args) throws CharacterCodingException,
			UnsupportedEncodingException {
		IoBuffer buff = IoBuffer
				.wrap(ByteUtilities
						.asByteArray("5E 7F 4E 1C 5E 7F 5D DE 5E 02 4E CE 53 16 5E 02 4E 09 4E 94 4E 09 77 01 90 53 57 30 6D 3E 95 47 58 58 57 FA 67 51 00 20 58 58 57 FA 5C 0F 5B 66 4E 1C 53 57 00 36 00 30 00 31 7C 73 00 28 00 31 00 31 00 34 00 2E 00 30 00 32 00 38 00 38 00 31 00 20 00 32 00 33 00 2E 00 38 00 35 00 34 00 36 00 32 00 29"
								.replaceAll("\\s", "")));
		byte[] bs = new byte[buff.limit()];
		buff.get(bs);

		String s = new String(new String(bs, "unicode").getBytes("UTF-8"),
				"UTF-8");

		System.out.println(s);
	}

}
