package jmp123.gui;

public class PlayListItem {
	private String title;
	private String path;
	private boolean available;

	public PlayListItem(String title, String path) {
		this.title = title;
		this.path = path;
		available = true;
	}

	public String getPath() {
		return path;
	}

	public boolean available() {
		return available;
	}

	public void enable(boolean b) {
		available = b;
	}

	@Override
	public String toString() {
		return title; // 返回在列表内显示的字符串
	}

}
