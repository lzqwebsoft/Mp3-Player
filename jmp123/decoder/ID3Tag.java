/*
* ID3Tag.java -- 解析MP3文件的ID3 v1/v2 tag.
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
package jmp123.decoder;

/**
 * 解析MP3文件的ID3 v1/v2 tag的部分信息。<br>
 * ID3 v1: 128-byte，通常位于文件尾。<br>
 * [0-2] 3-byte: ID3 v1标识 ，为'TAG'表示接下来的125字节为ID3 v1的标题等域。<br>
 * [3—32] 30-byte: 标题<br>
 * [33—62] 30-byte: 艺术家<br>
 * [63—92] 30-byte: 专辑名<br>
 * [93—96] 4-byte: 发行年份<br>
 * [97—126] 30-byte: v1.0 -- 注释/附加/备注信息； v1.1 -- 前29字节为注释/附加/备注信息，最后1字节为音轨信息<br>
 * [127] 1-byte : 流派
 * <p>
 * ID3 v2.2/2.3/2.4不同版本的帧结构不同，所以高版本不兼容低版本。详情见官网：<br>
 * <a href="http://www.id3.org/id3v2-00">id3 v2.2</a><br>
 * <a href="http://www.id3.org/id3v2.3.0">id3 v2.3</a><br>
 * <a href="http://www.id3.org/id3v2.4.0-structure">id3 v2.4</a><br>
 * <p>
 * ID2 v2 支持MP3文件内置歌词文本和唱片集图片，实际的情况是，很多网络MP3文件利用它在MP3文件内置广告。所以ID3Tag没有解析内置的歌词，
 * 如果你对其内置的图片感兴趣，可以用{@link #getPicture()}方法获取，图片的媒体类型为"image/png" 或 "image/jpeg"。
 * <p>
 * 网络MP3文件中ID3 v1/v2的标题、艺术家、专辑等域被篡改为某网站的网址或者某人的QQ号，这一情况并不少见，所以ID3Tag在解析时对这些文本
 * 域进行了过滤处理， 这导致你用其它播放器时得到的这些域的信息与ID3Tag解析结果不一致。
 */

public class ID3Tag {
	private byte[] apicData;

	// ID3v1 & ID3v2
	private String title;
	private String artist;
	private String album;
	private String year;

	// ID3v2
	//private String lyrics; // (内嵌的)歌词
	private int version;
	private int exheaderSize;
	private boolean haveID3v2Footer;
	//TEXT_ENCODING[0]应由ISO-8859-1改为GBK ?
	private String[] TEXT_ENCODING = {"GBK", "UTF-16", "UTF-16BE", "UTF-8"};

	//--------------------------------------------------------------------
	// ID3v1 & ID3v2

	/**
	 * 在控制台打印标签信息。
	 */
	public void printTag() {
		//if (lyrics != null)
		//	System.out.println("\r" + lyrics + "\n");
		if (title != null)
			System.out.println("      [ Title] " + title);
		if (artist != null)
			System.out.println("      [Artist] " + artist);
		if (album != null)
			System.out.println("      [ Album] " + album);
		if (year != null)
			System.out.println("      [  Year] " + year);
	}

	/**
	 * 清除标签信息。
	 */
	public void clear() {
		title = artist = album = year = null;
		//lyrics = null;
		version = exheaderSize = 0;
		haveID3v2Footer = false;
		apicData = null;
	}

	/**
	 * 设置歌曲标题指定为指定值。
	 * @param title 歌曲标题。
	 */
	public void settTitle(String title) {
		this.title = title;
	}

	/**
	 * 获取歌曲标题。
	 * @return 歌曲标题。
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * 设置歌曲艺术家指定为指定值。
	 * @param artist 艺术家。
	 */
	public void settArtist(String artist) {
		this.artist = artist;
	}

	/**
	 * 获取歌曲艺术家。
	 * @return 歌曲艺术家。
	 */
	public String getArtist() {
		return artist;
	}

	/**
	 * 获取歌曲唱片集。
	 * @return 歌曲唱片集。
	 */
	public String getAlbum() {
		return album;
	}

	/**
	 * 获取歌曲发行年份。
	 * @return 歌曲发行年份。
	 */
	public String getYear() {
		return year;
	}

	/**
	 * 获取文件中内置的唱片集图片。
	 * @return 唱片集图片。图片的MIME类型为"image/png" 或 "image/jpeg"，返回null表示文件没有内置的唱片集图片。
	 */
	public byte[] getPicture() {
		return apicData;
	}

	// ID3v1 ------------------------------------------------------------------

	/**
	 * 检测是否有ID3 v1标签信息。源数据可用长度不少于3字节。
	 * 
	 * @param b
	 *            源数据。
	 * @param off
	 *            源数据偏移量。
	 * @return 有ID3 v1标签信息返回true，否则返回false。
	 */
	public boolean checkID3V1(byte[] b, int off) {
		return b[off] == 'T' && b[off + 1] == 'A' && b[off + 2] == 'G';
	}

