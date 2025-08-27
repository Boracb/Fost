package ui;

import util.ActionLogger;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ResourceBundle;
import db.UserDatabaseHelper;

/**
 * UI prozor za prijavu korisnika. Sadrži polja za unos korisničkog imena,
 * lozinke i uloge (Korisnik ili Administrator). Validira unos i provjerava
 * vjerodajnice pomoću UserDatabaseHelper klase. Prikazuje poruke o greškama ili
 * uspjehu prijave. Na uspješnu prijavu otvara glavni UI prozor.
 */

public class LoginUI extends JFrame {

	// ResourceBundle za internacionalizaciju

    private static final ResourceBundle messages = ResourceBundle.getBundle("messages");

    // Konstruktor
    // Inicijalizira i prikazuje UI
    // Postavlja akcije za gumb prijave
    // Na uspješnu prijavu otvara glavni UI
    // Na neuspješnu prijavu prikazuje poruku o grešci
    //	 Dodaje gumb "Podaci" koji otvara KomitentiUI
    // Postavlja gradient pozadinu
    // Postavlja fokus na polje za lozinku prilikom pokretanja
    //	 Postavlja zadani gumb na gumb prijave
    //     Pamti posljednjeg korisnika i postavlja ga kao zadani u padajućem izborniku
    //     Automatski postavlja ulogu na "Administrator" ako je korisničko ime "admin" ili "administrator"
    //     Dodaje marginu oko glavnog panela
    //     Stilizira gumb prijave s bojama i fontom
    //	 Dodaje ikonu aplikacije (ako je dostupna)
    //     Postavlja veličinu prozora na 420x350 piksela
    //     Centra prozor na ekran
    //	 Postavlja naslov prozora iz ResourceBundle
    //     Dodaje razmak između komponenti u GridBagLayoutu
    //     Koristi sans-serif font za naslove i gumbe
    //	 Postavlja veličine komponenti za konzistentan izgled
    public LoginUI() {
        setTitle(messages.getString("login.title"));
        setSize(420, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setPaint(new GradientPaint(0,0,new Color(230,240,255),
                        0,getHeight(), new Color(180,200,240)));
                g2d.fillRect(0,0,getWidth(),getHeight());
            }
        };
        backgroundPanel.setLayout(new GridBagLayout());
        add(backgroundPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTitle = new JLabel(messages.getString("login.welcome"));
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        backgroundPanel.add(lblTitle, gbc);

        // --- Korisnik ---
        gbc.gridy++; gbc.gridwidth = 1; gbc.gridx = 0;
        backgroundPanel.add(new JLabel(messages.getString("login.username")), gbc);
        JComboBox<String> cmbUsername = new JComboBox<>();
        cmbUsername.setPreferredSize(new Dimension(180, 25));
        for (String user : UserDatabaseHelper.getAllUsernames()) cmbUsername.addItem(user);
        String lastUser = UserDatabaseHelper.loadLastUser();
        if (lastUser != null) cmbUsername.setSelectedItem(lastUser);
        gbc.gridx = 1;
        backgroundPanel.add(cmbUsername, gbc);

        // --- Lozinka ---
        gbc.gridy++; gbc.gridx = 0;
        backgroundPanel.add(new JLabel(messages.getString("login.password")), gbc);
        JPasswordField txtPassword = new JPasswordField();
        txtPassword.setPreferredSize(new Dimension(180, 25));
        gbc.gridx = 1;
        backgroundPanel.add(txtPassword, gbc);

        SwingUtilities.invokeLater(() -> txtPassword.requestFocusInWindow());

        // --- Uloga ---
        gbc.gridy++; gbc.gridx = 0;
        backgroundPanel.add(new JLabel(messages.getString("login.role")), gbc);

        JComboBox<String> cmbRole = new JComboBox<>(new String[]{messages.getString("role.user"), messages.getString("role.admin")});
        cmbRole.setPreferredSize(new Dimension(180, 25));
        gbc.gridx = 1;
        backgroundPanel.add(cmbRole, gbc);
// Automatski postavlja ulogu na "Administrator" ako je korisničko ime "admin" ili "administrator"
        cmbUsername.addActionListener(e -> {
            String u = (String) cmbUsername.getSelectedItem();
            boolean isAdmin = u != null && (u.equalsIgnoreCase("admin") || u.equalsIgnoreCase("administrator"));
            cmbRole.setSelectedItem(isAdmin ? messages.getString("role.admin") : messages.getString("role.user"));
        });
        cmbUsername.dispatchEvent(new ActionEvent(cmbUsername, ActionEvent.ACTION_PERFORMED, ""));

        // --- Gumb prijava ---
        
        gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2;
        JButton btnLogin = new JButton(messages.getString("login.button"));
        btnLogin.setPreferredSize(new Dimension(120, 30));
        btnLogin.setBackground(new Color(100,149,237));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFocusPainted(false);
        btnLogin.setFont(new Font("SansSerif", Font.BOLD, 14));

        btnLogin.addActionListener(e -> {
            String username = (String) cmbUsername.getSelectedItem();
            String password = new String(txtPassword.getPassword());
            String role = (String) cmbRole.getSelectedItem();

            if (username == null || username.trim().isEmpty() || password.isEmpty() || role == null) {
                JOptionPane.showMessageDialog(this, messages.getString("login.error.empty"), messages.getString("error.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }

            ActionLogger.log(username, "Pritisnuo PRIJAVA");
// Provjera vjerodajnica
            boolean ok = UserDatabaseHelper.authenticateHashed(username, password, role);
            if (ok) {
            	// Evidencija uspješne prijave
            	// Spremanje posljednjeg korisnika
            	// Otvaranje glavnog UI-ja
            	// Zatvaranje prozora prijave
                ActionLogger.log(username, "Uspješna prijava kao " + role);
                UserDatabaseHelper.saveLastUser(username);

                // Otvaranje glavnog UI-ja
                dispose();
                new UI(username, role).createAndShowGUI();
            } else {
                ActionLogger.log(username, "Neuspješna prijava");
                JOptionPane.showMessageDialog(this, messages.getString("login.error.invalid"), messages.getString("error.title"), JOptionPane.ERROR_MESSAGE);
            }
        });
        backgroundPanel.add(btnLogin, gbc);

        // --- Novi gumb "Podaci" ---
        gbc.gridy++;
        JButton btnPodaci = new JButton("Podaci");
        btnPodaci.addActionListener(ev -> new KomitentiUI());
        backgroundPanel.add(btnPodaci, gbc);

        getRootPane().setDefaultButton(btnLogin);
        setVisible(true);
    }
}
