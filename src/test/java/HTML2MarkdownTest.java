import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Suite de pruebas funcionales (P01 a P12 y casos adicionales de control de errores)
 * para HTML2Markdown utilizando exclusivamente la API estándar de Java
 * (com.sun.net.httpserver.HttpServer).
 */
public class HTML2MarkdownTest {

    private static HttpServer server;
    private static int puerto;
    private static final Path TEST_OUTPUT_DIR = Path.of("test_output");
    private static int aprobadas = 0;
    private static int fallidas = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("PRUEBAS FUNCIONALES (P01 - P12) - HTML2Markdown");
        System.out.println("Estudiante: José Carlos Tirado Lam | Matrícula: 225213509");
        System.out.println("============================================================\n");

        iniciarServidorPrueba();
        prepararDirectorioSalida();

        try {
            ejecutarPrueba("P01 - Sin argumentos (mensaje de uso)", HTML2MarkdownTest::pruebaP01SinArgumentos);
            ejecutarPrueba("P02 - URL inválida (error controlado)", HTML2MarkdownTest::pruebaP02UrlInvalida);
            ejecutarPrueba("P03 - Página inexistente 404 (error HTTP)", HTML2MarkdownTest::pruebaP03PaginaInexistente);
            ejecutarPrueba("P04 - Encabezados h1 a h6 y regla horizontal", HTML2MarkdownTest::pruebaP04Encabezados);
            ejecutarPrueba("P05 - Negritas y cursivas", HTML2MarkdownTest::pruebaP05NegritasYCursivas);
            ejecutarPrueba("P06 - Enlaces absolutos y relativos", HTML2MarkdownTest::pruebaP06Enlaces);
            ejecutarPrueba("P07 - Imágenes con texto alternativo", HTML2MarkdownTest::pruebaP07Imagenes);
            ejecutarPrueba("P08 - Listas ordenadas, no ordenadas y anidadas", HTML2MarkdownTest::pruebaP08Listas);
            ejecutarPrueba("P09 - Elementos anidados, citas y bloques de código", HTML2MarkdownTest::pruebaP09ElementosAnidados);
            ejecutarPrueba("P10 - Exclusión de etiquetas script y style", HTML2MarkdownTest::pruebaP10ScriptYStyle);
            ejecutarPrueba("P11 - Acentos, entidades HTML y escape Markdown", HTML2MarkdownTest::pruebaP11AcentosYEntidades);
            ejecutarPrueba("P12 - Página HTML vacía", HTML2MarkdownTest::pruebaP12PaginaVacia);
            ejecutarPrueba("P13 - Contenido no HTML rechazado", HTML2MarkdownTest::pruebaContenidoNoHtml);
            ejecutarPrueba("P14 - Servidor inaccesible controlado", HTML2MarkdownTest::pruebaServidorInaccesible);
            ejecutarPrueba("P15 - Error de escritura controlado", HTML2MarkdownTest::pruebaErrorEscritura);
            ejecutarPrueba("P16 - Documento completo vs salida esperada", HTML2MarkdownTest::pruebaDocumentoCompleto);
        } finally {
            detenerServidorPrueba();
        }

