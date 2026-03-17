package com.downstreamcallgraph.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CallGraphSettingsDialog extends DialogWrapper {
    private final CallGraphSettings settings;
    private final Project project;

    private JBRadioButton useCustomColorButton;
    private JBRadioButton useIdeColorButton;
    private JButton colorPickerButton;
    private JPanel colorPreview;
    private JSpinner maxDepthSpinner;
    private JBCheckBox filterLibraryCheckBox;
    private JBCheckBox includeConstructorsCheckBox;
    private JBCheckBox includeMethodRefsCheckBox;
    private JBCheckBox includeSourceCheckBox;
    private JBCheckBox renderVisualGraphCheckBox;
    private JTextArea excludedMethodsArea;

    private String selectedBackgroundType;
    private String selectedCustomColor;

    private final String originalBackgroundType;
    private final String originalCustomColor;

    public CallGraphSettingsDialog(Project project) {
        super(project, true);
        this.project = project;
        this.settings = CallGraphSettings.getInstance(project);

        this.originalBackgroundType = settings.getBackgroundType();
        this.originalCustomColor = settings.getCustomBackgroundColor();

        this.selectedBackgroundType = this.originalBackgroundType;
        this.selectedCustomColor = this.originalCustomColor;

        setTitle("Downstream Call Graph Settings");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel dialogPanel = new JPanel(new BorderLayout());
        dialogPanel.setPreferredSize(new Dimension(420, 500));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Graph", createGraphPanel());
        tabbedPane.addTab("Appearance", createAppearancePanel());

        dialogPanel.add(tabbedPane, BorderLayout.CENTER);
        return dialogPanel;
    }

    private JPanel createGraphPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(5));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = JBUI.insets(5, 5, 5, 5);

        // Max depth
        panel.add(new JLabel("Max Depth:"), c);
        c.gridx = 1;
        maxDepthSpinner = new JSpinner(new SpinnerNumberModel(settings.getMaxDepth(), 1, 15, 1));
        panel.add(maxDepthSpinner, c);

        // Filter library methods
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        filterLibraryCheckBox = new JBCheckBox("Filter library/JDK methods", settings.isFilterLibraryMethods());
        panel.add(filterLibraryCheckBox, c);

        // Include constructors
        c.gridy = 2;
        includeConstructorsCheckBox = new JBCheckBox("Include constructor calls (new Foo())", settings.isIncludeConstructors());
        panel.add(includeConstructorsCheckBox, c);

        // Include method references
        c.gridy = 3;
        includeMethodRefsCheckBox = new JBCheckBox("Include method references (Foo::bar)", settings.isIncludeMethodReferences());
        panel.add(includeMethodRefsCheckBox, c);

        // Render visual graph
        c.gridy = 4;
        renderVisualGraphCheckBox = new JBCheckBox("Render visual call graph", settings.isRenderVisualGraph());
        panel.add(renderVisualGraphCheckBox, c);

        // Include source in markdown
        c.gridy = 5;
        includeSourceCheckBox = new JBCheckBox("Include source code in Markdown export", settings.isIncludeSourceInMarkdown());
        panel.add(includeSourceCheckBox, c);

        // Excluded methods label
        c.gridy = 6;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;
        panel.add(new JLabel("Excluded methods (one per line, supports * wildcard, e.g. *.from):"), c);

        // Excluded methods textarea
        c.gridy = 7;
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1.0;
        excludedMethodsArea = new JTextArea(settings.getExcludedMethods());
        excludedMethodsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(excludedMethodsArea);
        scrollPane.setMinimumSize(new Dimension(350, 80));
        panel.add(scrollPane, c);

        return panel;
    }

    private JPanel createAppearancePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(5));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 1.0;

        JPanel backgroundPanel = new JPanel(new GridBagLayout());
        backgroundPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Background Color",
                TitledBorder.LEFT, TitledBorder.TOP));

        ButtonGroup backgroundGroup = new ButtonGroup();

        useIdeColorButton = new JBRadioButton("Use IDE Editor Background");
        useIdeColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_IDE.equals(selectedBackgroundType));
        backgroundGroup.add(useIdeColorButton);

        useCustomColorButton = new JBRadioButton("Use Custom Color");
        useCustomColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_CUSTOM.equals(selectedBackgroundType));
        backgroundGroup.add(useCustomColorButton);

        JPanel colorPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        colorPickerButton = new JButton("Choose Color...");

        colorPreview = new JPanel();
        colorPreview.setPreferredSize(new Dimension(24, 24));
        colorPreview.setBorder(BorderFactory.createLineBorder(JBColor.GRAY));
        updateColorPreview();

        colorPickerButton.addActionListener(e -> {
            Color initialColor;
            try {
                initialColor = Color.decode(selectedCustomColor);
            } catch (NumberFormatException ex) {
                initialColor = Color.BLACK;
            }
            Color color = JColorChooser.showDialog(panel, "Choose Background Color", initialColor);
            if (color != null) {
                selectedCustomColor = "#" + ColorUtil.toHex(color);
                updateColorPreview();
                useCustomColorButton.setSelected(true);
                selectedBackgroundType = CallGraphSettings.BACKGROUND_TYPE_CUSTOM;
            }
        });

        colorPickerPanel.add(colorPickerButton);
        colorPickerPanel.add(colorPreview);

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0;
        bc.gridy = 0;
        bc.anchor = GridBagConstraints.WEST;
        bc.insets = JBUI.insets(3, 3, 1, 3);
        bc.fill = GridBagConstraints.HORIZONTAL;
        backgroundPanel.add(useIdeColorButton, bc);

        bc.gridy = 1;
        bc.insets = JBUI.insets(1, 3, 1, 3);
        backgroundPanel.add(useCustomColorButton, bc);

        bc.gridy = 2;
        bc.insets = JBUI.insets(0, 20, 3, 3);
        backgroundPanel.add(colorPickerPanel, bc);

        panel.add(backgroundPanel, c);

        ActionListener radioListener = actionEvent -> {
            if (useCustomColorButton.isSelected()) {
                selectedBackgroundType = CallGraphSettings.BACKGROUND_TYPE_CUSTOM;
                colorPickerButton.setEnabled(true);
            } else {
                selectedBackgroundType = CallGraphSettings.BACKGROUND_TYPE_IDE;
                colorPickerButton.setEnabled(false);
            }
        };

        useCustomColorButton.addActionListener(radioListener);
        useIdeColorButton.addActionListener(radioListener);
        colorPickerButton.setEnabled(useCustomColorButton.isSelected());

        return panel;
    }

    private void updateColorPreview() {
        try {
            colorPreview.setBackground(Color.decode(selectedCustomColor));
        } catch (NumberFormatException e) {
            colorPreview.setBackground(Color.BLACK);
        }
    }

    @Override
    protected void doOKAction() {
        settings.setBackgroundType(selectedBackgroundType);
        settings.setCustomBackgroundColor(selectedCustomColor);
        settings.setMaxDepth((Integer) maxDepthSpinner.getValue());
        settings.setFilterLibraryMethods(filterLibraryCheckBox.isSelected());
        settings.setIncludeConstructors(includeConstructorsCheckBox.isSelected());
        settings.setIncludeMethodReferences(includeMethodRefsCheckBox.isSelected());
        settings.setRenderVisualGraph(renderVisualGraphCheckBox.isSelected());
        settings.setIncludeSourceInMarkdown(includeSourceCheckBox.isSelected());
        settings.setExcludedMethods(excludedMethodsArea.getText());

        com.downstreamcallgraph.browser.BrowserManager.getInstance(project).applySettings();
        super.doOKAction();
    }

    @Override
    protected Action @NotNull [] createActions() {
        Action[] defaultActions = super.createActions();
        for (int i = 0; i < defaultActions.length; i++) {
            if (defaultActions[i] == getCancelAction()) {
                defaultActions[i] = new DialogWrapperAction("Reset") {
                    @Override
                    protected void doAction(ActionEvent e) {
                        settings.setBackgroundType(originalBackgroundType);
                        settings.setCustomBackgroundColor(originalCustomColor);
                        com.downstreamcallgraph.browser.BrowserManager.getInstance(project).applySettings();

                        selectedBackgroundType = originalBackgroundType;
                        selectedCustomColor = originalCustomColor;
                        useIdeColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_IDE.equals(originalBackgroundType));
                        useCustomColorButton.setSelected(CallGraphSettings.BACKGROUND_TYPE_CUSTOM.equals(originalBackgroundType));
                        colorPickerButton.setEnabled(CallGraphSettings.BACKGROUND_TYPE_CUSTOM.equals(originalBackgroundType));
                        updateColorPreview();
                    }
                };
                break;
            }
        }
        return defaultActions;
    }
}