	/**
	 * 解析ID3 v1标签信息。源数据可用长度不少于128字节。
	 * 
	 * @param b
	 *            源数据。
	 * @param off
	 *            源数据偏移量。
	 */
	public void parseID3V1(byte[] b, int off) {
		int i;
		if (b.length < 128 || checkID3V1(b, off) == false)
			return;

		byte[] buf = new byte[125];
		System.arraycopy(b, 3 + off, buf, 0, 125);

		for (i = 0; i < 30 && buf[i] != 0; i++);
		try {
			//由ISO-8859-1改为GBK ?
			if (title == null)
				title = new String(buf, 0, i, "gbk");//.replaceAll("[^\u4e00-\u9fa5]", "");
			if (title.length() == 0)
				title = null;

			for (i = 30; i < 60 && buf[i] != 0; i++);
			if (artist == null)
				artist = new String(buf, 30, i-30, "gbk").replaceAll("[^\u4e00-\u9fa5]", "");
			if (artist.length() == 0)
				artist = null;

			for (i = 60; i < 90 && buf[i] != 0; i++);
			if (album == null)
				album = new String(buf, 60, i-60, "gbk");//.replaceAll("[^\u4e00-\u9fa5]", "");
			if (album.length() == 0)
				album = null;

			for (i = 90; i < 94 && buf[i] != 0; i++);
			if (year == null)
				year = new String(buf, 90, i-90, "gbk").replaceAll("[^0-9]", "");
			if (year.length() == 0)
				year = null;
		} catch (Exception e) {
			 e.printStackTrace();
		}

		buf = null;
	}

	// ID3v2 ------------------------------------------------------------------

	/**
	 * 获取ID3 v2标签信息长度。源数据可用长度不少于头信息长度10字节。
	 * 
	 * @param b
	 *            源数据。
	 * @param off
	 *            源数据偏移量。
	 * @return 标签信息长度，单位“字节”。以下两种情况返回0：
	 *         <ul>
	 *         <li>如果源数据b偏移量off开始的数据内未检测到ID3 v2标签信息；</li>
	 *         <li>如果源数据b的可用长度少于少于头信息长度10字节。</li>
	 *         </ul>
	 */
	public int checkID3V2(byte[] b, int off) {
		if (b.length - off < 10)
			return 0;
		if (b[off] != 'I' || b[off + 1] != 'D' || b[off + 2] != '3')
			return 0;

		version = b[off + 3] & 0xff;

		if (version > 2 && (b[off + 5] & 0x40) != 0)
			exheaderSize = 1; // 设置为1表示有扩展头

		haveID3v2Footer = (b[off + 5] & 0x10) != 0;
		int size = synchSafeInt(b, off + 6);
		size += 10; // ID3 header:10-byte
		return size;
	}

	/**
	 * 解析ID3 v2标签信息。从源数据b偏移量off开始的数据含ID3 v2头信息的10字节。
	 * 
	 * @param b
	 *            源数据。
	 * @param off
	 *            源数据偏移量。
	 * @param len
	 *            源数据长度。
	 */
	public void parseID3V2(byte[] b, int off, int len) {
		int max_size = off + len;
		int pos = off + 10;	//ID3 v2 header:10-byte
		if(exheaderSize == 1) {
			exheaderSize = synchSafeInt(b, off);
			pos += exheaderSize;
		}
		max_size -= 10;		//1 frame header: 10-byte
		if(haveID3v2Footer)
			max_size -= 10;

		//System.out.println("ID3 v2." + version);
		while(pos < max_size)
			pos += getFrame(b, pos, max_size);
	}

	private int synchSafeInt(byte[] b, int off) {
		int i = (b[off] & 0x7f) << 21;
		i |= (b[off + 1] & 0x7f) << 14;
		i |= (b[off + 2] & 0x7f) << 7;
		i |= b[off + 3] & 0x7f;
		return i;
	}

	private int byte2int(byte[] b, int off, int len) {
		int i, ret = b[off] & 0xff;
		for (i = 1; i < len; i++) {
			ret <<= 8;
			ret |= b[off + i] & 0xff;
		}
		return ret;
	}

