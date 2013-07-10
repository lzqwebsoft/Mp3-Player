/*
* BuffRandAcceURL.java -- (HTTP协议)读取远程文件.
* Copyright (C) 2010
* 没有用java.util.concurrent实现读写同步,可能不是个好主意.
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
*
*/
package jmp123.instream;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;

import jmp123.decoder.IAudio;

/**
 * 读取网络文件，带缓冲区。
 */
public class BuffRandReadURL extends RandomRead {
	private static final int BLOCKLEN = 4096 * 8; //32K
	private static final int BUFLEN = BLOCKLEN << 4;
	private static final int OFFMASK = BUFLEN - 1;
	private static final int BUFFERSIZE = BUFLEN - BLOCKLEN;
	private int offset;  // 相对于文件首的偏移量
	private byte[] lock; // 读和写(缓冲)互斥
	private byte[] buf;  // 同时作写线程同步锁
	private int bufsize; // buf已填充的字节数
	private boolean acceptRanges; // true: 目标文件可随机读取定位
	private volatile boolean eof; // true: 文件已经下载完.
	private IAudio audio;
	private HttpConnection connection;

	/**
	 * 创建一个读取网络文件的对象。并不会由audio指定的音频输出对象产生任何音频输出，仅仅使用audio定时刷新并显示缓冲等信息。
	 * @param audio 音频输出对象。如果为<b>null</b>，不显示读取过程中缓冲等信息。
	 */
	public BuffRandReadURL(IAudio audio) {
		this.audio = audio;
		buf = new byte[BUFLEN];
		lock = new byte[0];
		connection = new HttpConnection();
	}

	public boolean open(String spec, String title) throws IOException {
		String s1 = spec.substring(spec.lastIndexOf("/") + 1);
		String s2 = URLDecoder.decode(s1, "GBK");
		s1 = URLDecoder.decode(s1, "UTF-8");
		if (s1.length() > s2.length())
			s1 = s2;
		if(audio != null && title != null)
			audio.refreshMessage(title);

		connection.open(new URL(spec), null);
		int code = connection.getResponseCode();
		if (code < 200 || code >= 300)
			return printErrMsg("URL Connection Fails. ResponseCode: " + code
					+ ", ResponseMessage: " + connection.getResponseMessage());

		/*s2 = connect.getHeaderField("Content-Type");
		if(s2 == null || s2.startsWith("audio") == false)
			return printErrMsg("Illegal Content-Type: " + s2);*/

		if ((length = connection.getContentLength()) <= 0)
			return printErrMsg("Failed to get file length.");

		System.out.println("\nPLAY>> " + s1);

		acceptRanges = "bytes".equals(connection.getHeaderField("Accept-Ranges"));
		//if(!acceptRanges)
		//	System.out.println(url.getHost() + ": not support multi-threaded downloads.");

		// 创建"写"线程
		Writer w = new Writer();
		w.setName("writer_thrad");
		w.setPriority(Thread.NORM_PRIORITY + 2);
		w.start();

		return true;
	}

	private boolean  printErrMsg(String msg) {
		System.out.println();
		connection.printResponse();
		System.err.println(msg);
		return false;
	}

	public int read(byte[] b, int off, int len) {
		// 1.等待缓冲区有足够内容可读
		synchronized (lock) {
			while (bufsize < len && !eof) {
				try {
					waitForBuffering();
				} catch (InterruptedException e) {
					return -1;
				}
			}
			if (bufsize == 0)
				return -1;
			if (bufsize < len)
				len = bufsize;
		}

		// 2.从缓冲区读取
		int srcOff = offset & OFFMASK;
		int bytes = BUFLEN - srcOff;
		if (bytes < len) {
			System.arraycopy(buf, srcOff, b, off, bytes);
			System.arraycopy(buf, 0, b, off + bytes, len - bytes);
		} else
			System.arraycopy(buf, srcOff, b, off, len);
		synchronized (lock) {
			bufsize -= len;
		}
		offset += len;

		// 3.通知"写"线程
		synchronized (buf) {
			buf.notify();
		}

		return len;
	}

	private void waitForBuffering() throws InterruptedException {
		String msg;
		float kbps;
		long t, t1 = System.currentTimeMillis();
		while (bufsize < BUFFERSIZE && !eof) {
			lock.wait();
			if((t = System.currentTimeMillis() - t1) < 200)
				continue;
			kbps = (float) (BUFLEN >> 10) * 1000 / t;
			msg = String.format("\rbuffered: %6.2f%%, %6.02fKB/s ",
					100f * bufsize / BUFLEN, kbps);
			if(audio != null)
				audio.refreshMessage(msg);
			else
				System.out.print(msg);
			if (t > 10000 && kbps < 8) {
				System.out.println("\nDownloading speed too slow,please try again later.");
				close();
				break;
			}
		}
		System.out.print("\n");
	}

	public long getFilePointer() {
		return offset;
	}

	public void close() {
		// 结束Writer线程
		eof = true;
		synchronized (buf) {
			buf.notify();
		}

		try {
			connection.close();
		} catch (IOException e) {
		}
	}

	public boolean seek(long pos) {
		if(acceptRanges == false)
			return false;
		// 尚未完善随机读取定位...
		return false;
	}

	//=========================================================================
	private class Writer extends Thread {

		public void run() {
			int len, off = 0, rema = 0, retry = 0;
			long bytes = 0;
			InputStream instream = connection.getInputStream();
			if(instream == null)
				return;

			try {
				while (!eof) {
					// 1.等待空闲块
					if (retry == 0) {
						while(!eof) {
							synchronized (lock) {
								if(bufsize <= BUFFERSIZE)
									break;
							}
							synchronized (buf) {
								buf.wait();
							}
						}
						off &= OFFMASK;
						rema = BLOCKLEN;
					}

					// 2.下载一块,超时重试10次
					try {
						while (rema > 0 && !eof) {
							len = (rema < 4096) ? rema : 4096; // 每次读几K合适?
							if ((len = instream.read(buf, off, len)) == -1) {
								eof = true;
								break;
							}
							rema -= len;
							off += len;
							if((bytes += len) >= length) {
								eof = true;
								break;
							}
							//System.out.printf("bytes=%,d  len=%d  rema=%d\n", bytes, len, rema);
						}
					} catch (SocketTimeoutException e) {
						retry++;
						System.out.printf("[B# %,d] Timeout, retry=%d\n",bytes, retry);
						if (retry < 10)
							continue;
						System.out.printf("B# %,d: out of retries. Giving up.\n", bytes);
						eof = true; // 终止下载
					}
					retry = 0;

					// 3.通知读线程
					synchronized (lock) {
						bufsize += BLOCKLEN - rema;
						lock.notify();
					}
				}
			} catch (Exception e) {
				System.out.println("BuffRandReadURL.Writer.run(): " + e.toString());
			} finally {
				eof = true;
				synchronized (lock) {
					lock.notify();
				}
				try {
					instream.close();
				} catch (IOException e) {
				}
			}
			//System.out.println("\nBuffRandReadURL.Writer.run() ret.");
		}
	}
}