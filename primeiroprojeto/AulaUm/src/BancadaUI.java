import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GUI da Bancada com 4 setores (ESTOQUE, PROCESSO, MONTAGEM, EXPEDIÇÃO),
 * IPs pré-configurados e fundo rosa claro.
 * Esta classe usa o PlcConnector existente SEM alterar nenhuma linha das suas classes de comunicação.
 */
public class BancadaUI {

    // Mapa de Setor -> IP (fixo)
    private static final Map<String, String> SETORES = new LinkedHashMap<>() {{
        put("ESTOQUE",   "10.74.241.10");
        put("PROCESSO",  "10.74.241.20");
        put("MONTAGEM",  "10.74.241.30");
        put("EXPEDIÇÃO", "10.74.241.40");
    }};
    private static final int PORTA_S7 = 102;

    public static void main(String[] args) {
        // Look & Feel mais moderno (Nimbus)
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equalsIgnoreCase(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(BancadaUI::criarTelaPrincipal);
    }

    private static void criarTelaPrincipal() {
        JFrame frame = new JFrame("Bancada – Seleção de Setor");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(820, 560);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(18, 18));
        root.setBackground(Color.decode("#FFE6F0"));

        JLabel titulo = new JLabel("Bancada • Selecione o Setor", SwingConstants.CENTER);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titulo.setForeground(new Color(51, 51, 51));
        root.add(titulo, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 18, 18));
        grid.setOpaque(false);

        Map<String, String> emoji = Map.of(
                "ESTOQUE", "📦",
                "PROCESSO", "⚙️",
                "MONTAGEM", "🛠️",
                "EXPEDIÇÃO", "🚚"
        );

        for (var e : SETORES.entrySet()) {
            String setor = e.getKey();
            String ip = e.getValue();
            JButton btn = criarBotaoSetor(
                    (emoji.getOrDefault(setor, "🔹")) + " " + setor,
                    ip
            );
            btn.addActionListener(ev -> new JanelaSetor(frame, setor, ip).mostrar());
            grid.add(btn);
        }

        root.add(grid, BorderLayout.CENTER);

