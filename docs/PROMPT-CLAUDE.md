# Prompt para Claude Code · Dispensador IoT

## ROL

Actúa como ingeniero de sistemas embebidos y desarrollador Android con experiencia en ESP32, ESP32-CAM, Arduino, MQTT sobre TLS, EMQX Cloud, Firebase Authentication y Cloud Firestore. Trabaja directamente sobre el repositorio. Tienes que leer el código, implementar lo pendiente, compilar y aportar evidencia de las pruebas que puedas ejecutar.

## CONTEXTO

El proyecto es un **dispensador inteligente de alimento para mascotas**. La app Android controla el dispensador, muestra las lecturas de los sensores, recibe fotografías por presencia y consulta el historial del usuario. Este es el repositorio definitivo:

[Proyecto-DISPENSADOR-DEFINITIVO-IOT](https://github.com/bryanramosing-ux/Proyecto-DISPENSADOR-DEFINITIVO-IOT)

- **Red del laboratorio.** El teléfono Android ejecutará la app y compartirá sus datos móviles mediante una zona Wi-Fi de **2,4 GHz**. El ESP32 y el ESP32-CAM se conectarán a esa zona y desde allí al broker. La demostración debe funcionar con el teléfono y las dos placas, sin depender de un servidor en el computador.
- **MQTT.** El broker EMQX Cloud `dispensador-instituto` ya está creado y **MQTTX ya se conecta**. Conserva esa configuración. Usa MQTTX como observador durante las pruebas y verifica por separado la conexión de la app y de cada placa.
- **Android y Firebase.** Ya probé la app en simulación y tengo un proyecto Firebase existente. El identificador Android documentado es `cl.marea.app`. Revisa qué está configurado y completa lo pendiente para el modo real.
- **Firmware.** Parte de `firmware/Control/Control.ino` y `firmware/Camara/Camara.ino`. El controlador maneja dos sensores HC-SR04 y un servo. El firmware de cámara contiene el mapa de la AI Thinker ESP32-CAM con OV2640 y publica JPEG por MQTT. El README menciona un MG996R y en antecedentes del prototipo aparece un MG995. Confirma el modelo físico antes de fijar la calibración definitiva.
- **Documentación.** Toma `README.md`, `docs/SOFTWARE.md`, `docs/HARDWARE.md` y `firestore.rules` como punto de partida. Contrasta sus instrucciones con el código en la rama que recibas.

## OBJETIVO

Deja el proyecto listo para demostrar este recorrido con el teléfono del equipo: **iniciar sesión → conectar con EMQX → recibir lecturas reales → pulsar Dispensar y Detener → ver el resultado del controlador → consultar el historial en Firestore → detectar una visita y visualizar su foto**. Comprueba también la recuperación al apagar y volver a encender la zona Wi-Fi, sin repetir una dispensación.

## TAREA

1. **Audita el repositorio y corrige lo que falte.** Identifica la rama y el commit inicial. Revisa los módulos `app`, `domain`, `data` y `hardware`, ambos sketches, sus cabeceras y las plantillas de configuración. Sigue en el código el recorrido completo de una orden, una lectura y una foto. Corrige las discrepancias que encuentres entre productor y consumidor. Conserva el modo simulación y comprueba que el modo real identifique claramente las desconexiones y los datos antiguos.

2. **Prepara los dos firmwares para el hotspot.** Deja indicado qué valor va en cada archivo local `secrets.h`: SSID exacto, contraseña de la zona Wi-Fi, host y puerto de EMQX, certificado CA, credenciales del cliente e identificador del equipo. Comprueba los nombres reales de las constantes antes de documentarlos. Usa el mismo identificador de equipo en la app y las placas, con un `Client ID` MQTT distinto para cada conexión. Verifica el arranque sin red, la sincronización de hora para TLS, la reconexión y la recuperación de las suscripciones. Deja mensajes de diagnóstico por puerto serie que permitan distinguir un fallo de Wi-Fi, TLS o autenticación MQTT.

3. **Completa el ESP32 controlador.** Verifica que cada HC-SR04 corresponda a su función —plato o tolva— y que una lectura inválida se muestre como tal. Contrasta los GPIO con el manual. Revisa la validación de porciones, el nivel mínimo, la vigencia del `nonce`, la cuota persistente y la protección frente a órdenes repetidas o antiguas. Comprueba qué ocurre si la placa ejecuta la orden y la app pierde el `ack`. La interfaz debe poder mostrar un resultado sin confirmar y evitar que un reintento automático entregue otra porción.

4. **Completa el ESP32-CAM.** Verifica el modelo de placa, el sensor y el mapa de pines antes de cargar. Sigue el flujo real del repositorio: el controlador detecta presencia y publica en `camera/request` y la cámara responde con un JPEG binario en `jpeg`. Comprueba que Android decodifique una foto completa y que una visita continua genere una sola solicitud. Revisa los límites de tamaño, los fallos de captura y el consumo de datos. La cámara debe funcionar mediante el broker sin abrir puertos en el teléfono.

5. **Termina la integración con EMQX y Firebase.** Verifica los tópicos bajo `dispensador/equipo01/` o el prefijo equivalente si la configuración activa usa otro equipo. Contrasta los permisos de publicación y suscripción de cada cliente con `docs/SOFTWARE.md`. En Firebase, verifica la correspondencia entre `applicationId`, el proyecto existente y `app/google-services.json`, el acceso por correo y contraseña, la base Firestore y las reglas desplegadas. Comprueba que cada usuario solo pueda acceder a su historial en `users/{uid}/feedings/{id}`. Revisa que la app asocie cada resultado con el ID de la orden y evite registros duplicados. Si tienes acceso autorizado a la consola o CLI, completa y verifica los ajustes pendientes. Si falta acceso, termina el código y especifica la acción exacta que debo realizar en mi cuenta.

6. **Compila y prueba.** Ejecuta el Gradle Wrapper del proyecto, las pruebas disponibles y la compilación de depuración. Compila **cada sketch por separado** con el perfil de placa y las versiones de dependencias que documentes. Si hay placas conectadas y puedes identificarlas, carga el firmware correspondiente y revisa el monitor serie. Si trabajas remotamente, deja instrucciones de carga diferenciadas para cada placa, incluida la entrada y salida del modo de programación de la ESP32-CAM según su adaptador. Indica los pasos exactos para comprobar desde Android y MQTTX la telemetría, una dispensación, una orden rechazada, una foto, la pérdida del hotspot y la reconexión. Haz la primera prueba de movimiento con el mecanismo descargado.

7. **Actualiza la guía de laboratorio.** Corrige `docs/SOFTWARE.md` y `docs/HARDWARE.md` con los pasos que correspondan a la versión final. Quiero poder seguirlos desde cero: configurar la zona Wi-Fi, completar los archivos locales, programar cada placa, conectar los componentes pin por pin, verificar alimentación y tierra común, abrir la app y reconocer en el monitor serie y MQTTX cuándo funciona cada etapa. Distingue la alimentación del ESP32 por USB de la alimentación regulada del servo. Verifica la adaptación de tensión de las señales ECHO antes de indicar el montaje.

## FORMATO

Implementa los cambios en los archivos del proyecto. Al finalizar, entrega:

1. **Estado del proyecto** con una tabla de componente, cambio realizado, prueba ejecutada, resultado y pendiente.
2. **Resultados de compilación** de Android, ESP32 y ESP32-CAM por separado, con comandos, versiones y errores relevantes.
3. **Guía de puesta en marcha** en orden de ejecución, con rutas de archivo, campos a completar y resultado esperado en cada paso.
4. **Prueba de extremo a extremo** con una orden de ejemplo: cómo identificar su ID en Android, observar su `ack` en MQTTX y localizar el documento correspondiente en Firestore. Indica cómo verificar físicamente que cayó alimento.
5. **Pendientes que requieren mi intervención**, como conectar una placa por USB, identificar un componente o ingresar una credencial en mi equipo. Para cada uno, indica cómo saber que quedó resuelto.

## NIVEL

Aplica criterio de ingeniería al código y explica el montaje al nivel de un estudiante de Ingeniería Informática que hará la demostración con hardware real. Usa español claro, rutas exactas y ejemplos compatibles con el código final. Si un paso puede fallar, indica qué señal observar y dónde revisar primero.

## RESTRICCIONES

- Trabaja sobre el repositorio definitivo y conserva los servicios existentes. Mantén fuera del proyecto los dashboards antiguos, los backends anteriores, el panel solar y el fusible descartado.
- Mantén las credenciales privadas fuera de Git, del chat, de las capturas y de los registros. Usa plantillas con valores ficticios. Conserva la validación TLS y las reglas de acceso por usuario.
- Evita órdenes de dispensación MQTT con `retain`. Una reconexión o un reinicio debe ser seguro aunque haya mensajes pendientes.
- Identifica explícitamente qué quedó **compilado**, **probado en simulación**, **verificado con servicios reales** y **observado en hardware**. Un `ack` por sí solo no demuestra que cayó alimento. Si falta una placa, acceso a Firebase o una medición física, registra ese límite y continúa con el resto.
- **Empieza por inspeccionar el código y continúa con la implementación.** La entrega debe incluir los cambios y sus verificaciones, además de las instrucciones para el laboratorio.
