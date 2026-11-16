# Casillero inteligente — Evaluación Sumativa 2

Prototipo Android en Kotlin basado en el casillero con HC-SR04, SG90, buzzer y LED de la evaluación 1. En esta unidad el hardware se simula en el teléfono B.

## Primero

1. Instala `Casillero_Inteligente_Debug.apk` en dos teléfonos Android 8 o posterior con Bluetooth clásico. Es una APK de laboratorio firmada con clave de depuración; no es una publicación de producción.
2. Conecta ambos teléfonos a la misma red Wi-Fi sin aislamiento entre clientes, o usa el hotspot de uno. No necesitas internet para la demostración. Consulta la IP local de B en Ajustes de Wi-Fi o en la aplicación.
3. Empareja A y B desde Ajustes de Android ANTES de abrir la app. Activa Bluetooth.
4. En B crea una cuenta de rol **Terminal B**; en A crea otra de rol **Supervisor A**. Usuario de mínimo 3 caracteres y contraseña de mínimo 10. No hay contraseñas predeterminadas. El rol se fija en la instalación; para cambiarlo en laboratorio debes borrar datos y crear de nuevo la cuenta. Se borran también las claves y el historial.
5. En B pulsa **Iniciar servidor TLS**. Aparecen una huella de 64 caracteres y un token de 32 caracteres. Copia ambos directamente a A sin publicarlos ni incluirlos en GitHub. La huella permite comprobar la identidad de B sin una autoridad de certificación externa. Si reinstalas B, debes vincular de nuevo A.
6. En A ingresa la IP de B, la huella y el token. Pulsa **Conectar monitoreo TLS**. Debe aparecer distancia y estado actualizado cada segundo.
7. En B pulsa **Seleccionar teléfono emparejado**, concede el permiso si lo pide, pulsa de nuevo y selecciona A. B espera la conexión.
8. En A pulsa el mismo botón, concede permiso si corresponde y selecciona B. A envía únicamente `PRESENCE` cada segundo; no envía aperturas ni cambios de configuración.
9. Con ambos enlaces vigentes, pulsa **Abrir localmente por 5 segundos EN B** y confirma. Observa el cierre automático y la actualización del monitoreo en A.
10. Mantén la aplicación visible en ambos teléfonos. Salir de la aplicación cierra la sesión y las conexiones de forma intencional. Para reconectar, vuelve a iniciar sesión y repite los pasos de conexión.

## Seguridad y alcance

- La apertura es local en B. Los protocolos de red no admiten `OPEN`, `CLOSE` ni cambios de umbral. La presencia Bluetooth es una condición adicional y no una prueba criptográfica de distancia.
- Wi-Fi: sockets TLS 1.2 en puerto 8443 con huella SHA-256 fijada manualmente y token aleatorio de 128 bits. Es un protocolo JSON sobre TLS, no un servidor HTTP/HTTPS. Solo acepta `GET` autenticado.
- Bluetooth: RFCOMM seguro hacia el dispositivo emparejado seleccionado. La aplicación no realiza descubrimiento ni requiere ubicación.
- Clave AES-256 y clave privada RSA en Android Keystore. AES-GCM protege el registro de credenciales, tokens y contenido de eventos. PBKDF2-HMAC-SHA256 con sal aleatoria de 16 bytes, 210 000 iteraciones y salida de 256 bits verifica la contraseña.
- El hash derivado de la contraseña se cifra con una clave no exportable de Keystore; no se guarda una contraseña recuperable. No se promete respaldo por hardware: depende del dispositivo.
- Cinco fallos bloquean el login 30 s; contador y bloqueo persisten. El bloqueo usa reloj del sistema, por lo que no protege contra un administrador que manipule ese reloj.
- SQLite conserva hasta 500 eventos cifrados y muestra los últimos 15. No incluye sincronización en nube ni API MySQL.
- Fail-secure: pérdida de presencia o monitoreo durante 5 s bloquea y cierra el modelo en la siguiente revisión de 500 ms; salir de la app también lo cierra. Reestablecer conexiones nunca abre automáticamente.
- La app bloquea capturas de pantalla para proteger credenciales y datos de vinculación. Para evidencias, graba externamente las pantallas evitando mostrar los secretos.
- El SG90 es una simulación en pantalla. No se certifica una cerradura física segura, ni cumplimiento integral ISO. El diseño del cierre ante corte eléctrico exige hardware específico.
- La demostración Wi-Fi es en LAN/hotspot. Monitorear por internet exigiría infraestructura adicional (por ejemplo VPN o backend), no incluida.
- Los roles se provisionan bajo supervisión local durante el laboratorio. No existe una administración empresarial de usuarios ni resistencia ante root/reinstalación.

