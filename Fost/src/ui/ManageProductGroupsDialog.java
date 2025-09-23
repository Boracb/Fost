package ui;

import dao.ProductGroupDao;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class ManageProductGroupsDialog extends JDialog {
    private final ProductGroupDao groupDao;
    private final String productCode;

    private final JTextField tfGroupsCsv = new JTextField();

    public ManageProductGroupsDialog(Window owner, ProductGroupDao groupDao, String productCode, List<String> currentGroups) {
        super(owner, "Grupe proizvoda: " + productCode, ModalityType.APPLICATION_MODAL);
        this.groupDao = groupDao;
        this.productCode = productCode;

        tfGroupsCsv.setText(String.join(",", currentGroups));

        JPanel form = new JPanel(new BorderLayout(8,8));
        form.add(new JLabel("Grupe (CSV, npr. G1,G2,G3)"), BorderLayout.NORTH);
        form.add(tfGroupsCsv, BorderLayout.CENTER);

        JButton btnSave = new JButton("Spremi");
        JButton btnCancel = new JButton("Odustani");
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnCancel);
        south.add(btnSave);

        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e -> onSave());

        getContentPane().setLayout(new BorderLayout(8,8));
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        setSize(420, 140);
        setLocationRelativeTo(owner);
    }

    private void onSave() {
        try {
            String csv = tfGroupsCsv.getText().trim();
            List<String> groups = csv.isEmpty()
                    ? java.util.Collections.emptyList()
                    : Arrays.stream(csv.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .toList();
            groupDao.assignToProduct(productCode, groups);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Spremanje grupa nije uspjelo: " + ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }
}