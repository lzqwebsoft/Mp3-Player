package jmp123.gui.album;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class AlbumFrame extends JFrame{
	private JPanel contentPanel;
	private Image image;
	private int width, height;

	public AlbumFrame(String title) {
		contentPanel = new JPanel() {
			public void paint(Graphics g) {
				if (image != null)
					g.drawImage(image, 0, 0, null);
			}
		};

		this.setContentPane(contentPanel);
		this.setResizable(false);
		this.setTitle(title);
		//this.setFocusableWindowState(false);
		this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	}

	public AlbumFrame(byte[] b, String title) {
		this(title);
		updateImage(new ImageIcon(b).getImage());
	}
	
	public AlbumFrame(String location, String title) throws MalformedURLException {
		this(title);
		updateImage(new ImageIcon(new URL(location)).getImage());
	}

	public void updateImage(Image img) {
		this.image = img;
		width = img.getWidth(null);
		height = img.getHeight(null);
		contentPanel.setSize(width, height);
		contentPanel.setPreferredSize(new Dimension(width, height));
		this.pack();
		width = this.getWidth();
		height = this.getHeight();

		this.repaint();
	}
	
	public void setPosition(int x, int y) {
		this.setBounds(x, y, width, height);
	}
}
