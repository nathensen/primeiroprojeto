import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BancadaMonitorUI
 * Dois monitores independentes para acompanhar setores da Bancada ao mesmo tempo,
 * com seleção de setor, tipo de leitura e parâmetros (DB/Offset/Bit/Tamanho).
 *
 * Requisitos: classes PlcConnector / S7ProtocolClient existentes no projeto.
 */
public class BancadaUI extends JFrame {

    // Setores e IPs fixos
    private static final Map<String, String> SETORES = new LinkedHashMap<>() {{
        put("ESTOQUE",   "10.74.241.10");
        put("PROCESSO",  "10.74.241.20");
        put("MONTAGEM",  "10.74.241.30");
        put("EXPEDIÇÃO", "10.74.241.40");
    }};
    private static final int PORTA_S7 = 102;

    public static void main(String[] args) {
        // Look&Feel agradável
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equalsIgnoreCase(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            BancadaUI ui = new BancadaUI();
            ui.setVisible(true);
        });
    }

    public BancadaUI() {
        super("Bancada – Monitoramento em Paralelo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 640);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(Color.decode("#FFE6F0"));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titulo = new JLabel("Bancada • Monitoramento em Paralelo (selecione 2 setores)", SwingConstants.CENTER);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(new Color(51, 51, 51));
        root.add(titulo, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 12));
        grid.setOpaque(false);

        MonitorPanel monitorA = new MonitorPanel("Monitor A");
        MonitorPanel monitorB = new MonitorPanel("Monitor B");
        grid.add(monitorA);
        grid.add(monitorB);

        root.add(grid, BorderLayout.CENTER);

        JLabel rodape = new JLabel("CLP Siemens S7 – ISO-on-TCP (porta 102) | Fundo: #FFE6F0", SwingConstants.CENTER);
        rodape.setForeground(new Color(90, 90, 90));
        root.add(rodape, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // --------- Painel individual do monitor ---------

    private class MonitorPanel extends JPanel {
        private final String titulo;

        private JComboBox<String> cbSetor;
        private JComboBox<String> cbTipo;
        private JTextField tfDb, tfOffset, tfBit, tfSize;
        private JSpinner spIntervalo;
        private JButton btnIniciar, btnParar;
        private JLabel lblStatus, lblValorAtual;
        private JTextArea logArea;

        private MonitorWorker worker; // SwingWorker rodando este monitor

        MonitorPanel(String titulo) {
            super(new BorderLayout(10, 10));
            this.titulo = titulo;
            setOpaque(true);
            setBackground(Color.decode("#FDE7F3"));
            setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(209, 107, 165), 2),
                    titulo, TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 14)
            ));

            JPanel topo = new JPanel(new GridBagLayout());
            topo.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            cbSetor = new JComboBox<>(SETORES.keySet().toArray(new String[0]));
            cbTipo = new JComboBox<>(new String[]{"bit", "byte", "int", "float", "string", "block"});

            tfDb = new JTextField();
            tfOffset = new JTextField();
            tfBit = new JTextField();
            tfSize = new JTextField();

            spIntervalo = new JSpinner(new SpinnerNumberModel(1000, 200, 5000, 100));
            btnIniciar = new JButton("Iniciar");
            btnParar = new JButton("Parar");

            lblStatus = new JLabel("Desconectado", criarBola(Color.RED), SwingConstants.LEFT);
            lblValorAtual = new JLabel("—");

            int row = 0;
            c.gridx = 0; c.gridy = row; topo.add(new JLabel("Setor:"), c);
            c.gridx = 1; c.gridy = row; topo.add(cbSetor, c);
            c.gridx = 2; c.gridy = row; topo.add(new JLabel("Tipo:"), c);
            c.gridx = 3; c.gridy = row; topo.add(cbTipo, c);
            row++;

            c.gridx = 0; c.gridy = row; topo.add(new JLabel("DB:"), c);
            c.gridx = 1; c.gridy = row; topo.add(tfDb, c);
            c.gridx = 2; c.gridy = row; topo.add(new JLabel("Offset (byte):"), c);
            c.gridx = 3; c.gridy = row; topo.add(tfOffset, c);
            row++;

            c.gridx = 0; c.gridy = row; topo.add(new JLabel("Bit (0–7):"), c);
            c.gridx = 1; c.gridy = row; topo.add(tfBit, c);
            c.gridx = 2; c.gridy = row; topo.add(new JLabel("Tamanho (bytes):"), c);
            c.gridx = 3; c.gridy = row; topo.add(tfSize, c);
            row++;

            c.gridx = 0; c.gridy = row; topo.add(new JLabel("Intervalo (ms):"), c);
            c.gridx = 1; c.gridy = row; topo.add(spIntervalo, c);
            c.gridx = 2; c.gridy = row; topo.add(btnIniciar, c);
            c.gridx = 3; c.gridy = row; topo.add(btnParar, c);
            row++;

            c.gridx = 0; c.gridy = row; c.gridwidth = 2; topo.add(lblStatus, c);
            c.gridx = 2; c.gridy = row; c.gridwidth = 2;
            JPanel valorBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            valorBox.setOpaque(false);
            valorBox.add(new JLabel("Valor atual: "));
            valorBox.add(lblValorAtual);
            topo.add(valorBox, c);

            add(topo, BorderLayout.NORTH);

            logArea = new JTextArea();
            logArea.setEditable(false);
            logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
            JScrollPane sp = new JScrollPane(logArea);
            sp.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(209, 107, 165)),
                    "Log", TitledBorder.LEFT, TitledBorder.TOP
            ));
            add(sp, BorderLayout.CENTER);

            // Estado inicial de campos dependentes
            atualizarHabilitacaoCampos();
            cbTipo.addActionListener(e -> atualizarHabilitacaoCampos());

            btnIniciar.addActionListener(e -> iniciarMonitor());
            btnParar.addActionListener(e -> pararMonitor());
        }

        private void atualizarHabilitacaoCampos() {
            String tipo = String.valueOf(cbTipo.getSelectedItem()).toLowerCase();
            boolean isBit = "bit".equals(tipo);
            boolean needsSize = "string".equals(tipo) || "block".equals(tipo);

            tfBit.setEnabled(isBit);
            if (!isBit) tfBit.setText("");

            tfSize.setEnabled(needsSize);
            if (!needsSize) tfSize.setText("");
        }

        private void iniciarMonitor() {
            if (worker != null && !worker.isDone()) {
                appendLog("Monitor já está em execução.");
                return;
            }
            // Validação de campos
            String setor = String.valueOf(cbSetor.getSelectedItem());
            String ip = SETORES.get(setor);
            String tipo = String.valueOf(cbTipo.getSelectedItem()).toLowerCase();

            Integer db, offset, bit = null, size = null, intervalo;
            try {
                db = Integer.parseInt(tfDb.getText().trim());
                offset = Integer.parseInt(tfOffset.getText().trim());
                if ("bit".equals(tipo)) {
                    bit = Integer.parseInt(tfBit.getText().trim());
                    if (bit < 0 || bit > 7) throw new IllegalArgumentException("Bit deve ser 0..7");
                }
                if ("string".equals(tipo) || "block".equals(tipo)) {
                    size = Integer.parseInt(tfSize.getText().trim());
                    if (size <= 0) throw new IllegalArgumentException("Tamanho deve ser > 0");
                }
                intervalo = (Integer) spIntervalo.getValue();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Preencha os campos corretamente.\n" + ex.getMessage(),
                        "Campos inválidos", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Cria e inicia o worker
            worker = new MonitorWorker(setor, ip, tipo, db, offset, bit, size, intervalo);
            setRodando(true);
            worker.execute();
        }

        private void pararMonitor() {
            if (worker != null) {
                worker.cancel(true);
                worker = null;
            }
            setRodando(false);
            lblValorAtual.setText("—");
        }

        private void setRodando(boolean on) {
            cbSetor.setEnabled(!on);
            cbTipo.setEnabled(!on);
            tfDb.setEnabled(!on);
            tfOffset.setEnabled(!on);
            tfBit.setEnabled(!on && "bit".equals(String.valueOf(cbTipo.getSelectedItem()).toLowerCase()));
            tfSize.setEnabled(!on && ("string".equals(String.valueOf(cbTipo.getSelectedItem()).toLowerCase())
                    || "block".equals(String.valueOf(cbTipo.getSelectedItem()).toLowerCase())));
            spIntervalo.setEnabled(!on);

            if (on) {
                lblStatus.setText("Conectando...");
                lblStatus.setIcon(criarBola(Color.ORANGE));
            } else {
                lblStatus.setText("Desconectado");
                lblStatus.setIcon(criarBola(Color.RED));
            }
        }

        private void appendLog(String msg) {
            logArea.append(msg + "\n");
            // opcional: truncar se ficar muito grande
            if (logArea.getLineCount() > 1200) {
                logArea.setText(logArea.getText().replaceFirst("(?s)^.*?\\n", "")); // remove primeiras linhas
            }
        }

        private Icon criarBola(Color cor) {
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

        // ---------- Worker que realiza a leitura periódica ----------
        private class MonitorWorker extends SwingWorker<Void, String> {
            private final String setor, ip, tipo;
            private final int db, offset, intervaloMs;
            private final Integer bit, size;
            private PlcConnector connector;

            MonitorWorker(String setor, String ip, String tipo, int db, int offset,
                          Integer bit, Integer size, int intervaloMs) {
                this.setor = setor; this.ip = ip; this.tipo = tipo;
                this.db = db; this.offset = offset; this.bit = bit; this.size = size;
                this.intervaloMs = intervaloMs;
            }

            @Override
            protected Void doInBackground() {
                try {
                    connector = new PlcConnector(ip, PORTA_S7);
                    connector.connect();
                    publish("[%s] Conectado a %s".formatted(setor, ip));
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Conectado");
                        lblStatus.setIcon(criarBola(new Color(0,160,0)));
                    });
                } catch (Exception e) {
                    publish("[%s] Falha ao conectar: %s".formatted(setor, e.getMessage()));
                    cancelar();
                    return null;
                }

                while (!isCancelled()) {
                    try {
                        Object valor = ler();
                        String textoValor = formatarValor(valor);
                        SwingUtilities.invokeLater(() -> lblValorAtual.setText(textoValor));
                        publish("[%s] %s".formatted(setor, textoValor));
                        Thread.sleep(intervaloMs);
                    } catch (InterruptedException ie) {
                        break;
                    } catch (Exception e) {
                        publish("[%s] Erro de leitura: %s".formatted(setor, e.getMessage()));
                        // tenta reconectar depois de um tempo
                        dormir(1200);
                        try {
                            if (connector != null) {
                                connector.disconnect();
                            }
                        } catch (Exception ignored) {}
                        try {
                            connector = new PlcConnector(ip, PORTA_S7);
                            connector.connect();
                            publish("[%s] Reconectado".formatted(setor));
                        } catch (Exception ex) {
                            publish("[%s] Falha na reconexão: %s".formatted(setor, ex.getMessage()));
                            dormir(1500);
                        }
                    }
                }
                fechar();
                return null;
            }

            private Object ler() throws Exception {
                return switch (tipo) {
                    case "bit" -> connector.readBit(db, offset, bit);
                    case "byte" -> connector.readByte(db, offset);
                    case "int" -> connector.readInt(db, offset);
                    case "float" -> connector.readFloat(db, offset);
                    case "string" -> connector.readString(db, offset, size);
                    case "block" -> connector.readBlock(db, offset, size);
                    default -> "TIPO?";
                };
            }

            private String formatarValor(Object v) {
                if (v == null) return "—";
                if (v instanceof byte[] arr) return bytesToHex(arr);
                if (v instanceof Byte b) return String.valueOf(b & 0xFF);
                if (v instanceof Float f) return String.format("%.3f", f);
                return String.valueOf(v);
            }

            private void dormir(long ms) {
                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
            }

            private void fechar() {
                try { if (connector != null) connector.disconnect(); } catch (Exception ignored) {}
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Desconectado");
                    lblStatus.setIcon(criarBola(Color.RED));
                    setRodando(false);
                });
            }

            private void cancelar() {
                cancel(true);
                fechar();
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks) appendLog(s);
            }
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02X ", b));
            return sb.toString().trim();
        }
    }
}