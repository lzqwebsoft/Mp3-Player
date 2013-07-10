package org.websoft.widget;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.sound.sampled.FloatControl;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

import jmp123.decoder.Header;
import jmp123.decoder.IAudio;
import jmp123.gui.album.AlbumFrame;
import jmp123.gui.album.AlbumReceiver;
import jmp123.gui.album.AlbumThread;
import jmp123.output.Audio;
import jmp123.output.FFT;

/**
 * 一个承载频普图的面板
 * @author i-zqluo
 */
@SuppressWarnings("serial")
public class SpectrumPane extends JPanel implements IAudio, AlbumReceiver {
	// FFT
	private float realIO[];
	private FFT fft;
	
	// 音频输出
	private Audio theAudio;
	
	private final int band, width, height;
	private int maxFs, histogramType=3, deltax;
	private long lastTimeMillis;
	private static final int maxColums = 128;
	private boolean isEnable;
	private int[] xplot, lastPeak, lastY;
	private BufferedImage spectrumBufferdImage, barBufferedImage;
	private Graphics2D spectrumGraphics;
	private Color crPeak;
	private Header heder;
	private AlbumThread albumThread;
	private static final int Y0 = 1 << ((FFT.FFT_N_LOG + 3) << 1);
	private static final double logY0 = Math.log10(Y0); //lg((8*FFT_N)^2)
	private AlbumFrame albumFrame;
	private String filename;
	
	public SpectrumPane(int sampleRate, int width, int height) {
		
		setPreferredSize(new Dimension(width, height));
		setOpaque(false);
		
		fft = new FFT();
		realIO = new float[FFT.FFT_N];
		
		
		this.width = width;	 // 频谱窗口
		this.height = height;
		band = 31;		// 31段频谱
		isEnable = true;
		maxFs = sampleRate >> 1;
		lastTimeMillis = System.currentTimeMillis();
		xplot = new int[maxColums + 1];
		lastPeak = new int[maxColums];
		lastY = new int[maxColums];
		
		spectrumBufferdImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		spectrumGraphics = (Graphics2D)spectrumBufferdImage.getGraphics();
		
		setPlot(width);
		barBufferedImage = new BufferedImage(deltax - 1, height, BufferedImage.TYPE_3BYTE_BGR);
		render(0xe0e0ff, 0xff0000, 0xffff00, 0xb0b0ff);
		
		java.awt.Font f = spectrumGraphics.getFont();
		java.awt.Font myFont = new java.awt.Font(f.getName(), java.awt.Font.PLAIN, 18);
		spectrumGraphics.setFont(myFont);
	}
	
	/**
	 * 划分频段。
	 */
	private void setPlot(int width) {
		deltax = (width - band + 1) / band + 1;

		// fsband个频段落重新分划为band个频段，各频段宽度非线性划分。
		int fsband = FFT.FFT_N >> 1;
		if(maxFs > 20000) {
			float deltaFs = (float)maxFs / (FFT.FFT_N >> 1);
			fsband -= (maxFs - 20000) / deltaFs;
		}
		for (int i = 0; i <= band; i++) {
			xplot[i] = 0;
			xplot[i] = (int) (0.5 + Math.pow(fsband, (double) i / band));
			if (i > 0 && xplot[i] <= xplot[i - 1])
				xplot[i] = xplot[i - 1] + 1;
		}
	}
	
	private void render(int rgbPeak, int rgbTop, int rgbMid, int rgbBot) {
		crPeak = new Color(rgbPeak);
		spectrumGraphics.setColor(crPeak);

		Graphics2D g2d = (Graphics2D) barBufferedImage.getGraphics();
		Color crTop = new Color(rgbTop);
		Color crMid = new Color(rgbMid);
		Color crBot = new Color(rgbBot);
		g2d.setPaint(new GradientPaint(0, 0, crTop, deltax - 1, height / 2, crMid));
		g2d.fillRect(0, 0, deltax - 1, height / 2);
		g2d.setPaint(new GradientPaint(0, height / 2, crMid, deltax - 1, height, crBot));
		g2d.fillRect(0, height / 2, deltax - 1, height);
	}
	
