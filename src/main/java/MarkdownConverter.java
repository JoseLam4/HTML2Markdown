import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte un árbol de nodos HTML generado por {@link HtmlParser} en texto
 * con formato Markdown, conservando jerarquías, listas anidadas, formato de texto,
 * bloques de código, caracteres especiales y referencias relativas resueltas.
 */
public class MarkdownConverter {

    private static final Pattern PATRON_LENGUAJE =
            Pattern.compile("(?:language|lang)-([a-zA-Z0-9_+#.-]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Convierte el árbol HTML completo a una cadena Markdown limpia.
     *
     * @param raiz    Nodo raíz devuelto por {@link HtmlParser#parse(String)}.
     * @param baseUrl URL base del documento para resolver enlaces e imágenes relativas.
     * @return Documento en formato Markdown, o cadena vacía si no hay contenido visible.
     */
    public String convert(HtmlParser.HtmlNode raiz, URL baseUrl) {
        if (raiz == null) {
            return "";
        }
        StringBuilder salida = new StringBuilder();
        convertirHijosBloque(raiz.getChildren(), salida, baseUrl, 0);
        return limpiarDocumento(salida.toString());
    }

    private void convertirHijosBloque(List<HtmlParser.HtmlNode> hijos, StringBuilder sb,
                                      URL baseUrl, int nivelLista) {
        StringBuilder inlineBuffer = new StringBuilder();

        for (HtmlParser.HtmlNode hijo : hijos) {
            if (hijo.isText()) {
                inlineBuffer.append(normalizarYEscaparTexto(hijo.getText(), false));
                continue;
            }

            String tag = hijo.getTagName();
            if (esEtiquetaBloque(tag)) {
                vaciarParrafoInline(inlineBuffer, sb);
                convertirBloque(hijo, sb, baseUrl, nivelLista);
            } else {
                convertirInline(hijo, inlineBuffer, baseUrl);
            }
        }

        vaciarParrafoInline(inlineBuffer, sb);
    }

    private void convertirBloque(HtmlParser.HtmlNode nodo, StringBuilder sb,
                                 URL baseUrl, int nivelLista) {
        String tag = nodo.getTagName();

        switch (tag) {
            case "html":
            case "body":
            case "div":
            case "main":
            case "article":
            case "section":
            case "header":
            case "footer":
            case "nav":
            case "aside":
            case "figure":
            case "figcaption":
                convertirHijosBloque(nodo.getChildren(), sb, baseUrl, nivelLista);
                break;

            case "script":
            case "style":
            case "head":
            case "title":
            case "noscript":
            case "template":
                // Contenido excluido
                break;

            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6": {
                int nivel = tag.charAt(1) - '0';
                String contenido = convertirContenidoInline(nodo.getChildren(), baseUrl).trim();
                if (!contenido.isEmpty()) {
                    asegurarLineaEnBlanco(sb);
                    sb.append("#".repeat(nivel)).append(' ').append(contenido).append("\n\n");
                }
                break;
            }

            case "p": {
                String contenido = convertirContenidoInline(nodo.getChildren(), baseUrl);
                String limpio = limpiarParrafo(contenido);
                if (!limpio.isEmpty()) {
                    asegurarLineaEnBlanco(sb);
                    sb.append(limpio).append("\n\n");
                }
                break;
            }

            case "blockquote": {
                StringBuilder interno = new StringBuilder();
                convertirHijosBloque(nodo.getChildren(), interno, baseUrl, nivelLista);
                String contenido = limpiarDocumento(interno.toString()).trim();
                if (!contenido.isEmpty()) {
                    asegurarLineaEnBlanco(sb);
                    String[] lineas = contenido.split("\n", -1);
                    for (String linea : lineas) {
                        if (linea.isEmpty()) {
                            sb.append(">\n");
                        } else {
                            sb.append("> ").append(linea).append('\n');
                        }
                    }
                    sb.append('\n');
                }
                break;
            }

            case "pre": {
                String lenguaje = extraerLenguajePre(nodo);
                String codigo = extraerTextoLiteral(nodo);
                codigo = quitarSaltoFinalUnico(codigo);
                asegurarLineaEnBlanco(sb);
                String delimitador = calcularDelimitadorBloque(codigo);
                sb.append(delimitador);
                if (!lenguaje.isEmpty()) {
                    sb.append(lenguaje);
                }
                sb.append('\n');
                sb.append(codigo);
                if (!codigo.endsWith("\n")) {
                    sb.append('\n');
                }
                sb.append(delimitador).append("\n\n");
                break;
            }

            case "hr":
                asegurarLineaEnBlanco(sb);
                sb.append("---\n\n");
                break;

            case "ul":
                if (nivelLista == 0) {
                    asegurarLineaEnBlanco(sb);
                }
                convertirLista(nodo, sb, baseUrl, false, nivelLista);
                if (nivelLista == 0) {
                    sb.append('\n');
                }
                break;

            case "ol":
                if (nivelLista == 0) {
                    asegurarLineaEnBlanco(sb);
                }
                convertirLista(nodo, sb, baseUrl, true, nivelLista);
                if (nivelLista == 0) {
                    sb.append('\n');
                }
                break;

            case "li":
                // Caso atípico de <li> fuera de <ul>/<ol>
                convertirItemLista(nodo, sb, baseUrl, "- ", nivelLista);
                break;

            default:
                convertirHijosBloque(nodo.getChildren(), sb, baseUrl, nivelLista);
                break;
        }
    }

    private void convertirLista(HtmlParser.HtmlNode lista, StringBuilder sb,
                                URL baseUrl, boolean ordenada, int nivelLista) {
        int contador = 1;
        for (HtmlParser.HtmlNode hijo : lista.getChildren()) {
            if (hijo.isText()) {
                continue;
            }
            String tag = hijo.getTagName();
            if ("li".equals(tag)) {
                String marcador = ordenada ? (contador + ". ") : "- ";
                convertirItemLista(hijo, sb, baseUrl, marcador, nivelLista);
                contador++;
            } else if ("ul".equals(tag)) {
                convertirLista(hijo, sb, baseUrl, false, nivelLista + 1);
            } else if ("ol".equals(tag)) {
                convertirLista(hijo, sb, baseUrl, true, nivelLista + 1);
            }
        }
    }

    private void convertirItemLista(HtmlParser.HtmlNode item, StringBuilder sb,
                                    URL baseUrl, String marcador, int nivelLista) {
        String indentacion = "  ".repeat(nivelLista);
        StringBuilder textoItem = new StringBuilder();
        StringBuilder sublistas = new StringBuilder();

        for (HtmlParser.HtmlNode hijo : item.getChildren()) {
            if (!hijo.isText() && ("ul".equals(hijo.getTagName()) || "ol".equals(hijo.getTagName()))) {
                boolean subOrdenada = "ol".equals(hijo.getTagName());
                convertirLista(hijo, sublistas, baseUrl, subOrdenada, nivelLista + 1);
            } else if (!hijo.isText() && "p".equals(hijo.getTagName())) {
                String pTexto = limpiarParrafo(convertirContenidoInline(hijo.getChildren(), baseUrl));
                if (!pTexto.isEmpty()) {
                    if (textoItem.length() > 0) {
                        textoItem.append(' ');
                    }
                    textoItem.append(pTexto);
                }
            } else if (hijo.isText()) {
                textoItem.append(normalizarYEscaparTexto(hijo.getText(), false));
            } else {
                convertirInline(hijo, textoItem, baseUrl);
            }
        }

        String lineaPrincipal = limpiarParrafo(textoItem.toString());
        if (!lineaPrincipal.isEmpty() || sublistas.length() > 0) {
            sb.append(indentacion).append(marcador).append(lineaPrincipal).append('\n');
            sb.append(sublistas);
        }
    }

    private String convertirContenidoInline(List<HtmlParser.HtmlNode> hijos, URL baseUrl) {
        StringBuilder sb = new StringBuilder();
        for (HtmlParser.HtmlNode hijo : hijos) {
            if (hijo.isText()) {
                sb.append(normalizarYEscaparTexto(hijo.getText(), false));
            } else {
                convertirInline(hijo, sb, baseUrl);
            }
        }
        return sb.toString();
    }

    private void convertirInline(HtmlParser.HtmlNode nodo, StringBuilder sb, URL baseUrl) {
        String tag = nodo.getTagName();
        switch (tag) {
            case "strong":
            case "b": {
                String contenido = convertirContenidoInline(nodo.getChildren(), baseUrl);
                envolverConDelimitador(sb, contenido, "**");
                break;
            }

            case "em":
            case "i": {
                String contenido = convertirContenidoInline(nodo.getChildren(), baseUrl);
                envolverConDelimitador(sb, contenido, "*");
                break;
            }

            case "code": {
                String codigo = extraerTextoLiteral(nodo).replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
                if (!codigo.isEmpty()) {
                    if (codigo.contains("`")) {
                        sb.append("`` ").append(codigo).append(" ``");
                    } else {
                        sb.append('`').append(codigo).append('`');
                    }
                }
                break;
            }

            case "a": {
                String texto = limpiarParrafo(convertirContenidoInline(nodo.getChildren(), baseUrl));
                String href = nodo.getAttribute("href");
                if (href == null || href.isBlank()) {
                    sb.append(texto);
                    break;
                }
                String hrefLimpio = href.trim();
                if (hrefLimpio.toLowerCase(Locale.ROOT).startsWith("javascript:")) {
                    sb.append(texto);
                    break;
                }
                String urlResuelta = resolverUrlRelativa(baseUrl, hrefLimpio);
                String etiqueta = texto.isEmpty() ? urlResuelta : texto;
                sb.append('[').append(etiqueta).append("](").append(escaparUrlMarkdown(urlResuelta)).append(')');
                break;
            }

            case "img": {
                String src = nodo.getAttribute("src");
                if (src == null || src.isBlank()) {
                    break;
                }
                String altRaw = nodo.getAttribute("alt");
                String alt = altRaw != null ? escaparTextoAlt(altRaw.replaceAll("\\s+", " ").trim()) : "";
                String urlResuelta = resolverUrlRelativa(baseUrl, src.trim());
                sb.append("![").append(alt).append("](").append(escaparUrlMarkdown(urlResuelta)).append(')');
                break;
            }

            case "br":
                // Limpiar espacio previo al salto de línea y emitir salto Markdown (2 espacios + \n)
                while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                sb.append("  \n");
                break;

            case "script":
            case "style":
            case "head":
            case "title":
            case "noscript":
                break;

            default:
                sb.append(convertirContenidoInline(nodo.getChildren(), baseUrl));
                break;
        }
    }

    /**
     * Resuelve una referencia relativa o absoluta con respecto a la URL base.
     */
    public static String resolverUrlRelativa(URL baseUrl, String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return "";
        }
        String ref = referencia.trim();
        if (ref.startsWith("#")
                || ref.toLowerCase(Locale.ROOT).startsWith("mailto:")
                || ref.toLowerCase(Locale.ROOT).startsWith("tel:")
                || ref.toLowerCase(Locale.ROOT).startsWith("data:")) {
            return ref;
        }
        if (baseUrl == null) {
            return ref;
        }
        try {
            URI baseUri = baseUrl.toURI();
            URI resuelta = baseUri.resolve(ref.replace(" ", "%20"));
            return resuelta.toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return ref;
        }
    }

