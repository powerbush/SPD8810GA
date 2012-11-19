package com.globalLock.location;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderAdapter;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

import android.util.Log;

public class UniguardCodecFilter extends ProtocolCodecFilter {
	// 鎵撳寘
	public static IoBuffer makePacket(byte[] deviceNo, short command,
			IoBuffer content) {
		IoBuffer buffer = IoBuffer.allocate(2 + 2 + 6 + 2
				+ (content == null ? 0 : content.remaining()) + 1);

		buffer.put((byte) '&')
				.put((byte) '&')
				.putShort(
						(short) (6 + 2 + (content == null ? 0 : content
								.remaining()) + 1)).put(deviceNo)
				.putShort(command);
		if (content != null) {
			buffer.put(content);
			content.rewind();
		}
		return buffer.put(checksum(buffer, 4, buffer.position() - 1)).flip();
	}

	public static IoBuffer makePacket(Command command) {
		return makePacket(command.getDeviceNoBuffer(), command.getCmd(),
				command.getContent());
	}

	// 姹傚拰鏍￠獙
	private static byte checksum(IoBuffer buffer, int startPos, int endPos) {
		// System.out.println(String.format("from %d to %d", startPos, endPos
		// ));
		int sum = 0;
		for (int i = startPos; i <= endPos; i++)
			sum ^= buffer.get(i);
		return (byte) (sum & 0xff);
	}

	// 瑙ｅ寘
	public static Command parsePacket(IoBuffer buff) {
		// move to @, the head char
		while (buff.hasRemaining() && buff.get() != (byte) '&')
			;
		while (buff.hasRemaining() && buff.get() != (byte) '&')
			;
		if (buff.limit() - buff.position() < 11) {// 鍖呬笉瀹屾暣
													// System.err.println("鍖呬笉瀹屾暣1");
			buff.position(buff.position() - 1);
			return null;
		}

		int oldPos = buff.position() - 2;

		// String s = buff.ToString();

		int len = buff.getShort();

		Command command = null;
		byte[] bs = new byte[6];
		buff.get(bs);
		IoBuffer buffer = IoBuffer.allocate(8).putShort((short) 0).put(bs)
				.rewind();
		command = new Command(0, buffer.getLong(), buff.getShort());

		if (len > 0) {
			int oldLimit = buff.limit();
			if (buff.position() - 6 - 2 + len > buff.limit()) { // 鍖呬笉瀹屾暣
																// System.err.println("鍖呬笉瀹屾暣2 "
																// + s
																// +
																// String.format("锟�s + %s + 2 > %s",
																// buff.position(),
																// len,
																// buff.limit()));
				buff.position(oldPos);
				return null;
			}
			// System.out.println(buff.getHexDump());

			buff.limit(buff.position() - 6 - 2 + len - 1);
			command.setContent(buff.slice());
			buff.position(buff.limit());
			buff.limit(oldLimit);

			// System.out.println(buff.getHexDump());
		}

		command.setBad(true);

		if (checksum(buff, oldPos + 4, buff.position() - 1) != buff.get()) {
			// System.err.println("鏍￠獙閿欒" + s);
		}

		else {
			command.setBad(false);
		}

		return command;
	}

	// 杞箟
	private static IoBuffer TranSend(IoBuffer in) {
		IoBuffer out = IoBuffer.allocate(2048);

		out.put(in.get());
		out.put(in.get());
		while (in.hasRemaining()) {
			byte b = in.get();
			if (b == 0x24) {
				out.put((byte) 0x24);
				out.put((byte) 0);

			} else if (b == 0x26) {
				out.put((byte) 0x24);
				out.put((byte) 0x2);

			} else
				out.put(b);
		}
		out.flip();
		return out;
	}

	public static IoBuffer TranRecv(IoBuffer in) {
		IoBuffer out = IoBuffer.allocate(4096);
		for (int i = 0; i < in.limit(); i++) {
			if (in.get(i) == 0x24)
				if (in.get(i + 1) == 0x00)
					out.put(in.get(i++));
				else if (in.get(i + 1) == 0x02) {
					out.put((byte) 0x26);
					i++;
				} else
					out.put(in.get(i));
			else
				out.put(in.get(i));
		}

		out.flip();
		in.rewind();
		in.put(out.slice()).flip();
		return in;
	}

	public UniguardCodecFilter() {
		super(new ProtocolCodecFactory() {

			public ProtocolEncoder getEncoder(IoSession session)
					throws Exception {
				return new ProtocolEncoderAdapter() {
					public void encode(IoSession session, Object message,
							ProtocolEncoderOutput out) throws Exception {
						IoBuffer buffer = makePacket((Command) message);
						Log.i("", "SEND HEX " + buffer.getHexDump());
						out.write(TranSend(buffer));
					}
				};
			}

			public ProtocolDecoder getDecoder(IoSession session)
					throws Exception {
				return new ProtocolDecoderAdapter() {
					public void decode(IoSession session, IoBuffer in,
							ProtocolDecoderOutput out) throws Exception {
						Log.i("", session.getAttribute("userId") + " RECV HEX "
								+ in.getHexDump());
						// String s = in.getHexDump();
						TranRecv(in);
						Log.i("", session.getAttribute("userId")
								+ " RECV HEX Tran " + in.getHexDump());
						IoBuffer buff = in;

						boolean continuous = session.getAttachment() != null;
						if (continuous) {
							// 濡備笂娆℃湁锟� 鍜屼笂娆＄殑鍖呮帴璧锋潵瑙ｅ寘
							byte[] prevData = (byte[]) session.getAttachment();
							IoBuffer newBuff = IoBuffer
									.allocate(prevData.length + in.remaining());
							newBuff.put(prevData).put(in).flip();

							// System.err.println(session.getAttribute("userId")
							// + " 鎺ヤ笂娆＄殑锟�" + newBuff.getHexDump());

							buff = newBuff;
						}

						// 瑙ｅ寘
						while (buff.hasRemaining()) {
							Command cmd = parsePacket(buff);
							if (cmd != null) {
								out.write(cmd);

								// if (cmd.isBad()) {
								// System.err.println(session.getAttribute("userId")
								// + " 鏀跺埌鍧忓寘");
								// }
							} else {
								break;
							}
						}
						// ...
						if (continuous) {
							if (!buff.hasRemaining()) {
								// buff.release();
								session.setAttachment(null);
							} else {
								byte[] remain = new byte[buff.remaining()];
								buff.get(remain);
								session.setAttachment(remain);

							}
						} else {
							if (buff.hasRemaining()) {
								// System.err.println(session.getAttribute("userId")
								// + " 娌℃湁瑙ｅ畬 " + s);
								byte[] remain = new byte[in.remaining()];
								in.get(remain);
								session.setAttachment(remain);
							}
						}
					}
				};
			}

		});
	}

}
