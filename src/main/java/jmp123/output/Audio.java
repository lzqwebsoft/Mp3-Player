/*
 * Audio.java -- 音频输出
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
 * so by contacting the author: <http://jmp123.sf.net/>
 */
package jmp123.output;

import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;

import jmp123.decoder.Header;
import jmp123.decoder.IAudio;

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 * 
 */
public class Audio implements IAudio {
	private SourceDataLine dateline;
	/**音量控制器 */
	private FloatControl volControl;

	@Override
	public boolean open(Header h, String artist) {
		AudioFormat af = new AudioFormat(h.getSamplingRate(), 16,
				h.getChannels(), true, false);
		try {
			dateline = (SourceDataLine) AudioSystem.getSourceDataLine(af);
			dateline.open(af, 8 * h.getPcmSize());
			// dateline.open(af);
			volControl = (FloatControl) dateline.getControl(FloatControl.Type.MASTER_GAIN);
		} catch (LineUnavailableException e) {
			System.err.println("初始化音频输出失败。");
			return false;
		} catch (IllegalArgumentException e) {
			System.err.println("加载音量控制失败。");
		}
		
		dateline.start();
		return true;
	}

	@Override
	public int write(byte[] b, int size) {
		return dateline.write(b, 0, size);
	}

	public void start(boolean b) {
		if (dateline == null)
			return;
		if (b)
			dateline.start();
		else
			dateline.stop();
	}
	
	
	//=============添加的方法===========
	@Override
	public void setLineGain(float gain) {
		if(volControl != null) {
			float newGain = Math.min(Math.max(gain, volControl.getMinimum()), volControl.getMaximum());
			volControl.setValue(newGain);
		}
	}
	
	@Override
	public FloatControl getFloatControl() {
		return volControl;
	}
	//==================================

	@Override
	public void drain() {
		if (dateline != null)
			dateline.drain();
	}

	@Override
	public void close() {
		if (dateline != null) {
			dateline.stop();
			dateline.close();
		}
	}

	@Override
	public void refreshMessage(String msg) {
		System.out.print(msg);
	}
}