    private static void envolverConDelimitador(StringBuilder sb, String contenido, String delimitador) {
        if (contenido == null || contenido.isEmpty()) {
            return;
        }
        if (contenido.isBlank()) {
            sb.append(' ');
            return;
        }
        boolean espacioInicio = Character.isWhitespace(contenido.charAt(0));
        boolean espacioFin = Character.isWhitespace(contenido.charAt(contenido.length() - 1));
        String centro = contenido.trim();
        if (espacioInicio) {
            sb.append(' ');
        }
        sb.append(delimitador).append(centro).append(delimitador);
        if (espacioFin) {
            sb.append(' ');
        }
    }

    private void vaciarParrafoInline(StringBuilder inlineBuffer, StringBuilder sb) {
        if (inlineBuffer.length() == 0) {
            return;
        }
        String limpio = limpiarParrafo(inlineBuffer.toString());
        inlineBuffer.setLength(0);
        if (!limpio.isEmpty()) {
            asegurarLineaEnBlanco(sb);
            sb.append(limpio).append("\n\n");
        }
    }

    private static boolean esEtiquetaBloque(String tag) {
        return switch (tag) {
            case "html", "body", "head", "title", "script", "style", "noscript", "template",
                 "div", "main", "article", "section", "header", "footer", "nav", "aside",
                 "figure", "figcaption", "h1", "h2", "h3", "h4", "h5", "h6",
                 "p", "blockquote", "pre", "hr", "ul", "ol", "li" -> true;
            default -> false;
        };
    }

