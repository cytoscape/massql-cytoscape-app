package org.cytoscape.massql.app.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.cytoscape.application.swing.CyColumnComboBox;
import org.cytoscape.application.swing.CyColumnPresentationManager;
import org.cytoscape.massql.Massql;
import org.cytoscape.massql.MassqlParseException;
import org.cytoscape.massql.app.MassqlRunRequest;
import org.cytoscape.massql.app.run.ResultAttribute;
import org.cytoscape.model.CyColumn;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.util.swing.FileChooserFilter;
import org.cytoscape.util.swing.FileUtil;
import org.cytoscape.util.swing.LookAndFeelUtil;

/** The Run MassQL dialog. Collects a request; running it is someone else's job. */
public class RunMassqlDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient RunMassqlForm form;
    private final transient CyServiceRegistrar registrar;

    private final JTextField fileField = new JTextField(28);
    private final JTextField nameField = new JTextField(20);
    private final JTextArea queryArea = new JTextArea(6, 40);
    private final JTextField toleranceField = new JTextField(8);
    private final JLabel toleranceNote = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton applyButton = new JButton("Apply");

    private final transient Map<ResultAttribute, JCheckBox> attributeBoxes =
            new EnumMap<>(ResultAttribute.class);

    private transient CyColumnComboBox scanColumnCombo;
    private transient JComponent rejectedField;
    private transient MassqlRunRequest request;

    public RunMassqlDialog(Window owner, RunMassqlForm form, CyServiceRegistrar registrar) {
        super(owner, "Run MassQL", ModalityType.APPLICATION_MODAL);
        this.form = form;
        this.registrar = registrar;

        setContentPane(buildContent());
        wireValidation();
        refresh();

        pack();
        setLocationRelativeTo(owner);
    }

    /** The request the user applied, or null if they cancelled. */
    public MassqlRunRequest request() {
        return request;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.add(filePanel());
        fields.add(Box.createVerticalStrut(6));
        fields.add(labelled("Query name:", nameField));
        fields.add(Box.createVerticalStrut(6));
        fields.add(scanColumnPanel());
        fields.add(Box.createVerticalStrut(6));
        fields.add(queryPanel());
        fields.add(Box.createVerticalStrut(6));
        fields.add(columnsPanel());
        fields.add(Box.createVerticalStrut(6));
        fields.add(advancedPanel());

        content.add(fields, BorderLayout.CENTER);
        content.add(footer(), BorderLayout.SOUTH);
        return content;
    }

    private JPanel filePanel() {
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> chooseFile());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel("Peak list data file:"), BorderLayout.WEST);
        row.add(fileField, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    private JPanel scanColumnPanel() {
        List<CyColumn> candidates = RunMassqlForm.scanColumnCandidates(form.network());
        scanColumnCombo =
                new CyColumnComboBox(
                        registrar.getService(CyColumnPresentationManager.class), candidates);

        for (CyColumn column : candidates) {
            if (column.getName().equals(form.scanColumn())) {
                scanColumnCombo.setSelectedItem(column);
            }
        }
        // The combo selects its first entry on its own. Adopting whatever it ended up showing keeps
        // the form and the display from disagreeing about which column is chosen.
        adoptSelectedScanColumn();

        scanColumnCombo.addActionListener(
                e -> {
                    adoptSelectedScanColumn();
                    refresh();
                });

        return labelled("Node column holding the scan number:", scanColumnCombo);
    }

    private JPanel queryPanel() {
        queryArea.setLineWrap(true);
        queryArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel("MassQL query:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(queryArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel columnsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(LookAndFeelUtil.createTitledBorder("Columns to add to the node table"));

        JCheckBox full = new JCheckBox("Full result (JSON)", form.createResultColumn());
        full.addActionListener(
                e -> {
                    form.setCreateResultColumn(full.isSelected());
                    refresh();
                });
        panel.add(full);

        JPanel attributes = new JPanel(new GridLayout(0, 3, 4, 0));
        for (ResultAttribute attribute : ResultAttribute.derivableAttributes()) {
            JCheckBox box = new JCheckBox(attribute.jsonName());
            if (attribute.requiresMs1()) {
                box.setToolTipText("Measured in an MS1 survey scan: choose an mzML or mzXML file.");
            }
            box.addActionListener(
                    e -> {
                        form.setDerived(attribute, box.isSelected());
                        refresh();
                    });
            attributeBoxes.put(attribute, box);
            attributes.add(box);
        }
        panel.add(attributes);
        return panel;
    }

    private JPanel advancedPanel() {
        toleranceField.setText(String.valueOf(form.precursorTolPpm()));
        toleranceNote.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(LookAndFeelUtil.createTitledBorder("Advanced"));
        panel.add(new JLabel("Precursor tolerance (ppm):"), BorderLayout.WEST);
        panel.add(toleranceField, BorderLayout.CENTER);
        panel.add(toleranceNote, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel footer() {
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        applyButton.addActionListener(e -> apply());

        AbstractAction ok = action(this::apply);
        AbstractAction cancel = action(this::dispose);
        LookAndFeelUtil.setDefaultOkCancelKeyStrokes(getRootPane(), ok, cancel);
        getRootPane().setDefaultButton(applyButton);

        JPanel footer = new JPanel(new BorderLayout(0, 4));
        statusLabel.setForeground(LookAndFeelUtil.getErrorColor());
        footer.add(statusLabel, BorderLayout.NORTH);
        footer.add(
                LookAndFeelUtil.createOkCancelPanel(applyButton, cancelButton), BorderLayout.SOUTH);
        return footer;
    }

    private void adoptSelectedScanColumn() {
        CyColumn selected = scanColumnCombo.getSelectedItem();
        form.setScanColumn(selected == null ? null : selected.getName());
    }

    private void chooseFile() {
        FileUtil files = registrar.getService(FileUtil.class);
        File chosen =
                files.getFile(
                        this,
                        "Select a peak list",
                        FileUtil.LOAD,
                        List.of(
                                new FileChooserFilter(
                                        "Mass spectra (*.mgf, *.mzML, *.mzXML)",
                                        new String[] {"mgf", "mzML", "mzXML"})));
        if (chosen != null) {
            fileField.setText(chosen.getAbsolutePath());
        }
    }

    /**
     * Values are judged here rather than as the user types: a half-typed query is not a mistake,
     * and saying so on every keystroke reads as nagging.
     */
    private void apply() {
        readFields();

        RunMassqlForm.Problem problem = form.validate();
        if (problem != null) {
            report(problem.message(), fieldFor(problem.field()));
            return;
        }
        // Parsing needs no file and costs nothing, so a bad query is caught here rather than after
        // the peak list has been read.
        try {
            Massql.parse(form.queryText());
        } catch (MassqlParseException e) {
            report(describe(e), queryArea);
            if (e.position() > 0 && e.position() <= queryArea.getText().length()) {
                queryArea.setCaretPosition(e.position() - 1);
            }
            return;
        }
        request = form.toRequest();
        dispose();
    }

    private void report(String message, JComponent field) {
        statusLabel.setText("<html>" + message + "</html>");
        rejectedField = field;
        if (field != null) {
            field.requestFocusInWindow();
        }
    }

    private JComponent fieldFor(RunMassqlForm.Field field) {
        return switch (field) {
            case FILE -> fileField;
            case QUERY_NAME -> nameField;
            case QUERY_TEXT -> queryArea;
            case COLUMNS -> null;
        };
    }

    private static String describe(MassqlParseException e) {
        return "Query error"
                + (e.position() > 0 ? " at position " + e.position() : "")
                + ": "
                + e.getMessage();
    }

    private void readFields() {
        String path = fileField.getText().trim();
        form.setFile(path.isEmpty() ? null : new File(path));
        form.setQueryName(nameField.getText());
        form.setQueryText(queryArea.getText());
        form.setPrecursorTolPpm(parseTolerance());
    }

    /** An unreadable tolerance leaves the default in place rather than blocking the run. */
    private double parseTolerance() {
        try {
            return Double.parseDouble(toleranceField.getText().trim());
        } catch (NumberFormatException notANumber) {
            return form.precursorTolPpm();
        }
    }

    private void wireValidation() {
        DocumentListener listener =
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        refresh();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        refresh();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        refresh();
                    }
                };
        fileField.getDocument().addDocumentListener(listener);
        nameField.getDocument().addDocumentListener(listener);
        queryArea.getDocument().addDocumentListener(listener);
    }

    /** Keeps Apply, the hint, and the tolerance field in step with what has been entered. */
    private void refresh() {
        readFields();

        boolean ms1 = form.fileCarriesMs1();
        toleranceField.setEnabled(ms1);
        toleranceNote.setText(
                ms1 ? " " : "Applies only to mzML and mzXML, which carry the MS1 survey scans.");

        // The three MS1 attributes are offered only for a file that measures them. Clearing a box
        // as it is disabled keeps the form honest: a tick made while an mzML was chosen must not
        // follow the user to an MGF.
        for (Map.Entry<ResultAttribute, JCheckBox> entry : attributeBoxes.entrySet()) {
            boolean applies = form.applies(entry.getKey());
            JCheckBox box = entry.getValue();
            box.setEnabled(applies);
            if (!applies && box.isSelected()) {
                box.setSelected(false);
                form.setDerived(entry.getKey(), false);
            }
        }

        applyButton.setEnabled(form.isComplete());
    }

    private static JPanel labelled(String label, java.awt.Component field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private static AbstractAction action(Runnable body) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                body.run();
            }
        };
    }

    // Package-private seams for RunMassqlDialogIT, which drives these widgets as a user would.

    JButton applyButton() {
        return applyButton;
    }

    JTextField fileField() {
        return fileField;
    }

    JTextField nameField() {
        return nameField;
    }

    JTextArea queryArea() {
        return queryArea;
    }

    String statusText() {
        return statusLabel.getText();
    }

    /** The component the dialog pointed at when it last refused a value. */
    JComponent rejectedField() {
        return rejectedField;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension packed = super.getPreferredSize();
        return new Dimension(Math.max(packed.width, 520), packed.height);
    }
}
