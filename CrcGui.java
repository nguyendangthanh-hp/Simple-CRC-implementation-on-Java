package Java.CNDC;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.math.BigInteger;
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
    private JPanel notDivByXLight;
    private JPanel divByXPlus1Light;
    private String lastCodeword = "";
    private JLabel customWidthLabel;

    // Cache for lookup table
    private BigInteger cachedPoly = null;
    private int cachedWidth = -1;
    private BigInteger[] cachedTable = null;

    public CrcGui() {
        super("CRC Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        inputArea.setFont(new Font("Courier New", Font.PLAIN, 12));
        outputArea.setFont(new Font("Courier New", Font.PLAIN, 12));

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
            String selected = (String) crcTypeCombo.getSelectedItem();
            boolean isCustom = "Custom CRC".equals(selected);
            customWidthLabel.setVisible(isCustom);
            customWidthField.setVisible(isCustom);
            setDefaultPolynomial(selected);
            String poly = removeWhitespace(divisorField.getText());
            if (!poly.isEmpty()) {
                String error = validatePoly(poly, getCrcBitWidth(selected));
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
                String selectedCrc = (String) crcTypeCombo.getSelectedItem();
                String error = validatePoly(poly, getCrcBitWidth(selectedCrc));
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
        JButton clearBtn = new JButton("Clear");
        buttons.add(clearBtn);

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
        clearBtn.addActionListener(e -> {
            inputArea.setText("");
            outputArea.setText("");
            lastCodeword = "";
        });

        notDivByXLight.setToolTipText("Checks if the polynomial is not divisible by x (constant term is 1)");
        divByXPlus1Light.setToolTipText("Checks if the polynomial is divisible by x+1 (even number of 1s)");

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // --- CRC Core ---

    /**
     * Bitwise MSB-first CRC encode.
     * width: length of polynomial (poly bitstring length), degree = width - 1
     * poly: polynomial as BigInteger with bit-length width (leading 1 set)
     */
    private String encodeBitwise(String data, BigInteger poly, int width) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Input data cannot be empty");
        }
        int degree = width - 1;
        BigInteger msg = new BigInteger(data, 2).shiftLeft(degree);
        BigInteger divisor = poly;
        int totalBits = data.length() + degree;

        for (int i = totalBits - 1; i >= degree; i--) {
            if (msg.testBit(i)) {
                msg = msg.xor(divisor.shiftLeft(i - degree));
            }
        }

        BigInteger remainder = msg.and(BigInteger.ONE.shiftLeft(degree).subtract(BigInteger.ONE));
        String remainderStr = String.format("%" + degree + "s", remainder.toString(2)).replace(' ', '0');
        return data + remainderStr;
    }

    /**
     * Lookup (byte-wise) MSB-first encode. Fallback to bitwise if degree < 8.
     * poly: full polynomial BigInteger.
     */
    private String encodeLookup(String data, BigInteger poly, int width) {
        if (data == null || data.isEmpty()) throw new IllegalArgumentException("Input data cannot be empty");
        int degree = width - 1;
        if (degree < 8) return encodeBitwise(data, poly, width);

        BigInteger[] table = buildCrcTable(poly, width);
        BigInteger maskFull = BigInteger.ONE.shiftLeft(degree).subtract(BigInteger.ONE);

        String augStr = data; // Fixed: no augment for encode
        int totalBits = augStr.length();
        int fullBytes = totalBits / 8;

        BigInteger crc = BigInteger.ZERO;
        for (int i = 0; i < fullBytes; i++) {
            String byteStr = augStr.substring(i * 8, (i + 1) * 8);
            int byteVal = Integer.parseInt(byteStr, 2);
            int crcTop = crc.shiftRight(degree - 8).and(BigInteger.valueOf(0xFF)).intValue();
            int index = (crcTop ^ byteVal) & 0xFF;
            crc = crc.shiftLeft(8).xor(table[index]).and(maskFull);
            outputArea.append(String.format("Encode - Byte %d: index=%d, crc=%s\n", (i + 1), index, crc.toString(2)));
        }

        // Fixed: bit-by-bit for remaining bits
        int remainingBits = totalBits % 8;
        if (remainingBits > 0) {
            String remainingStr = augStr.substring(fullBytes * 8);
            for (int j = 0; j < remainingBits; j++) {
                int bit = remainingStr.charAt(j) - '0';
                crc = crc.xor(BigInteger.valueOf(bit).shiftLeft(degree - 1)).and(maskFull);
                if (crc.testBit(degree - 1)) {
                    crc = crc.shiftLeft(1).xor(poly).and(maskFull);
                } else {
                    crc = crc.shiftLeft(1).and(maskFull);
                }
                outputArea.append(String.format("Encode - Bit %d: bit=%d, crc=%s\n", j + 1, bit, crc.toString(2)));
            }
        }

        String remainderStr = String.format("%" + degree + "s", crc.toString(2)).replace(' ', '0');
        return data + remainderStr;
    }

    /**
     * Bitwise MSB-first CRC decode.
     */
    private boolean decodeBitwise(String codeword, BigInteger poly, int width) {
        if (codeword == null || codeword.isEmpty()) return false;
        int degree = width - 1;
        if (codeword.length() < degree) return false;

        BigInteger msg = new BigInteger(codeword, 2);
        BigInteger divisor = poly;
        int totalBits = codeword.length();

        for (int i = totalBits - 1; i >= degree; i--) {
            if (msg.testBit(i)) {
                msg = msg.xor(divisor.shiftLeft(i - degree)).and(BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE));
            }
        }
        BigInteger remainder = msg.and(BigInteger.ONE.shiftLeft(degree).subtract(BigInteger.ONE));
        outputArea.append("DecodeBitwise remainder: " + remainder.toString(2) + "\n");
        return remainder.equals(BigInteger.ZERO);
    }

    /**
     * Lookup (byte-wise) MSB-first decode. Fallback to bitwise if degree < 8.
     */
    private boolean decodeLookup(String codeword, BigInteger poly, int width) {
        if (codeword == null || codeword.isEmpty()) return false;
        int degree = width - 1;
        if (codeword.length() < degree) return false;
        if (degree < 8) return decodeBitwise(codeword, poly, width);

        BigInteger[] table = buildCrcTable(poly, width);
        BigInteger maskFull = BigInteger.ONE.shiftLeft(degree).subtract(BigInteger.ONE);
        String augStr = codeword;
        int totalBits = augStr.length();
        int fullBytes = totalBits / 8;

        BigInteger crc = BigInteger.ZERO;
        for (int i = 0; i < fullBytes; i++) {
            String byteStr = augStr.substring(i * 8, (i + 1) * 8);
            int byteVal = Integer.parseInt(byteStr, 2);
            int crcTop = crc.shiftRight(degree - 8).and(BigInteger.valueOf(0xFF)).intValue();
            int index = (crcTop ^ byteVal) & 0xFF;
            crc = crc.shiftLeft(8).xor(table[index]).and(maskFull);
            outputArea.append(String.format("Decode - Byte %d: index=%d, crc=%s\n", (i + 1), index, crc.toString(2)));
        }

        // Fixed: bit-by-bit for remaining bits
        int remainingBits = totalBits % 8;
        if (remainingBits > 0) {
            String remainingStr = augStr.substring(fullBytes * 8);
            for (int j = 0; j < remainingBits; j++) {
                int bit = remainingStr.charAt(j) - '0';
                crc = crc.xor(BigInteger.valueOf(bit).shiftLeft(degree - 1)).and(maskFull);
                if (crc.testBit(degree - 1)) {
                    crc = crc.shiftLeft(1).xor(poly).and(maskFull);
                } else {
                    crc = crc.shiftLeft(1).and(maskFull);
                }
                outputArea.append(String.format("Decode - Bit %d: bit=%d, crc=%s\n", j + 1, bit, crc.toString(2)));
            }
        }

        outputArea.append("DecodeLookup remainder: " + crc.toString(2) + "\n");
        return crc.equals(BigInteger.ZERO);
    }

    /**
     * Build a 256-entry CRC table for MSB-first (non-reflected) algorithm.
     * Only for degree >= 8. Poly is full polynomial with leading 1.
     */
    private BigInteger[] buildCrcTable(BigInteger poly, int width) {
        if (cachedTable != null && Objects.equals(cachedPoly, poly) && cachedWidth == width) {
            outputArea.append("Using cached lookup table.\n");
            return cachedTable;
        }

        int degree = width - 1;
        if (degree < 8) {
            outputArea.append("Polynomial degree < 8, not using lookup table.\n");
            cachedTable = null;
            cachedPoly = poly;
            cachedWidth = width;
            return null;
        }

        BigInteger[] table = new BigInteger[256];
        BigInteger maskFull = BigInteger.ONE.shiftLeft(degree).subtract(BigInteger.ONE);
        BigInteger polyXor = poly.and(maskFull); // Remove leading bit (x^degree)

        for (int i = 0; i < 256; i++) {
            BigInteger crc = BigInteger.valueOf(i).shiftLeft(degree - 8).and(maskFull);
            for (int j = 0; j < 8; j++) {
                if (crc.testBit(degree - 1)) {
                    crc = crc.shiftLeft(1).xor(polyXor).and(maskFull);
                } else {
                    crc = crc.shiftLeft(1).and(maskFull);
                }
            }
            table[i] = crc;
            if (i == 80 || i == 208 || i == 219) {
                outputArea.append(String.format("Table[%d] = %s\n", i, crc.toString(2)));
            }
        }

        cachedTable = table;
        cachedPoly = poly;
        cachedWidth = width;
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
        if (binaryData.length() > 1_000_000) {
            showError("Input is too long. Maximum length is 1,000,000 bits.");
            return;
        }
        String selectedCrc = (String) crcTypeCombo.getSelectedItem();
        int bitWidth;
        try {
            bitWidth = getCrcBitWidth(selectedCrc);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        String error = validatePoly(divisor, bitWidth);
        if (!error.isEmpty()) {
            showError(error);
            return;
        }
        BigInteger poly;
        try {
            poly = new BigInteger(divisor, 2);
        } catch (NumberFormatException ex) {
            showError("Invalid polynomial: too large for processing.");
            return;
        }
        boolean useLookup = lookupOn.isSelected();
        outputArea.setText(""); // Clear outputArea before new operation
        outputArea.append(String.format("Selected: %s | Lookup: %s\n", selectedCrc, useLookup ? "ON" : "OFF"));
        long start = System.nanoTime();
        if (mode == 1) {
            try {
                lastCodeword = useLookup ? encodeLookup(binaryData, poly, bitWidth) : encodeBitwise(binaryData, poly, bitWidth);
                String remainder = lastCodeword.substring(binaryData.length());
                long end = System.nanoTime();
                outputArea.append("Data: " + binaryData + "\n");
                outputArea.append("Divisor: " + divisor + "\n");
                outputArea.append("Remainder: " + remainder + "\n");
                outputArea.append("Codeword: " + lastCodeword + "\n");
                outputArea.append(String.format("Encode done in %.3f ms\n\n", (end - start) / 1e6));
                inputArea.setText(lastCodeword);
            } catch (IllegalArgumentException ex) {
                showError("Encoding failed: " + ex.getMessage());
                lastCodeword = "";
            }
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
        mark.setText(color == Color.GREEN ? "✓" : color == Color.RED ? "✗" : "-");
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
                if (expectedLength > 128) {
                    return "Custom CRC width cannot exceed 127.";
                }
            } catch (NumberFormatException ex) {
                return "Invalid custom CRC width. Please enter a number between 1 and 127.";
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
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 255) throw new IllegalArgumentException("Non-ASCII character '" + c + "' at position " + (i + 1));
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
                    if (width < 2 || width > 128) {
                        throw new NumberFormatException("Custom CRC width must be between 1 and 127");
                    }
                    yield width;
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid custom CRC width: " + customWidthField.getText());
                }
            }
            default -> throw new IllegalArgumentException("Invalid CRC type: " + type);
        };
    }

    private void setDefaultPolynomial(String type) {
        String defaultPoly = switch (type) {
            case "CRC-8" -> "100000111"; // CRC-8-CCITT
            case "CRC-16" -> "10000000000000101"; // CRC-16-CCITT
            case "CRC-32" -> "100000100110000010001110110110111"; // CRC-32-IEEE
            case "CRC-64" -> "10000000000000000000000000000000000000000000000000000000000001011"; // CRC-64-ECMA
            default -> "";
        };
        if (!defaultPoly.isEmpty()) {
            divisorField.setText(defaultPoly);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CrcGui::new);
    }
}