	private int getFrame(byte[] b, int off, int endPos)  {
		int id_part = 4, frame_header = 10;
		if(version == 2) {
			id_part = 3;
			frame_header = 6;
		}
		String id = new String(b, off, id_part);
		off += id_part;		// Frame ID

		int fsize, len;
		if(version <= 3)
			fsize = len = byte2int(b, off, id_part);//Size  $xx xx xx xx
		else
			fsize = len = synchSafeInt(b,off);		//Size 4 * %0xxxxxxx
		if (fsize < 0)		// 防垃圾数据
			return frame_header;

		off += id_part;		// frame size = frame id bytes
		if (version > 2)
			off += 2;		// flag: 2-byte

		int enc = b[off];
		len--;				// Text encoding: 1-byte
		off++;				// Text encoding: 1-byte
		if (len <= 0 || off + len > endPos || enc < 0 || enc >= TEXT_ENCODING.length)
			return fsize + frame_header;
		//System.out.println(len+" -------- off = " + off);
		//System.out.println("ID: " + id + ", id.hashCode()=" + id.hashCode());
		//System.out.println("text encoding: " + TEXT_ENCODING[enc]);
		//System.out.println("frame size: " + fsize);
		//if(off>=171)
		//	System.out.println(len+" -------- off = " + off);

		try {
			switch(id.hashCode()) {
			case 83378:		// TT2: (ID3 v2.2)标题
			case 2575251:	//TIT2:  标题
				if (title == null)
					title = new String(b, off, len, TEXT_ENCODING[enc]).replaceAll("[^\u4e00-\u9fa5]", "");
				break;
			case 83552:
			case 2590194:	//TYER:  发行年
				if (year == null)
					year = new String(b, off, len, TEXT_ENCODING[enc]).replaceAll("[^0-9]", "");
				break;
			case 2569358:	//TCON:  流派
				break;
			case 82815:
			case 2567331:	//TALB:  唱片集
				if (album == null)
					album = new String(b, off, len, TEXT_ENCODING[enc]).replaceAll("[^\u4e00-\u9fa5]", "");
				break;
			case 83253:
			case 2581512:	//TPE1:  艺术家
				if (artist == null) {
					artist = new String(b, off, len, TEXT_ENCODING[enc]);
					artist = artist.split("[^\u4e00-\u9fa5]")[0];
				}
				break;
			case 2583398:	//TRCK:  音轨
				break;
			/*case 2614438:	//USLT:  歌词
				off += 4;	//Languge: 4-byte
				len -= 4;
				//System.out.println(new String(b, off, len, TEXT_ENCODING[enc]));
				lyrics = new String(b, off, len, TEXT_ENCODING[enc]);
				break;*/
			case 2015625:	//APIC
				//MIMEtype: "image/png" or "image/jpeg"
				for(id_part = off; b[id_part]!= 0 && id_part < endPos; id_part++);
				String MIMEtype = new String(b, off, id_part-off, TEXT_ENCODING[enc]);
				System.out.println("[APIC MIME type] " + MIMEtype);
				len -= id_part - off + 1;
				off = id_part + 1;
				int picture_type = b[off] & 0xff;
				System.out.println("[APIC Picture type] "+picture_type);
				off++;	//Picture type
				len--;
				for(id_part = off; b[id_part]!= 0 && id_part < endPos; id_part++);
				System.out.println("[APIC Description] "
								+ new String(b, off, id_part - off, TEXT_ENCODING[enc]));
				len -= id_part - off + 1;
				off = id_part + 1;
				//<text string according to encoding> $00 (00)
				if(b[off] == 0) { //(00)
					len--;
					off++;
				}
				//Picture data (binary data): 从b[off]开始的len字节
				if (apicData == null) {
					apicData = new byte[len];
					System.arraycopy(b, off, apicData, 0, len);
					// 内置于MP3的图片存盘
					/* try {
						String ext = MIMEtype.substring(MIMEtype.indexOf('/')+1);
						FileOutputStream fos = new FileOutputStream("apic."+ext);
						fos.write(b, off, len);
						fos.flush();
						fos.close();
					} catch (Exception e) {}*/
				}
				break;
			}
		} catch (Exception e) {}

		return fsize + frame_header;
	}

	// APE tag ----------------------------------------------------------------
	private int apeVer;

	private int apeInt32(byte[] b, int off) {
		if(b.length - off < 4)
			return 0;
		return ((b[off + 3] & 0xff) << 24) | ((b[off + 2] & 0xff) << 16)
				| ((b[off + 1] & 0xff) << 8) | (b[off] & 0xff);
	}

	/**
	 * 获取APE标签信息长度。源数据b的可用长度不少于32字节。
	 * 
	 * @param b
	 *            源数据。
	 * @param off
	 *            源数据偏移量。
	 * @return APE标签信息长度。以下两种情况返回0：
	 *         <ul>
	 *         <li>如果源数据b偏移量off开始的数据内未检测到APE标签信息；</li>
	 *         <li>如果源数据b的可用长度少于32字节。</li>
	 *         </ul>
	 */
	public int checkAPEtagFooter(byte[] b, int off) {
		if (b.length - off < 32)
			return 0;
		if (b[off] == 'A' && b[off + 1] == 'P' && b[off + 2] == 'E'
				&& b[off + 3] == 'T' && b[off + 4] == 'A' && b[off + 5] == 'G'
				&& b[off + 6] == 'E' && b[off + 7] == 'X') {
			apeVer = apeInt32(b, off + 8);
			return apeInt32(b, off + 12) + 32;
		}
		return 0;
	}

	/**
	 * 获取APE标签信息版本。
	 * @return 以整数形式返回APE标签信息版本。
	 */
	public int getApeVer() {
		return apeVer;
	}
}
