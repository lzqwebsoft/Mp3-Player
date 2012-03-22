package org.websoft.widget;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;
import jmp123.gui.PlayListThread;

@SuppressWarnings("serial")
public class PlayWindow extends JFrame
{
	private File currentDirectory;   //当前的文件
	private int locationX, locationY;
	private boolean isDraging = false;  //用于指示拖动
	private SpectrumPane spectrumPane; //频率面板
	private ListPane listPane;   //歌曲面板
	private ControllePane controllePanel; //控制面板
	private PlayListThread playlistThread; //播放线程
	private JPopupMenu popupMenu;    //弹出菜单
	private JMenuItem removeItem;    //删除菜单项
	private JMenuItem deleteItem;    //直接删除文件
	
	public PlayWindow() {
		setSize(300, 500);
		setLocationRelativeTo(null);
		// 去掉标题栏
		setUndecorated(true);
		setTitle("Mp3 Player");
		initializeTray();
		
		JRootPane rootPane= new JRootPane();
		rootPane.setLayout(new BorderLayout());
		
		//==================顶部面板==================
		JPanel topPanel = new JPanel();
		topPanel.setPreferredSize(new Dimension(getWidth(), 27));
		topPanel.setLayout(null);
		//添加一个总的设置菜单
		JMenuBar menuBar = new JMenuBar();
		menuBar.setBorder(BorderFactory.createEtchedBorder());
		JMenu menu = new JMenu("Mp3 Player");
		getMenuItem(menu, "Open file", 'O', KeyEvent.CTRL_DOWN_MASK, "openFile");
		getMenuItem(menu, "Open folder", 'O', KeyEvent.CTRL_DOWN_MASK|KeyEvent.SHIFT_DOWN_MASK, "openFolder");
		menu.addSeparator();
		removeItem = getMenuItem(menu, "Remove", KeyEvent.VK_DELETE, 0, "removeSelectFile");
		removeItem.setEnabled(false);
		deleteItem = getMenuItem(menu, "Delete file", KeyEvent.VK_DELETE, KeyEvent.SHIFT_DOWN_MASK|KeyEvent.SHIFT_MASK, "deleteSelectFile");
		deleteItem.setEnabled(false);
		menu.addSeparator();
		getMenuItem(menu, "Language", 'L', KeyEvent.CTRL_DOWN_MASK, "setLanguage");
		getMenuItem(menu, "About", 'A', KeyEvent.CTRL_DOWN_MASK, "about");
		menu.addSeparator();
		getMenuItem(menu, "Exit", 'E', KeyEvent.CTRL_DOWN_MASK, "exitSystem");
		menuBar.add(menu);
		topPanel.add(menuBar);
		menuBar.setBounds(0, 0, 75, 25);
		
		//添加最小化和关闭按钮
		JButton minButton = getJButton("Minimize", "toMinimize");
		topPanel.add(minButton);
		minButton.setBounds(getWidth()-50, 5, 20, 20);
		
		JButton closeButton = getJButton("Close", "exitSystem");
		topPanel.add(closeButton);
		closeButton.setBounds(getWidth()-25, 5, 20, 20);
		
		rootPane.add(topPanel, BorderLayout.NORTH);
		//=======================================
		
		//================中间面板================
		JPanel centerPane = new JPanel();
		centerPane.setOpaque(false);
		centerPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		centerPane.setLayout(new BorderLayout());
		//频普图
		spectrumPane = new SpectrumPane(41000, getWidth(), 140);
		centerPane.add(spectrumPane, BorderLayout.NORTH);
		//播放控制面板
		controllePanel = new ControllePane(getWidth(), 100);
		centerPane.add(controllePanel, BorderLayout.CENTER);
		controllePanel.getProgressBar().addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if(playlistThread!=null&&listPane.getCount() != 0) {
					playlistThread.stopShowProcess();
				}
			}
			
