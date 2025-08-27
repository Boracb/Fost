package ui;

import javax.swing.*;
import java.awt.*;
import db.UserDatabaseHelper;

/**
 * UI prozor za dodavanje novog korisnika u aplikaciju. Sadrži polja za unos
 * korisničkog imena, lozinke i uloge (djelatnik/administrator). Validira unos i
 * sprema korisnika u bazu podataka putem UserDatabaseHelper klase. Prikazuje
 * poruke o uspjehu ili greškama tijekom unosa. Nakon uspješnog dodavanja,
 * poziva predani Runnable (ako postoji) i zatvara se.
 */

public class AddUserUI extends JFrame {
	
	
	// Konstruktor prima Runnable koji se poziva nakon uspješnog dodavanja korisnika
    public AddUserUI(Runnable onUserAdded) {
        setTitle("Dodaj novog korisnika");
        setSize(340, 230);
        setLocationRelativeTo(null);
        setResizable(false);

        // Glavni panel s marginama
        JPanel mainPanel = new JPanel(new GridLayout(5, 2, 5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField txtUsername = new JTextField();
        JPasswordField txtPassword = new JPasswordField();
        JComboBox<String> cmbRole = new JComboBox<>(new String[]{"Djelatnik", "Administrator"});

        mainPanel.add(new JLabel("Korisničko ime:")); mainPanel.add(txtUsername);
        mainPanel.add(new JLabel("Lozinka:")); mainPanel.add(txtPassword);
        mainPanel.add(new JLabel("Uloga:")); mainPanel.add(cmbRole);

        // Gumb za dodavanje korisnika
        JButton btnAdd = new JButton("Dodaj");
        getRootPane().setDefaultButton(btnAdd);

        btnAdd.addActionListener(e -> {
            String user = txtUsername.getText().trim();
            char[] passChars = txtPassword.getPassword();
            String pass = new String(passChars);
            String role = (String) cmbRole.getSelectedItem();

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Sva polja su obavezna.", "Greška", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (UserDatabaseHelper.userExists(user)) {
                JOptionPane.showMessageDialog(this, "Korisnik već postoji.", "Greška", JOptionPane.ERROR_MESSAGE);
                return;
            }

            boolean success = UserDatabaseHelper.addUser(user, pass, role);
            java.util.Arrays.fill(passChars, ' '); // brišemo iz memorije

            if (success) {
                JOptionPane.showMessageDialog(this, "Korisnik dodan.", "Uspjeh", JOptionPane.INFORMATION_MESSAGE);
                if (onUserAdded != null) onUserAdded.run();
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Greška pri dodavanju.", "Greška", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Novi gumb za otvaranje Admin panela
        JButton btnManage = new JButton("Upravljanje korisnicima");
        btnManage.addActionListener(e -> new AdminPanelUI());

        // Dodaj gumbe u layout
        mainPanel.add(btnAdd);
        mainPanel.add(btnManage);

        add(mainPanel);
        setVisible(true);
    }
}
