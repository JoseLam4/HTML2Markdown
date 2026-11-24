import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP basado exclusivamente en la API estándar de Java
 * (URL, URLConnection y HttpURLConnection) para descargar documentos HTML.
 */
public class WebClient {

    public static final int TIMEOUT_CONEXION_MS = 10_000;
    public static final int TIMEOUT_LECTURA_MS = 15_000;
    public static final int MAX_HTML_BYTES = 5 * 1024 * 1024; // 5 MB
    public static final int MAX_REDIRECCIONES = 5;
    private static final String USER_AGENT = "HTML2Markdown/1.0 (Java)";

    private static final Pattern PATRON_CHARSET_HEADER =
            Pattern.compile("charset\\s*=\\s*\"?([^\";\\s]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATRON_META_CHARSET =
            Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private int ultimoCodigoEstado = -1;
    private String ultimoContentType = "";
    private Charset ultimoCharset = StandardCharsets.UTF_8;
    private URL ultimaUrlFinal;
    private int ultimosBytesDescargados = 0;

    /**
     * Descarga el contenido HTML de una URL HTTP o HTTPS validando código de estado,
     * tipo de contenido, codificación y límite máximo de tamaño.
     *
     * @param url URL destino del documento HTML.
     * @return Contenido HTML decodificado como cadena.
     * @throws IOException Si ocurre un error de red, HTTP, tipo de contenido o tamaño.
     */
    public String descargarHTML(URL url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("La URL no puede ser nula.");
        }
        validarProtocolo(url);

        URL urlActual = url;
        HttpURLConnection conexion = null;

        try {
            int redirecciones = 0;
            while (true) {
                URLConnection baseConn = urlActual.openConnection();
                if (!(baseConn instanceof HttpURLConnection httpConn)) {
                    throw new IOException("La conexión no corresponde al protocolo HTTP/HTTPS.");
                }
                conexion = httpConn;
                conexion.setRequestMethod("GET");
                conexion.setConnectTimeout(TIMEOUT_CONEXION_MS);
                conexion.setReadTimeout(TIMEOUT_LECTURA_MS);
                conexion.setInstanceFollowRedirects(false);
                conexion.setRequestProperty("User-Agent", USER_AGENT);
                conexion.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");
                conexion.setRequestProperty("Accept-Charset", "utf-8");

                conexion.connect();
                int codigo = conexion.getResponseCode();
                this.ultimoCodigoEstado = codigo;
                this.ultimaUrlFinal = urlActual;

                if (esRedireccion(codigo)) {
                    if (redirecciones >= MAX_REDIRECCIONES) {
                        throw new IOException("Se superó el límite máximo de redirecciones (" + MAX_REDIRECCIONES + ").");
                    }
                    String ubicacion = conexion.getHeaderField("Location");
                    if (ubicacion == null || ubicacion.isBlank()) {
                        throw new IOException("Respuesta de redirección (" + codigo + ") sin cabecera Location.");
                    }
                    URL siguienteUrl = resolverRedireccion(urlActual, ubicacion);
                    validarProtocolo(siguienteUrl);
                    conexion.disconnect();
                    conexion = null;
                    urlActual = siguienteUrl;
                    redirecciones++;
                    continue;
                }
                break;
            }

            if (this.ultimoCodigoEstado < 200 || this.ultimoCodigoEstado >= 300) {
                descartarStreamError(conexion);
                String mensajeHttp = conexion.getResponseMessage();
                throw new IOException("Respuesta HTTP no exitosa: " + this.ultimoCodigoEstado
                        + (mensajeHttp != null ? " (" + mensajeHttp + ")" : ""));
            }

            String contentType = conexion.getContentType();
            this.ultimoContentType = contentType != null ? contentType : "";
            if (!esContenidoHtml(contentType)) {
                throw new IOException("El recurso no es un documento HTML válido (Content-Type: "
                        + (contentType != null ? contentType : "no especificado") + ").");
            }

            long contentLength = conexion.getContentLengthLong();
            if (contentLength > MAX_HTML_BYTES) {
                throw new IOException("El documento HTML excede el tamaño máximo permitido ("
                        + MAX_HTML_BYTES + " bytes).");
            }

            byte[] datos = leerConLimite(conexion);
            this.ultimosBytesDescargados = datos.length;

            Charset charset = detectarCharset(contentType, datos);
            this.ultimoCharset = charset;
            return new String(datos, charset);
        } finally {
            if (conexion != null) {
                conexion.disconnect();
            }
        }
    }