			public void mouseReleased(MouseEvent e) {
				if(playlistThread!=null&&listPane.getCount() != 0) {
					long countFrame = playlistThread.getSumFrames();
					float frameDuration = playlistThread.getFrameDuration()*1000;
					int rate = playlistThread.rateOfRefresh();
					int currentValue = controllePanel.getProgressBar().getValue();
					long startFrams = (long)(currentValue/100.0f*countFrame);
					int times = (int)(frameDuration*startFrams/rate);
					int currentPlayeIndex = listPane.getCurrentIndex();
					playlistThread.setStartFrame(startFrams, times);
					playlistThread.startPlay(currentPlayeIndex);
				}
			}
		});
		
		// 上一曲
		controllePanel.getPreviousButton().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				playlistThread.playPrevious();
				controllePanel.getProgressBar().setValue(0);
			}
		});
		
		
		// 暂停\播放
		controllePanel.getStartButton().addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				playlistThread.pause();   //播放暂停
			}
		});
		
		// 停止
		controllePanel.getStopButton().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				playlistThread.interrupt();
				controllePanel.getProgressBar().setValue(0);
			}
		});
		
		// 下一曲
		controllePanel.getNextButton().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				playlistThread.playNext();
				controllePanel.getProgressBar().setValue(0);
			}
		});
		// 歌曲列表面板
		listPane = new ListPane(getWidth(), 220);
		listPane.getMusicList().addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
					playlistThread.startPlay(listPane.getMusicList().locationToIndex(e.getPoint()));
					startPlaylistThread();
				} else if(e.getButton() == MouseEvent.BUTTON3) {
					int index = listPane.getMusicList().locationToIndex(e.getPoint());
					listPane.getMusicList().setSelectedIndex(index);
					initPopupMenu();
					popupMenu.show(listPane.getMusicList(), e.getX(), e.getY());
				}
			}});
		centerPane.add(listPane, BorderLayout.SOUTH);
		
		rootPane.add(centerPane, BorderLayout.CENTER);
		//=======================================
		
		//===============底部面板=================
		JPanel bottomPane = new JPanel();
		bottomPane.setOpaque(false);
		bottomPane.setBorder(BorderFactory.createEmptyBorder());
		bottomPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
		//过滤,播放模式，添加
		JButton filterBtn = getJButton("Filter", "filterMusic");
		bottomPane.add(filterBtn);
		JMenuBar bottomBar = new JMenuBar();
		bottomBar.setOpaque(false);
		bottomBar.setBorder(BorderFactory.createEmptyBorder());
		JMenu modeMenu = getJMenu("Play Mode");
		
		String[] itemContents = {"play one", "play list", "play random"};
		int[] keycodes = {'1', '2', '3'};
		initPlayModeMenu(modeMenu, itemContents, keycodes);

		modeMenu.setMenuLocation(bottomBar.getX(), bottomBar.getY()-68);
		bottomBar.add(modeMenu);
		JMenu addMenu = getJMenu("Add file/folder");
		getMenuItem(addMenu, "Add file", 'O', KeyEvent.CTRL_DOWN_MASK, "openFile");
		getMenuItem(addMenu, "Add floder", 'O', KeyEvent.CTRL_DOWN_MASK|KeyEvent.SHIFT_DOWN_MASK, "openFolder");
		addMenu.setMenuLocation(bottomBar.getX(), bottomBar.getY() - 47);
		bottomBar.add(addMenu);
		bottomPane.add(bottomBar);
		
		rootPane.add(bottomPane, BorderLayout.SOUTH);
		//========================================
		this.setContentPane(rootPane);
		addMoveWindowAttribute(topPanel);
		
		setVisible(true);
	}
	
	//添加移动
	private void addMoveWindowAttribute(JComponent comp) {
		comp.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				isDraging = true;
				locationX = e.getX();
				locationY = e.getY();
			}

			public void mouseReleased(MouseEvent e) {
				isDraging = false;
			}
		});
		comp.addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent e) {
				if (isDraging) {
					int left = getLocation().x;
					int top = getLocation().y;
					setLocation(left + e.getX() - locationX, top + e.getY() - locationY);
				}
			}
		});
	}
	
	//初始化托盘
	public void initializeTray() {
		if(SystemTray.isSupported()) {
			SystemTray tray = SystemTray.getSystemTray();
			URL imageUrl = getClass().getResource("../resources/images/trayIcon.png");
			Image image = Toolkit.getDefaultToolkit().getImage(imageUrl);
			
			PopupMenu popup = new PopupMenu();
			MenuItem item = new MenuItem("Exit");
			item.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					exitSystem();
				}
			});
			popup.add(item);
			
			TrayIcon trayIcon = new TrayIcon(image, "Mp3播放器", popup);
			// 添加单击显示事件
			trayIcon.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					PlayWindow.this.setVisible(true);
					PlayWindow.this.toFront();
				}
			});
			try {
				tray.add(trayIcon);
			} catch(AWTException e) {
				System.out.println("无法加载托盘!");
			}
		} else {
			System.out.println("系统不支持托盘!");
		}
	}
	
	//添加一个菜单
	private JMenu getJMenu(String hit) {
		JMenu menu = new JMenu();
		Icon minIcon = new ImageIcon(this.getClass().getResource("../resources/images/min.png"));
		menu.setIcon(minIcon);
		menu.setToolTipText(hit);
		menu.setOpaque(false);
		menu.setBorder(BorderFactory.createEmptyBorder());
		return menu;
	}
	
	//添加一个菜单项
	private JMenuItem getMenuItem(JMenu menu, String text, int keycode,
			int modifiers, final String method) {
		JMenuItem item = new JMenuItem(text, keycode);
		item.setAccelerator(KeyStroke.getKeyStroke(keycode, modifiers, true));
		menu.add(item);

		item.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				try {
					PlayWindow.this.getClass().getDeclaredMethod(method).invoke(PlayWindow.this);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		return item;
	}
	
	// 初始化播放模式菜单
	private void  initPlayModeMenu(JMenu menu, String[] itemContent, int[] keycodes) {
		ButtonGroup group = new ButtonGroup();
		for(int i=0; i<itemContent.length; i++) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem (itemContent[i]); 
			item.setAccelerator(KeyStroke.getKeyStroke(keycodes[i], KeyEvent.CTRL_DOWN_MASK, true));
			group.add(item);
			menu.add(item);
			//选中顺序播放
			if(i==1)
				item.setSelected(true);
			final int index = i+1;
			item.addActionListener(new ActionListener() {
				
				public void actionPerformed(ActionEvent e) {
					if(playlistThread!=null&&listPane.getCount() != 0)
						playlistThread.setPlayMode(index);
				}
			});
		}
	}
	
	// 初始化弹出菜单
	private JPopupMenu initPopupMenu() {
		if(popupMenu == null) {
			popupMenu = new JPopupMenu("Mp3 Player");
			JMenuItem item = new JMenuItem("Remove");
			item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, true));
			item.addActionListener(new ActionListener() {
				
				@Override
				// 删除播放列表中的歌曲
				public void actionPerformed(ActionEvent e) {
					removeSelectFile();
				}
				
			});
			popupMenu.add(item);
		}
		Component[] comps = popupMenu.getComponents();
		if(listPane.getSelectedIndex()!=-1&&listPane.getCount()!=0) {
			comps[0].setEnabled(true);
		} else {
			comps[0].setEnabled(false);
		}
		
		return popupMenu;
		
	}
	
	//得到一个按钮
	private JButton getJButton(String hit, final String method) {
		JButton button = new JButton();
		Icon closeIcon = new ImageIcon(this.getClass().getResource("../resources/images/min.png"));
		button.setToolTipText(hit);
		button.setIcon(closeIcon);
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setBorder(BorderFactory.createEmptyBorder());
		
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				try {
					PlayWindow.this.getClass().getDeclaredMethod(method).invoke(PlayWindow.this);
				} catch (Exception e) {
					System.out.println(e.toString());
				}
			}
		});
		return button;
	}
	
	//开始播放线程
	private void startPlaylistThread() {
		if (listPane.getCount() == 0) {
			removeItem.setEnabled(false);
			deleteItem.setEnabled(false);
			return;
		}

		if (playlistThread == null || playlistThread.isAlive() == false) {
			playlistThread = new PlayListThread(listPane, spectrumPane, controllePanel.getProgressBar(), controllePanel.getVolumeBar());
			playlistThread.start();
		}

		controllePanel.getPreviousButton().setEnabled(true);
		controllePanel.getStartButton().setEnabled(true);
		controllePanel.getStopButton().setEnabled(true);
		controllePanel.getNextButton().setEnabled(true);
		removeItem.setEnabled(true);
		deleteItem.setEnabled(true);
	}
	
	//退出系统
	public void exitSystem() {
		System.exit(0);
	}
	
	//最小化隐藏
	public void toMinimize() {
		setVisible(false);
	}
	
	// 打开文件
	public void openFile() {
		JFileChooser jfc = new JFileChooser();
		jfc.setMultiSelectionEnabled(true);
		jfc.removeChoosableFileFilter(jfc.getChoosableFileFilters()[0]); 
		//jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		
		FileNameExtensionFilter filterMP3 = new FileNameExtensionFilter("Mp3 files(*.mp3)", "mp3");
		jfc.addChoosableFileFilter(filterMP3);
		
		jfc.addChoosableFileFilter(new FileNameExtensionFilter("VCD,DVD files (*.dat,*.vob)", "dat", "vob"));
		
		FileNameExtensionFilter filterM3u = new FileNameExtensionFilter("Music list(*.m3u)", "m3u");
		jfc.addChoosableFileFilter(filterM3u);
		
		jfc.setFileFilter(filterMP3);
		
		jfc.setCurrentDirectory(currentDirectory);
		
		int f = jfc.showOpenDialog(this);
		if (f == JFileChooser.APPROVE_OPTION) {
			File[] files = jfc.getSelectedFiles();
			int i;
			String strPath = jfc.getSelectedFile().getPath();
			if (jfc.getFileFilter().equals(filterM3u)) {
				listPane.openM3U(strPath);
			} else {
				for (i = 0; i < files.length; i++)
					listPane.append(files[i].getName(), files[i].getPath());
			}
		}
		currentDirectory = jfc.getCurrentDirectory();
		
		startPlaylistThread();
	}
	
	// 打开文件夹
	public void openFolder() {
		JFileChooser jfc = new JFileChooser();
		jfc.setMultiSelectionEnabled(true);
		
		jfc.removeChoosableFileFilter(jfc.getChoosableFileFilters()[0]); 
		// 仅文件夹
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		jfc.setCurrentDirectory(currentDirectory);
		
		int f = jfc.showOpenDialog(this);
		if (f == JFileChooser.APPROVE_OPTION) {
			File file = jfc.getSelectedFile();
			if(file.isDirectory()) {
				File[] files = file.listFiles();
				for(int i=0; i<files.length; i++) {
					if(files[i].isFile()&&files[i].getName().endsWith("mp3")) {
						listPane.append(files[i].getName(), files[i].getPath());
					}
				}
			}
		}
		
		currentDirectory = jfc.getCurrentDirectory();
		startPlaylistThread();
	}
	
	// 删除选中的选项
	public void removeSelectFile() {
		if(playlistThread!=null&&listPane.getCount() != 0) {
			playlistThread.removeSelectedItem();
			if(listPane.getCount()==0) {
				removeItem.setEnabled(false);
				deleteItem.setEnabled(false);
			}
		}
	}
	
	// 删除选中的文件
	public void deleteSelectFile() {
		if(playlistThread!=null&&listPane.getCount() != 0) {
			String filename = listPane.getPlayListItem(listPane.getSelectedIndex()).toString();
			int option = JOptionPane.showConfirmDialog(this,
					"Are you sure to delete '"+filename+"' from disk?", "Delete", JOptionPane.YES_NO_OPTION);
			if(option == JOptionPane.YES_OPTION) {
				playlistThread.deleteSelectedItem();
				if(listPane.getCount()==0) {
					deleteItem.setEnabled(false);
					removeItem.setEnabled(false);
				}
			}
		}
	}
	
	// 关于
	public void about() {
		JOptionPane.showMessageDialog(this, "Mp3 Player 测试版 \r\n解码器基于： jmp123(http://jmp123.sourceforge.net)",
				"About", JOptionPane.INFORMATION_MESSAGE);
	}
	
	public static void main(String[] args) {
		new PlayWindow();
	}
}