package nurgling;

import haven.WorkshopLauncher;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Steam Workshop entry: pull the latest client from GitHub, then launch it.
 * Keeps files in {@code ~/NurglingEvolution} so Steam does not overwrite them.
 */
public final class NSteamBoot {
    public static final String FEED = NUpdateFeed.SOURCE_RELEASE_DIR;
    public static final String APP_ID = "3051280";
    static final String TITLE = "Nurgling Evolution";

    private NSteamBoot() {}

    public static Path cacheDir() {
        return Paths.get(System.getProperty("user.home"), "NurglingEvolution");
    }

    public static List<String> command(Path java, Path launcher) {
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-jar");
        cmd.add(launcher.toString());
        cmd.add("update");
        cmd.add(FEED);
        cmd.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED");
        cmd.add("-Dsun.java2d.uiScale.enabled=false");
        cmd.add("-Dhaven.authmech=steam");
        cmd.add("-jar");
        cmd.add("./hafen.jar");
        return cmd;
    }

    static String stripLog(String raw) {
        if (raw == null)
            return "";
        String line = raw.trim();
        if (line.startsWith("[LOG]"))
            return line.substring(5).trim();
        if (line.startsWith("[ERROR]"))
            return line.substring(7).trim();
        return line;
    }

    public static String statusForLine(String raw) {
        String line = stripLog(raw);
        if (line.startsWith("Downloading ") && line.contains(" ->")) {
            return "Downloading " + line.substring("Downloading ".length(), line.indexOf(" ->")).trim();
        }
        if (line.startsWith("Downloading new version"))
            return "Downloading version list...";
        if (line.startsWith("Comparing"))
            return "Checking for updates...";
        if (line.equals("No update required"))
            return "Already up to date";
        if (line.startsWith("Moving over"))
            return "Applying update...";
        if (line.startsWith("Starting client"))
            return "Starting...";
        if (line.startsWith("Generating hash"))
            return "Checking files...";
        return null;
    }

    public static boolean isClientStarting(String raw) {
        return stripLog(raw).equals("Starting client");
    }

    public static void main(String[] args) throws Exception {
        ProgressUi ui = ProgressUi.open();
        Thread t = new Thread(() -> {
            try {
                System.exit(boot(ui));
            } catch (Throwable e) {
                ui.fail(e);
                System.exit(1);
            }
        }, "nsteam-boot");
        t.start();
    }

    static int boot(ProgressUi ui) throws Exception {
        ui.status("Preparing...");
        Path cache = cacheDir();
        Files.createDirectories(cache);
        Path launcher = cache.resolve("nurgling_launcher.jar");
        Path bundled = bundledLauncher();
        if (bundled != null && Files.isRegularFile(bundled))
            Files.copy(bundled, launcher, StandardCopyOption.REPLACE_EXISTING);
        if (!Files.isRegularFile(launcher))
            throw new IOException("nurgling_launcher.jar missing in workshop item and cache");
        Files.writeString(cache.resolve("steam_appid.txt"), APP_ID);

        Path java;
        try {
            java = WorkshopLauncher.findjvm();
        } catch (IOException e) {
            java = Path.of("java");
        }
        ui.status("Checking for updates...");
        ProcessBuilder pb = new ProcessBuilder(command(java, launcher));
        pb.directory(cache.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("SteamAppID", APP_ID);
        Process proc = pb.start();
        ui.attach(proc);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                ui.log(line);
                String status = statusForLine(line);
                if (status != null)
                    ui.status(status);
                if (isClientStarting(line))
                    ui.close();
            }
        }
        return proc.waitFor();
    }

    static Path bundledLauncher() {
        try {
            URI loc = NSteamBoot.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path jar = Paths.get(loc);
            Path dir = Files.isDirectory(jar) ? jar : jar.getParent();
            return dir.resolve("nurgling_launcher.jar");
        } catch (Exception e) {
            return null;
        }
    }

    static final class ProgressUi {
        private final JFrame frame;
        private final JLabel label;
        private final JProgressBar bar;
        private final JTextArea log;
        private volatile Process proc;

        static ProgressUi open() {
            if (GraphicsEnvironment.isHeadless())
                return new ProgressUi(null, null, null, null);
            ProgressUi[] box = new ProgressUi[1];
            Runnable make = () -> {
                JLabel label = new JLabel("Updating client from GitHub...");
                label.setAlignmentX(0);
                JProgressBar bar = new JProgressBar();
                bar.setIndeterminate(true);
                bar.setStringPainted(true);
                bar.setString("Please wait");
                bar.setMinimumSize(new Dimension(360, 0));
                JTextArea log = new JTextArea(8, 48);
                log.setEditable(false);
                log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                JPanel body = new JPanel(new BorderLayout(0, 6));
                JPanel top = new JPanel();
                top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.PAGE_AXIS));
                top.add(label);
                top.add(bar);
                body.add(top, BorderLayout.NORTH);
                body.add(new JScrollPane(log), BorderLayout.CENTER);
                JButton cancel = new JButton("Cancel");
                JPanel buttons = new JPanel();
                buttons.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING));
                buttons.setAlignmentX(0);
                buttons.add(cancel);
                body.add(buttons, BorderLayout.SOUTH);

                JFrame frame = new JFrame(TITLE);
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                frame.add(body);
                ProgressUi ui = new ProgressUi(frame, label, bar, log);
                cancel.addActionListener(ev -> ui.cancel());
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent ev) {
                        ui.cancel();
                    }
                });
                frame.pack();
                Dimension ssz = Toolkit.getDefaultToolkit().getScreenSize();
                Dimension fsz = frame.getSize();
                frame.setLocation((ssz.width - fsz.width) / 2, (ssz.height - fsz.height) / 2);
                frame.setVisible(true);
                box[0] = ui;
            };
            try {
                if (SwingUtilities.isEventDispatchThread())
                    make.run();
                else
                    SwingUtilities.invokeAndWait(make);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return box[0];
        }

        private ProgressUi(JFrame frame, JLabel label, JProgressBar bar, JTextArea log) {
            this.frame = frame;
            this.label = label;
            this.bar = bar;
            this.log = log;
        }

        void attach(Process proc) {
            this.proc = proc;
        }

        void status(String msg) {
            if (label == null)
                return;
            SwingUtilities.invokeLater(() -> {
                label.setText(msg);
                bar.setString(msg);
            });
        }

        void log(String line) {
            String text = stripLog(line);
            if (text.isEmpty())
                return;
            if (log == null) {
                System.out.println(text);
                return;
            }
            SwingUtilities.invokeLater(() -> {
                log.append(text + "\n");
                log.setCaretPosition(log.getDocument().getLength());
            });
        }

        void close() {
            if (frame == null)
                return;
            SwingUtilities.invokeLater(frame::dispose);
        }

        void fail(Throwable e) {
            log("[ERROR]" + e);
            status("Update failed");
            if (frame == null)
                return;
            Runnable dlg = () -> javax.swing.JOptionPane.showMessageDialog(
                    frame, e.getMessage() == null ? e.toString() : e.getMessage(),
                    TITLE, javax.swing.JOptionPane.ERROR_MESSAGE);
            try {
                if (SwingUtilities.isEventDispatchThread())
                    dlg.run();
                else
                    SwingUtilities.invokeAndWait(dlg);
            } catch (Exception ignored) {}
        }

        void cancel() {
            Process p = proc;
            if (p != null)
                p.destroy();
            System.exit(0);
        }
    }
}
