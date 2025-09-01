package ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class StatistikaFrame extends JFrame {

    public StatistikaFrame(JPanel statistikaPanel) {
        setTitle("Statistika proizvodnje - FOST TAPE d.o.o.");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(new Color(245, 248, 255));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1;

        JLabel title = new JLabel("📊 FOST TAPE d.o.o. – Statistika", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 36));
        title.setBorder(new MatteBorder(0, 0, 2, 0, new Color(0, 70, 140)));
        title.setOpaque(true);
        title.setBackground(new Color(220, 230, 250));
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weighty = 0;
        mainPanel.add(title, gbc);

        statistikaPanel.setBorder(new CompoundBorder(
                new LineBorder(new Color(180, 200, 240), 2, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        gbc.gridy = 1;
        gbc.weighty = 1;
        mainPanel.add(statistikaPanel, gbc);

        JLabel footer = new JLabel("Legenda: ✅ – završeno | 🛠 – u tijeku | 📅 – dani preostali", SwingConstants.CENTER);
        footer.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        footer.setForeground(new Color(60, 60, 60));
        footer.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        gbc.gridy = 2;
        gbc.weighty = 0;
        mainPanel.add(footer, gbc);

        add(mainPanel);
    }
}