	/**
	 * 绘制"频率-幅值"直方图并显示到屏幕。
	 * @param amp amp[0..FFT_N/2-1]为频谱"幅值"(复数模的平方)。
	 */
	private void drawHistogram(float[] amp) {
		float maxAmp;
		int i = 0, x = 1, y, xi, peaki, w = deltax - 1;
		long t = System.currentTimeMillis();
		int speed = (int)(t - lastTimeMillis) / 40;	//峰值下落速度
		lastTimeMillis = t;
		
//		drawBackground();
		spectrumGraphics.clearRect(0, 0, width, height);
		//spectrumGraphics.drawString(title, 10, 40);
		
		for (; i != band; i++, x += deltax) {
			// 查找当前频段的最大"幅值"
			maxAmp = 0; xi = xplot[i]; y = xplot[i + 1];
			for (; xi < y; xi++) {
				if (amp[xi] > maxAmp)
					maxAmp = amp[xi];
			}

			/*
			 * maxAmp转换为用对数表示的"分贝数"y:
			 * y = (int) Math.sqrt(maxAmp);
			 * y /= FFT_N; //幅值
			 * y /= 8;	//调整
			 * if(y > 0) y = (int)(Math.log10(y) * 20 * 1.7);
			 * 
			 * 为了突出幅值y显示时强弱的"对比度"，计算时作了调整。y不是严格意义上的分贝数，并且未作等响度修正。
			 */
			y = (maxAmp > Y0) ? (int) ((Math.log10(maxAmp) - logY0) * 17) : 0;

			// 使幅值匀速度下落
			lastY[i] -= speed << 2;
			if(y < lastY[i]) {
				y = lastY[i];
				if(y < 0) y = 0;
			}
			lastY[i] = y;

			if(y >= lastPeak[i]) {
				lastPeak[i] = y + 2;
			} else {
				// 使峰值匀速度下落
				peaki = lastPeak[i] - speed;
				if(peaki < 0)
					peaki = 0;
				lastPeak[i] = peaki;
				peaki = height - peaki;
				spectrumGraphics.setColor(Color.red);
				spectrumGraphics.drawLine(x, peaki, x + w - 1, peaki);
			}
			y = height - y;	//坐标转换

			// 画当前频段的直方图
			switch (histogramType) {
			case 0:
				spectrumGraphics.drawImage(barBufferedImage, x, y, x + w,
						height, 0, y, w, height, null);
				break;
			case 1:
				spectrumGraphics.drawRect(x, y, w - 1, height);
				break;
			case 2:
				spectrumGraphics.fillRect(x, y, w, height);
				break;
			case 3:
				spectrumGraphics.drawImage(barBufferedImage, x, y, null);
				break;
			}
		}

		// 文字信息
		spectrumGraphics.setFont(new Font("", Font.PLAIN, 14));
		float dura = this.heder.getDuration();
		i = (int) (dura / 60);
		x = this.heder.getElapse();
		y = x / 60;
		String strMsg = String.format("[%02d:%02d] %02d:%02d", i,
				(int) (dura - i * 60 + 0.5), y, x - y * 60);
		spectrumGraphics.drawString(strMsg, 5, 20);
		
		//滚动显示正在播放的歌曲名
		if(filename!=null&&filename.trim().length()!=0) {
			spectrumGraphics.setColor(Color.BLUE);
			String tempfile = filename;
			filename = tempfile.substring(2)+ filename.substring(0, 2);
			spectrumGraphics.drawString(filename, 120, 20);
		}

		// 刷到屏幕
		repaint();
	}

