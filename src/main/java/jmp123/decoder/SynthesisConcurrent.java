/*
 * ConcurrentSynthesis.java -- 一个声道多相频率倒置和多相合成滤波.
 */
package jmp123.decoder;

/**
 * 一个声道多相频率倒置和多相合成滤波。用于两个声道并发运算。<p>
 * 由于大量浮点运算，多相合成滤波耗时最多。解码一帧2声道MP3，实测多相合成滤波耗时占60%以上，若并发运算可提高解码速度。<p>
 * 实测解码（不播放输出）一个64904帧（播放时长28:20）的MP3文件得到的对比数据，可以大致看出异步多相合成滤波对解码速度的影响：
 * <table border="1" cellpadding="8">
 * <tr><th>单线程串行多相合成滤波</th><th>2线程异步多相合成滤波</th><th>绝对加速比</th><th>备注</th></tr>
 * <tr><td>9.85s</td><td>6.47s</td><td>1.52</td><td>client模式</td></tr>
 * <tr><td>6.24s</td><td>4.08s</td><td>1.53</td><td>server模式</td></tr>
 * <tr><td align="left" colspan="4">注(1) CPU：双核2.93GHz / JDK： sun 1.6.0_27
 * <p>(2) 基础测试是一个比较复杂的问题，本次测结果试并不一定是问题的最终结论。</td></tr>
 * </table>
 */
public final class SynthesisConcurrent implements Runnable {
	private int ch;
	private float[] samples;
	private float[][] preXR, curXR; // 和调用者并发运算用双缓冲
	private Layer3 owner;
	private boolean pause, alive;

	/**
	 * 构造SynthesisConcurrent实例。便于调用者实现多相合成滤波并发运算。
	 * 
	 * @param owner
	 *            拥有此多相合成滤波器的对象。
	 * @param ch
	 *            指定的声道：0或1。
	 */
	public SynthesisConcurrent(Layer3 owner, int ch) {
		this.owner = owner;
		this.ch = ch;
		alive = pause = true;
		samples = new float[32];
		preXR = new float[owner.granules][32 * 18];
		curXR = new float[owner.granules][32 * 18];
	}

	/**
	 * 交换缓冲区并唤醒SynthesisConcurrent线程。
	 * 
	 * @return 一个空闲的缓冲区，该缓冲区用于使用SynthesisConcurrent线程的对象在逆量化、抗锯齿和IMDCT时暂存数据。
	 */
	public float[][] startSynthesis() {
		// 1. 交换缓冲区
		float[][] p = preXR;
		preXR = curXR;
		curXR = p;

		// 2. 通知run()干活
		synchronized (this) {
			pause = false;
			notify();
		}

		// 3. 返回"空闲的"缓冲区，该缓冲区内的数据已被run()方法使用完毕
		return preXR;
	}

	/**
	 * 获取一个空闲的缓冲区。
	 * 
	 * @return 一个空闲的缓冲区，该缓冲区用于使用SynthesisConcurrent的对象在逆量化、抗锯齿和IMDCT时暂存数据。
	 */
	public float[][] getBuffer() {
		return preXR;
	}

	/**
	 * 关闭SynthesisConcurrent线程。
	 */
	public synchronized void shutdown() {
		alive = pause = false;
		notify();
	}

	/**
	 * 使用SynthesisConcurrent的对象创建一个线程时，启动该线程将导致在独立执行的线程中调用SynthesisConcurrent的 run
	 * 方法进行异步多相合成滤波。
	 */
	public void run() {
		int gr, sub, ss, i;
		int granules = owner.granules;
		Synthesis filter = owner.filter;
		float[] xr;

		try {
			while (alive) {
				// 1. 休息
				synchronized (this) {
					while (pause)
						wait();
					pause = true;
				}

				// 2. 干活
				for (gr = 0; gr < granules; gr++) {
					xr = curXR[gr];
					for (ss = 0; ss < 18; ss += 2) {
						for (i = ss, sub = 0; sub < 32; sub++, i += 18)
							samples[sub] = xr[i];
						filter.synthesisSubBand(samples, ch);

						for (i = ss + 1, sub = 0; sub < 32; sub += 2, i += 36) {
							samples[sub] = xr[i];

							// 多相频率倒置(INVERSE QUANTIZE SAMPLES)
							samples[sub + 1] = -xr[i + 18];
						}
						filter.synthesisSubBand(samples, ch);
					}
				}

				// 3. 提交结果
				owner.submitSynthesis();
			}
		} catch (InterruptedException e) {
			//e.printStackTrace();
		}
		//System.out.println(Thread.currentThread().getName() + " shutdown. ");
	}
}
