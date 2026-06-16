import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;
import javax.microedition.rms.*;
import javax.microedition.io.*;
import javax.microedition.io.file.*;
import java.io.*;
import java.util.Random;
import java.util.Vector;
import java.util.Enumeration;

public class HDD40Player extends MIDlet implements CommandListener {

    public Vector stationNames = new Vector();
    public Vector stationUrls = new Vector();

    private WinampUI ui;
    private List mainMenu;
    private M3UBrowser m3uBrowser;
    
    // Элементы меню
    private Form urlForm;
    private TextField nameField;
    private TextField urlField;
    private Command saveCmd;
    
    private Form settingsForm;
    private TextField bufferField;
    private ChoiceGroup lcdEffectChoice;
    private TextField lcdTextField;
    private ChoiceGroup lcdColor1Choice;
    private ChoiceGroup lcdColor2Choice;
    
    // Новые элементы для LCD Визуализации
    private TextField lcdVisTextField;
    private ChoiceGroup lcdVisSizeChoice;
    private ChoiceGroup lcdVisColorChoice;
    
    // Элементы управления обводкой
    private ChoiceGroup skOutlineChoice;
    private ChoiceGroup skColorChoice;
    private Command saveSettingsCmd;
    
    private Form aboutForm;
    private TextBox logBox;
    private Command backCmd;
    
    // --- ГЛОБАЛЬНЫЕ НАСТРОЙКИ ---
    public int bufferTimeSec = 2; 
    public int visMode = 2;       
    public int visSubMode = 0;    
    
    public String lcdText = "ON AIR";
    public int lcdEffectMode = 1; 
    public int lcdColor1Idx = 0;  
    public int lcdColor2Idx = 1;  
    
    public String lcdVisText = "PIONEER";
    public int lcdVisSize = 0;     
    public int lcdVisColorIdx = 0; 
    
    public int skOutlineMode = 1; 
    public int skColorIdx = 0;    
    
    public static final int[] PALETTE_HEX = {0x00FF00, 0xFF0000, 0x0000FF, 0xFFFF00, 0xFF8800, 0x00FFFF, 0xFF00FF, 0xFFFFFF, 0x000000};
    public static final String[] PALETTE_NAMES = {"Green", "Red", "Blue", "Yellow", "Orange", "Cyan", "Magenta", "White", "Black"};
    public static final String[] OUTLINE_MODES = {"Off", "Thin", "Thick", "Ultra Thin V1", "Ultra Thin V2"};

    private StringBuffer logBuffer = new StringBuffer();

    public HDD40Player() {
        loadSettings();
        
        if (stationNames.size() == 0) {
            stationNames.addElement("40gbFm_96kbs");
            stationUrls.addElement("http://radio.40gb.club/radio/8000/HDD40radio96.aac");

            stationNames.addElement("40gbFm_64kbs");
            stationUrls.addElement("http://radio.40gb.club/radio/8000/HDD40radio64.aac");

            stationNames.addElement("Lite40gbFm_96kbs");
            stationUrls.addElement("http://radio.40gb.club/radio/8010/HD40Fm_Lite_96.aac");

            stationNames.addElement("Lite40gbFm_64kbs");
            stationUrls.addElement("http://radio.40gb.club/radio/8010/HD40Fm_Lite_64.aac");
        }
    }

