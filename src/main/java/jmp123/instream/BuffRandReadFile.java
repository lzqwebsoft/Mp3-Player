/*
* BuffRandAcceFile.java -- 本地文件随机读取
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

import java.io.RandomAccessFile;
import java.io.IOException;

public final class BuffRandReadFile extends RandomRead {
	private RandomAccessFile rafIn;

	public boolean open(String name, String title) throws IOException {
		rafIn = new RandomAccessFile(name, "r");
		length = rafIn.length();
		return true;
	}

	public int read(byte b[], int off, int len) throws IOException {
		return rafIn.read(b, off, len);
	}

	public boolean seek(long pos) throws IOException {
		rafIn.seek(pos);
		return true;
	}

	public void close() {
		try {
			rafIn.close();
		} catch (IOException e) {}
	}
}
