package org.websoft.widget;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/**
 * 
 * 自定义的一个列表单元渲染器
 * @author i-zqluo
 *
 */
@SuppressWarnings("serial")
public class MusicListCellRenderer extends JPanel implements ListCellRenderer {
	
	private String musicInfo;
	private int currentIndex;
	private boolean isSelected;
	private Color background;
	
	public MusicListCellRenderer() {
		setOpaque(false);
	}
	
	@Override
	public Component getListCellRendererComponent(JList list, Object value,
			int index, boolean isSelected, boolean cellHasFocus) {
		musicInfo = value.toString();
		currentIndex = index + 1;
		if (currentIndex % 2 == 0) {
			background = new Color(200, 255, 10);
		} else {
			background = new Color(200, 255, 150);
		}
		this.isSelected = isSelected;
		this.setPreferredSize(new Dimension(list.getWidth(), 20));
		repaint();
		return this;
	}
	
	public void paint(Graphics g) {
		super.paint(g);
		g.setColor(background);
		g.fillRect(30, 0, getWidth()-30, getHeight());
		if (isSelected) {
			g.setColor(new Color(0, 255, 0));
			g.fillRect(30, 0, getWidth()-30, getHeight());
		}
		g.setColor(new Color(250, 255, 100));
		g.fillRect(0, 0, 30, 20);
		g.setColor(Color.BLACK);
		g.drawString(String.valueOf(currentIndex), 5, 13);
		g.drawString(musicInfo, 40, 13);
	}

}
