package ui;

import logic.InventoryTurnoverPlanner;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class InventoryTurnoverDialog extends JDialog {

    private final JTextField tfNaziv = new JTextField(20);
    private final JTextField tfGodisnja = new JTextField("24000", 10);
    private final JTextField tfZaliha = new JTextField("1500", 10);
    private final JTextField tfRok = new JTextField("14", 10);
    private final JTextField tfMinZaliha = new JTextField("800", 10);
    private final JTextField tfMOQ = new JTextField("500", 10);
    private final JTextField tfKoef = new JTextField("12", 10);
    private final JTextField tfRadnih = new JTextField("365", 10);

    private final JTextArea taRezultat = new JTextArea(10, 50);

    public InventoryTurnoverDialog(Window owner) {
        super(owner, "Plan nabave (koef. obrtaja)", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;

        addRow(form, gc, "Naziv artikla:", tfNaziv);
        addRow(form, gc, "Godišnja potrošnja (kom/god):", tfGodisnja);
        addRow(form, gc, "Stanje zaliha (kom):", tfZaliha);
        addRow(form, gc, "Rok isporuke (dani):", tfRok);
        addRow(form, gc, "Minimalna zaliha (kom):", tfMinZaliha);
        addRow(form, gc, "Minimalna količina za naručiti (MOQ, kom):", tfMOQ);
        addRow(form, gc, "Koeficijent obrtaja (x/god):", tfKoef);
        addRow(form, gc, "Radnih dana u godini:", tfRadnih);

        JButton btnCalc = new JButton(new AbstractAction("Izračunaj") {
            @Override public void actionPerformed(ActionEvent e) { compute(); }
        });
        JButton btnClose = new JButton(new AbstractAction("Zatvori") {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(btnCalc);
        buttons.add(btnClose);

        taRezultat.setEditable(false);
        taRezultat.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(form, BorderLayout.NORTH);
        main.add(new JScrollPane(taRezultat), BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);

        setContentPane(main);
        pack();
        setLocationRelativeTo(owner);
    }

    private static void addRow(JPanel p, GridBagConstraints gc, String label, JComponent field) {
        gc.gridx = 0; p.add(new JLabel(label), gc);
        gc.gridx = 1; p.add(field, gc);
        gc.gridy++;
    }

    private void compute() {
        try {
            String naziv = tfNaziv.getText().trim();
            double godisnja = parseDouble(tfGodisnja.getText(), "Godišnja potrošnja");
            double zaliha = parseDouble(tfZaliha.getText(), "Stanje zaliha");
            int rok = (int) Math.round(parseDouble(tfRok.getText(), "Rok isporuke"));
            double minZaliha = parseDouble(tfMinZaliha.getText(), "Minimalna zaliha");
            double moq = parseDouble(tfMOQ.getText(), "Minimalna količina za naručiti");
            double koef = parseDouble(tfKoef.getText(), "Koeficijent obrtaja");
            int radnih = (int) Math.round(parseDouble(tfRadnih.getText(), "Radnih dana u godini"));

            InventoryTurnoverPlanner.Params p = new InventoryTurnoverPlanner.Params()
                    .withNazivArtikla(naziv)
                    .withGodisnjaPotrosnja(godisnja)
                    .withStanjeZaliha(zaliha)
                    .withRokIsporukeDana(rok)
                    .withMinimalnaZaliha(minZaliha)
                    .withMinimalnaKolicinaZaNaruciti(moq)
                    .withKoeficijentObrtaja(koef)
                    .withRadnihDanaUGodini(radnih);

            InventoryTurnoverPlanner.Plan plan = InventoryTurnoverPlanner.compute(p);

            String out = ""
                    + "Artikl: " + plan.nazivArtikla + "\n"
                    + "Dnevna potrošnja (kom/dan): " + round(plan.dnevnaPotrosnja) + "\n"
                    + "Reorder point (ROP, kom): " + round(plan.reorderPoint) + "\n"
                    + "Ciljna ciklusna zaliha (kom): " + round(plan.ciljnaCiklusnaZaliha) + "\n"
                    + "Ciljna max razina (kom): " + round(plan.ciljnaMaxRazina) + "\n"
                    + "Naručiti odmah: " + (plan.narucitiOdmah ? "DA" : "NE") + "\n"
                    + "Dana do narudžbe: " + (plan.narucitiOdmah ? 0 : plan.danaDoNabave) + "\n"
                    + "Preporučena narudžba (kom): " + round(plan.preporucenaNarudzbaKom) + "\n"
                    + "Datum narudžbe: " + plan.datumNarudzbe + "\n"
                    + "Očekivani dolazak: " + plan.ocekivaniDolazak + "\n"
                    + "Procijenjeni koef. obrtaja: " + round(plan.procijenjeniKoeficijentObrtaja) + "\n";

            taRezultat.setText(out);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Neispravan unos", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Greška: " + ex.getMessage(), "Greška", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static double parseDouble(String s, String field) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); }
        catch (Exception ex) { throw new NumberFormatException("Polje \"" + field + "\" nije broj."); }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}