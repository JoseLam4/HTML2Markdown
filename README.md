# HTML2Markdown - Traductor de páginas HTML a Markdown en Java

## Datos del estudiante

- Asignatura: Desarrollo de Sistemas III
- Unidad: Programación cliente-servidor y procesamiento de documentos
- Estudiante: José Carlos Tirado Lam
- Matrícula: 225213509
- División: Ciencias e Ingeniería
- Carrera: Ingeniería en Sistemas de Información
- Lenguaje: Java 17 o superior (compilado y probado en OpenJDK 25.0.4.1)
- Entorno: IntelliJ IDEA, Git y GitHub
- Restricción técnica: Exclusivamente clases de la API estándar de Java

---

## Descripción del proyecto

`HTML2Markdown` es una aplicación de línea de comandos en Java que recibe la URL de un documento HTML vía HTTP o HTTPS, recupera su contenido mediante `URL`, `URLConnection` y `HttpURLConnection`, analiza su estructura con `ParserDelegator` y `HTMLEditorKit.ParserCallback`, transforma los elementos HTML admitidos a sintaxis Markdown y guarda el resultado en un archivo `.md` con codificación UTF-8 usando `java.nio.file`.

---

## Arquitectura y estructura del proyecto

La aplicación se divide en cinco clases principales con responsabilidades separadas:

- `HTML2Markdown`: Punto de entrada, validación de argumentos y URL, coordinación de componentes, manejo de errores y presentación del resumen en consola.
- `WebClient`: Cliente HTTP/HTTPS que configura tiempos de espera (`10 s` de conexión, `15 s` de lectura), controla redirecciones (máximo `5`), verifica códigos HTTP exitosos (`2xx`), valida tipo MIME HTML (`text/html` o `application/xhtml+xml`), identifica el conjunto de caracteres (cabecera HTTP, etiqueta `<meta>` o UTF-8 por defecto) y limita la descarga a `5 MB`.
- `HtmlParser`: Analizador basado en `javax.swing.text.html.parser.ParserDelegator` y `HTMLEditorKit.ParserCallback` que excluye `head`, `title`, `script`, `style`, `noscript` y `template`, y construye un árbol de nodos (`HtmlNode`) preservando etiquetas, atributos y jerarquía.
- `MarkdownConverter`: Recorre el árbol de nodos para aplicar las reglas de transformación a Markdown, mantener la indentación de listas anidadas, escapar caracteres especiales fuera de bloques de código y resolver URLs relativas respecto a la URL base.
- `MarkdownWriter`: Resuelve la ruta de salida predeterminada cuando no se proporciona un segundo argumento, crea los directorios padre necesarios y escribe el archivo `.md` en UTF-8 mediante `Files` y `Path`.

Estructura de archivos:

```text
HTML2Markdown/
├── README.md
├── .gitignore
├── src/
│   ├── main/
│   │   └── java/
│   │       ├── HTML2Markdown.java
│   │       ├── WebClient.java
│   │       ├── HtmlParser.java
│   │       ├── MarkdownConverter.java
│   │       └── MarkdownWriter.java
│   └── test/
│       └── java/
│           └── HTML2MarkdownTest.java
├── tests/
│   ├── encabezados.html
│   ├── listas.html
│   ├── enlaces.html
│   ├── documento_completo.html
│   └── expected/
│       ├── encabezados.md
│       ├── listas.md
│       ├── enlaces.md
│       └── documento_completo.md
└── output/
    └── articulo.md
```

---

## Reglas de conversión implementadas

| Elemento HTML | Conversión Markdown |
|---|---|
| `<h1>` a `<h6>` | `#` a `######` |
| `<p>` | Párrafo separado por una línea en blanco |
| `<strong>`, `<b>` | `**texto**` |
| `<em>`, `<i>` | `*texto*` |
| `<a href="...">` | `[texto](URL)` con resolución de URL relativa |
| `<img src="..." alt="...">` | `![alt](URL)` con resolución de URL relativa |
| `<ul>` y `<li>` | Lista no ordenada con `- ` (soporta anidamiento) |
| `<ol>` y `<li>` | Lista numerada (`1. `, `2. `, etc., soporta anidamiento) |
| `<br>` | Salto de línea Markdown (dos espacios y salto de línea) |
| `<blockquote>` | `> texto` |
| `<code>` | Código en línea entre acentos graves |
| `<pre>` | Bloque de código delimitado por tres acentos graves |
| `<hr>` | `---` |
| `<script>`, `<style>` | Contenido omitido |

---

## Instrucciones de compilación y ejecución

### Compilación

Desde la raíz del proyecto (`HTML2Markdown/`):

