package client;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.prefs.Preferences;

public class Launcher {

    public static void main(String[] args) throws Exception {
        extractNativesIfJar();
        SwingUtilities.invokeLater(Launcher::showLauncher);
    }

    private static void extractNativesIfJar() throws Exception {
        URL location = Launcher.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null || !location.getFile().endsWith(".jar")) return;

        Path nativesDir = Files.createTempDirectory("lwjgl-natives-");
        nativesDir.toFile().deleteOnExit();

        String[] libs = getNativeLibs();
        for (String lib : libs) {
            try (InputStream in = Launcher.class.getResourceAsStream("/natives/" + lib)) {
                if (in != null) {
                    Files.copy(in, nativesDir.resolve(lib), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        System.setProperty("org.lwjgl.librarypath", nativesDir.toAbsolutePath().toString());
    }

    private static String[] getNativeLibs() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))  return new String[]{"lwjgl.dll", "lwjgl64.dll", "OpenAL32.dll", "OpenAL64.dll"};
        if (os.contains("mac"))  return new String[]{"liblwjgl.jnilib", "openal.dylib"};
        return new String[]{"liblwjgl.so", "liblwjgl64.so", "libopenal.so", "libopenal64.so"};
    }

    private static void showLauncher() {
        Preferences prefs = Preferences.userNodeForPackage(Launcher.class);

        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(300, 200);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(Color.decode("#141414"));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 10, 10, 10);
        c.fill = GridBagConstraints.HORIZONTAL;

        addRow(frame, c, "rd-multiplayer launcher", null, 0, true);
        JTextField ipField       = addRow(frame, c, "IP:",       prefs.get("ip",       "localhost"), 1, false);
        JTextField portField     = addRow(frame, c, "Port:",     prefs.get("port",     "9090"),      2, false);
        JTextField usernameField = addRow(frame, c, "Username:", prefs.get("username", "Player"),    3, false);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        buttonPanel.setBackground(Color.decode("#141414"));

        JButton connectButton = styledButton("Connect");
        JButton closeButton   = styledButton("Close");
        closeButton.addActionListener(e -> System.exit(0));

        buttonPanel.add(connectButton);
        buttonPanel.add(closeButton);

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.weightx = 1.0;
        frame.add(buttonPanel, c);

        connectButton.addActionListener(e -> {
            String ip = ipField.getText().trim();
            String username = usernameField.getText().trim();

            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Username cannot be empty");
                return;
            }

            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid port number");
                return;
            }

            prefs.put("ip", ip);
            prefs.put("port", String.valueOf(port));
            prefs.put("username", username);

            frame.dispose();
            startGame(ip, port, username);
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JTextField addRow(JFrame frame, GridBagConstraints c, String label, String value, int row, boolean isTitle) {
        if (isTitle) {
            JLabel title = new JLabel(label, SwingConstants.CENTER);
            title.setForeground(Color.WHITE);
            title.setFont(new Font("Arial", Font.BOLD, 18));
            c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1.0;
            frame.add(title, c);
            return null;
        }

        JLabel jLabel = new JLabel(label);
        jLabel.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0.2;
        frame.add(jLabel, c);

        JTextField field = new JTextField(value);
        field.setBackground(Color.decode("#141414"));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(new LineBorder(Color.WHITE, 1));
        c.gridx = 1; c.weightx = 0.8;
        frame.add(field, c);

        return field;
    }

    private static JButton styledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(Color.decode("#141414"));
        button.setForeground(Color.WHITE);
        button.setBorder(new LineBorder(Color.WHITE, 1));
        return button;
    }

    private static void startGame(String ip, int port, String username) {
        try {
            new Thread(new Minecraft(ip, port, username)).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to start game: " + e.getMessage());
        }
    }
}