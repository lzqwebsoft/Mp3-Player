package org.websoft.widget;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import jmp123.gui.PlayListItem;

@SuppressWarnings("serial")
public class ListPane extends JPanel {
	private JList list;
	private DefaultListModel dataListModel;
	private int curIndex = -1; //当前正在播放的文件
	private int nextIndex; //下一播放的文件
	private int playMode = 2;   //播放模式，默认为顺序播放
	
	public ListPane(int width, int height) {
		setOpaque(false);
		setPreferredSize(new Dimension(width, height));
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder());
//		String[] test = {"test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9"};
		list = new JList();
		//list.setPreferredSize(new Dimension(width-30, height));
		list.setBorder(BorderFactory.createEtchedBorder());
		list.setCellRenderer(new MusicListCellRenderer());
		dataListModel = new DefaultListModel();
		list.setModel(dataListModel);
		JScrollPane scrollPanel = new JScrollPane(list, 
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		add(scrollPanel);
	}
	
	/**
	 * 从播放列表(*.m3u)文件添加到列表。如果文件读取成功，先清空列表再添加。
	 * @param name 播放列表文件名。
	 */
	public void openM3U(String name) {
		BufferedReader br = null;
		java.io.InputStream instream = null;
		int idx;
		StringBuilder info = new StringBuilder("[open M3U] ");
		info.append(name);
		try {
			// 打开以UTF-8格式编码的播放列表文件
			if (name.toLowerCase().startsWith("http://")) {
				URL url = new URL(name);
				HttpURLConnection huc = (HttpURLConnection) url.openConnection();
				huc.setConnectTimeout(5000);
				huc.setReadTimeout(10000);
				instream = huc.getInputStream();
			} else
				instream = new FileInputStream(name);
			br = new BufferedReader(new InputStreamReader(instream,"utf-8"));

			String path, title = br.readLine();
			// BOM: 0xfeff
			if(!"#EXTM3U".equals(title) && !"\ufeff#EXTM3U".equals(title)) {
				info.append("\nIllegal file format.");
				return;
			}
			clear();
			while ((title = br.readLine()) != null && (path = br.readLine()) != null) {
				if (!title.startsWith("#EXTINF")
						|| (idx = title.indexOf(',') + 1) == 0) {
					info.append("\nIllegal file format.");
					break;
				}
				this.append(title.substring(idx), path);
			}
			info.append("\n");
			info.append(this.getCount());
			info.append(" items");
		} catch (IOException e) {
			info.append("\nfalse: ");
			info.append(e.getMessage());
		} finally {
			try {
				if(instream != null)
					instream.close();
				if (br != null)
					br.close();
			} catch (IOException e) {
			}
			System.out.println(info.toString());
		}
	}
	
	/**
	 * 清空列表。
	 */
	public synchronized void clear() {
		nextIndex = 0;
		curIndex = -1;
		dataListModel.clear();
	}
	
	public synchronized void append(String title, String path) {
		dataListModel.addElement(new PlayListItem(title, path));
	}
	
	public synchronized int getCount() {
		return dataListModel.getSize();
	}
	
	/**
	 * 获取下一个可用的列表索引。
	 * 
	 * @return 一个可用的列表索引。当列表中的文件全部不可用时返回值为-1。
	 */
	public synchronized int getNextIndex() {
		int i, count = dataListModel.getSize();
		if (nextIndex == -1) {
			switch(playMode) {
			case 1: break;     //不改变
			case 3: curIndex = (int)(Math.random()*count); break; //任意的
				default: curIndex = (curIndex + 1 == count) ? 0 : curIndex + 1; break;
			}
		}
		else {
			curIndex = nextIndex;
			nextIndex = -1;
		}

		for (i = 0; i < count; i++) {
			PlayListItem item = (PlayListItem) dataListModel.get(curIndex);
			if (item.available()) {
				repaint();
				return curIndex;
			}
			switch(playMode) {
			case 1: break;
			case 3: curIndex = (int)(Math.random()*count); break;
				default: curIndex = (curIndex + 1 == count) ? 0 : curIndex + 1; break;
			}
		}
		return -1;
	}
	
	/**
	 * 用于设置播放的模式
	 * @param
	 */
	public synchronized void setPlayMode(int playMode) {
		this.playMode = playMode;
	}

	/**
	 * 设置下一个即将被播放的文件。同时要中断当前播放的文件时调用此方法。
	 * @param i 下一个即将被播放的文件索引。
	 */
	public synchronized void setNextIndex(int i) {
		nextIndex = (i < 0 || i >= dataListModel.getSize()) ? 0 : i;
	}

	/**
	 * 播放当前文件时是否被用户调用 {@link #setNextIndex(int)} 方法中断。
	 * @return 返回<b>true</b>表示播放当前文件时是否被用户中断，否则返回<b>false</b>。
	 */
	public synchronized boolean isInterrupted() {
		return nextIndex != -1;
	}
	
	/**
	 * 获取当前正在播放的文件的列表索引。
	 * @return 当前正在播放的文件的列表索引。
	 */
	public synchronized int getCurrentIndex() {
		return curIndex;
	}
	
	/**
	 * 从列表中删除指定项。
	 * @param index 将要删除的列表项的索引。
	 */
	public synchronized void removeItem(int index) {
		if(index < 0 || index >= dataListModel.getSize())
			return;
		dataListModel.remove(index);
		if(index == curIndex)
			curIndex = -1;

		if(index >= dataListModel.getSize())
			index = 0;
		nextIndex = index;
		list.setSelectedIndex(index);
	}
	
	/**
	 * 获取当前选中的列表项
	 */
	public int getSelectedIndex() {
		return list.getSelectedIndex();
	}
	
	/**
	 * 设置选中的列表项
	 */
	public void setSelectedIndex(int index) {
		list.setSelectedIndex(index);
	}
	
	/**
	 * 获取指定的列表项。
	 * @param index 列表项的索引。
	 * @return 列表项。
	 */
	public synchronized PlayListItem getPlayListItem(int index) {
		return (PlayListItem) dataListModel.get(index);
	}
	
	/**
	 * 返回列表
	 * @return
	 */
	public JList getMusicList() {
		return list;
	}
}