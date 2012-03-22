package jmp123.decoder;

/**
 * 解码1帧MPEG Audio LayerⅠ/Ⅱ/Ⅲ 。
 * <p>源码下载： http://jmp123.sf.net/
 * @version 0.3
 */
public abstract class Layer123 {
	protected Synthesis filter;
	private AudioBuffer audioBuf;

	/**
	 * 创建一个指定头信息和音频输出的帧解码器。
	 * 
	 * @param h
	 *            已经解码的帧头信息。
	 * @param audio
	 *            音频输出对象。
	 */
	public Layer123(Header h, IAudio audio) {
		audioBuf = new AudioBuffer(audio, 4 * h.getPcmSize());
		filter = new Synthesis(audioBuf, h.getChannels());
	}

	/**
	 * 从此字节输入流中给定偏移量处开始解码一帧。
	 * 
	 * @param b
	 *            源数据缓冲区。
	 * @param off
	 *            开始解码字节处的偏移量。
	 * @return 源数据缓冲区新的偏移量，用于计算解码下一帧数据的开始位置在源数据缓冲区的偏移量。
	 */
	public abstract int decodeFrame(byte[] b, int off);

	//public void normalizeVolume() {}

	/**
	 * 音频输出。完成一帧多相合成滤波后调用此方法将多相合成滤波输出的PCM数据写入音频输出对象。
	 * @see AudioBuffer#output()
	 */
	public void outputAudio() {
		audioBuf.output();
	}

	/**
	 * 音频输出缓冲区的全部内容刷向音频输出对象并将缓冲区偏移量复位。
	 * 
	 * @see AudioBuffer#flush()
	 */
	public void close() {
		// System.out.println("maxPCM=" + filter.getMaxPCM());
		audioBuf.flush();
	}

}
