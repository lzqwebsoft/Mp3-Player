/*
* SplitMultiplex.java -- 从MPEG-1,MPEG-2分离(读取)音频流
* Copyright (C) 2010
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
* If you would like to negotiate alternate licensing terms, you may do
* so by contacting the author: <http://jmp123.sourceforge.net/>
*/
package jmp123.instream;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 从MPEG-1和MPEG-2文件读取音频流。仅支持以下的多路复用格式：
 * 	<ul><li>VCD格式(*.dat)</li>
 * 	<li>DVD程序流格式(*.vob)</li></ul>
 */
public class MultiplexAudio extends RandomRead {
	private static final int BUF_LEN = 1024;
	private byte[] buf;
	private int curPos;	// buf[curPos], curPos=0..BUF_LEN-1
	private int mpegVersion;
	private int packet_data_bytes;
	//private int audiopacket_data_bytes;
	private RandomAccessFile raf;

	public boolean open(String name, String title) throws IOException {
		buf = new byte[BUF_LEN];
		raf = new RandomAccessFile(name, "r");

		// 先填满缓冲区
		if (raf.read(buf) < BUF_LEN)
			return false;
		curPos = 0;

		// 定位到第一个音频包
		if (!audio_stream())
			return false;

		return true;
	}

	private boolean needBytes(int n) throws IOException {
		int len = BUF_LEN - curPos;
		if(len < n) {
			System.arraycopy(buf, curPos, buf, 0, len);
			if(raf.read(buf, len, curPos) < curPos)
				return false;
			curPos = 0;
		}
		return true;
	}

	private boolean skip(int n) throws IOException {
		int remain = BUF_LEN - curPos;
		if(n < remain) {
			curPos += n;
			needBytes(6);
		} else {
			n -= remain;
			raf.seek(n + raf.getFilePointer());
			raf.read(buf);
			curPos = 0;
		}
		return true;
	}

	/**
	 * 查找同步字 0x000001<br>
	 * 返回前buf内至少有6字节可用<br>
	 *   curPos += n 跳过n(n<6)字节<br>
	 *   skip(n) 跳过n(n为任意长度)字节<br>
	 */
	private boolean start_code() throws IOException {
		int idx = 0;
		while(buf[curPos] != 0 || buf[curPos+1] != 0 || buf[curPos+2] != 1) {
			if(++idx > 0x80000) {	//512K
				return false;
			}
			curPos++;
			if(BUF_LEN - curPos < 6)
				needBytes(6);
		}
		//if(idx > 0)
		//	System.out.println("skip bytes:" + idx);
		return true;
	}

	private void pack() throws IOException {
		// 4bits: MPEG version
		if((buf[curPos+4] & 0xf0) == 0x20) {		// '0010 xxxx'
			if(!needBytes(12))
				return;

			if (this.duration == 0) {
				/*
				 *  1-bit: marker_bit
				 * 22-bit: mux_rate
				 *  1-bit: marker_bit
				 */
				int mux_rate = (buf[curPos + 9] & 0x7f) << 16;
				mux_rate |= (buf[curPos + 10] & 0xff) << 8;
				mux_rate |= buf[curPos + 11] & 0xff;
				mux_rate >>= 1;
				this.duration = (int) (raf.length() / mux_rate / 50);
				System.out.printf("duration: %ds\n", duration);
			}

			mpegVersion = 1;
			skip(12);
		} else if((buf[curPos+4] & 0xf0) == 0x40) {	// '0100 xxxx'
			if(!needBytes(14))
				return;
			mpegVersion = 2;
			skip(14 + (buf[curPos+13] & 0x7));
		}
	}