    public void startApp() {
        if (ui == null) {
            ui = new WinampUI(this);
            log("ObscureAmp Init");
            
            mainMenu = new List("Menu", List.IMPLICIT);
            mainMenu.append("Settings", null);
            mainMenu.append("Add Custom URL", null);
            mainMenu.append("Import M3U Playlist", null); // Новый пункт меню
            mainMenu.append("About", null);
            mainMenu.append("Debug Logs", null);
            
            backCmd = new Command("Back", Command.BACK, 2);
            saveCmd = new Command("Save", Command.OK, 1);
            saveSettingsCmd = new Command("Save", Command.OK, 1);
            
            mainMenu.addCommand(backCmd);
            mainMenu.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(ui);
    }

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {
        saveSettings(); 
        if (ui != null) {
            ui.stopAudioSilent();
            ui.shutdown();
        }
        notifyDestroyed();
    }

    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("ObscurePrefs", true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
                
                bufferTimeSec = dis.readInt();
                visMode = dis.readInt();
                visSubMode = dis.readInt();
                lcdText = dis.readUTF();
                lcdEffectMode = dis.readInt();
                lcdColor1Idx = dis.readInt();
                lcdColor2Idx = dis.readInt();
                skOutlineMode = dis.readInt();
                skColorIdx = dis.readInt();
                
                if (dis.available() > 0) {
                    lcdVisText = dis.readUTF();
                    lcdVisSize = dis.readInt();
                    lcdVisColorIdx = dis.readInt();
                }
                
                if (dis.available() > 0) {
                    int stationCount = dis.readInt();
                    if (stationCount > 0) {
                        stationNames.removeAllElements();
                        stationUrls.removeAllElements();
                        for (int i = 0; i < stationCount; i++) {
                            stationNames.addElement(dis.readUTF());
                            stationUrls.addElement(dis.readUTF());
                        }
                    }
                }
                dis.close();
            }
        } catch (Exception e) {
            log("Load RMS: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    public void saveSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("ObscurePrefs", true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeInt(bufferTimeSec);
            dos.writeInt(visMode);
            dos.writeInt(visSubMode);
            dos.writeUTF(lcdText);
            dos.writeInt(lcdEffectMode);
            dos.writeInt(lcdColor1Idx);
            dos.writeInt(lcdColor2Idx);
            dos.writeInt(skOutlineMode);
            dos.writeInt(skColorIdx);
            
            dos.writeUTF(lcdVisText);
            dos.writeInt(lcdVisSize);
            dos.writeInt(lcdVisColorIdx);
            
            dos.writeInt(stationNames.size());
            for (int i = 0; i < stationNames.size(); i++) {
                dos.writeUTF((String)stationNames.elementAt(i));
                dos.writeUTF((String)stationUrls.elementAt(i));
            }
            
            byte[] data = baos.toByteArray();
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(1, data, 0, data.length);
            }
            dos.close();
        } catch (Exception e) {
            log("Save RMS: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    public void log(String msg) {
        logBuffer.append(msg).append("\n");
        if (logBuffer.length() > 3000) logBuffer.delete(0, 1000);
        if (logBox != null) logBox.setString(logBuffer.toString());
    }

    public void showMainMenu() {
        Display.getDisplay(this).setCurrent(mainMenu);
    }

    public void showPlayer() {
        Display.getDisplay(this).setCurrent(ui);
    }
    
    public void onM3UImported() {
        // Вызывается после парсинга файла
        saveSettings();
        ui.setStationIndex(stationNames.size() - 1);
        Display.getDisplay(this).setCurrent(ui);
        log("M3U Import OK");
    }

    private void showSettingsForm() {
        if (settingsForm == null) {
            settingsForm = new Form("Settings");
            bufferField = new TextField("Buffer (sec):", String.valueOf(bufferTimeSec), 2, TextField.NUMERIC);
            lcdTextField = new TextField("Upper LCD Text:", lcdText, 16, TextField.ANY);
            lcdEffectChoice = new ChoiceGroup("Upper LCD Effect:", ChoiceGroup.POPUP);
            lcdEffectChoice.append("Off", null);
            lcdEffectChoice.append("Fade Blink", null);
            lcdEffectChoice.append("Scroll", null);
            lcdEffectChoice.append("Pulse Colors", null);
            lcdColor1Choice = new ChoiceGroup("Upper LCD Color 1:", ChoiceGroup.POPUP, PALETTE_NAMES, null);
            lcdColor2Choice = new ChoiceGroup("Upper LCD Color 2:", ChoiceGroup.POPUP, PALETTE_NAMES, null);
            lcdVisTextField = new TextField("LCD Vis Text (Eng):", lcdVisText, 16, TextField.ANY);
            lcdVisSizeChoice = new ChoiceGroup("LCD Vis Size:", ChoiceGroup.POPUP, new String[]{"Large", "Medium", "Small"}, null);
            lcdVisColorChoice = new ChoiceGroup("LCD Vis Color:", ChoiceGroup.POPUP, PALETTE_NAMES, null);
            skOutlineChoice = new ChoiceGroup("Key Outline Mode:", ChoiceGroup.POPUP, OUTLINE_MODES, null);
            skColorChoice = new ChoiceGroup("Outline Color:", ChoiceGroup.POPUP, PALETTE_NAMES, null);
            
            settingsForm.append(bufferField);
            settingsForm.append(lcdTextField);
            settingsForm.append(lcdEffectChoice);
            settingsForm.append(lcdColor1Choice);
            settingsForm.append(lcdColor2Choice);
            settingsForm.append(lcdVisTextField);
            settingsForm.append(lcdVisSizeChoice);
            settingsForm.append(lcdVisColorChoice);
            settingsForm.append(skOutlineChoice);
            settingsForm.append(skColorChoice);
            settingsForm.addCommand(saveSettingsCmd);
            settingsForm.addCommand(backCmd);
            settingsForm.setCommandListener(this);
        }
        bufferField.setString(String.valueOf(bufferTimeSec));
        lcdTextField.setString(lcdText);
        lcdEffectChoice.setSelectedIndex(lcdEffectMode, true);
        lcdColor1Choice.setSelectedIndex(lcdColor1Idx, true);
        lcdColor2Choice.setSelectedIndex(lcdColor2Idx, true);
        lcdVisTextField.setString(lcdVisText);
        lcdVisSizeChoice.setSelectedIndex(lcdVisSize, true);
        lcdVisColorChoice.setSelectedIndex(lcdVisColorIdx, true);
        skOutlineChoice.setSelectedIndex(skOutlineMode, true);
        skColorChoice.setSelectedIndex(skColorIdx, true);
        Display.getDisplay(this).setCurrent(settingsForm);
    }

    private void showUrlForm() {
        if (urlForm == null) {
            urlForm = new Form("Add Custom URL");
            nameField = new TextField("Name:", "My Radio", 32, TextField.ANY);
            urlField = new TextField("Stream URL:", "http://", 256, TextField.URL);
            urlForm.append(nameField);
            urlForm.append(urlField);
            urlForm.addCommand(saveCmd);
            urlForm.addCommand(backCmd);
            urlForm.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(urlForm);
    }

    private void showAboutForm() {
        if (aboutForm == null) {
            aboutForm = new Form("About");
            try {
                Image beardImg = Image.createImage("/beard.png");
                ImageItem beardItem = new ImageItem(null, beardImg, ImageItem.LAYOUT_CENTER | ImageItem.LAYOUT_NEWLINE_AFTER, "Beard");
                aboutForm.append(beardItem);
            } catch (Exception e) {}
            String text = 
                "Приветствую тебя, любитель старых, но не бесполезных технологий.\n" +
                "Спасибо, что используешь это приложение, созданное с любовью к ретро-девайсам. Надеюсь, оно окажется для тебя полезным. Оставайся на нашей волне 40gbFm!\n\n" +
                
				"Найти нас в сети:.\n" +
				"YT:@HDD40Gb.\n" +
				"TG:t.me/IDE_HDD40Gb.\n" +
				"С уважением к пердолингу.\n" +
                "Основатель HDD40Gb — Дядя Алех aka [Tualatin]\n" +
                "made in Summertime Sadness (c) 2026";
            aboutForm.append(new StringItem(null, text));
            aboutForm.addCommand(backCmd);
            aboutForm.setCommandListener(this);
        }
        Display.getDisplay(this).setCurrent(aboutForm);
    }

    private void showLogBox() {
        if (logBox == null) {
            logBox = new TextBox("Logs", logBuffer.toString(), 4096, TextField.ANY);
            logBox.addCommand(backCmd);
            logBox.setCommandListener(this);
        } else {
            logBox.setString(logBuffer.toString());
        }
        Display.getDisplay(this).setCurrent(logBox);
    }

    public void commandAction(Command c, Displayable d) {
        if (d == mainMenu) {
            if (c == List.SELECT_COMMAND) {
                int idx = mainMenu.getSelectedIndex();
                if (idx == 0) showSettingsForm();
                else if (idx == 1) showUrlForm();
                else if (idx == 2) {
                    if (m3uBrowser == null) m3uBrowser = new M3UBrowser(this);
                    m3uBrowser.loadRoots();
                    Display.getDisplay(this).setCurrent(m3uBrowser);
                }
                else if (idx == 3) showAboutForm();
                else if (idx == 4) showLogBox();
            } else if (c == backCmd) {
                Display.getDisplay(this).setCurrent(ui);
            }
        } else if (c == backCmd) {
            Display.getDisplay(this).setCurrent(ui);
        } else if (c == saveSettingsCmd) {
            try { bufferTimeSec = Integer.parseInt(bufferField.getString().trim()); } catch (Exception e) {}
            lcdText = lcdTextField.getString();
            lcdEffectMode = lcdEffectChoice.getSelectedIndex();
            lcdColor1Idx = lcdColor1Choice.getSelectedIndex();
            lcdColor2Idx = lcdColor2Choice.getSelectedIndex();
            lcdVisText = lcdVisTextField.getString();
            lcdVisSize = lcdVisSizeChoice.getSelectedIndex();
            lcdVisColorIdx = lcdVisColorChoice.getSelectedIndex();
            skOutlineMode = skOutlineChoice.getSelectedIndex();
            skColorIdx = skColorChoice.getSelectedIndex();
            saveSettings(); 
            Display.getDisplay(this).setCurrent(ui);
        } else if (c == saveCmd) {
            String n = nameField.getString().trim();
            String u = urlField.getString().trim();
            if (n.length() > 0 && u.length() > 7) {
                stationNames.addElement(n);
                stationUrls.addElement(u);
                ui.setStationIndex(stationNames.size() - 1);
                saveSettings(); 
            }
            Display.getDisplay(this).setCurrent(ui);
        }
    }
}

// --- НОВЫЙ КЛАСС ФАЙЛОВОГО БРАУЗЕРА ---
class M3UBrowser extends List implements CommandListener {
    private HDD40Player midlet;
    private String currentPath = "";
    private Command backCmd = new Command("Back", Command.BACK, 1);
    
    public M3UBrowser(HDD40Player midlet) {
        super("Select M3U File", List.IMPLICIT);
        this.midlet = midlet;
        addCommand(backCmd);
        setCommandListener(this);
    }
    
    public void loadRoots() {
        deleteAll();
        currentPath = "";
        try {
            Enumeration e = FileSystemRegistry.listRoots();
            while (e.hasMoreElements()) {
                append((String) e.nextElement(), null);
            }
        } catch (Exception ex) {
            append("Error: JSR-75 Denied", null);
            midlet.log("JSR-75 Err: " + ex.getMessage());
        }
    }
    
    private void loadDir(String path) {
        deleteAll();
        append(".. (Up)", null);
        try {
            FileConnection fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
            Enumeration e = fc.list();
            while (e.hasMoreElements()) {
                String f = (String) e.nextElement();
                // Показываем только папки и m3u файлы
                if (f.endsWith("/") || f.toLowerCase().endsWith(".m3u")) {
                    append(f, null);
                }
            }
            fc.close();
        } catch (Exception ex) {
            midlet.log("Dir err: " + ex.getMessage());
            append("Access Denied", null);
        }
    }
    
    private void parseM3U(String path) {
        try {
            FileConnection fc = (FileConnection) Connector.open("file:///" + path, Connector.READ);
            InputStream is = fc.openInputStream();
            // Используем UTF-8 ридер для защиты от кракозябр
            InputStreamReader reader = new InputStreamReader(is, "UTF-8");
            
            StringBuffer lineBuf = new StringBuffer();
            String tempName = "Imported Station";
            int c;
            
            while ((c = reader.read()) != -1) {
                if (c == '\n' || c == '\r') {
                    if (lineBuf.length() > 0) {
                        String line = lineBuf.toString().trim();
                        if (line.startsWith("#EXTINF:")) {
                            int comma = line.indexOf(',');
                            if (comma != -1) tempName = line.substring(comma + 1).trim();
                        } else if (line.startsWith("http")) {
                            midlet.stationNames.addElement(tempName);
                            midlet.stationUrls.addElement(line);
                            tempName = "Imported Station"; // сброс для следующего
                        }
                        lineBuf.setLength(0);
                    }
                } else {
                    lineBuf.append((char)c);
                }
            }
            // Проверяем последнюю строку без Enter
            if (lineBuf.length() > 0) {
                String line = lineBuf.toString().trim();
                if (line.startsWith("http")) {
                    midlet.stationNames.addElement(tempName);
                    midlet.stationUrls.addElement(line);
                }
            }
            
            reader.close();
            is.close();
            fc.close();
            midlet.onM3UImported();
            
        } catch (Exception e) {
            midlet.log("Parse M3U Err: " + e.getMessage());
            midlet.showPlayer();
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCmd) {
            if (currentPath.length() == 0) {
                midlet.showMainMenu();
            } else {
                // Идем на уровень вверх (удаляем последний слеш и слово до предыдущего)
                int lastSlash = currentPath.lastIndexOf('/', currentPath.length() - 2);
                if (lastSlash == -1) {
                    loadRoots();
                } else {
                    currentPath = currentPath.substring(0, lastSlash + 1);
                    loadDir(currentPath);
                }
            }
        } else if (c == List.SELECT_COMMAND) {
            String selected = getString(getSelectedIndex());
            if (selected.equals(".. (Up)")) {
                int lastSlash = currentPath.lastIndexOf('/', currentPath.length() - 2);
                if (lastSlash == -1) loadRoots();
                else {
                    currentPath = currentPath.substring(0, lastSlash + 1);
                    loadDir(currentPath);
                }
            } else if (selected.endsWith("/")) {
                currentPath += selected;
                loadDir(currentPath);
            } else if (selected.toLowerCase().endsWith(".m3u")) {
                parseM3U(currentPath + selected);
            }
        }
    }
}
// ------------------------------------------

class WinampUI extends Canvas implements Runnable {
    private HDD40Player midlet;

    private boolean isPlaying = false;
    private int currentStationIdx = 0; 
    private int volume = 5; 

    private boolean isRunning = true;
    private boolean showPlaylist = true;
    private int playlistCursor = 0; 
    private boolean showDeleteConfirm = false;

    private int marqueeOffset = 0;
    private int lcdScrollOffset = 0;
    private int lcdVisScrollOffset = 0; 
    private int tick = 0;

    private Random random = new Random();
    private long startTime = 0;
    private long playTimeSeconds = 0;
    private int[] peaks = new int[10];
    private int waveOffset = 0;
    private int[] matrixDrops = new int[40]; 

    private Player audioPlayer;
    private VolumeControl volumeControl;
    private Thread audioThread;
    
    private Font sysFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

    public WinampUI(final HDD40Player midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
        for(int i=0; i<matrixDrops.length; i++) matrixDrops[i] = random.nextInt(15);
        new Thread(this).start();
    }

    public void setStationIndex(int idx) {
        this.currentStationIdx = idx;
        this.playlistCursor = idx;
        this.marqueeOffset = 0; 
    }

    public void run() {
        while (isRunning) {
            tick++; 
            if (isPlaying) {
                if (tick % 2 == 0) {
                    for (int i = 0; i < 10; i++) peaks[i] = random.nextInt(25);
                }
                if (startTime > 0) playTimeSeconds = (System.currentTimeMillis() - startTime) / 1000;
                
                waveOffset += 3;
                marqueeOffset += 2; 
                lcdScrollOffset += 3; 
                lcdVisScrollOffset += 2; 
            } else {
                for (int i = 0; i < 10; i++) if (peaks[i] > 0) peaks[i]--;
            }
            repaint();
            try { Thread.sleep(80); } catch (Exception e) {}
        }
    }

    private int blendColor(int c1, int c2, int factor255) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = r1 + ((r2 - r1) * factor255 / 255);
        int g = g1 + ((g2 - g1) * factor255 / 255);
        int b = b1 + ((b2 - b1) * factor255 / 255);
        return (r << 16) | (g << 8) | b;
    }

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);