        JLabel rodape = new JLabel("CLP Siemens S7 – ISO-on-TCP (porta 102)", SwingConstants.CENTER);
        rodape.setForeground(new Color(90, 90, 90));
        rodape.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        root.add(rodape, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private static JButton criarBotaoSetor(String titulo, String ip) {
        String html = """
                <html><center>
                <div style='font-size:18px; font-weight:bold;'>%s</div>
                <div style='margin-top:6px; color:#444;'>%s</div>
                </center></html>
                """.formatted(titulo, ip);

        JButton btn = new JButton(html);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btn.setBackground(Color.WHITE);
        btn.setForeground(new Color(51, 51, 51));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 107, 165), 2),
                BorderFactory.createEmptyBorder(20, 24, 20, 24)
        ));
        return btn;
    }

    // ------------------------ Janela do Setor ------------------------

    private static class JanelaSetor extends JDialog {
        private final String setor;
        private final String ip;
        private PlcConnector connector;

        // UI
        private final JLabel lblStatus = new JLabel("Desconectado");
        private final JTextArea log = new JTextArea(8, 20);

        // Leituras
        private final JComboBox<String> cbTipoRead = new JComboBox<>(new String[]{"bit", "byte", "int", "float", "string", "block"});
        private final JTextField tfDbRead = new JTextField();
        private final JTextField tfOffsetRead = new JTextField();
        private final JTextField tfBitRead = new JTextField();     // habilita apenas quando "bit"
        private final JTextField tfSizeRead = new JTextField();    // habilita em "string" e "block"
        private final JButton btnLer = new JButton("Ler");

        // Escritas
        private final JComboBox<String> cbTipoWrite = new JComboBox<>(new String[]{"bit", "byte", "int", "float", "string"});
        private final JTextField tfDbWrite = new JTextField();
        private final JTextField tfOffsetWrite = new JTextField();
        private final JTextField tfBitWrite = new JTextField();    // habilita apenas quando "bit"
        private final JTextField tfValorWrite = new JTextField();
        private final JButton btnEscrever = new JButton("Escrever");

        private final JButton btnVoltar = new JButton("Desconectar / Voltar");

        JanelaSetor(Frame owner, String setor, String ip) {
            super(owner, true);
            this.setor = setor;
            this.ip = ip;

            setTitle(setor + " – " + ip);
            setSize(900, 640);
            setLocationRelativeTo(owner);

            JPanel root = new JPanel(new BorderLayout(12, 12));
            root.setBackground(Color.decode("#FDE7F3"));
            root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Topo: título + status
            JPanel topo = new JPanel(new BorderLayout());
            topo.setOpaque(false);
            JLabel titulo = new JLabel("Setor: " + setor + "   |   CLP: " + ip, SwingConstants.LEFT);
            titulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
            topo.add(titulo, BorderLayout.WEST);

            lblStatus.setIcon(criarBola(Color.RED));
            lblStatus.setForeground(new Color(80, 80, 80));
            lblStatus.setHorizontalTextPosition(SwingConstants.RIGHT);
            topo.add(lblStatus, BorderLayout.EAST);

            root.add(topo, BorderLayout.NORTH);

            // Centro: abas
            JTabbedPane tabs = new JTabbedPane();
            tabs.setBackground(Color.decode("#FDE7F3"));
            tabs.addTab("Leitura", criarPainelLeitura());
            tabs.addTab("Escrita", criarPainelEscrita());
            tabs.addTab("Ajuda", criarPainelAjuda());
            root.add(tabs, BorderLayout.CENTER);

            // Bottom: Log + Voltar
            JPanel bottom = new JPanel(new BorderLayout(8, 8));
            bottom.setOpaque(false);

            log.setEditable(false);
            log.setFont(new Font("Consolas", Font.PLAIN, 13));
            JScrollPane spLog = new JScrollPane(log);
            spLog.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(209, 107, 165)),
                    "Log", TitledBorder.LEFT, TitledBorder.TOP
            ));

            bottom.add(spLog, BorderLayout.CENTER);

            JPanel boxVoltar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            boxVoltar.setOpaque(false);
            estilizarBotaoPerigo(btnVoltar);
            boxVoltar.add(btnVoltar);
            bottom.add(boxVoltar, BorderLayout.SOUTH);

            root.add(bottom, BorderLayout.SOUTH);

            setContentPane(root);

            // Eventos UI
            cbTipoRead.addActionListener(e -> atualizarCamposLeitura());
            cbTipoWrite.addActionListener(e -> atualizarCamposEscrita());

            btnLer.addActionListener(e -> executarLeitura());
            btnEscrever.addActionListener(e -> executarEscrita());
            btnVoltar.addActionListener(e -> {
                desconectarSilencioso();
                dispose();
            });

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    desconectarSilencioso();
                }
            });
        }

        void mostrar() {
            // Conectar em background (não travar UI)
            setControlesHabilitados(false);
            lblStatus.setText("Conectando...");
            lblStatus.setIcon(criarBola(Color.ORANGE));

            new SwingWorker<Void, Void>() {
                private Exception erro;

                @Override
                protected Void doInBackground() {
                    try {
                        connector = new PlcConnector(ip, PORTA_S7);
                        connector.connect();
                    } catch (Exception ex) {
                        erro = ex;
                    }
                    return null;
                }

                @Override
                protected void done() {
                    if (erro != null) {
                        lblStatus.setText("Falha na conexão");
                        lblStatus.setIcon(criarBola(Color.RED));
                        JOptionPane.showMessageDialog(JanelaSetor.this,
                                "Erro ao conectar: " + erro.getMessage(),
                                "Conexão", JOptionPane.ERROR_MESSAGE);
                        dispose();
                    } else {
                        lblStatus.setText("Conectado");
                        lblStatus.setIcon(criarBola(new Color(0, 160, 0)));
                        log.append("Conectado a " + ip + "\n");
                        setControlesHabilitados(true);
                        setVisible(true);
                    }
                }
            }.execute();
        }

        private JPanel criarPainelLeitura() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            int row = 0;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Tipo:"), c);
            c.gridx = 1; c.gridy = row; p.add(cbTipoRead, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("DB:"), c);
            c.gridx = 1; c.gridy = row; p.add(tfDbRead, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Offset (byte):"), c);
            c.gridx = 1; c.gridy = row; p.add(tfOffsetRead, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Bit (0-7):"), c);
            c.gridx = 1; c.gridy = row; p.add(tfBitRead, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Tamanho (bytes):"), c);
            c.gridx = 1; c.gridy = row; p.add(tfSizeRead, c);
            row++;

            JButton btn = btnLer;
            estilizarBotaoPrimario(btn);
            c.gridx = 0; c.gridy = row; c.gridwidth = 2; p.add(btn, c);
            row++;

            atualizarCamposLeitura();
            return p;
        }

        private JPanel criarPainelEscrita() {
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            int row = 0;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Tipo:"), c);
            c.gridx = 1; c.gridy = row; p.add(cbTipoWrite, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("DB:"), c);
            c.gridx = 1; c.gridy = row; p.add(tfDbWrite, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Offset (byte):"), c);
            c.gridx = 1; c.gridy = row; p.add(tfOffsetWrite, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Bit (0-7):"), c);
            c.gridx = 1; c.gridy = row; p.add(tfBitWrite, c);
            row++;

            c.gridx = 0; c.gridy = row; p.add(new JLabel("Valor:"), c);
            c.gridx = 1; c.gridy = row; p.add(tfValorWrite, c);
            row++;

            JButton btn = btnEscrever;
            estilizarBotaoPrimario(btn);
            c.gridx = 0; c.gridy = row; c.gridwidth = 2; p.add(btn, c);
            row++;

            atualizarCamposEscrita();
            return p;
        }

        private JPanel criarPainelAjuda() {
            JPanel p = new JPanel(new BorderLayout());
            p.setOpaque(false);

            JTextArea ajuda = new JTextArea("""
                    💡 Dicas de uso
                    • Utilize DB / Offset conforme o mapeamento da Bancada.
                    • Tipos suportados:
                      - Leitura: bit, byte, int, float, string, block
                      - Escrita: bit, byte, int, float, string
                    • Para BIT, informe o número do bit (0 a 7).
                    • Para STRING e BLOCO, informe o "Tamanho (bytes)".
                    • Porta S7 padrão: 102
                    • Área: DB (S7-ANY)
                    """);
            ajuda.setEditable(false);
            ajuda.setLineWrap(true);
            ajuda.setWrapStyleWord(true);
            ajuda.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            ajuda.setBackground(new Color(0,0,0,0));

            p.add(ajuda, BorderLayout.CENTER);
            return p;
        }

        private void atualizarCamposLeitura() {
            String tipo = (String) cbTipoRead.getSelectedItem();
            boolean isBit = "bit".equalsIgnoreCase(tipo);
            boolean needsSize = "string".equalsIgnoreCase(tipo) || "block".equalsIgnoreCase(tipo);

            tfBitRead.setEnabled(isBit);
            tfBitRead.setToolTipText(isBit ? "Informe um valor entre 0 e 7" : "Não aplicável");
            if (!isBit) tfBitRead.setText("");

            tfSizeRead.setEnabled(needsSize);
            tfSizeRead.setToolTipText(needsSize ? "Informe o tamanho em bytes" : "Não aplicável");
            if (!needsSize) tfSizeRead.setText("");
        }

        private void atualizarCamposEscrita() {
            String tipo = (String) cbTipoWrite.getSelectedItem();
            boolean isBit = "bit".equalsIgnoreCase(tipo);

            tfBitWrite.setEnabled(isBit);
            tfBitWrite.setToolTipText(isBit ? "Informe um valor entre 0 e 7" : "Não aplicável");
            if (!isBit) tfBitWrite.setText("");

            tfValorWrite.setToolTipText(sugestaoValor(tipo));
        }

        private String sugestaoValor(String tipo) {
            return switch (tipo.toLowerCase()) {
                case "bit" -> "true / false";
                case "byte" -> "0..255";
                case "int" -> "inteiro (ex.: 123)";
                case "float" -> "real (ex.: 12.34)";
                case "string" -> "texto";
                default -> "";
            };
        }

        private void executarLeitura() {
            setControlesHabilitados(false);
            new SwingWorker<Void, Void>() {
                private String mensagem;

                @Override
                protected Void doInBackground() {
                    try {
                        String tipo = ((String) cbTipoRead.getSelectedItem()).toLowerCase();
                        int db = Integer.parseInt(tfDbRead.getText().trim());
                        int offset = Integer.parseInt(tfOffsetRead.getText().trim());

                        switch (tipo) {
                            case "bit" -> {
                                int bit = Integer.parseInt(tfBitRead.getText().trim());
                                boolean v = connector.readBit(db, offset, bit);
                                mensagem = String.format("[%s] LER BIT DB%d.DBX%d.%d = %s%n", setor, db, offset, bit, v);
                            }
                            case "byte" -> {
                                byte v = connector.readByte(db, offset);
                                mensagem = String.format("[%s] LER BYTE DB%d.DBB%d = %d (0x%02X)%n", setor, db, offset, v, v);
                            }
                            case "int" -> {
                                int v = connector.readInt(db, offset);
                                mensagem = String.format("[%s] LER INT DB%d.DBW%d = %d%n", setor, db, offset, v);
                            }
                            case "float" -> {
                                float v = connector.readFloat(db, offset);
                                mensagem = String.format("[%s] LER REAL DB%d.DBD%d = %.4f%n", setor, db, offset, v);
                            }
                            case "string" -> {
                                int size = Integer.parseInt(tfSizeRead.getText().trim());
                                String v = connector.readString(db, offset, size);
                                mensagem = String.format("[%s] LER STRING DB%d.DBB%d[%d] = \"%s\"%n", setor, db, offset, size, v);
                            }
                            case "block" -> {
                                int size = Integer.parseInt(tfSizeRead.getText().trim());
                                byte[] bytes = connector.readBlock(db, offset, size);
                                mensagem = String.format("[%s] LER BLOCO DB%d.DBB%d [%d] = %s%n",
                                        setor, db, offset, size, bytesToHex(bytes));
                            }
                            default -> mensagem = "Tipo inválido para leitura.\n";
                        }
                    } catch (Exception ex) {
                        mensagem = "Erro na leitura: " + ex.getMessage() + "\n";
                    }
                    return null;
                }

                @Override
                protected void done() {
                    log.append(mensagem);
                    setControlesHabilitados(true);
                }
            }.execute();
        }

        private void executarEscrita() {
            setControlesHabilitados(false);
            new SwingWorker<Void, Void>() {
                private String mensagem;

                @Override
                protected Void doInBackground() {
                    try {
                        String tipo = ((String) cbTipoWrite.getSelectedItem()).toLowerCase();
                        int db = Integer.parseInt(tfDbWrite.getText().trim());
                        int offset = Integer.parseInt(tfOffsetWrite.getText().trim());
                        boolean ok;

                        switch (tipo) {
                            case "bit" -> {
                                int bit = Integer.parseInt(tfBitWrite.getText().trim());
                                boolean val = Boolean.parseBoolean(tfValorWrite.getText().trim());
                                ok = connector.writeBit(db, offset, bit, val);
                                mensagem = String.format("[%s] ESCREVER BIT DB%d.DBX%d.%d = %s -> %s%n",
                                        setor, db, offset, bit, val, ok);
                            }
                            case "byte" -> {
                                byte v = Byte.parseByte(tfValorWrite.getText().trim());
                                ok = connector.writeByte(db, offset, v);
                                mensagem = String.format("[%s] ESCREVER BYTE DB%d.DBB%d = %d -> %s%n",
                                        setor, db, offset, v, ok);
                            }
                            case "int" -> {
                                int v = Integer.parseInt(tfValorWrite.getText().trim());
                                ok = connector.writeInt(db, offset, v);
                                mensagem = String.format("[%s] ESCREVER INT DB%d.DBW%d = %d -> %s%n",
                                        setor, db, offset, v, ok);
                            }
                            case "float" -> {
                                float v = Float.parseFloat(tfValorWrite.getText().trim());
                                ok = connector.writeFloat(db, offset, v);
                                mensagem = String.format("[%s] ESCREVER REAL DB%d.DBD%d = %s -> %s%n",
                                        setor, db, offset, v, ok);
                            }
                            case "string" -> {
                                String s = tfValorWrite.getText();
                                ok = connector.writeString(db, offset, s.length(), s);
                                mensagem = String.format("[%s] ESCREVER STRING DB%d.DBB%d = \"%s\" -> %s%n",
                                        setor, db, offset, s, ok);
                            }
                            default -> mensagem = "Tipo inválido para escrita.\n";
                        }
                    } catch (Exception ex) {
                        mensagem = "Erro na escrita: " + ex.getMessage() + "\n";
                    }
                    return null;
                }

                @Override
                protected void done() {
                    log.append(mensagem);
                    setControlesHabilitados(true);
                }
            }.execute();
        }

        private void setControlesHabilitados(boolean enabled) {
            cbTipoRead.setEnabled(enabled);
            tfDbRead.setEnabled(enabled);
            tfOffsetRead.setEnabled(enabled);
            tfBitRead.setEnabled(enabled);
            tfSizeRead.setEnabled(enabled);
            btnLer.setEnabled(enabled);

            cbTipoWrite.setEnabled(enabled);
            tfDbWrite.setEnabled(enabled);
            tfOffsetWrite.setEnabled(enabled);
            tfBitWrite.setEnabled(enabled);
            tfValorWrite.setEnabled(enabled);
            btnEscrever.setEnabled(enabled);

            btnVoltar.setEnabled(true); // sempre pode voltar
        }

        private void desconectarSilencioso() {
            try {
                if (connector != null) {
                    connector.disconnect();
                    lblStatus.setText("Desconectado");
                    lblStatus.setIcon(criarBola(Color.RED));
                }
            } catch (Exception ignored) {}
        }

        private static Icon criarBola(Color cor) {
            return new Icon() {
                private final int SIZE = 10;
                @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(cor);
                    g.fillOval(x, y, SIZE, SIZE);
                    g.setColor(new Color(0,0,0,60));
                    g.drawOval(x, y, SIZE, SIZE);
                }
                @Override public int getIconWidth() { return SIZE; }
                @Override public int getIconHeight() { return SIZE; }
            };
        }

        private static void estilizarBotaoPrimario(JButton b) {
            b.setBackground(Color.WHITE);
            b.setForeground(new Color(51, 51, 51));
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(209, 107, 165), 2),
                    BorderFactory.createEmptyBorder(10, 16, 10, 16)
            ));
        }

        private static void estilizarBotaoPerigo(JButton b) {
            b.setBackground(Color.WHITE);
            b.setForeground(new Color(160, 0, 0));
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 80, 80), 2),
                    BorderFactory.createEmptyBorder(10, 16, 10, 16)
            ));
        }

        private static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02X ", b));
            return sb.toString().trim();
        }
    }
}