	/**
	 * 查找音频流,定位到下一音频包始址
	 */
	private boolean audio_stream() throws IOException {
		int startcode, header_length, len;
		while(true) {
			if(!start_code())
				return false;
			if((startcode = buf[curPos+3] & 0xff) < 0xb9) {
				//System.out.println("Not system stream. stream_id=" + startcode + ", skip...");
				curPos += 4;
				continue;
			}

			switch(startcode) {
			case 0xb9:	// end_code
				//System.out.println("正常结束.");
				return false;
			case 0xba:	// pack_start_code
				pack();
				break;
			case 0xbd:	// private_stream_1,DVD Audio
				if(mpegVersion == 1)
					continue;
			case 0xc0:	// '110x xxxx': audio stream
				header_length = ((buf[curPos+4] & 0xff) << 8) | (buf[curPos+5] & 0xff);
				len = 6;	// 4(packet_start_code) + 2(header_length)
				if((buf[curPos+6] & 0xc0) == 0x80) {
					//mpeg-2
					len += 3 + (buf[curPos+8] & 0xff);
				} else {
					//mpeg-1
					while ((buf[curPos + len] & 0xff) == 0xff) {
						// '1111 1111': stuffing_byte,不超过16字节.
						len++;
					}

					if ((buf[curPos + len] & 0xc0) == 0x40) // '01xx xxxx'
						len += 2;

					if ((buf[curPos + len] & 0xf0) == 0x20) { // '0010 xxxx'
						// presentation_time_stamp: 5bytes
						len += 5;
					} else if ((buf[curPos + len] & 0xf0) == 0x30) { // '0011 xxxx'
						// presentation_time_stamp: 10bytes
						len += 10;
					} else if (buf[curPos + len] == 0xf) { // '0000 1111'
						len++; // ?
					}
				}
				// packet_data_byte
				packet_data_bytes = header_length - len + 6;
				//audiopacket_data_bytes += packet_data_bytes;
				if(packet_data_bytes > 2324) {
					//System.out.println("packet_data_byte_len=" + packet_data_bytes + " skip...");
					curPos += 4;
					break;
				}
				skip(len);		// 跳过len个字节,指向packet_data_byte
				return true;
			default:
				header_length = ((buf[curPos+4] & 0xff) << 8) | (buf[curPos+5] & 0xff);
				if(header_length > 2324) {
					//System.out.println("header_length = " + header_length + " skip...");
					curPos += 4;
				} else
					skip(header_length + 6);
			}
		}
	}

	public void close() {
		try {
			this.raf.close();
			//System.out.println("audiopacket_data_bytes="+audiopacket_data_bytes);
		} catch (IOException e) {}
	}

	/**
	 * 查找并读取一个音频包
	 */
	private int readAudioPacket(byte[] b, int off, int len) throws IOException {
		if(packet_data_bytes <= 0 && !audio_stream())
				return -1;
		if(packet_data_bytes < len)
			len = packet_data_bytes;

		int remain = BUF_LEN - curPos;
		if(len >= remain) {
			System.arraycopy(buf, curPos, b, off, remain);
			raf.read(b, off + remain, len - remain);
			raf.read(buf);
			curPos = 0;
		} else {
			System.arraycopy(buf, curPos, b, off, len);
			curPos += len;
		}
		needBytes(6);
		packet_data_bytes -= len;
		return len;
	}

	/**
	 * 将音频流中最多 len 个数据字节读入 byte 数组。尝试读取 len 个字节，但读取的字节也可能小于该值。以整数形式返回实际读取的字节数。
	 * @param b 读入数据的缓冲区。
	 * @param off 数组 b 中将写入数据的初始偏移量。
	 * @param len 要读取的最大字节数。
	 * @return 读入缓冲区的总字节数；如果因为已到达音频流末尾而不再有数据可用，则返回-1。
	 * @throws IOException
	 */
	public int read(byte[] b, int off, int len) throws IOException {
		int r, rema = len;
		while(rema > 0) {
			if((r = readAudioPacket(b, off, rema)) == -1) {
				if(len == rema)
					return -1;
				break;
			}
			off += r;
			rema -= r;
		}
		return len - rema;
	}

	public boolean seek(long pos) throws IOException {
		return false;
	}
}
