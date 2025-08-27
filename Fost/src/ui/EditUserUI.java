package ui;

import javax.swing.*;
import java.awt.*;
import db.UserDatabaseHelper;

/**
 * UI prozor za uređivanje postojećeg korisnika u aplikaciji. Omogućava promjenu
 * lozinke i uloge (djelatnik/administrator). Validira unos i ažurira korisnika
 * u bazi podataka putem UserDatabaseHelper klase. Prikazuje poruke o uspjehu
 * ili greškama tijekom unosa. Nakon uspješnog ažuriranja, poziva predani
 * Runnable (ako postoji) i zatvara se.
 */

public class EditUserUI extends JFrame {
// Konstruktor prima korisničko ime, trenutnu ulogu i Runnable koji se poziva nakon uspješnog spremanja
    public EditUserUI(String username, String currentRole, Runnable onSaved) {
        setTitle("Uredi korisnika: " + username);
        setSize(300, 200);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 2, 5, 5));
        setResizable(false);
// Glavni panel s marginama
		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPasswordField txtPassword = new JPasswordField();
        JComboBox<String> cmbRole = new JComboBox<>(new String[]{"Djelatnik", "Administrator"});
        cmbRole.setSelectedItem(currentRole);

        add(new JLabel("Nova lozinka:"));
        add(txtPassword);
        add(new JLabel("Uloga:"));
        add(cmbRole);

        JButton btnSave = new JButton("Spremi");
        getRootPane().setDefaultButton(btnSave);

        btnSave.addActionListener(e -> {
            char[] passChars = txtPassword.getPassword();
            String role = (String) cmbRole.getSelectedItem();

            if (passChars.length > 0) {
                UserDatabaseHelper.updatePassword(username, new String(passChars));
                java.util.Arrays.fill(passChars, ' ');
            }
            UserDatabaseHelper.updateRole(username, role);

            JOptionPane.showMessageDialog(this, "Podaci ažurirani.", "Uspjeh", JOptionPane.INFORMATION_MESSAGE);
            if (onSaved != null) onSaved.run();
            dispose();
        });

        add(new JLabel());
        add(btnSave);

        setVisible(true);
    }
}
