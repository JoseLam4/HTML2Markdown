import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Responsable de resolver la ruta destino, crear directorios faltantes y
 * guardar el documento Markdown resultante con codificación UTF-8.
 */
public class MarkdownWriter {

    /**
     * Guarda el contenido Markdown en la ruta indicada usando UTF-8.
     * Crea automáticamente los directorios padre si no existen.
     *
     * @param destino   Ruta del archivo Markdown de salida.
     * @param contenido Contenido Markdown a escribir.
     * @throws IOException Si ocurre un error al crear directorios o escribir el archivo.
     */
    public void guardar(Path destino, String contenido) throws IOException {
        if (destino == null) {
            throw new IllegalArgumentException("La ruta de destino no puede ser nula.");
        }
        Path normalizada = destino.toAbsolutePath().normalize();
        Path directorioPadre = normalizada.getParent();
        if (directorioPadre != null) {
            Files.createDirectories(directorioPadre);
        }
        String textoSeguro = contenido != null ? contenido : "";
        Files.writeString(normalizada, textoSeguro, StandardCharsets.UTF_8);
    }

    /**
     * Determina la ruta de salida a partir del argumento opcional o del nombre
     * del recurso en la URL cuando el usuario no especifica una ruta.
     *
     * @param url          URL de origen.
     * @param rutaOpcional Ruta indicada por el usuario, o null si se omitió.
     * @return Ruta de archivo destino con extensión .md cuando se genera automáticamente.
     */
    public static Path resolverRutaSalida(URL url, String rutaOpcional) {
        if (rutaOpcional != null && !rutaOpcional.isBlank()) {
            return Path.of(rutaOpcional.trim());
        }
        String path = url != null ? url.getPath() : "";
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return Path.of("index.md");
        }
        int ultimoSlash = path.lastIndexOf('/');
        String nombreBase = ultimoSlash >= 0 ? path.substring(ultimoSlash + 1) : path;
        if (nombreBase.isBlank()) {
            return Path.of("index.md");
        }

        int punto = nombreBase.lastIndexOf('.');
        if (punto > 0) {
            nombreBase = nombreBase.substring(0, punto);
        }
        nombreBase = nombreBase.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (nombreBase.isEmpty()) {
            nombreBase = "index";
        }
        return Path.of(nombreBase + ".md");
    }
}