        g.setColor(0x333333);
        g.drawRect(5, 5, w - 10, 100);
        g.setColor(0x555555);
        g.drawRect(6, 6, w - 12, 98);

        int lx = 12, ly = 10;
        g.setColor(0xFFCC00); 
        g.fillTriangle(lx+3, ly, lx, ly+7, lx+5, ly+7); 
        g.fillTriangle(lx+2, ly+6, lx+6, ly+6, lx+1, ly+14);

        g.setColor(0x888888);
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_SMALL));
        g.drawString("ObscureAmp-HDD40Fm", lx + 12, 10, Graphics.TOP | Graphics.LEFT);

        g.setColor(0x001100);
        g.fillRect(12, 28, w - 24, 45);
        g.setColor(0x333333);
        g.drawRect(11, 27, w - 22, 47);

        g.setFont(sysFont);
        g.setColor(0x00FF00);
        long m = playTimeSeconds / 60;
        long s = playTimeSeconds % 60;
        String timeStr = (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
        g.drawString(timeStr, 16, 32, Graphics.TOP | Graphics.LEFT);
        
        int sepX = 16 + sysFont.stringWidth(timeStr) + 4;
        g.setColor(0x006600); 
        g.drawString("|", sepX, 32, Graphics.TOP | Graphics.LEFT);

        if (midlet.lcdEffectMode > 0 && midlet.lcdText.length() > 0) {
            int startX = sepX + sysFont.stringWidth("|") + 4;
            int maxW = (w - 14) - startX; 
            g.setClip(startX, 28, maxW, 20); 
            
            int c1 = midlet.PALETTE_HEX[midlet.lcdColor1Idx];
            int c2 = midlet.PALETTE_HEX[midlet.lcdColor2Idx];
            int drawColor = c1;
            int drawX = startX;

            if (midlet.lcdEffectMode == 1 && isPlaying) { 
                drawColor = blendColor(0x001100, c1, (int)((Math.sin(tick * 0.2) + 1.0) * 127.5));
            } else if (midlet.lcdEffectMode == 3 && isPlaying) { 
                drawColor = blendColor(c1, c2, (int)((Math.sin(tick * 0.2) + 1.0) * 127.5));
            } else if (midlet.lcdEffectMode == 2 && isPlaying) { 
                drawX = startX + maxW - (lcdScrollOffset % (maxW + sysFont.stringWidth(midlet.lcdText)));
            }

            g.setColor(drawColor);
            g.drawString(midlet.lcdText, drawX, 32, Graphics.TOP | Graphics.LEFT);
            g.setClip(0, 0, w, h); 
        }

        String stName = (String) midlet.stationNames.elementAt(currentStationIdx);
        int strW = sysFont.stringWidth(stName);
        int maxW = w - 75 - 16 - 5; 
        
        g.setColor(0x00FF00);
        if (strW > maxW) {
            int gap = 25;
            int currentOffset = marqueeOffset % (strW + gap);
            g.setClip(16, 52, maxW, sysFont.getHeight());
            g.drawString(stName, 16 - currentOffset, 52, Graphics.TOP | Graphics.LEFT);
            if (16 - currentOffset + strW + gap < 16 + maxW) {
                 g.drawString(stName, 16 - currentOffset + strW + gap, 52, Graphics.TOP | Graphics.LEFT);
            }
            g.setClip(0, 0, w, h); 
        } else {
            g.drawString(stName, 16, 52, Graphics.TOP | Graphics.LEFT);
        }

        int eqStartX = w - 75;
        g.setColor(0x00CC00);
        for (int i = 0; i < 10; i++) {
            int barHeight = peaks[i];
            if (barHeight > 0) {
                g.fillRect(eqStartX + (i * 6), 68 - barHeight, 4, barHeight);
                g.setColor(0xFF0000);
                g.fillRect(eqStartX + (i * 6), 68 - barHeight - 1, 4, 1);
                g.setColor(0x00CC00);
            }
        }

        g.setColor(0x888888);
        g.drawRect(12, 85, 40, 6);
        g.setColor(0x00AA00);
        g.fillRect(12, 85, volume * 4, 6);

        int btnY = 82;
        g.setColor(0x004400); 
        g.drawRect(64, btnY-2, 22, 14); 
        g.drawRect(97, btnY-2, 16, 14); 
        g.drawRect(127, btnY-2, 22, 14); 

        g.setColor(0x888888);
        g.fillTriangle(75, btnY+5, 83, btnY, 83, btnY+10);
        g.fillTriangle(67, btnY+5, 75, btnY, 75, btnY+10);
        
        if (isPlaying) {
            g.setColor(0x00FF00);
            g.fillRect(100, btnY+1, 3, 8);
            g.fillRect(107, btnY+1, 3, 8);
        } else {
            g.setColor(0xAAAAAA);
            g.fillTriangle(100, btnY, 100, btnY+10, 110, btnY+5);
        }
        
        g.setColor(0x888888);
        g.fillTriangle(130, btnY, 130, btnY+10, 138, btnY+5);
        g.fillTriangle(138, btnY, 138, btnY+10, 146, btnY+5);

        int plY = 115;
        int plH = h - plY - 5; 

        g.setColor(0x000000);
        g.fillRect(5, plY, w - 10, plH);

        if (isPlaying && midlet.visMode == 1) { 
            int step = (midlet.visSubMode == 1) ? 4 : (midlet.visSubMode == 2 ? 12 : 8);
            int barW = step - 2;
            g.setColor(0x003300);
            for (int i = 8; i < w - 8; i += step) {
                g.fillRect(i, plY + plH - random.nextInt(plH - 10), barW, plH);
            }
        } 
        else if (isPlaying && midlet.visMode == 2) { 
            g.setColor(0x004400);
            for (int i = 6; i < w - 6; i += 4) {
                int height;
                if (midlet.visSubMode == 0) height = (int)(Math.sin((i + waveOffset) * 0.1) * (plH/3)) + (plH/2);
                else if (midlet.visSubMode == 1) height = (int)(Math.sin((i + waveOffset) * 0.2) * (plH/4) + Math.cos((i * 2 + waveOffset) * 0.15) * (plH/4)) + (plH/2);
                else if (midlet.visSubMode == 2) height = (int)(Math.abs(Math.sin((i + waveOffset*2) * 0.05)) * (plH/2)) + 10;
                else {
                    int randomNoise = random.nextInt(plH / 3) - (plH / 6);
                    height = (int)(Math.sin((i + waveOffset) * 0.12) * (plH / 4)) + (plH / 2) + randomNoise;
                    if (height < 5) height = 5;
                    if (height > plH - 5) height = plH - 5;
                }
                g.fillRect(i, plY + plH - height, 3, height);
            }
        } 
        else if (isPlaying && midlet.visMode == 3) { 
            g.setColor(0x00FF00);
            Font mf;
            int colStep;
            
            if (midlet.visSubMode == 0) { mf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE); colStep = 14; } 
            else if (midlet.visSubMode == 1) { mf = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM); colStep = 10; } 
            else if (midlet.visSubMode == 2) { mf = sysFont; colStep = 7; } 
            else if (midlet.visSubMode == 3) { mf = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL); colStep = 5; } 
            else { mf = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL); colStep = 4; }
            
            g.setFont(mf);
            for (int i = 0; i < matrixDrops.length; i++) {
                int dx = 10 + (i * colStep);
                int dy = plY + (matrixDrops[i] * mf.getHeight());
                if (dx < w - 10 && dy < plY + plH) {
                    g.drawChar((char)(random.nextInt(26) + 65), dx, dy, Graphics.TOP | Graphics.LEFT);
                }
                matrixDrops[i]++;
                if (matrixDrops[i] * mf.getHeight() > plH || random.nextInt(15) == 0) matrixDrops[i] = 0;
            }
        }
        else if (isPlaying && midlet.visMode == 4) { 
            g.setColor(0x001A00);
            g.fillRect(7, plY + 2, w - 14, plH - 4);
            
            Font lf = (midlet.lcdVisSize == 0) ? Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_LARGE) :
                       (midlet.lcdVisSize == 1) ? Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM) : sysFont;
            g.setFont(lf);
            
            int drawColor = midlet.PALETTE_HEX[midlet.lcdVisColorIdx];
            int lxPos = 15;
            int lyPos = plY + (plH / 2) - (lf.getHeight() / 2);
            int clipW = w - 30;
            
            g.setClip(15, plY + 2, clipW, plH - 4);
            
            int animMode = midlet.visSubMode % 3;
            if (animMode == 0) { 
                int tW = lf.stringWidth(midlet.lcdVisText);
                lxPos = 15 + (lcdVisScrollOffset % (clipW + tW)) - tW;
                g.setColor(drawColor);
                g.drawString(midlet.lcdVisText, lxPos, lyPos, Graphics.TOP | Graphics.LEFT);
            } 
            else if (animMode == 1) { 
                int fade = (int)((Math.sin(tick * 0.2) + 1.0) * 127.5);
                g.setColor(blendColor(0x001A00, drawColor, fade));
                g.drawString(midlet.lcdVisText, lxPos, lyPos, Graphics.TOP | Graphics.LEFT);
            } 
            else if (animMode == 2) { 
                int totalLen = midlet.lcdVisText.length();
                if (totalLen > 0) {
                    int currentLen = (tick / 4) % (totalLen + 4);
                    if (currentLen > totalLen) currentLen = totalLen;
                    
                    int visibleWidth = 0;
                    for(int i=0; i<currentLen; i++) {
                        visibleWidth += lf.charWidth(midlet.lcdVisText.charAt(i));
                    }
                    
                    g.setClip(15, plY + 2, visibleWidth, plH - 4);
                    g.setColor(drawColor);
                    g.drawString(midlet.lcdVisText, lxPos, lyPos, Graphics.TOP | Graphics.LEFT);
                }
            }
            g.setClip(0, 0, w, h);
        }

        if (showPlaylist) {
            g.setFont(sysFont);
            g.setClip(6, plY + 1, w - 12, plH - 2);
            for (int i = 0; i < midlet.stationNames.size(); i++) {
                int itemY = plY + 5 + (i * 16);
                if (i == playlistCursor) {
                    g.setColor(0x333355); g.fillRect(6, itemY - 1, w - 12, 15);
                }
                g.setColor((i == currentStationIdx) ? 0x00FF00 : 0xFFFFFF);
                g.drawString((String)midlet.stationNames.elementAt(i), 10, itemY, Graphics.TOP | Graphics.LEFT);
            }
            g.setClip(0, 0, w, h); 
        }

        if (showDeleteConfirm) {
            int boxH = 45;
            int boxY = plY + (plH / 2) - (boxH / 2);
            
            g.setColor(0x000000);
            g.fillRect(10, boxY, w - 20, boxH);
            g.setColor(0xFF0000);
            g.drawRect(10, boxY, w - 20, boxH);
            g.drawRect(11, boxY+1, w - 22, boxH-2);
            
            g.setFont(sysFont);
            g.drawString("Delete Station?", w / 2, boxY + 5, Graphics.TOP | Graphics.HCENTER);
            g.setColor(0xAAAAAA);
            g.drawString("[5] YES     [R] NO", w / 2, boxY + 25, Graphics.TOP | Graphics.HCENTER);
        }

        g.setColor(0x444466);
        g.drawRect(5, plY, w - 10, plH);
        
        g.setFont(sysFont);
        String lText = "Menu[L]";
        String rText = "[R]Play/Stop";
        int skY = h - 20;

        if (midlet.skOutlineMode > 0) {
            g.setColor(midlet.PALETTE_HEX[midlet.skColorIdx]);
            int mode = midlet.skOutlineMode;
            
            if (mode == 1) { 
                g.drawString(lText, 4, skY, Graphics.TOP | Graphics.LEFT);
                g.drawString(lText, 6, skY, Graphics.TOP | Graphics.LEFT);
                g.drawString(lText, 5, skY-1, Graphics.TOP | Graphics.LEFT);
                g.drawString(lText, 5, skY+1, Graphics.TOP | Graphics.LEFT);
                g.drawString(rText, w - 4, skY, Graphics.TOP | Graphics.RIGHT);
                g.drawString(rText, w - 6, skY, Graphics.TOP | Graphics.RIGHT);
                g.drawString(rText, w - 5, skY-1, Graphics.TOP | Graphics.RIGHT);
                g.drawString(rText, w - 5, skY+1, Graphics.TOP | Graphics.RIGHT);
            } else if (mode == 2) { 
                for(int dx = -1; dx <= 1; dx++) {
                    for(int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0) continue;
                        g.drawString(lText, 5 + dx, skY + dy, Graphics.TOP | Graphics.LEFT);
                        g.drawString(rText, w - 5 + dx, skY + dy, Graphics.TOP | Graphics.RIGHT);
                    }
                }
            } else if (mode == 3) { 
                g.drawString(lText, 6, skY+1, Graphics.TOP | Graphics.LEFT);
                g.drawString(rText, w - 4, skY+1, Graphics.TOP | Graphics.RIGHT);
            } else if (mode == 4) { 
                g.drawString(lText, 5, skY+1, Graphics.TOP | Graphics.LEFT);
                g.drawString(rText, w - 5, skY+1, Graphics.TOP | Graphics.RIGHT);
            }
        }
        
        g.setColor(0x555555);
        g.drawString(lText, 5, skY, Graphics.TOP | Graphics.LEFT);
        g.drawString(rText, w - 5, skY, Graphics.TOP | Graphics.RIGHT);
    }

    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);

        if (showDeleteConfirm) {
            if (keyCode == KEY_NUM5 || action == Canvas.FIRE) {
                midlet.stationNames.removeElementAt(playlistCursor);
                midlet.stationUrls.removeElementAt(playlistCursor);
                
                if (playlistCursor >= midlet.stationNames.size()) {
                    playlistCursor = midlet.stationNames.size() - 1;
                }
                if (currentStationIdx >= midlet.stationNames.size()) {
                    currentStationIdx = midlet.stationNames.size() - 1;
                    stopAudio(); 
                }
                
                midlet.saveSettings(); 
                showDeleteConfirm = false;
            } else if (keyCode == -7 || keyCode == -22 || action == Canvas.RIGHT) {
                showDeleteConfirm = false;
            }
            repaint();
            return;
        }

        if (keyCode == -8 || keyCode == 8) {
            if (midlet.stationNames.size() > 1 && showPlaylist) {
                showDeleteConfirm = true;
                repaint();
            }
            return;
        }

        if (keyCode == -6 || keyCode == -21) { 
            midlet.showMainMenu();
            return;
        } else if (keyCode == -7 || keyCode == -22) { 
            if (isPlaying) stopAudio(); else startAudio();
            return;
        }

        switch (keyCode) {
            case KEY_NUM4:
                stopAudio();
                currentStationIdx--;
                if (currentStationIdx < 0) currentStationIdx = midlet.stationNames.size() - 1;
                playlistCursor = currentStationIdx;
                marqueeOffset = 0; lcdScrollOffset = 0; lcdVisScrollOffset = 0;
                startAudio();
                break;
            case KEY_NUM6:
                stopAudio();
                currentStationIdx = (currentStationIdx + 1) % midlet.stationNames.size();
                playlistCursor = currentStationIdx;
                marqueeOffset = 0; lcdScrollOffset = 0; lcdVisScrollOffset = 0;
                startAudio();
                break;
            case KEY_NUM0: 
                showPlaylist = !showPlaylist; 
                break;
            case KEY_NUM5:
                if (isPlaying) stopAudio(); else startAudio();
                break;
            case KEY_POUND: 
                midlet.visMode = (midlet.visMode + 1) % 5;
                midlet.visSubMode = 0; 
                midlet.saveSettings(); 
                break;
            case KEY_STAR: 
                int maxSubModes = 3; 
                if (midlet.visMode == 1) maxSubModes = 3; 
                else if (midlet.visMode == 2) maxSubModes = 5; 
                else if (midlet.visMode == 3) maxSubModes = 5; 
                
                midlet.visSubMode = (midlet.visSubMode + 1) % maxSubModes;
                midlet.saveSettings(); 
                break;
        }

        if (action == Canvas.UP && showPlaylist) {
            playlistCursor--;
            if (playlistCursor < 0) playlistCursor = midlet.stationNames.size() - 1;
        } else if (action == Canvas.DOWN && showPlaylist) {
            playlistCursor = (playlistCursor + 1) % midlet.stationNames.size();
        } else if (action == Canvas.LEFT && volume > 0) {
            volume--;
            updateVolume();
        } else if (action == Canvas.RIGHT && volume < 10) {
            volume++;
            updateVolume();
        } else if (action == Canvas.FIRE) {
            if (playlistCursor != currentStationIdx || !isPlaying) {
                stopAudio();
                currentStationIdx = playlistCursor;
                marqueeOffset = 0; lcdScrollOffset = 0; lcdVisScrollOffset = 0;
                startAudio();
            } else {
                stopAudio(); 
            }
        }
        repaint();
    }

    private void updateVolume() {
        if (volumeControl != null) {
            try { volumeControl.setLevel(volume * 10); } catch (Exception e) {}
        }
    }

    public void startAudio() {
        if (isPlaying) return;
        isPlaying = true;
        marqueeOffset = 0; lcdScrollOffset = 0; lcdVisScrollOffset = 0;
        startTime = System.currentTimeMillis();
        midlet.log("----");

        audioThread = new Thread(new Runnable() {
            public void run() {
                try {
                    String url = (String) midlet.stationUrls.elementAt(currentStationIdx);
                    audioPlayer = Manager.createPlayer(url);
                    audioPlayer.realize();
                    audioPlayer.setLoopCount(1);
                    audioPlayer.addPlayerListener(new PlayerListener() {
                        public void playerUpdate(Player p, String event, Object eventData) {
                            if (event.equals(PlayerListener.END_OF_MEDIA) || event.equals(PlayerListener.ERROR)) {
                                midlet.log("Stream stalled. Reconnecting...");
                                stopAudioSilent(); startAudio(); 
                            }
                        }
                    });

                    if (midlet.bufferTimeSec > 0) {
                        audioPlayer.prefetch(); 
                        try { Thread.sleep(midlet.bufferTimeSec * 1000); } catch (Exception t) {}
                    }

                    volumeControl = (VolumeControl) audioPlayer.getControl("VolumeControl");
                    if (volumeControl != null) volumeControl.setLevel(volume * 10);
                    audioPlayer.start();
                    midlet.log("PLAYING!");
                } catch (Exception e) {
                    midlet.log("ERR: " + e.toString());
                    stopAudioSilent(); 
                }
            }
        });
        audioThread.start();
    }

    public void stopAudio() {
        stopAudioSilent(); midlet.log("Stopped"); repaint();
    }

    public void stopAudioSilent() {
        isPlaying = false; startTime = 0; playTimeSeconds = 0;
        try {
            if (audioPlayer != null) {
                if (audioPlayer.getState() == Player.STARTED) audioPlayer.stop();
                audioPlayer.deallocate(); audioPlayer.close(); audioPlayer = null;
            }
            volumeControl = null;
        } catch (Exception e) {}
    }

    public void shutdown() { isRunning = false; }
}