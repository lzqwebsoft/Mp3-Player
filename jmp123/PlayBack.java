/*
 * PlayBack.java -- 播放一个文件
 * Copyright (C) 2011
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
package jmp123;

import java.io.IOException;

import javax.swing.JSlider;

import org.websoft.widget.SpectrumPane;

import jmp123.decoder.Header;
import jmp123.decoder.IAudio;
import jmp123.decoder.ID3Tag;
import jmp123.decoder.Layer123;
import jmp123.decoder.Layer1;
import jmp123.decoder.Layer2;
import jmp123.decoder.Layer3;
import jmp123.instream.BuffRandReadFile;
import jmp123.instream.BuffRandReadURL;
import jmp123.instream.RandomRead;
import jmp123.instream.MultiplexAudio;

/**
 * 播放一个文件及播放时暂停等控制。用PlayBack播放一个文件的步骤为：
 * <ol>
 * <li>用构造器 {@link #PlayBack(IAudio)} 创建一个PlayBack对象；</li>
 * <li>调用PlayBack对象的 {@link #open(String, String)} 打开源文件；</li>
 * <ul>
 * <li>可以调用PlayBack对象的 {@link #getHeader()} 方法获取 {@link jmp123.decoder.Header} 对象；</li>
 * <li>可以调用PlayBack对象的 {@link #getID3Tag()} 方法获取 {@link jmp123.decoder.ID3Tag} 对象；</li>
 * </ul>
 * <li>调用PlayBack对象的 {@link #start(boolean)} 开始播放；</li>
 * <ul>
 * <li>可调用PlayBack对象的 {@link #pause()} 方法控制播放暂停或继续；</li>
 * <li>可调用PlayBack对象的 {@link #stop()} 方法终止播放；</li>
 * </ul>
 * <li>播放完一个文件，调用PlayBack对象的 {@link #close()} 作必要的清理。</li>
 * </ol>
 * 
 */
public class PlayBack {
	private byte[] buf;
	private final int BUFLEN = 8192;
	private boolean eof, paused;
	private RandomRead instream;
	private ID3Tag id3tag;
	private int off, maxOff;
	private Header header;
	private IAudio audio;
	private float currentVolume = 0.0f;

	/**
	 * 用指定的音频输出对象构造一个PlayBack对象。
	 * 
	 * @param audio
	 *            指定的音频输出 {@link jmp123.decoder.IAudio} 对象。若指定为 <b>null</b> 则只解码不播放输出。
	 * @see jmp123.output.Audio
	 */
	public PlayBack(IAudio audio) {
		this.audio = audio;
		header = new Header();
		id3tag = new ID3Tag();
		buf = new byte[BUFLEN];
	}

	/**
	 * 暂停或继续此文件播放。这相当于一个单稳态的触发开关，第一次调用该方法暂停播放，第二次调用继续播放，以此类推。
	 * @return 返回当前状态。处于暂停状态返回true，否则返回false。
	 */
	public boolean pause() {
		audio.start(paused);

		if (paused) {
			synchronized (this) {
				notify();
			}
		}
		paused = !paused;

		return paused;
	}

	/**
	 * 终止此文件播放。
	 */
	public void stop() {
		eof = true;
		synchronized (this) {
			notify();
		}

		if (instream != null)
			instream.close();
	}

	/**
	 * 关闭此文件播放并清除关联的资源。
	 */
	public void close() {
		if (id3tag != null)
			id3tag.clear();
		if (audio != null)
			audio.close();

		// 若正读取网络文件通过调用close方法中断下载(缓冲)
		if (instream != null)
			instream.close();
		//System.out.println("jmp123.PlayBack.close() ret.");
	}