        System.out.println("\n------------------------------------------------------------");
        System.out.println("Resumen: " + aprobadas + " aprobadas, " + fallidas + " fallidas.");
        System.out.println("------------------------------------------------------------");
        if (fallidas > 0) {
            System.exit(1);
        }
    }

    @FunctionalInterface
    private interface CasoPrueba {
        void ejecutar() throws Exception;
    }

    private static void ejecutarPrueba(String nombre, CasoPrueba caso) {
        try {
            caso.ejecutar();
            aprobadas++;
            System.out.println("[OK]   " + nombre);
        } catch (Throwable t) {
            fallidas++;
            System.out.println("[FAIL] " + nombre + " -> " + t.getMessage());
            t.printStackTrace(System.out);
        }
    }

    private static void iniciarServidorPrueba() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        puerto = server.getAddress().getPort();

        registrarArchivoHtml("/encabezados.html", Path.of("tests/encabezados.html"));
        registrarArchivoHtml("/listas.html", Path.of("tests/listas.html"));
        registrarArchivoHtml("/docs/enlaces.html", Path.of("tests/enlaces.html"));
        registrarArchivoHtml("/documento_completo.html", Path.of("tests/documento_completo.html"));

        registrarHtml("/negritas.html",
                "<html><body><p>Texto en <strong>negrita fuerte</strong>, <b>negrita b</b>, "
                        + "<em>cursiva em</em> e <i>cursiva i</i>.</p></body></html>");

        registrarHtml("/imagenes.html",
                "<html><body><p>Icono: <img src=\"img/icono.png\" alt=\"Icono [principal]\"></p>"
                        + "<p>Externa: <img src=\"https://ejemplo.org/foto.jpg\" alt=\"Foto\"></p></body></html>");

        registrarHtml("/anidados.html",
                "<html><body>"
                        + "<blockquote><p>Cita con <strong>texto <em>muy</em> importante</strong>.</p></blockquote>"
                        + "<p>Enlace <a href=\"/guia.html\"><strong>en negrita</strong></a> y código <code>x * y</code>.</p>"
                        + "<pre><code class=\"language-java\">int a = 1;\nint b = 2;</code></pre>"
                        + "</body></html>");

        registrarHtml("/scripts_estilos.html",
                "<html><head><title>Oculto</title><style>body > p { color: red; }</style></head>"
                        + "<body><script>var a = 1 < 2; console.log('oculto');</script>"
                        + "<p>Contenido visible</p>"
                        + "<style>.x { display: none; }</style></body></html>");

        registrarHtml("/entidades.html",
                "<html><body><p>Acentos: &aacute; &eacute; &iacute; &oacute; &uacute; &ntilde; &amp; "
                        + "caracteres *literal* y [corchetes].</p></body></html>");

        registrarHtml("/vacia.html",
                "<html><head><title>Vacía</title></head><body>   </body></html>");

        server.createContext("/datos.json", new TextoHandler(200, "application/json; charset=utf-8", "{\"ok\":true}"));
        server.createContext("/noexiste.html", new TextoHandler(404, "text/html; charset=utf-8", "<h1>404</h1>"));

        server.start();
    }

    private static void registrarArchivoHtml(String ruta, Path archivo) throws IOException {
        String html = Files.readString(archivo, StandardCharsets.UTF_8);
        registrarHtml(ruta, html);
    }

    private static void registrarHtml(String ruta, String html) {
        server.createContext(ruta, new TextoHandler(200, "text/html; charset=utf-8", html));
    }

    private static void detenerServidorPrueba() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void prepararDirectorioSalida() throws IOException {
        if (Files.exists(TEST_OUTPUT_DIR)) {
            try (var stream = Files.walk(TEST_OUTPUT_DIR)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
        Files.createDirectories(TEST_OUTPUT_DIR);
    }

    private static ResultadoEjecucion correrApp(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);
        int codigo = new HTML2Markdown().ejecutar(args, out, err);
        return new ResultadoEjecucion(
                codigo,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8)
        );
    }

    private static String urlBase() {
        return "http://127.0.0.1:" + puerto;
    }

    private static String normalizarSaltos(String texto) {
        return texto.replace("\r\n", "\n");
    }

    private static void afirmarIgual(String esperado, String actual, String contexto) {
        String espNorm = normalizarSaltos(esperado);
        String actNorm = normalizarSaltos(actual);
        if (!espNorm.equals(actNorm)) {
            throw new AssertionError(contexto + "\n--- ESPERADO ---\n" + espNorm + "\n--- ACTUAL ---\n" + actNorm);
        }
    }

    private static void afirmarVerdadero(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    // --- Casos de prueba ---

    private static void pruebaP01SinArgumentos() {
        ResultadoEjecucion r = correrApp();
        afirmarVerdadero(r.codigo != 0, "Debe retornar código de error sin argumentos.");
        afirmarVerdadero(r.out.contains("Uso: java HTML2Markdown"), "Debe mostrar mensaje de uso.");
    }

    private static void pruebaP02UrlInvalida() {
        ResultadoEjecucion r1 = correrApp("no-es-una-url");
        afirmarVerdadero(r1.codigo != 0, "URL sin esquema debe fallar.");
        afirmarVerdadero(r1.err.contains("URL inválida"), "Debe informar URL inválida.");

        ResultadoEjecucion r2 = correrApp("ftp://ejemplo.org/archivo.html");
        afirmarVerdadero(r2.codigo != 0, "Protocolo FTP debe rechazarse.");
        afirmarVerdadero(r2.err.contains("http o https"), "Debe indicar restricción de protocolo.");
    }

    private static void pruebaP03PaginaInexistente() {
        Path salida = TEST_OUTPUT_DIR.resolve("p03.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/noexiste.html", salida.toString());
        afirmarVerdadero(r.codigo != 0, "HTTP 404 debe retornar error.");
        afirmarVerdadero(r.out.contains("Respuesta HTTP: 404") || r.err.contains("404"), "Debe reportar código 404.");
        afirmarVerdadero(!Files.exists(salida), "No debe crearse archivo en error HTTP.");
    }

    private static void pruebaP04Encabezados() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("encabezados.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/encabezados.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P04 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = Files.readString(Path.of("tests/expected/encabezados.md"), StandardCharsets.UTF_8);
        afirmarIgual(esperado, actual, "P04 encabezados");
    }

    private static void pruebaP05NegritasYCursivas() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("negritas.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/negritas.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P05 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = "Texto en **negrita fuerte**, **negrita b**, *cursiva em* e *cursiva i*.\n";
        afirmarIgual(esperado, actual, "P05 negritas y cursivas");
    }

    private static void pruebaP06Enlaces() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("enlaces.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/docs/enlaces.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P06 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = Files.readString(Path.of("tests/expected/enlaces.md"), StandardCharsets.UTF_8)
                .replace("https://ejemplo.org/docs/", urlBase() + "/docs/")
                .replace("https://ejemplo.org/indice.html", urlBase() + "/indice.html")
                .replace("https://ejemplo.org/recursos/", urlBase() + "/recursos/")
                .replace("https://ejemplo.org/imagenes/", urlBase() + "/imagenes/");
        afirmarIgual(esperado, actual, "P06 enlaces");
    }

    private static void pruebaP07Imagenes() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("imagenes.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/imagenes.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P07 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = "Icono: ![Icono \\[principal\\]](" + urlBase() + "/img/icono.png)\n\n"
                + "Externa: ![Foto](https://ejemplo.org/foto.jpg)\n";
        afirmarIgual(esperado, actual, "P07 imágenes");
    }

    private static void pruebaP08Listas() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("listas.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/listas.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P08 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = Files.readString(Path.of("tests/expected/listas.md"), StandardCharsets.UTF_8);
        afirmarIgual(esperado, actual, "P08 listas");
    }

    private static void pruebaP09ElementosAnidados() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("anidados.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/anidados.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P09 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = "> Cita con **texto *muy* importante**.\n\n"
                + "Enlace [**en negrita**](" + urlBase() + "/guia.html) y código `x * y`.\n\n"
                + "```java\nint a = 1;\nint b = 2;\n```\n";
        afirmarIgual(esperado, actual, "P09 elementos anidados");
    }

    private static void pruebaP10ScriptYStyle() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("scripts_estilos.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/scripts_estilos.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P10 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        afirmarIgual("Contenido visible\n", actual, "P10 script y style");
    }

    private static void pruebaP11AcentosYEntidades() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("entidades.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/entidades.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P11 debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = "Acentos: á é í ó ú ñ & caracteres \\*literal\\* y \\[corchetes\\].\n";
        afirmarIgual(esperado, actual, "P11 acentos y entidades");
    }

    private static void pruebaP12PaginaVacia() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("vacia.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/vacia.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "P12 debe finalizar con éxito: " + r.err);
        afirmarVerdadero(r.out.contains("archivo vacío"), "Debe mostrar mensaje informativo de página vacía.");
        afirmarIgual("", Files.readString(salida, StandardCharsets.UTF_8), "P12 archivo vacío");
    }

    private static void pruebaContenidoNoHtml() {
        Path salida = TEST_OUTPUT_DIR.resolve("json.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/datos.json", salida.toString());
        afirmarVerdadero(r.codigo != 0, "Debe rechazar respuestas que no son HTML.");
        afirmarVerdadero(r.err.contains("no es un documento HTML"), "Debe reportar Content-Type no HTML.");
    }

    private static void pruebaServidorInaccesible() throws IOException {
        int puertoCerrado;
        try (ServerSocket socket = new ServerSocket(0)) {
            puertoCerrado = socket.getLocalPort();
        }
        Path salida = TEST_OUTPUT_DIR.resolve("inaccesible.md");
        ResultadoEjecucion r = correrApp("http://127.0.0.1:" + puertoCerrado + "/index.html", salida.toString());
        afirmarVerdadero(r.codigo != 0, "Servidor inaccesible debe retornar error.");
        afirmarVerdadero(r.err.contains("Error al recuperar"), "Debe informar error de conexión.");
    }

    private static void pruebaErrorEscritura() throws IOException {
        Path rutaInvalida = TEST_OUTPUT_DIR.resolve("directorio_existente");
        Files.createDirectories(rutaInvalida);
        ResultadoEjecucion r = correrApp(urlBase() + "/negritas.html", rutaInvalida.toString());
        afirmarVerdadero(r.codigo != 0, "Escribir sobre un directorio debe retornar error.");
        afirmarVerdadero(r.err.contains("Error al escribir"), "Debe informar error de escritura.");
    }

    private static void pruebaDocumentoCompleto() throws IOException {
        Path salida = TEST_OUTPUT_DIR.resolve("documento_completo.md");
        ResultadoEjecucion r = correrApp(urlBase() + "/documento_completo.html", salida.toString());
        afirmarVerdadero(r.codigo == 0, "Documento completo debe finalizar con éxito: " + r.err);
        String actual = Files.readString(salida, StandardCharsets.UTF_8);
        String esperado = Files.readString(Path.of("tests/expected/documento_completo.md"), StandardCharsets.UTF_8)
                .replace("https://ejemplo.org/", urlBase() + "/");
        afirmarIgual(esperado, actual, "P16 documento completo");
    }

    private record ResultadoEjecucion(int codigo, String out, String err) {
    }

    private static class TextoHandler implements HttpHandler {
        private final int codigo;
        private final String contentType;
        private final byte[] cuerpo;

        TextoHandler(int codigo, String contentType, String contenido) {
            this.codigo = codigo;
            this.contentType = contentType;
            this.cuerpo = contenido.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(codigo, cuerpo.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(cuerpo);
            }
        }
    }
}