```bash
javac -encoding UTF-8 -d out src/main/java/*.java
```

### Ejecución

Con ruta de salida predeterminada (derivada de la URL):

```bash
java -cp out HTML2Markdown https://ejemplo.org/articulo.html
```

Con ruta de salida explícita:

```bash
java -cp out HTML2Markdown https://ejemplo.org/articulo.html output/articulo.md
```

### Compilación y ejecución de pruebas

```bash
javac -encoding UTF-8 -d out src/main/java/*.java src/test/java/*.java
java -cp out HTML2MarkdownTest
```

---

## Pruebas de funcionamiento

La clase `HTML2MarkdownTest` levanta un servidor HTTP local mediante `com.sun.net.httpserver.HttpServer` y verifica los 12 casos de prueba solicitados además de controles adicionales de error y comparación contra los archivos esperados en `tests/expected/`.

| Prueba | Condición | Resultado esperado | Estado |
|---|---|---|---|
| P01 | Sin argumentos | Mensaje de uso | Aprobada |
| P02 | URL inválida | Error controlado | Aprobada |
| P03 | Página inexistente | Error HTTP (404) | Aprobada |
| P04 | Encabezados `h1` - `h6` | Conversión correcta | Aprobada |
| P05 | Negritas y cursivas | Formato preservado | Aprobada |
| P06 | Enlaces absolutos y relativos | Enlaces Markdown válidos | Aprobada |
| P07 | Imágenes con texto alternativo | Sintaxis `![alt](URL)` | Aprobada |
| P08 | Listas ordenadas y no ordenadas | Estructura conservada | Aprobada |
| P09 | Elementos anidados | Jerarquía y contenido conservados | Aprobada |
| P10 | Etiquetas `script` y `style` | Contenido excluido | Aprobada |
| P11 | Acentos y entidades HTML | Caracteres preservados | Aprobada |
| P12 | Página HTML vacía | Archivo vacío y mensaje informativo | Aprobada |
| P13 | Contenido no HTML (`application/json`) | Rechazo controlado por tipo MIME | Aprobada |
| P14 | Servidor inaccesible | Error de conexión controlado | Aprobada |
| P15 | Ruta de destino inválida (directorio) | Error de escritura controlado | Aprobada |
| P16 | Documento HTML completo | Coincidencia exacta con salida esperada | Aprobada |

---

## Evidencias de funcionamiento

### Ejecución en línea de comandos

Ejemplo de ejecución sirviendo `tests/` mediante `jwebserver` y generando `output/articulo.md`:

```text
HTML2Markdown
--------------------------------
URL: http://127.0.0.1:18080/documento_completo.html
Conectando con el servidor...
Respuesta HTTP: 200
Analizando HTML...
Convirtiendo a Markdown...
Archivo generado: output\articulo.md
Tamaño: 762 bytes
Conversión finalizada.
```

Contenido generado en `output/articulo.md`:

````markdown
# Introducción a Java

Java es un lenguaje **orientado a objetos** y *multiplataforma*.

## Características principales

- Portabilidad entre sistemas
- Seguridad y manejo de memoria

## Ejemplo de código

Usa el método `System.out.println()` para imprimir en consola:

```java
public class Hola {
    public static void main(String[] args) {
        System.out.println("Hola, mundo");
    }
}
```

> La separación de responsabilidades facilita el mantenimiento del software.

Primera línea  
Segunda línea con caracteres especiales: 2 \* 3 = 6 y árbol & café.

---