    private static void validarProtocolo(URL url) throws IOException {
        String protocolo = url.getProtocol() != null ? url.getProtocol().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(protocolo) && !"https".equals(protocolo)) {
            throw new IOException("Protocolo no admitido: '" + protocolo + "'. Solo se permite HTTP o HTTPS.");
        }
    }

    private static boolean esRedireccion(int codigo) {
        return codigo == HttpURLConnection.HTTP_MOVED_PERM
                || codigo == HttpURLConnection.HTTP_MOVED_TEMP
                || codigo == HttpURLConnection.HTTP_SEE_OTHER
                || codigo == 307
                || codigo == 308;
    }

    private static URL resolverRedireccion(URL base, String ubicacion) throws IOException {
        try {
            URI resuelta = base.toURI().resolve(ubicacion.trim());
            return resuelta.toURL();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("URL de redirección inválida: " + ubicacion, e);
        }
    }

    private static void descartarStreamError(HttpURLConnection conexion) {
        try (InputStream err = conexion.getErrorStream()) {
            if (err != null) {
                byte[] buffer = new byte[1024];
                while (err.read(buffer) != -1) {
                    // Consumir y cerrar ordenadamente el flujo de error
                }
            }
        } catch (IOException ignored) {
            // Ignorar errores secundarios al limpiar el stream de error
        }
    }

    private static boolean esContenidoHtml(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String mime = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return "text/html".equals(mime) || "application/xhtml+xml".equals(mime);
    }

    private static byte[] leerConLimite(HttpURLConnection conexion) throws IOException {
        try (InputStream in = conexion.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int leidos;
            int total = 0;
            while ((leidos = in.read(buffer)) != -1) {
                total += leidos;
                if (total > MAX_HTML_BYTES) {
                    throw new IOException("El tamaño del HTML superó el límite máximo de "
                            + MAX_HTML_BYTES + " bytes.");
                }
                baos.write(buffer, 0, leidos);
            }
            return baos.toByteArray();
        }
    }

    private static Charset detectarCharset(String contentType, byte[] datos) {
        if (contentType != null) {
            Matcher m = PATRON_CHARSET_HEADER.matcher(contentType);
            if (m.find()) {
                Charset cs = intentarCharset(m.group(1));
                if (cs != null) {
                    return cs;
                }
            }
        }

        int limiteCabecera = Math.min(datos.length, 4096);
        String inicio = new String(datos, 0, limiteCabecera, StandardCharsets.ISO_8859_1);
        Matcher meta = PATRON_META_CHARSET.matcher(inicio);
        if (meta.find()) {
            Charset cs = intentarCharset(meta.group(1));
            if (cs != null) {
                return cs;
            }
        }

        return StandardCharsets.UTF_8;
    }

    private static Charset intentarCharset(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(nombre.trim());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return null;
        }
    }

    public int getUltimoCodigoEstado() {
        return ultimoCodigoEstado;
    }

    public String getUltimoContentType() {
        return ultimoContentType;
    }

    public Charset getUltimoCharset() {
        return ultimoCharset;
    }

    public URL getUltimaUrlFinal() {
        return ultimaUrlFinal;
    }

    public int getUltimosBytesDescargados() {
        return ultimosBytesDescargados;
    }
}