	/**
	 * 打开文件并解析文件信息。
	 * 
	 * @param name
	 *            文件路径。
	 * @param title
	 *            歌曲标题，可以为null。
	 * @return 打开失败返回 <b>false</b>；否则返回 <b>true</b> 。
	 * @throws IOException 发生I/O错误。
	 */
	public boolean open(String name, String title) throws IOException {
		maxOff = off = 0;
		paused = eof = false;

		boolean id3v1 = false;
		String str = name.toLowerCase();
		if (str.startsWith("http://") && str.endsWith(".mp3")) {
			instream = new BuffRandReadURL(audio);
		} else if (str.endsWith(".mp3")) {
			instream = new BuffRandReadFile();
			id3v1 = true;
		} else if (str.endsWith(".dat") || str.endsWith(".vob")) {
			instream = new MultiplexAudio();
		} else {
			System.err.println("Invalid File Format.");
			return false;
		}

		if (instream.open(name, title) == false)
			return false;

		int tagSize = parseTag(id3v1);
		if (tagSize == -1)
			return false;

		// 初始化header. 设置文件的音乐数据长度,用于CBR格式的文件计算帧数
		header.initialize(instream.length() - tagSize, instream.getDuration());

		// 定位到第一帧并完成帧头解码
		nextHeader();
		if (eof)
			return false;

		if (audio != null && title != null) {
			// 歌曲的标题和艺术家，优先使用播放列表(*.m3u)中指定的参数
			String[] strArray = title.split(" ");
			if (strArray.length >= 2) {
				// if (id3Tag.getTitle() == null)
				id3tag.settTitle(strArray[0]);
				// if (id3Tag.getArtist() == null)
				id3tag.settArtist(strArray[1]);

				/*StringBuilder strbuilder = new StringBuilder();
				strbuilder.append(id3Tag.getTitle());
				strbuilder.append('@');
				strbuilder.append(id3Tag.getArtist());
				audio.refreshMessage(strbuilder.toString());*/
			}
		}

		// 成功解析帧头后初始化音频输出
		if (audio != null && audio.open(header, id3tag.getArtist()) == false)
			return false;
		
		////////////////////////
		////添加方法/////////////
		////////////////////////
		if(audio instanceof SpectrumPane) {
			SpectrumPane sp = (SpectrumPane)audio;
			sp.setPlayFileName(title.substring(0, title.lastIndexOf(".")));
		}

		return true;
	}

	private int parseTag(boolean id3v1) throws IOException {
		int tagSize = 0;

		// ID3 v1
		if (id3v1 && instream.seek(instream.length() - 128 - 32)) {
			if (instream.read(buf, 0, 128 + 32) == 128 + 32) {
				if (id3tag.checkID3V1(buf, 32)) {
					tagSize = 128;
					id3tag.parseID3V1(buf, 32);
				}
			} else
				return -1;
			instream.seek(0);
			tagSize += id3tag.checkAPEtagFooter(buf, 0); // APE tag footer
		}

		if ((maxOff = instream.read(buf, 0, BUFLEN)) <= 10) {
			eof = true;
			return -1;
		}

		// ID3 v2
		int sizev2 = id3tag.checkID3V2(buf, 0);
		tagSize += sizev2;
		if (sizev2 > maxOff) {
			byte[] b = new byte[sizev2];
			System.arraycopy(buf, 0, b, 0, maxOff);
			sizev2 -= maxOff;
			if ((maxOff = instream.read(b, maxOff, sizev2)) < sizev2) {
				eof = true;
				return -1;
			}
			id3tag.parseID3V2(b, 0, b.length);
			if ((maxOff = instream.read(buf, 0, BUFLEN)) <= 4)
				eof = true;
		} else if (sizev2 > 10) {
			id3tag.parseID3V2(buf, 0, sizev2);
			off = sizev2;
		}
		return tagSize;
	}

	/**
	 * 获取帧头信息。
	 * 
	 * @return 取帧 {@link jmp123.decoder.Header} 对象。
	 * @see jmp123.decoder.Header
	 */
	public Header getHeader() {
		return header;
	}

	/**
	 * 获取文件的标签信息。
	 * 
	 * @return 文件的标签信息 {@link jmp123.decoder.ID3Tag} 对象。
	 * @see jmp123.decoder.ID3Tag
	 */
	public ID3Tag getID3Tag() {
		return id3tag;
	}