## Abrir y compilar en Android Studio

1. Descomprime la carpeta completa. En Android Studio usa **Open** y selecciona la carpeta con `settings.gradle.kts`.
2. Configura Gradle JDK 17 (un JDK compatible superior también puede funcionar). Instala SDK Android 35 y Build Tools 34.0.0 desde SDK Manager.
3. Espera Gradle Sync. La primera compilación requiere internet para descargar dependencias.
4. En Windows: `gradlew.bat assembleDebug testDebugUnitTest lintDebug`. En Linux/macOS: `./gradlew assembleDebug testDebugUnitTest lintDebug`.
5. APK resultante: `app/build/outputs/apk/debug/app-debug.apk`.

Versiones fijadas para reproducibilidad: Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, AppCompat 1.7.0 y Material Components 1.12.0. `minSdk=26`, `compileSdk=35`, `targetSdk=34`. Es una entrega académica por instalación directa, no una preparación para publicar en Google Play.

## Archivos para explicar al docente

- `MainActivity.kt`: login, navegación, Material Components, roles y ciclo de vida.
- `Locker.kt`: reglas de apertura local, temporizadores y vigilancia.
- `Vault.kt`: PBKDF2 y cifrado AES-GCM con Android Keystore.
- `EventStore.kt`: SQLite con eventos cifrados y límite de retención.
- `TlsMonitor.kt`: monitoreo Wi-Fi autenticado de solo lectura.
- `BluetoothPresence.kt`: conexión Bluetooth y presencia, sin actuadores remotos.
- `LockerTest.kt`: 12 casos automatizados del modelo. No equivalen a ensayos de radio o Keystore.
- `docs/`: informe, mockups, diagramas y registro de pruebas.

## Subir a GitHub y entregar en AAI

La guía pide un bloc de notas con el enlace REAL al repositorio.

1. Crea un repositorio en tu cuenta de GitHub, por ejemplo `casillero-inteligente-u2`.
2. Sube el contenido de esta carpeta, conservando las subcarpetas. Puedes usar GitHub Desktop: File > Add local repository y, si hace falta, Create a repository; luego Commit y Publish repository. También puedes subir archivos desde la web.
3. NO subas `local.properties`, `.gradle`, `.idea`, carpetas `build`, contraseñas, tokens, datos de usuarios o claves. El `.gitignore` ya excluye los directorios de compilación y claves habituales.
4. Comprueba que el repositorio contiene `app`, `gradle`, `gradlew`, `gradlew.bat`, `settings.gradle.kts`, los scripts de compilación y README. Si es privado, habilita acceso al docente según sus indicaciones.
5. Copia la URL real y pégala en un archivo de Bloc de notas, por ejemplo `Entrega_Casillero.txt`. No entregues una URL de ejemplo.
6. Abre la URL desde otra sesión para confirmar que el docente tendrá acceso. Sube el TXT al AAI antes del cierre. Lleva también la APK y el informe como respaldo de la demostración.

## Si algo falla

- Sin lista Bluetooth: empareja desde Ajustes, activa Bluetooth y concede Dispositivos cercanos. Después pulsa otra vez el botón.
- Wi-Fi no responde: comprueba IP, puerto 8443 y red compartida; algunas redes de institución aíslan clientes. Prueba hotspot. No uses la IP del teléfono A.
- Huella/token incorrectos: cierra sesión en A, vuelve a entrar y corrige. Confirma exactamente los caracteres de B. No desactives la validación del certificado.
- Apertura bloqueada: verifica presencia BT y consultas Wi-Fi recientes, sesión en B y app visible en ambos teléfonos. Espera una actualización antes de abrir.
- Cambio de teléfono/rol o datos borrados: vuelve a provisionar, emparejar y copiar huella/token. La reinstalación elimina secretos locales.
- No prometas resultados de pruebas físicas antes de ejecutarlas: completa `docs/Registro_Pruebas.csv`.
