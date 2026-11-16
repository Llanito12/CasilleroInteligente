# Verificación del proyecto nuevo

- Compilación Gradle: BUILD SUCCESSFUL.
- Pruebas Locker: 12, sin fallos ni errores.
- Android Lint: 0 errores y 8 advertencias.
- Firma APK de depuración: válida (esquema v2).
- SHA-256 de la APK: `b75b4ead268ef8e04f218c6a855e0020043ea2d6942514b5ff747169211f58be`.

Las advertencias restantes corresponden a targetSdk de laboratorio, orientación vertical, textos sin internacionalización completa y el TrustManager personalizado. Este último verifica vigencia y huella SHA-256 exacta; no acepta cualquier certificado. Se mantiene visible la advertencia para revisión, sin suprimirla.

Las correcciones durante compilación incluyeron la referencia explícita al contenedor de MainActivity y reglas que excluyen datos de las copias y transferencias. El análisis estático no prueba la seguridad integral del diseño.

No se ejecutó en teléfonos ni emulador. Login real, Android Keystore, TLS entre equipos, Bluetooth, interacción de la UI y alcance físico siguen pendientes. Completar Registro_Pruebas.csv con los dispositivos reales antes de presentar resultados.
