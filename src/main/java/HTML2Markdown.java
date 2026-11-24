import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Punto de entrada y coordinador principal de la aplicación HTML2Markdown.
 * Valida argumentos, coordina descarga HTTP, análisis HTML, conversión a Markdown
 * y escritura en disco, mostrando un resumen claro de la operación.
 */
public class HTML2Markdown {

    private final WebClient webClient;
    private final HtmlParser htmlParser;
    private final MarkdownConverter markdownConverter;
    private final MarkdownWriter markdownWriter;

    public HTML2Markdown() {
        this(new WebClient(), new HtmlParser(), new MarkdownConverter(), new MarkdownWriter());
    }

    public HTML2Markdown(WebClient webClient, HtmlParser htmlParser,
                         MarkdownConverter markdownConverter, MarkdownWriter markdownWriter) {
        this.webClient = webClient;
        this.htmlParser = htmlParser;
        this.markdownConverter = markdownConverter;
        this.markdownWriter = markdownWriter;
    }

    public static void main(String[] args) {
        int codigoSalida = new HTML2Markdown().ejecutar(args, System.out, System.err);
        if (codigoSalida != 0) {
            System.exit(codigoSalida);
        }
    }

    /**
     * Ejecuta el flujo completo de conversión HTML a Markdown.
     *
     * @param args Argumentos de línea de comandos: &lt;url&gt; [ruta_salida.md].
     * @param out  Flujo de salida estándar.
     * @param err  Flujo de salida de error.
     * @return 0 si la operación finalizó correctamente; distinto de 0 en caso de error.
     */
    public int ejecutar(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0 || args.length > 2) {
            mostrarUso(out);
            return 1;
        }

        String urlTexto = args[0] != null ? args[0].trim() : "";
        String rutaOpcional = args.length == 2 ? args[1] : null;

        URL urlOrigen;
        try {
            urlOrigen = validarUrl(urlTexto);
        } catch (IllegalArgumentException e) {
            err.println("Error: URL inválida -> " + e.getMessage());
            return 1;
        }

        Path destino = MarkdownWriter.resolverRutaSalida(urlOrigen, rutaOpcional);

        out.println("HTML2Markdown");
        out.println("--------------------------------");
        out.println("URL: " + urlOrigen);
        out.println("Conectando con el servidor...");

        String html;
        try {
            html = webClient.descargarHTML(urlOrigen);
            out.println("Respuesta HTTP: " + webClient.getUltimoCodigoEstado());
        } catch (IOException e) {
            if (webClient.getUltimoCodigoEstado() > 0) {
                out.println("Respuesta HTTP: " + webClient.getUltimoCodigoEstado());
            }
            err.println("Error al recuperar el documento HTML: " + e.getMessage());
            return 1;
        }

        out.println("Analizando HTML...");
        HtmlParser.HtmlNode raiz;
        try {
            raiz = htmlParser.parse(html);
        } catch (IOException e) {
            err.println("Error al analizar la estructura HTML: " + e.getMessage());
            return 1;
        }

        out.println("Convirtiendo a Markdown...");
        URL urlBase = webClient.getUltimaUrlFinal() != null ? webClient.getUltimaUrlFinal() : urlOrigen;
        String markdown = markdownConverter.convert(raiz, urlBase);

        if (markdown.isEmpty()) {
            out.println("Aviso: la página HTML no contiene elementos de contenido visibles; se generará un archivo vacío.");
        }

        try {
            markdownWriter.guardar(destino, markdown);
            long tamano = Files.size(destino.toAbsolutePath().normalize());
            out.println("Archivo generado: " + destino);
            out.println("Tamaño: " + String.format(Locale.US, "%,d", tamano) + " bytes");
            out.println("Conversión finalizada.");
            return 0;
        } catch (IOException e) {
            err.println("Error al escribir el archivo Markdown '" + destino + "': " + e.getMessage());
            return 1;
        }
    }

    /**
     * Valida que la cadena corresponda a una URL HTTP o HTTPS bien formada y con host.
     */
    public static URL validarUrl(String urlTexto) {
        if (urlTexto == null || urlTexto.isBlank()) {
            throw new IllegalArgumentException("La URL no puede estar vacía.");
        }
        try {
            URI uri = URI.create(urlTexto);
            String esquema = uri.getScheme();
            if (esquema == null
                    || (!"http".equalsIgnoreCase(esquema) && !"https".equalsIgnoreCase(esquema))) {
                throw new IllegalArgumentException("Solo se admiten URL con protocolo http o https.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("La URL debe incluir un nombre de host válido.");
            }
            return uri.toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static void mostrarUso(PrintStream out) {
        out.println("Uso: java HTML2Markdown <url_http_o_https> [ruta_salida.md]");
        out.println("Ejemplo: java HTML2Markdown https://ejemplo.org/articulo.html");
        out.println("Ejemplo: java HTML2Markdown https://ejemplo.org/articulo.html salida.md");
    }
}