Visita la [**documentación oficial**](https://dev.java/) o consulta la [referencia local](http://127.0.0.1:18080/referencia.html).

![Logo de Java](http://127.0.0.1:18080/img/java.png)
````

### Ejecución de la suite de pruebas

```text
============================================================
PRUEBAS FUNCIONALES (P01 - P12) - HTML2Markdown
Estudiante: José Carlos Tirado Lam | Matrícula: 225213509
============================================================

[OK]   P01 - Sin argumentos (mensaje de uso)
[OK]   P02 - URL inválida (error controlado)
[OK]   P03 - Página inexistente 404 (error HTTP)
[OK]   P04 - Encabezados h1 a h6 y regla horizontal
[OK]   P05 - Negritas y cursivas
[OK]   P06 - Enlaces absolutos y relativos
[OK]   P07 - Imágenes con texto alternativo
[OK]   P08 - Listas ordenadas, no ordenadas y anidadas
[OK]   P09 - Elementos anidados, citas y bloques de código
[OK]   P10 - Exclusión de etiquetas script y style
[OK]   P11 - Acentos, entidades HTML y escape Markdown
[OK]   P12 - Página HTML vacía
[OK]   P13 - Contenido no HTML rechazado
[OK]   P14 - Servidor inaccesible controlado
[OK]   P15 - Error de escritura controlado
[OK]   P16 - Documento completo vs salida esperada

------------------------------------------------------------
Resumen: 16 aprobadas, 0 fallidas.
------------------------------------------------------------
```

---

## Preguntas de reflexión

1. **¿Qué papel desempeña la aplicación dentro de una arquitectura cliente-servidor?**
   La aplicación actúa como cliente HTTP que inicia la comunicación solicitando un recurso HTML a un servidor web remoto, recibe la respuesta y procesa localmente los datos para transformarlos en un archivo Markdown.

2. **¿Qué responsabilidades corresponden al servidor y cuáles al cliente?**
   El servidor recibe las peticiones HTTP, localiza o genera el recurso solicitado, envía el código de estado, las cabeceras (como `Content-Type` y longitud) y el cuerpo HTML. El cliente valida la URL, abre la conexión con tiempos límite, verifica que la respuesta sea exitosa y de tipo HTML, decodifica el texto según el charset, interpreta la estructura del documento y guarda el resultado en el sistema de archivos local.

3. **¿Qué diferencia existe entre recuperar HTML e interpretar su estructura?**
   Recuperar HTML consiste únicamente en descargar una secuencia de bytes por red y decodificarla como texto plano. Interpretar su estructura implica analizar sintácticamente ese texto para distinguir etiquetas de apertura y cierre, etiquetas vacías, atributos, entidades HTML, texto visible y relaciones padre-hijo entre elementos.

4. **¿Por qué no basta reemplazar etiquetas HTML con expresiones regulares?**
   Porque HTML tiene estructura jerárquica y recursiva (etiquetas anidadas del mismo tipo como `<ul>` dentro de `<ul>` o `<div>` dentro de `<div>`), atributos con comillas simples o dobles, etiquetas sin cierre explícito, entidades codificadas y bloques especiales como `<pre>` o `<script>`. Las expresiones regulares no mantienen estado de pila ni distinguen fácilmente contexto o profundidad de anidamiento.

5. **¿Qué dificultades presentan los elementos anidados?**
   Exigen preservar el orden exacto de texto y etiquetas hijas, calcular la indentación y numeración correcta en sublistas (`<ul>`/`<ol>`), evitar delimitadores mal cerrados cuando se combinan formatos (`<strong>` con `<em>` o `<a>` con `<strong>`), manejar espacios en los bordes del formato y evitar aplicar doble formato dentro de `<pre><code>`.

6. **¿Qué información puede perderse al convertir HTML a Markdown?**
   Se pierden estilos visuales CSS, diseño de columnas o posicionamiento, atributos como `id`, `class` (salvo uso puntual como lenguaje en bloques de código) o `style`, metadatos del `<head>`, scripts interactivos, formularios y elementos complejos sin equivalente directo en Markdown básico.

7. **¿Por qué deben resolverse las URL relativas?**
   Porque un archivo Markdown guardado localmente pierde el contexto del dominio y ruta del servidor web original. Si enlaces (`href`) o imágenes (`src`) permanecen como rutas relativas (`/docs/guia.html` o `../img/logo.png`), dejarán de apuntar a un recurso válido al abrir el `.md` fuera del servidor.

8. **¿Qué ventajas ofrece separar descarga, análisis y conversión en clases?**
   Mejora la modularidad y mantenibilidad: permite probar el analizador y el conversor sin depender de conexión de red, facilita cambiar el mecanismo de análisis HTML o añadir nuevas reglas Markdown sin tocar el cliente HTTP, y asigna a cada clase una única responsabilidad clara.

9. **¿Qué limitaciones presenta `ParserDelegator` con HTML moderno?**
   `ParserDelegator` implementa un DTD heredado basado en HTML 3.2, por lo que no reconoce nativamente etiquetas semánticas de HTML5 (`<main>`, `<article>`, `<section>`, `<nav>`), tratándolas como etiquetas desconocidas de tipo simple, y puede malinterpretar bloques `<style>` o `<script>` ubicados dentro de `<body>` o con caracteres `<` y `>` en su interior.

10. **¿Qué modificaciones serían necesarias para convertir tablas HTML a Markdown?**
    Habría que reconocer `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th>` y `<td>` en `HtmlParser` como elementos de bloque/celda estructurados, y agregar lógica en `MarkdownConverter` para almacenar las filas y columnas en memoria, detectar la fila de encabezados (o generar una fila de separación `| --- | --- |` después de la primera fila), escapar caracteres `|` dentro del contenido de cada celda y formatear cada fila con separadores `|`.
