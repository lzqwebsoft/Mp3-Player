package org.websoft.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.plaf.metal.MetalSliderUI;

public class ProgressSliderUI extends MetalSliderUI {
	/**
	 * 绘制指示物
	 */
	public void paintThumb(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		// 填充椭圆框为当前thumb位置
		g2d.fillOval(thumbRect.x, thumbRect.y, thumbRect.width - 4,
				thumbRect.height);
		// 也可以帖图(利用鼠标事件转换image即可体现不同状态)
		// g2d.drawImage(image, thumbRect.x, thumbRect.y,
		// thumbRect.width,thumbRect.height,null);
	}
}