	/**
	 * 解码已打开的文件。
	 * 
	 * @param verbose
	 *            指定为 <b>true</b> 在控制台打印播放进度条。
	 * @return 成功播放指定的文件返回true，否则返回false。
	 */
	public boolean start(boolean verbose) {
		Layer123 layer = null;
		int frames = 0;
		paused = false;

		switch (header.getLayer()) {
		case 1:
			layer = new Layer1(header, audio);
			break;
		case 2:
			layer = new Layer2(header, audio);
			break;
		case 3:
			layer = new Layer3(header, audio);
			break;
		default:
			return false;
		}

		try {
			while (!eof) {
				// 1. 解码一帧并输出(播放)
				off = layer.decodeFrame(buf, off);

				if (verbose && (++frames & 0x7) == 0)
					header.printProgress();

				// 2. 定位到下一帧并解码帧头
				nextHeader();

				// 3. 检测并处理暂停
				if (paused) {
					synchronized (this) {
						while (paused && !eof)
							wait();
					}
				}
			}

			if (verbose) {
				header.printProgress();
				System.out.println("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			// System.out.println("jmp123.PlayBack.start() interrupt.");
		} finally {
			if (layer != null)
				layer.close();
		}
		// System.out.println("jmp123.PlayBack.start() ret.");

		return true;
	}
	
	//=======================添加的方法=====================
	/**
	 * 播放从start帧到end的音乐段
	 * @param start 开始帧的位置
	 * @param end 结束帧的位置
	 * @throws IOException
	 */
	public boolean  start(long start, long end, JSlider progressBar, JSlider volumeBar) {
		//当前帧的总长度
		long frameCount = header.getTrackFrames();
		if(end > frameCount)
			end = frameCount;
		
		Layer123 layer = null ; //, layer2 = null;
		paused = false;

		switch (header.getLayer()) {
		case 1:
			layer = new Layer1(header, audio);
//			layer2 = new Layer1(header, null);
			break;
		case 2:
			layer = new Layer2(header, audio);
//			layer2 = new Layer2(header, null);
			break;
		case 3:
			layer = new Layer3(header, audio);
//			layer2 = new Layer3(header, null);
			break;
		default:
			return false;
		}

		try {
			while (!eof&&end-->0) {
				if (start-->0) {
					// 1.1. 解码一帧不输出(不播放)
					// 如果不播放则可以不需要去解码这个帧，则直接跳过这一帧
//					off = layer2.decodeFrame(buf, off); 
				}
				else {
					//设置音量
					//
					if(volumeBar!=null) {
						float currentValue = volumeBar.getValue();
						float maxValue = 0; //audio.getFloatControl().getMaximum();
						float minValue = audio.getFloatControl().getMinimum()+30;
						audio.setLineGain(currentValue/100*(maxValue-minValue)+minValue);
					} else {
					audio.setLineGain(currentVolume); }
					// 1.2. 解码一帧并输出(播放)
					off = layer.decodeFrame(buf, off);
				}
				// 2. 定位到下一帧并解码帧头
				nextHeader();

				// 3. 检测并处理暂停
				if (paused) {
					synchronized (this) {
						while (paused && !eof)
							wait();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (layer != null)
				layer.close();
		}
		return true;
	}
	//====================================================================

	private void nextHeader() throws IOException {
		int len, chunk = 0;
		while (!eof && header.syncFrame(buf, off, maxOff) == false) {
			// buf内帧同步失败或数据不足一帧，刷新缓冲区buf
			off = header.offset();
			len = maxOff - off;
			System.arraycopy(buf, off, buf, 0, len);
			maxOff = len + instream.read(buf, len, off);
			off = 0;
			if( maxOff <= len || (chunk += BUFLEN) > 0x10000)
				eof = true;
		}
		off = header.offset();
	}
	
	public void setVolume(float volume) {
		currentVolume = volume;
	}
	
	public float getVolume() {
		return currentVolume;
	}
}
