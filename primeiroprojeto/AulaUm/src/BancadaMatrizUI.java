import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * BancadaMatrizUI (com escrita por painel)
 *
 * - ESTOQUE: offsets 68..95 (um quadrinho por offset)
 * - EXPEDIÇÃO: 12 posições de 2 bytes (INT), iniciando no offset 6, passo 2 (6,8,10,...,28)
 * - Cada setor com DB, tipo (bit/byte/int/float), bit (se for bit), intervalo, Start/Stop.
 * - Atualização com SwingWorker; reconexão automática; fundo rosa claro.
 * - Acrescentado: barra de escrita (Offset, Valor, Gravar) em cada painel.
 *
 * Requer: PlcConnector / S7ProtocolClient.
 */
public class BancadaMatrizUI extends JFrame {

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
            BancadaMatrizUI ui = new BancadaMatrizUI();
            ui.setVisible(true);
        });
    }

    public BancadaMatrizUI() {
        super("Bancada – Posições (Estoque x Expedição)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 760);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(Color.decode("#FFE6F0"));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titulo = new JLabel(
                "Bancada • Estoque (68..95)  |  Expedição (12 posições de 2 bytes a partir do offset 6)",
                SwingConstants.CENTER);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(new Color(51, 51, 51));
        root.add(titulo, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 12));
        grid.setOpaque(false);

        // Painel do ESTOQUE: offsets 68..95, passo 1 (default colunas=6)
        int[] offsetsEstoque = rangeInclusive(68, 95, 1);
        SetorMatrixPanel estoquePanel = new SetorMatrixPanel(
                "ESTOQUE", "10.74.241.10",
                new Color(0xFD, 0xE7, 0xF3),
                offsetsEstoque,
                6, // colunas visuais
                "byte" // tipo default sugerido
        );

        // Painel da EXPEDIÇÃO: 12 posições INT de 2 bytes, iniciando no 6: 6,8,10,...,28
        int[] offsetsExpedicao = steppedOffsets(6, 12, 2);
        SetorMatrixPanel expedicaoPanel = new SetorMatrixPanel(
                "EXPEDIÇÃO", "10.74.241.40",
                new Color(0xFD, 0xE7, 0xF3),
                offsetsExpedicao,
                6, // 2 linhas x 6 colunas (limpo para 12 posições)
                "int" // tipo default (2 bytes)
        );

        grid.add(estoquePanel);
        grid.add(expedicaoPanel);

        root.add(grid, BorderLayout.CENTER);

        JLabel rodape = new JLabel(
                "CLP Siemens S7 – ISO-on-TCP (porta 102) | Fundo #FFE6F0 / painéis #FDE7F3",
                SwingConstants.CENTER);
        rodape.setForeground(new Color(90, 90, 90));
        root.add(rodape, BorderLayout.SOUTH);

        setContentPane(root);
    }

    // Utilitários para offsets
    private static int[] rangeInclusive(int start, int end, int step) {
        int count = ((end - start) / step) + 1;
        int[] arr = new int[count];
        int v = start;
        for (int i = 0; i < count; i++, v += step) arr[i] = v;
        return arr;
    }

    private static int[] steppedOffsets(int start, int count, int step) {
        int[] arr = new int[count];
        int v = start;
        for (int i = 0; i < count; i++, v += step) arr[i] = v;
        return arr;
    }

    // ------------------ Painel por Setor ------------------

    static class SetorMatrixPanel extends JPanel {
        private static final int PORTA_S7 = 102;

        private final String setor;
        private final String ip;
        private final int[] offsets; // offsets a apresentar (cada quadrinho)
        private final int columns;
        private PlcConnector connector;
        private MatrixWorker worker;

        // Lock p/ sincronizar leitura X escrita no mesmo socket
        private final Object ioLock = new Object();

        // Controles
        private final JTextField tfDb = new JTextField();
        private final JComboBox<String> cbTipo = new JComboBox<>(new String[]{"byte", "bit", "int", "float"});
        private final JTextField tfBit = new JTextField(); // habilita só quando bit
        private final JSpinner spIntervalo = new JSpinner(new SpinnerNumberModel(1000, 200, 5000, 100));
        private final JButton btnStart = new JButton("Iniciar");
        private final JButton btnStop = new JButton("Parar");
        private final JLabel lblStatus = new JLabel("Desconectado", criarBola(Color.RED), SwingConstants.LEFT);

        // Barra de escrita
        private final JTextField tfWriteOffset = new JTextField();
        private final JTextField tfWriteValor  = new JTextField();
        private final JButton btnGravar        = new JButton("Gravar");
        private final JLabel lblFaixa          = new JLabel();

        // Grade de posições
        private final JTextField[] campos;
        private final JLabel[] rotulos;

        SetorMatrixPanel(String setor, String ip, Color fundo,
                         int[] offsets, int columns, String defaultTipo) {
            super(new BorderLayout(10, 10));
            this.setor = setor;
            this.ip = ip;
            this.offsets = offsets;
            this.columns = Math.max(1, columns);
            this.campos = new JTextField[offsets.length];
            this.rotulos = new JLabel[offsets.length];

            setOpaque(true);
            setBackground(fundo);
            setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(209, 107, 165), 2),
                    setor + "  |  " + ip,
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font("Segoe UI", Font.BOLD, 14)
            ));

            // Topo: parâmetros
            JPanel topo = new JPanel(new GridBagLayout());
            topo.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(6, 6, 6, 6);
            c.fill = GridBagConstraints.HORIZONTAL;

            if (defaultTipo != null) cbTipo.setSelectedItem(defaultTipo);

            int row = 0;
            c.gridx = 0; c.gridy = row; topo.add(new JLabel("DB:"), c);
            c.gridx = 1; c.gridy = row; topo.add(tfDb, c);
            c.gridx = 2; c.gridy = row; topo.add(new JLabel("Tipo:"), c);
            c.gridx = 3; c.gridy = row; topo.add(cbTipo, c);
            row++;

            c.gridx = 0; c.gridy = row; topo.add(new JLabel("Bit (0-7):"), c);
            c.gridx = 1; c.gridy = row; topo.add(tfBit, c);
            c.gridx = 2; c.gridy = row; topo.add(new JLabel("Intervalo (ms):"), c);
            c.gridx = 3; c.gridy = row; topo.add(spIntervalo, c);
            row++;

            JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            botoes.setOpaque(false);
            estilizarBotaoPrimario(btnStart);
            estilizarBotaoPerigo(btnStop);
            botoes.add(btnStart);
            botoes.add(btnStop);

            c.gridx = 0; c.gridy = row; c.gridwidth = 2; topo.add(lblStatus, c);
            c.gridx = 2; c.gridy = row; c.gridwidth = 2; topo.add(botoes, c);

            // ---- Barra de escrita (Offset, Valor, Gravar) ----
            row++;
            c.gridx = 0; c.gridy = row; c.gridwidth = 1; topo.add(new JLabel("Offset:"), c);
            c.gridx = 1; c.gridy = row; c.gridwidth = 1; topo.add(tfWriteOffset, c);
            c.gridx = 2; c.gridy = row; c.gridwidth = 1; topo.add(new JLabel("Valor:"), c);
            c.gridx = 3; c.gridy = row; c.gridwidth = 1; topo.add(tfWriteValor, c);
            row++;

            JPanel linhaGravar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            linhaGravar.setOpaque(false);
            estilizarBotaoPrimario(btnGravar);
            linhaGravar.add(btnGravar);
            linhaGravar.add(lblFaixa);

            c.gridx = 0; c.gridy = row; c.gridwidth = 4; topo.add(linhaGravar, c);

            add(topo, BorderLayout.NORTH);

            // Centro: grade de offsets
            int rows = (int) Math.ceil(offsets.length / (double) this.columns);
            JPanel grade = new JPanel(new GridLayout(rows, this.columns, 8, 8));
            grade.setOpaque(false);

            for (int i = 0; i < offsets.length; i++) {
                int off = offsets[i];

                JPanel cel = new JPanel(new BorderLayout(4, 4));
                cel.setOpaque(true);
                cel.setBackground(Color.WHITE);
                cel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(209, 107, 165), 1),
                        BorderFactory.createEmptyBorder(6, 6, 6, 6)
                ));

                JLabel lbl = new JLabel("Offset " + off, SwingConstants.LEFT);
                lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));

                JTextField tf = new JTextField("—");
                tf.setEditable(false);
                tf.setHorizontalAlignment(JTextField.CENTER);
                tf.setFont(new Font("Consolas", Font.BOLD, 14));

                cel.add(lbl, BorderLayout.NORTH);
                cel.add(tf, BorderLayout.CENTER);

                rotulos[i] = lbl;
                campos[i] = tf;
                grade.add(cel);
            }
            add(grade, BorderLayout.CENTER);

            // Habilitações e dicas
            cbTipo.addActionListener(e -> atualizarCampos());
            atualizarCampos();

            // Ações principais
            btnStart.addActionListener(e -> iniciar());
            btnStop.addActionListener(e -> parar());
            btnGravar.addActionListener(e -> escreverValor());

            // Sugestões de preenchimento e faixa
            if ("EXPEDIÇÃO".equalsIgnoreCase(setor)) {
                cbTipo.setSelectedItem("int"); // 2 bytes por posição
                lblFaixa.setText("Faixa offsets: 6..28 (pares, INT 2 bytes)");
            } else {
                lblFaixa.setText("Faixa offsets: 68..95 (byte por padrão)");
            }

            // Desabilitar escrita até conectar
            setWriteEnabled(false);
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

        private static void estilizarBotaoPrimario(AbstractButton b) {
            b.setBackground(Color.WHITE);
            b.setForeground(new Color(51, 51, 51));
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(209, 107, 165), 2),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)
            ));
        }
        private static void estilizarBotaoPerigo(AbstractButton b) {
            b.setBackground(Color.WHITE);
            b.setForeground(new Color(160, 0, 0));
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 80, 80), 2),
                    BorderFactory.createEmptyBorder(8, 14, 8, 14)
            ));
        }

        private void atualizarCampos() {
            String tipo = String.valueOf(cbTipo.getSelectedItem()).toLowerCase();
            boolean isBit = "bit".equals(tipo);
            tfBit.setEnabled(isBit);
            if (!isBit) tfBit.setText("");

            // Ajuste visual dos rótulos conforme tipo
            for (int i = 0; i < offsets.length; i++) {
                int off = offsets[i];
                String prefixo = switch (tipo) {
                    case "bit"  -> "DBX";
                    case "byte" -> "DBB";
                    case "int"  -> "DBW";
                    case "float"-> "DBD";
                    default     -> "DB?";
                };
                rotulos[i].setText("Offset " + off + "  (" + prefixo + off + ")");
            }

            // Dica do valor esperado
            if (isBit) {
                tfWriteValor.setToolTipText("true/false ou 1/0");
            } else if ("byte".equals(tipo)) {
                tfWriteValor.setToolTipText("0..255");
            } else if ("int".equals(tipo)) {
                tfWriteValor.setToolTipText("Inteiro (2 bytes, signed)");
            } else if ("float".equals(tipo)) {
                tfWriteValor.setToolTipText("Número decimal (4 bytes)");
            } else {
                tfWriteValor.setToolTipText(null);
            }
        }

        private void iniciar() {
            if (worker != null && !worker.isDone()) {
                JOptionPane.showMessageDialog(this, "Este monitor já está rodando.", "Aviso",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // Validação
            final int db, intervaloMs;
            final String tipo = String.valueOf(cbTipo.getSelectedItem()).toLowerCase();
            Integer bit = null;
            try {
                db = Integer.parseInt(tfDb.getText().trim());
                intervaloMs = (Integer) spIntervalo.getValue();
                if ("bit".equals(tipo)) {
                    bit = Integer.parseInt(tfBit.getText().trim());
                    if (bit < 0 || bit > 7) throw new IllegalArgumentException("Bit deve ser 0..7");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Preencha DB/Bit/Intervalo corretamente.\n" + ex.getMessage(),
                        "Campos inválidos", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Estado visual
            setControlesHabilitados(false);
            setWriteEnabled(false);
            lblStatus.setText("Conectando...");
            lblStatus.setIcon(criarBola(Color.ORANGE));

            // Limpa campos
            for (JTextField tf : campos) tf.setText("…");

            // Inicia o worker
            worker = new MatrixWorker(db, tipo, bit, intervaloMs);
            worker.execute();
        }

        private void parar() {
            if (worker != null) {
                worker.cancel(true);
                worker = null;
            }
            try { if (connector != null) connector.disconnect(); } catch (Exception ignored) {}
            connector = null;

            lblStatus.setText("Desconectado");
            lblStatus.setIcon(criarBola(Color.RED));
            setControlesHabilitados(true);
            setWriteEnabled(false);
        }

        private void setControlesHabilitados(boolean enabled) {
            tfDb.setEnabled(enabled);
            cbTipo.setEnabled(enabled);
            tfBit.setEnabled(enabled && "bit".equals(String.valueOf(cbTipo.getSelectedItem()).toLowerCase()));
            spIntervalo.setEnabled(enabled);
            btnStart.setEnabled(enabled);
            btnStop.setEnabled(!enabled);
            // Específico de escrita fica a cargo de setWriteEnabled(...)
        }

        private void setWriteEnabled(boolean enabled) {
            tfWriteOffset.setEnabled(enabled);
            tfWriteValor.setEnabled(enabled);
            btnGravar.setEnabled(enabled);
        }

        // ---------------- Worker: lê todos os offsets definidos ----------------
        private class MatrixWorker extends SwingWorker<Void, MatrixWorker.Frame> {
            private final int db;
            private final String tipo;
            private final Integer bit;
            private final int intervaloMs;

            MatrixWorker(int db, String tipo, Integer bit, int intervaloMs) {
                this.db = db;
                this.tipo = tipo;
                this.bit = bit;
                this.intervaloMs = intervaloMs;
            }

            @Override
            protected Void doInBackground() {
                // Conectar
                try {
                    connector = new PlcConnector(ip, PORTA_S7);
                    connector.connect();
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Conectado");
                        lblStatus.setIcon(criarBola(new Color(0,160,0)));
                        setWriteEnabled(true); // habilitar escrita quando conectado
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("Falha ao conectar");
                        lblStatus.setIcon(criarBola(Color.RED));
                        setControlesHabilitados(true);
                        setWriteEnabled(false);
                    });
                    cancel(true);
                    return null;
                }

                while (!isCancelled()) {
                    try {
                        String[] valores = new String[offsets.length];
                        synchronized (ioLock) {
                            for (int i = 0; i < offsets.length; i++) {
                                int off = offsets[i];
                                valores[i] = lerComoTexto(db, off, tipo, bit);
                            }
                        }
                        publish(new Frame(valores));
                        Thread.sleep(intervaloMs);
                    } catch (InterruptedException ie) {
                        break;
                    } catch (Exception e) {
                        // Erro: tenta reconectar
                        SwingUtilities.invokeLater(() -> {
                            lblStatus.setText("Reconectando...");
                            lblStatus.setIcon(criarBola(Color.ORANGE));
                            setWriteEnabled(false);
                        });
                        try { if (connector != null) connector.disconnect(); } catch (Exception ignored) {}
                        connector = null;
                        dormir(1200);
                        try {
                            connector = new PlcConnector(ip, PORTA_S7);
                            connector.connect();
                            SwingUtilities.invokeLater(() -> {
                                lblStatus.setText("Conectado");
                                lblStatus.setIcon(criarBola(new Color(0,160,0)));
                                setWriteEnabled(true);
                            });
                        } catch (Exception ex) {
                            dormir(1500);
                        }
                    }
                }
                try { if (connector != null) connector.disconnect(); } catch (Exception ignored) {}
                connector = null;
                return null;
            }

            private void dormir(long ms) {
                try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
            }

            private String lerComoTexto(int db, int offset, String tipo, Integer bit) throws Exception {
                return switch (tipo) {
                    case "bit" -> {
                        boolean b;
                        synchronized (ioLock) { b = connector.readBit(db, offset, bit); }
                        yield String.valueOf(b);
                    }
                    case "byte" -> {
                        byte b;
                        synchronized (ioLock) { b = connector.readByte(db, offset); }
                        yield String.valueOf(b & 0xFF);
                    }
                    case "int" -> {
                        int v;
                        synchronized (ioLock) { v = connector.readInt(db, offset); }
                        yield String.valueOf(v);
                    }
                    case "float" -> {
                        float f;
                        synchronized (ioLock) { f = connector.readFloat(db, offset); }
                        yield String.format("%.3f", f);
                    }
                    default -> "—";
                };
            }

            @Override
            protected void process(List<Frame> chunks) {
                Frame f = chunks.get(chunks.size() - 1);
                String[] vs = f.valores();
                for (int i = 0; i < vs.length; i++) {
                    campos[i].setText(vs[i]);
                }
            }

            record Frame(String[] valores) {}
        }

        // ---------------- Escrita manual via botão "Gravar" ----------------
        private void escreverValor() {
            if (connector == null) {
                JOptionPane.showMessageDialog(this, "Conecte primeiro (Iniciar).",
                        "Atenção", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String tipo = String.valueOf(cbTipo.getSelectedItem()).toLowerCase();

            final int db;
            final int offset;
            Integer bit = null;
            String valStr = tfWriteValor.getText().trim();

            try {
                db = Integer.parseInt(tfDb.getText().trim());
                offset = Integer.parseInt(tfWriteOffset.getText().trim());

                // Regras de faixa por setor
                if ("ESTOQUE".equalsIgnoreCase(setor)) {
                    if (offset < 68 || offset > 95) {
                        throw new IllegalArgumentException("Offset (Estoque) deve estar entre 68 e 95.");
                    }
                } else if ("EXPEDIÇÃO".equalsIgnoreCase(setor)) {
                    if (offset < 6 || offset > 28) {
                        throw new IllegalArgumentException("Offset (Expedição) deve estar entre 6 e 28.");
                    }
                    if ("int".equals(tipo) && (offset % 2 != 0)) {
                        int resposta = JOptionPane.showConfirmDialog(
                                this,
                                "Offset não é par (padrão: 6,8,10,...). Deseja continuar mesmo assim?",
                                "Aviso de alinhamento",
                                JOptionPane.YES_NO_OPTION
                        );
                        if (resposta != JOptionPane.YES_OPTION) return;
                    }
                }

                if ("bit".equals(tipo)) {
                    bit = Integer.parseInt(tfBit.getText().trim());
                    if (bit < 0 || bit > 7) throw new IllegalArgumentException("Bit deve ser 0..7.");
                }

                boolean ok;
                synchronized (ioLock) {
                    switch (tipo) {
                        case "bit" -> {
                            boolean logic;
                            if (valStr.equalsIgnoreCase("true") || valStr.equals("1")) {
                                logic = true;
                            } else if (valStr.equalsIgnoreCase("false") || valStr.equals("0")) {
                                logic = false;
                            } else {
                                throw new IllegalArgumentException("Valor para bit deve ser true/false ou 1/0.");
                            }
                            ok = connector.writeBit(db, offset, bit, logic);
                        }
                        case "byte" -> {
                            int n = Integer.parseInt(valStr);
                            if (n < 0 || n > 255) throw new IllegalArgumentException("Byte deve ser 0..255.");
                            ok = connector.writeByte(db, offset, (byte) n);
                        }
                        case "int" -> {
                            int n = Integer.parseInt(valStr);
                            ok = connector.writeInt(db, offset, n);
                        }
                        case "float" -> {
                            float f = Float.parseFloat(valStr.replace(",", "."));
                            ok = connector.writeFloat(db, offset, f);
                        }
                        default -> throw new IllegalArgumentException("Tipo não suportado para escrita: " + tipo);
                    }
                }

                if (ok) {
                    JOptionPane.showMessageDialog(this, "Escrita realizada com sucesso!",
                            "OK", JOptionPane.INFORMATION_MESSAGE);
                    // feedback visual imediato se o offset estiver no painel
                    for (int i = 0; i < offsets.length; i++) {
                        if (offsets[i] == offset) {
                            campos[i].setText(valStr);
                            break;
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "Falha na escrita (retorno do CLP).",
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao escrever: " + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}