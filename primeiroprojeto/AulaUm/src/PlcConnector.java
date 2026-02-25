public class PlcConnector {

    private String ipAddress;
    private int port;
    S7ProtocolClient client;
    boolean connected = false;

    // Construtor para inicializar o cliente S7ProtocolClient com o IP e porta do CLP
    public PlcConnector(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;

        client = new S7ProtocolClient(this.ipAddress, this.port);

    }

    public void connect() throws Exception {
        if (connected) {
            return;
        }

        try {
            if (client.connect()) {
                client.sendConnectionRequest();
                client.sendSetupCommunication();
                connected = true;
            } else {
                connected = false;
            }
        } catch (Exception e) {
            throw new Exception("Erro ao conectar ao CLP" + ipAddress + " ---- " + e.getMessage(), e);
        }
    }

    public void disconnect() throws Exception {
        try {
            client.disconnect();
        } catch (Exception e) {
            throw new Exception("Erro ao tentar desconectar do CLP" + e.getMessage(), e);
        }
    }

    // --------------------------------------------------------------------------------------------
    // Funções para leituras de TAGs
    // --------------------------------------------------------------------------------------------
    public String readString(int db, int startAdd, int size) throws Exception {

        if (!connected) {
            throw new Exception("Conexão não estabelecida. Chame o método connect() primeiro.");
        } else {

            String data = (String) client.sendReadRequest(db, startAdd, 0, size, "string");

            return data;
        }

    }

    public byte[] readBlock(int db, int startAdd, int size) throws Exception {

        if (!connected) {
            throw new Exception("Conexão não estabelecida. Chame o método connect() primeiro.");
        } else {

            //System.out.println("SOlicitando leitura de BLOCO");
            byte[] data = (byte[]) client.sendReadRequest(db, startAdd, 0, size, "block");

            //System.out.println("Resposta de conexão recebida [readBlock]: " + S7ProtocolClient.bytesToHex(data, data.length));
            return data;
        }

    }

    public float readFloat(int db, int startAdd) throws Exception {

        if (!connected) {
            throw new Exception("Conexão não estabelecida. Chame o método connect() primeiro.");
        } else {

            float data = (float) client.sendReadRequest(db, startAdd, 0, 4, "float");

            return data;
        }

    }

    public int readInt(int db, int startAdd) throws Exception {
        if (!connected) {
            throw new Exception("Conexão não estabelecida. Chame o método connect() primeiro.");
        } else {

            int data = (int) client.sendReadRequest(db, startAdd, 0, 2, "integer");

            return data;
        }
    }

    public byte readByte(int db, int startAdd) throws Exception {

        if (!connected) {
            throw new Exception("Conexão não estabelecida. Chame o método connect() primeiro.");
        } else {

            byte data = (byte) client.sendReadRequest(db, startAdd, 0, 1, "byte");

            return data;
        }
    }

    public boolean readBit(int db, int startAdd, int bitNumber) throws Exception {

        if (!connected) {
            throw new Exception("Conexão não estabelecida. Chame o método connect() primeiro.");
        } else {

            boolean data = (boolean) client.sendReadRequest(db, startAdd, bitNumber, 1, "boolean");
            //System.out.println("Valor do BIT = " + data);
            return data;
        }
    }

    // --------------------------------------------------------------------------------------------
    // Funções para escritas de TAGs
    // --------------------------------------------------------------------------------------------
    public boolean writeString(int db, int startAdd, int size, String str) throws Exception {

        return client.sendWriteRequest(db, startAdd, 0, size, "string", str);

    }

    public boolean writeBlock(int db, int startAdd, int size, byte[] block) throws Exception {

        return client.sendWriteRequest(db, startAdd, 0, size, "block", block);
    }

    public boolean writeFloat(int db, int startAdd, float value) throws Exception {

        return client.sendWriteRequest(db, startAdd, 0, 4, "float", value);

    }

    public boolean writeInt(int db, int startAdd, int value) throws Exception {

        return client.sendWriteRequest(db, startAdd, 0, 2, "integer", value);
    }

    public boolean writeByte(int db, int startAdd, byte value) throws Exception {

        return client.sendWriteRequest(db, startAdd, 0, 1, "byte", value);
    }

    public boolean writeBit(int db, int startAdd, int bitNumber, boolean logic) throws Exception {

        return client.sendWriteRequest(db, startAdd, bitNumber, 1, "boolean", logic);
    }

    // --------------------------------------------------------------------------------------------
    // Funções auxiliar para conversão de String de bytes ByteArrays
    // --------------------------------------------------------------------------------------------
    public static byte[] hexStringToByteArray(String hexString) {
        // Verifica se a string é válida
        if (hexString == null || hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("A string hex deve ter um número par de caracteres.");
        }

        // Cria um array de bytes para armazenar o resultado
        byte[] byteArray = new byte[hexString.length() / 2];

        // Para cada par de caracteres hexadecimais na string, converte para um byte
        for (int i = 0; i < hexString.length(); i += 2) {
            // Pega os dois caracteres hexadecimais
            String hexPair = hexString.substring(i, i + 2);
            // Converte o par hexadecimal para um byte
            byteArray[i / 2] = (byte) Integer.parseInt(hexPair, 16);
        }

        return byteArray;
    }

}