import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPairGenerator;
import java.util.Base64;

// Run directly with the JDK; independent of Maven and the service's DB configuration.
class GenerateDevKeys {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Path privatePath = directory.resolve("private.pem");
        Path publicPath = directory.resolve("public.pem");
        if (Files.exists(privatePath) || Files.exists(publicPath)) {
            throw new IllegalStateException("Keys already exist; refusing to overwrite them");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Files.createDirectories(directory);
        Files.writeString(privatePath, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardOpenOption.CREATE_NEW);
        Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