	@Override
	public void close() {
		if(theAudio != null)
			theAudio.close();

		//中断播放当前文件时可能搜索唱片集的线程(albumThread)正在运行,终止并等待其退出.
		if (albumThread != null) {
			albumThread.interrupt();
			/*try {
				albumThread.join();
			} catch (InterruptedException e) {
			}*/
		}
		
//		// 清除,准备播放下一文件
//		albumImage = null;
//		albumDisplayTick = albumImageIndex = albumImageWidth = 0;
//		if (albumImageList != null) {
//			synchronized (albumImageList) {
//				albumImageList.clear();
//			}
//		}
		
		spectrumGraphics.clearRect(0, 0, width, height);
		repaint(0, 0, width, height);
	}

	@Override
	public void drain() {
		theAudio.drain();
	}

	@Override
	public FloatControl getFloatControl() {
		return theAudio.getFloatControl();
	}

	@Override
	public boolean open(Header h, String artist) {
		this.heder = h;
//		if (playerGUI.miViewAlbumFrame != null) {
//			albumThread = new AlbumThread(this, artist);
//			albumThread.start();
//		}
		theAudio = new Audio();
		return theAudio.open(h, null);
	}

	@Override
	public void refreshMessage(String msg) {
		if (msg.indexOf('@') != -1) {
			// if(!msg.startsWith("null"))
			// title = msg;
		} else {
//			if (bkImage != null)
//				spectrumGraphics.drawImage(bkImage, 0, 0, null);
//			else
				spectrumGraphics.clearRect(0, 0, width, height);
			spectrumGraphics.drawString(msg, 10, 20);
			repaint(0, 0, width, height);
		}
	}

	@Override
	public void setLineGain(float gain) {
		theAudio.setLineGain(gain);
	}

	@Override
	public void start(boolean b) {
		theAudio.start(b);
	}

	@Override
	public int write(byte[] b, int size) {
		int len;

		//1. 音频输出
		if((len = theAudio.write(b, size)) == 0)
			return 0;

		if (isEnable) {
			int i, j;

			//2. 获取PCM数据。如果是双声道，转换为单声道
			if (heder.getChannels() == 2) {
				for (i = 0; i < FFT.FFT_N; i++) {
					j = i << 2;
					// (左声道 + 右声道) / 2
					realIO[i] = (((b[j + 1] << 8) | (b[j] & 0xff))
							+ (b[j + 3] << 8) | (b[j + 2] & 0xff)) >> 1;
				}
			} else {
				for (i = 0; i < 512; i++) {
					j = i << 1;
					realIO[i] = ((b[j + 1] << 8) | (b[j] & 0xff));
				}
			}

			//3. PCM变换到频域并取回模
			fft.getModulus(realIO);

			//4. 绘制
			drawHistogram(realIO);
		}

		return len;
	}
	
	//-------------------------------------------------------------------------
	// 重载父类的2个方法
	
	/**
	 * 根据参数 b 的值显示或隐藏频谱组件。隐藏频谱组件后自动停止对PCM数据的FFT运算并停止获取音频PCM数据。
	 * @param b 如果为 true，则显示频谱组件；否则隐藏频谱组件。
	 */
	public void setVisible(boolean b) {
		super.setVisible(b);
		this.isEnable = b;
	}
	
	/**
	 * 绘制频谱组件。
	 * @param g 用于绘制的图像上下文。
	 */
	public void paint(Graphics g) {
		super.paint(g);
		g.drawImage(spectrumBufferdImage, 0, 0, null);
	}

	@Override
	public void addImageIcon(ImageIcon imgIcon) {
	}

	@Override
	public void completed(boolean interrupted) {
	}

	@Override
	public void ready(String artist) {
		if(albumFrame == null) {
			albumFrame = new AlbumFrame(artist);
		} else
			albumFrame.setTitle(artist);
	}
	
	//////////////////////////
	////自添加的一个方法///////
	//////////////////////////
	public void setPlayFileName(String playFileName) {
		filename = "                                         正在播放: "+playFileName;
	}
}