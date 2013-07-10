package org.websoft.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JSlider;
import javax.swing.Timer;


/**
 * 使用JSilder来显示播放的进度
 * @author i-zqluo
 *
 */
public class ProcessBarTimer implements ActionListener {
	
	public static int DELAY = 1500;  //每次触发的时间间隔
	
	private long sumTimes;  //播放总的时间
	private JSlider processBar; //进度条
	private Timer timer;
	private long times;    //记录触发的次数
	private boolean paused;          //判断是否暂停
	
	public ProcessBarTimer(long sumTimes, JSlider processBar) {
		this.sumTimes = sumTimes*1000;
		this.processBar = processBar;
		//创建一个在指定的时间段内触发的Timer
		timer = new Timer(DELAY, this);
	}
	
	public void actionPerformed(ActionEvent e) {
		times = times + 1;
		long currentTime = times * DELAY;
		int value = (int)(currentTime*100/sumTimes);
		processBar.setValue(value);
	}
	
	// 暂停
	public boolean pause() {
		if(paused) {
			timer.start();
		} else {
			timer.stop();
		}
		paused = !paused;
		
		return paused;
	}
	
	// 停止
	public void stop() {
		timer.stop();
		times = 0;
	}
	
	// 开始
	public void start() {
		timer.start();
	}
	
	// 用于改变times的值
	public void setTimes(int times) {
		this.times = times;
	}
}