    private static String extraerTextoLiteral(HtmlParser.HtmlNode nodo) {
        StringBuilder sb = new StringBuilder();
        extraerTextoLiteralRec(nodo, sb);
        return sb.toString().replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void extraerTextoLiteralRec(HtmlParser.HtmlNode nodo, StringBuilder sb) {
        if (nodo.isText()) {
            sb.append(nodo.getText());
            return;
        }
        String tag = nodo.getTagName();
        if ("br".equals(tag)) {
            sb.append('\n');
            return;
        }
        for (HtmlParser.HtmlNode hijo : nodo.getChildren()) {
            extraerTextoLiteralRec(hijo, sb);
        }
    }

    private static String extraerLenguajePre(HtmlParser.HtmlNode preNode) {
        String clsPre = preNode.getAttribute("class");
        String lang = buscarLenguajeEnClase(clsPre);
        if (!lang.isEmpty()) {
            return lang;
        }
        for (HtmlParser.HtmlNode hijo : preNode.getChildren()) {
            if (!hijo.isText() && "code".equals(hijo.getTagName())) {
                lang = buscarLenguajeEnClase(hijo.getAttribute("class"));
                if (!lang.isEmpty()) {
                    return lang;
                }
            }
        }
        return "";
    }

    private static String buscarLenguajeEnClase(String classAttr) {
        if (classAttr == null || classAttr.isBlank()) {
            return "";
        }
        Matcher m = PATRON_LENGUAJE.matcher(classAttr);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
    }

    private static String calcularDelimitadorBloque(String codigo) {
        String delimitador = "```";
        while (codigo.contains(delimitador)) {
            delimitador += "`";
        }
        return delimitador;
    }

    private static String quitarSaltoFinalUnico(String s) {
        if (s.endsWith("\n")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String normalizarYEscaparTexto(String texto, boolean esLiteral) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        if (esLiteral) {
            return texto;
        }
        String normalizado = texto.replace('\u00A0', ' ').replaceAll("[ \\t\\r\\n]+", " ");
        StringBuilder escaped = new StringBuilder(normalizado.length());
        for (int i = 0; i < normalizado.length(); i++) {
            char c = normalizado.charAt(i);
            switch (c) {
                case '\\', '*', '_', '`', '[', ']' -> escaped.append('\\').append(c);
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static String escaparTextoAlt(String alt) {
        return alt.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private static String escaparUrlMarkdown(String url) {
        return url.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }

    private static String limpiarParrafo(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String[] lineas = texto.split("\n", -1);
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            boolean conservarSaltoMd = linea.endsWith("  ");
            String limpia = linea.replaceAll(" {2,}", " ").trim();
            if (i == 0 && (limpia.startsWith("#") || limpia.startsWith(">"))) {
                limpia = "\\" + limpia;
            }
            if (!limpia.isEmpty()) {
                resultado.append(limpia);
                if (conservarSaltoMd && i < lineas.length - 1) {
                    resultado.append("  ");
                }
            }
            if (i < lineas.length - 1) {
                resultado.append('\n');
            }
        }
        return resultado.toString().trim();
    }

    private static void asegurarLineaEnBlanco(StringBuilder sb) {
        int len = sb.length();
        if (len == 0) {
            return;
        }
        if (len >= 2 && sb.charAt(len - 1) == '\n' && sb.charAt(len - 2) == '\n') {
            return;
        }
        if (sb.charAt(len - 1) == '\n') {
            sb.append('\n');
        } else {
            sb.append("\n\n");
        }
    }

    private static String limpiarDocumento(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return "";
        }
        String normalizado = bruto.replaceAll("\n{3,}", "\n\n").trim();
        return normalizado.isEmpty() ? "" : normalizado + "\n";
    }
}
