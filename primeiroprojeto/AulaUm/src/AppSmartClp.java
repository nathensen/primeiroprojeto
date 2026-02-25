import java.util.*;

public class AppSmartClp {
    private static Scanner scanner = new Scanner(System.in);
    private static PlcConnector connector;

    public static void main(String[] args) throws Exception {

        System.out.println("===================================================");
        System.out.println("===    Terminal de Comunicaçao CLP Siemens S7   ===");
        System.out.println("===================================================");

        System.out.println("Digite o IP do CLP: ");
        String ip = scanner.nextLine();
        int porta = 102; // porta padrao S7

        connector = new PlcConnector(ip, porta);

        try {
            System.out.println("Conectando ao CLP...");
            connector.connect();
            System.out.println("Conectado com sucesso!");

            boolean sair = false;
            while (!sair) {
                exibirMenu();
                int opcao = Integer.parseInt(scanner.nextLine());

                switch (opcao) {
                    case 1:
                        processarLeitura();
                        break;

                    case 2:
                        processarEscrita();
                        break;

                    case 0:
                        connector.disconnect();
                        sair = true;
                        break;

                    default:
                        System.out.println("Opção inválida!");
                }
            }

        } catch (Exception e) {

            System.err.print("Erro critico" + e.getMessage());
        } finally {
            System.out.println("Aplicaçao encerrada");
        }
    }

    private static void exibirMenu() {
        System.out.println("\n--- MENU PRINCIPAL ---");
        System.out.println("1. Ler Variável");
        System.out.println("2. Escrever Variável");
        System.out.println("0. Sair e Desconectar");
        System.out.print("Escolha uma Opção: ");

    }

    private static void processarLeitura() {
        try {
            System.out.print("DB Number: ");
            int db = Integer.parseInt(scanner.nextLine());
            System.out.print("Offset (Start Address): ");
            int offset = Integer.parseInt(scanner.nextLine());

            System.out.println("Tipo: 1-Bit, 2-Byte, 3-Int, 4-Float, 5-String, 6-Block");
            int tipo = Integer.parseInt(scanner.nextLine());

            switch (tipo) {
                case 1:
                    System.out.print("Bit Number (0-7): ");
                    int bit = Integer.parseInt(scanner.nextLine());
                    System.out.println("Resultado: " + connector.readBit(db, offset, bit));
                    break;
                case 2:
                    System.out.println("Resultado: " + connector.readByte(db, offset));
                    break;
                case 3:
                    System.out.println("Resultado: " + connector.readInt(db, offset));
                    break;
                case 4:
                    System.out.println("Resultado: " + connector.readFloat(db, offset));
                    break;
                case 5:
                    System.out.print("Tamanho da String: ");
                    int size = Integer.parseInt(scanner.nextLine());
                    System.out.println("Resultado: " + connector.readString(db, offset, size));
                    break;
                case 6:
                    System.out.print("Tamanho do Bloco (bytes): ");
                    int bSize = Integer.parseInt(scanner.nextLine());
                    byte[] data = connector.readBlock(db, offset, bSize);
                    System.out.println("Dados (Hex): " + bytesToHex(data));
                    break;
            }
        } catch (Exception e) {
            System.out.println("Erro na leitura: " + e.getMessage());
        }

    }

    private static void processarEscrita() {

        try {
            System.out.print("DB Number: ");
            int db = Integer.parseInt(scanner.nextLine());
            System.out.print("Offset (Start Address): ");
            int offset = Integer.parseInt(scanner.nextLine());

            System.out.println("Tipo: 1-Bit, 2-Byte, 3-Int, 4-Float, 5-String");
            int tipo = Integer.parseInt(scanner.nextLine());

            System.out.print("Digite o valor para escrita: ");
            String valor = scanner.nextLine();

            boolean sucesso = false;
            switch (tipo) {
                case 1:
                    System.out.print("Bit Number (0-7): ");
                    int bit = Integer.parseInt(scanner.nextLine());
                    sucesso = connector.writeBit(db, offset, bit, Boolean.parseBoolean(valor));
                    break;
                case 2:
                    sucesso = connector.writeByte(db, offset, Byte.parseByte(valor));
                    break;
                case 3:
                    sucesso = connector.writeInt(db, offset, Integer.parseInt(valor));
                    break;
                case 4:
                    sucesso = connector.writeFloat(db, offset, Float.parseFloat(valor));
                    break;
                case 5:
                    sucesso = connector.writeString(db, offset, valor.length(), valor);
                    break;
            }
            System.out.println(sucesso ? "Escrita realizada!" : "Falha na escrita.");
        } catch (Exception e) {
            System.out.println("Erro na escrita: " + e.getMessage());
        }
    }

    // auxiliar para exibir o bloco de bytes de froma legivel

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

}