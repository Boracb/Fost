package ui;

import service.ProductService;
import service.ProductService.OrderSuggestion;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class OrderSuggestionsDialog extends JDialog {

    public OrderSuggestionsDialog(Window owner,
                                  List<OrderSuggestion> list) {
        super(owner, "Prijedlog narudžbi", ModalityType.APPLICATION_MODAL);

        String[] cols = {
                "Šifra","Dobavljač","Trenutno","Dnevna potražnja",
                "Lead time (d)","Reorder point","Predloženo","Min nar."
        };
        Object[][] data = new Object[list.size()][cols.length];
        for (int i = 0; i < list.size(); i++) {
            var o = list.get(i);
            data[i][0] = o.productCode;
            data[i][1] = o.supplierCode;
            data[i][2] = o.currentQty;
            data[i][3] = o.dailyDemand;
            data[i][4] = o.leadTimeDays;
            data[i][5] = o.reorderPoint;
            data[i][6] = o.suggestedQty;
            data[i][7] = o.minOrderQty;
        }
        JTable table = new JTable(data, cols);
        JScrollPane sp = new JScrollPane(table);

        JButton btnClose = new JButton("Zatvori");
        btnClose.addActionListener(e -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(btnClose);

        getContentPane().add(sp, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);
        setSize(900, 450);
        setLocationRelativeTo(owner);
    }
}