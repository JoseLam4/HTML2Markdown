import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Analizador HTML basado en {@link ParserDelegator} y {@link HTMLEditorKit.ParserCallback}
 * de la biblioteca estándar de Java. Construye un árbol ligero de nodos HTML para
 * preservar la jerarquía y separar el análisis de la conversión a Markdown.
 */
public class HtmlParser {

    private static final Set<String> ETIQUETAS_EXCLUIDAS = Set.of(
            "head", "title", "script", "style", "noscript", "template"
    );

    private static final Set<String> ETIQUETAS_VACIAS_SOPORTADAS = Set.of(
            "img", "br", "hr"
    );

    /**
     * Representa un nodo del árbol HTML (elemento con hijos y atributos, o nodo de texto).
     */
    public static class HtmlNode {
        private final String tagName;
        private final String text;
        private final Map<String, String> attributes;
        private final List<HtmlNode> children;

        private HtmlNode(String tagName, String text, Map<String, String> attributes) {
            this.tagName = tagName;
            this.text = text;
            this.attributes = attributes != null ? attributes : Collections.emptyMap();
            this.children = tagName != null ? new ArrayList<>() : Collections.emptyList();
        }

        public static HtmlNode element(String tagName, Map<String, String> attributes) {
            return new HtmlNode(tagName.toLowerCase(Locale.ROOT), null, attributes);
        }

        public static HtmlNode text(String content) {
            return new HtmlNode(null, content, null);
        }

        public boolean isText() {
            return tagName == null;
        }

        public String getTagName() {
            return tagName;
        }

        public String getText() {
            return text;
        }

        public String getAttribute(String name) {
            return attributes.get(name.toLowerCase(Locale.ROOT));
        }

        public List<HtmlNode> getChildren() {
            return children;
        }
    }

    /**
     * Analiza una cadena HTML y devuelve el nodo raíz con la estructura de elementos.
     *
     * @param html Contenido HTML en texto.
     * @return Nodo raíz del árbol analizado.
     * @throws IOException Si ocurre un error durante la lectura del flujo HTML.
     */
    public HtmlNode parse(String html) throws IOException {
        HtmlNode raiz = HtmlNode.element("root", Collections.emptyMap());
        if (html == null || html.isBlank()) {
            return raiz;
        }

        Deque<HtmlNode> pilaElementos = new ArrayDeque<>();
        pilaElementos.push(raiz);
        Deque<String> pilaOmitidos = new ArrayDeque<>();

        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                String tag = t.toString().toLowerCase(Locale.ROOT);

                if (!pilaOmitidos.isEmpty()) {
                    if (ETIQUETAS_EXCLUIDAS.contains(tag)) {
                        pilaOmitidos.push(tag);
                    }
                    return;
                }

                if (ETIQUETAS_EXCLUIDAS.contains(tag)) {
                    pilaOmitidos.push(tag);
                    return;
                }

                HtmlNode nodo = HtmlNode.element(tag, extraerAtributos(a));
                pilaElementos.peek().getChildren().add(nodo);
                pilaElementos.push(nodo);
            }

            @Override
            public void handleEndTag(HTML.Tag t, int pos) {
                String tag = t.toString().toLowerCase(Locale.ROOT);

                if (!pilaOmitidos.isEmpty()) {
                    if (pilaOmitidos.contains(tag)) {
                        while (!pilaOmitidos.isEmpty()) {
                            String cerrada = pilaOmitidos.pop();
                            if (cerrada.equals(tag)) {
                                break;
                            }
                        }
                    }
                    return;
                }

                if (contieneEtiquetaEnPila(pilaElementos, tag)) {
                    while (pilaElementos.size() > 1) {
                        HtmlNode actual = pilaElementos.pop();
                        if (tag.equals(actual.getTagName())) {
                            break;
                        }
                    }
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (!pilaOmitidos.isEmpty()) {
                    return;
                }
                if (a != null && a.getAttribute(HTML.Attribute.ENDTAG) != null) {
                    return;
                }

                String tag = t.toString().toLowerCase(Locale.ROOT);
                if (ETIQUETAS_EXCLUIDAS.contains(tag)) {
                    return;
                }
                if (ETIQUETAS_VACIAS_SOPORTADAS.contains(tag)) {
                    HtmlNode nodo = HtmlNode.element(tag, extraerAtributos(a));
                    pilaElementos.peek().getChildren().add(nodo);
                }
            }

            @Override
            public void handleText(char[] data, int pos) {
                if (!pilaOmitidos.isEmpty() || data == null || data.length == 0) {
                    return;
                }
                pilaElementos.peek().getChildren().add(HtmlNode.text(new String(data)));
            }

            @Override
            public void handleComment(char[] data, int pos) {
                // Los comentarios HTML (y scripts tratados como comentario) se omiten.
            }
        };

        ParserDelegator delegator = new ParserDelegator();
        delegator.parse(new StringReader(html), callback, true);
        return raiz;
    }

    private static boolean contieneEtiquetaEnPila(Deque<HtmlNode> pila, String tagBuscado) {
        for (HtmlNode nodo : pila) {
            if ("root".equals(nodo.getTagName())) {
                return false;
            }
            if (tagBuscado.equals(nodo.getTagName())) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> extraerAtributos(MutableAttributeSet attrs) {
        if (attrs == null || attrs.getAttributeCount() == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> mapa = new LinkedHashMap<>();
        Enumeration<?> nombres = attrs.getAttributeNames();
        while (nombres.hasMoreElements()) {
            Object claveObj = nombres.nextElement();
            if (claveObj == HTMLEditorKit.ParserCallback.IMPLIED || claveObj == HTML.Attribute.ENDTAG) {
                continue;
            }
            Object valorObj = attrs.getAttribute(claveObj);
            if (claveObj != null && valorObj != null) {
                mapa.put(claveObj.toString().toLowerCase(Locale.ROOT), valorObj.toString());
            }
        }
        return mapa;
    }
}
