package Java.CNDC;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.*;

public class CrcGui extends JFrame {
    private final JTextArea inputArea = new JTextArea(8, 60);
    private final JTextField divisorField = new JTextField(20);
    private final JTextField customWidthField = new JTextField(5);
    private final JTextArea outputArea = new JTextArea(10, 60);
    private final JButton inputModeBtn = new JButton("Input mode: Binary");
    private boolean asciiMode = false;
    private final String INPUT_PLACEHOLDER = "Input goes here...";
    private final String OUTPUT_PLACEHOLDER = "Output goes here...";
    private final JButton simulateErrorBtn = new JButton("Simulate Error");
    private final JComboBox<String> crcTypeCombo = new JComboBox<>(new String[]{"CRC-8", "CRC-16", "CRC-32", "CRC-64", "Custom CRC"});
    private final JRadioButton lookupOn = new JRadioButton("Yes");
    private final JRadioButton lookupOff = new JRadioButton("No", true);
    private JPanel notDivByXLight;git commit -m "Initial commit - add CRC GUI code"

    private JPanel divByXPlus1Light;
    private String lastCodeword = "";
    private JLabel customWidthLabel;

    public CrcGui() {
        super("CRC Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        JScrollPane inScroll = new JScrollPane(addPlaceholder(inputArea, INPUT_PLACEHOLDER));
        inScroll.setBorder(BorderFactory.createTitledBorder("Input Data"));

        outputArea.setEditable(false);
        JScrollPane outScroll = new JScrollPane(addPlaceholder(outputArea, OUTPUT_PLACEHOLDER));
        outScroll.setBorder(BorderFactory.createTitledBorder("Output / Log"));

        JPanel genRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        genRow.add(new JLabel("Divisor (binary):"));
        genRow.add(divisorField);
        notDivByXLight = createLightPanel("Not divisible by x");
        divByXPlus1Light = createLightPanel("Divisible by x+1");
        setLight(notDivByXLight, Color.LIGHT_GRAY);
        setLight(divByXPlus1Light, Color.LIGHT_GRAY);
        genRow.add(notDivByXLight);
        genRow.add(divByXPlus1Light);

        JPanel crcSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        crcSelectPanel.add(new JLabel("CRC Type:"));
        crcSelectPanel.add(crcTypeCombo);
        crcSelectPanel.add(new JLabel("Use Lookup Table:"));
        ButtonGroup lookupGroup = new ButtonGroup();
        lookupGroup.add(lookupOn);
        lookupGroup.add(lookupOff);
        crcSelectPanel.add(lookupOn);
        crcSelectPanel.add(lookupOff);

        customWidthLabel = new JLabel("Custom CRC Width:");
        customWidthField.setText("8");
        customWidthLabel.setVisible(false);
        customWidthField.setVisible(false);
        crcSelectPanel.add(customWidthLabel);
        crcSelectPanel.add(customWidthField);

        crcTypeCombo.addActionListener(e -> {
            boolean isCustom = "Custom CRC".equals(crcTypeCombo.getSelectedItem());
            customWidthLabel.setVisible(isCustom);
            customWidthField.setVisible(isCustom);
            // Kích hoạt kiểm tra đa thức khi thay đổi loại CRC
            String poly = removeWhitespace(divisorField.getText());
            if (!poly.isEmpty()) {
                String error = validatePoly(poly, getCrcBitWidth((String) crcTypeCombo.getSelectedItem()));
                if (!error.isEmpty()) {
                    showError(error);
                    divisorField.requestFocus();
                    divisorField.selectAll();
                    setLight(notDivByXLight, Color.LIGHT_GRAY);
                    setLight(divByXPlus1Light, Color.LIGHT_GRAY);
                } else {
                    boolean notDivByX = checkNotDivByX(poly);
                    boolean divByXPlus1 = checkDivByXPlus1(poly);
                    setLight(notDivByXLight, notDivByX ? Color.GREEN : Color.RED);
                    setLight(divByXPlus1Light, divByXPlus1 ? Color.GREEN : Color.RED);
                }
            }
        });

        divisorField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String poly = removeWhitespace(divisorField.getText());
                if (poly.isEmpty()) {
                    setLight(notDivByXLight, Color.LIGHT_GRAY);
                    setLight(divByXPlus1Light, Color.LIGHT_GRAY);
                    return;
                }
                String error = validatePoly(poly, getCrcBitWidth((String) crcTypeCombo.getSelectedItem()));
                if (!error.isEmpty()) {
                    showError(error);
                    divisorField.requestFocus();
                    divisorField.selectAll();
                    setLight(notDivByXLight, Color.LIGHT_GRAY);
                    setLight(divByXPlus1Light, Color.LIGHT_GRAY);
                    return;
                }
                boolean notDivByX = checkNotDivByX(poly);
                boolean divByXPlus1 = checkDivByXPlus1(poly);
                setLight(notDivByXLight, notDivByX ? Color.GREEN : Color.RED);
                setLight(divByXPlus1Light, divByXPlus1 ? Color.GREEN : Color.RED);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(inputModeBtn);
        JButton encodeBtn = new JButton("Encode");
        buttons.add(encodeBtn);
        JButton checkBtn = new JButton("Decode");
        buttons.add(checkBtn);
        buttons.add(simulateErrorBtn);

        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.add(inScroll, BorderLayout.CENTER);
        topPanel.add(buttons, BorderLayout.SOUTH);
        JPanel topNorth = new JPanel(new GridLayout(2, 1));
        topNorth.add(crcSelectPanel);
        topNorth.add(genRow);
        topPanel.add(topNorth, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, outScroll);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.6);
        splitPane.setOneTouchExpandable(true);
        add(splitPane, BorderLayout.CENTER);

        encodeBtn.addActionListener(e -> filterInvalidInputsAndAction(1));
        checkBtn.addActionListener(e -> filterInvalidInputsAndAction(2));
        inputModeBtn.addActionListener(e -> toggleAsciiMode());
        simulateErrorBtn.addActionListener(e -> simulateError());

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // --- CRC Core ---
    private String encodeBitwise(String data, long poly, int width) {
        StringBuilder augmentedData = new StringBuilder(data);
        for (int i = 0; i < width - 1; i++) {
            augmentedData.append('0');
        }
        for (int i = 0; i < augmentedData.length() - (width - 1); i++) {
            if (augmentedData.charAt(i) == '1') {
                for (int j = 0; j < width; j++) {
                    char bit = (augmentedData.charAt(i + j) == '1') ? '0' : '1';
                    augmentedData.setCharAt(i + j, (poly & (1L << (width - 1 - j))) != 0 ? bit : augmentedData.charAt(i + j));
                }
            }
        }
        String remainder = augmentedData.substring(augmentedData.length() - (width - 1));
        return data + remainder;
    }

    private String encodeLookup(String data, long poly, int width) {
        long[] table = buildCrcTable(poly, width);
        long crc = 0;
        for (int i = 0; i < data.length(); i += 8) {
            int byteVal = 0;
            for (int j = 0; j < 8 && i + j < data.length(); j++) {
                byteVal = (byteVal << 1) | (data.charAt(i + j) - '0');
            }
            crc = table[(int) ((crc >> (width - 8)) ^ byteVal) & 0xFF] ^ (crc << 8);
            crc &= (1L << width) - 1;
        }
        for (int i = 0; i < width - 1; i++) {
            crc = table[(int) (crc >> (width - 8)) & 0xFF] ^ (crc << 8);
            crc &= (1L << width) - 1;
        }
        return data + String.format("%" + (width - 1) + "s", Long.toBinaryString(crc)).replace(' ', '0');
    }

    private boolean decodeBitwise(String codeword, long poly, int width) {
        StringBuilder temp = new StringBuilder(codeword);
        for (int i = 0; i < temp.length() - (width - 1); i++) {
            if (temp.charAt(i) == '1') {
                for (int j = 0; j < width; j++) {
                    char bit = (temp.charAt(i + j) == '1') ? '0' : '1';
                    temp.setCharAt(i + j, (poly & (1L << (width - 1 - j))) != 0 ? bit : temp.charAt(i + j));
                }
            }
        }
        for (int i = temp.length() - (width - 1); i < temp.length(); i++) {
            if (temp.charAt(i) != '0') return false;
        }
        return true;
    }

    private boolean decodeLookup(String codeword, long poly, int width) {
        long[] table = buildCrcTable(poly, width);
        long crc = 0;
        for (int i = 0; i < codeword.length(); i += 8) {
            int byteVal = 0;
            for (int j = 0; j < 8 && i + j < codeword.length(); j++) {
                byteVal = (byteVal << 1) | (codeword.charAt(i + j) - '0');
            }
            crc = table[(int) ((crc >> (width - 8)) ^ byteVal) & 0xFF] ^ (crc << 8);
            crc &= (1L << width) - 1;
        }
        return crc == 0;
    }

    private long[] buildCrcTable(long poly, int width) {
        long[] table = new long[256];
        for (int i = 0; i < 256; i++) {
            long crc = (long) i << (width - 8);
            for (int j = 0; j < 8; j++) {
                if ((crc & (1L << (width - 1))) != 0) crc = (crc << 1) ^ poly;
                else crc <<= 1;
                crc &= (1L << width) - 1;
            }
            table[i] = crc;
        }
        return table;
    }

    private void filterInvalidInputsAndAction(int mode) {
        String divisor = removeWhitespace(divisorField.getText());
        if (divisor.isEmpty()) {
            showError("Divisor field is empty.");
            return;
        }
        String data = inputArea.getText().trim();
        if (data.isEmpty() || data.equals(INPUT_PLACEHOLDER)) {
            showError("Input field is empty.");
            return;
        }
        if (asciiMode) {
            try {
                data = asciiToBinary(data);
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
                return;
            }
        }
        String binaryData = removeWhitespace(data);
        if (!binaryData.matches("[01]+")) {
            showError("Input must be binary (0/1) or use ASCII mode.");
            return;
        }

        String selectedCrc = (String) crcTypeCombo.getSelectedItem();
        int bitWidth = getCrcBitWidth(selectedCrc);
        String error = validatePoly(divisor, bitWidth);
        if (!error.isEmpty()) {
            showError(error);
            return;
        }
        long poly;
        try {
            poly = Long.parseLong(divisor, 2);
        } catch (NumberFormatException ex) {
            showError("Invalid polynomial: too large for processing.");
            return;
        }
        boolean useLookup = lookupOn.isSelected();
        outputArea.append(String.format("Selected: %s | Lookup: %s\n", selectedCrc, useLookup ? "ON" : "OFF"));

        long start = System.nanoTime();
        if (mode == 1) {
            lastCodeword = useLookup ? encodeLookup(binaryData, poly, bitWidth) : encodeBitwise(binaryData, poly, bitWidth);
            String remainder = lastCodeword.length() >= binaryData.length() ? lastCodeword.substring(binaryData.length()) : "";
            long end = System.nanoTime();
            outputArea.setText("");
            outputArea.append("Data: " + binaryData + "\n");
            outputArea.append("Divisor: " + divisor + "\n");
            outputArea.append("Remainder: " + (remainder.isEmpty() ? "N/A" : remainder) + "\n");
            outputArea.append("Codeword: " + lastCodeword + "\n");
            outputArea.append(String.format("Encode done in %.3f ms\n\n", (end - start) / 1e6));
            inputArea.setText(lastCodeword);
        } else if (mode == 2) {
            String codeword = removeWhitespace(inputArea.getText());
            boolean valid = useLookup ? decodeLookup(codeword, poly, bitWidth) : decodeBitwise(codeword, poly, bitWidth);
            long end = System.nanoTime();
            outputArea.append("Codeword: " + codeword + "\n");
            outputArea.append(valid ? "✅ No error.\n" : "❌ Error detected.\n");
            outputArea.append(String.format("Decode done in %.3f ms\n\n", (end - start) / 1e6));
        }
    }

    // --- GUI & Utility ---
    private void simulateError() {
        String data = removeWhitespace(inputArea.getText());
        if (data.isEmpty() || !data.matches("[01]+")) {
            showError("Input must be binary.");
            return;
        }
        String input = JOptionPane.showInputDialog(this, "Bits to flip:", "1");
        if (input == null) return;
        int num;
        try {
            num = Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            showError("Invalid number.");
            return;
        }
        if (num <= 0 || num > data.length()) {
            showError("Number of bits must be between 1 and " + data.length());
            return;
        }
        char[] bits = data.toCharArray();
        Random rand = new Random();
        Set<Integer> flipped = new HashSet<>();
        while (flipped.size() < num) {
            int pos = rand.nextInt(bits.length);
            if (flipped.add(pos)) bits[pos] = bits[pos] == '0' ? '1' : '0';
        }
        String corrupted = new String(bits);
        inputArea.setText(corrupted);
        JOptionPane.showMessageDialog(this, "Flipped " + num + " bit(s) at: " + flipped);
    }

    private void toggleAsciiMode() {
        asciiMode = !asciiMode;
        inputModeBtn.setText(asciiMode ? "Input mode: ASCII" : "Input mode: Binary");
    }

    private JPanel createLightPanel(String label) {
        JPanel container = new JPanel();
        JPanel light = new JPanel();
        light.setPreferredSize(new Dimension(20, 20));
        light.setBorder(new LineBorder(Color.DARK_GRAY));
        JLabel mark = new JLabel("-");
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        light.add(mark);
        light.putClientProperty("mark", mark);
        container.add(light);
        container.add(new JLabel(label));
        return container;
    }

    private void setLight(JPanel panel, Color color) {
        JPanel light = (JPanel) panel.getComponent(0);
        light.setBackground(color);
        JLabel mark = (JLabel) light.getClientProperty("mark");
        mark.setText(color == Color.GREEN ? "V" : color == Color.RED ? "X" : "-");
    }

    private String removeWhitespace(String s) {
        return s.replaceAll("\\s+", "");
    }

    private String validatePoly(String p, int expectedLength) {
        if (p.isEmpty()) {
            return "Polynomial cannot be empty.";
        }
        if (!p.matches("[01]+")) {
            return "Polynomial must contain only 0 and 1.";
        }
        if ("Custom CRC".equals(crcTypeCombo.getSelectedItem())) {
            try {
                expectedLength = Integer.parseInt(customWidthField.getText()) + 1;
                if (expectedLength < 2) {
                    return "Custom CRC width must be at least 1.";
                }
                if (expectedLength > 64) {
                    return "Custom CRC width cannot exceed 63 (due to long type limitation).";
                }
            } catch (NumberFormatException ex) {
                return "Invalid custom CRC width. Please enter a number between 1 and 63.";
            }
        }
        if (p.length() != expectedLength) {
            return String.format("Polynomial length must be %d bits for %s.", expectedLength, crcTypeCombo.getSelectedItem());
        }
        if (p.charAt(0) != '1') {
            return "Polynomial must start with 1 (highest degree).";
        }
        return "";
    }

    private boolean checkNotDivByX(String poly) {
        return poly.charAt(poly.length() - 1) == '1';
    }

    private boolean checkDivByXPlus1(String poly) {
        int ones = 0;
        for (char c : poly.toCharArray()) if (c == '1') ones++;
        return ones % 2 == 0;
    }

    private String asciiToBinary(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c > 255) throw new IllegalArgumentException("Non-ASCII character: " + c);
            sb.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        }
        return sb.toString();
    }

    private JTextArea addPlaceholder(JTextArea area, String placeholder) {
        area.setForeground(Color.GRAY);
        area.setText(placeholder);
        area.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (area.getText().equals(placeholder)) {
                    area.setText("");
                    area.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (area.getText().isEmpty()) {
                    area.setForeground(Color.GRAY);
                    area.setText(placeholder);
                }
            }
        });
        return area;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private int getCrcBitWidth(String type) {
        return switch (type) {
            case "CRC-8" -> 9;
            case "CRC-16" -> 17;
            case "CRC-32" -> 33;
            case "CRC-64" -> 65;
            case "Custom CRC" -> {
                try {
                    int width = Integer.parseInt(customWidthField.getText()) + 1;
                    if (width < 2 || width > 64) throw new NumberFormatException();
                    yield width;
                } catch (NumberFormatException ex) {
                    showError("Invalid custom CRC width. Please enter a number between 1 and 63.");
                    yield 9; // Default to CRC-8 width
                }
            }
            default -> 9;
        };
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CrcGui::new);
    }
}
