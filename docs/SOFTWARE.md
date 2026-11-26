# Configuración de la app y la red

## Android Studio Panda 1

1. Clona el repositorio y abre su raíz. Selecciona el Gradle Wrapper y el JBR 21 incluido en Android Studio; instala Android SDK Platform 35. Ejecuta Gradle Sync y selecciona app.
2. Para el equipo real, usa el proyecto Firebase existente. En Project settings → General → Your apps selecciona la app Android con applicationId cl.marea.app. Descarga google-services.json y colócalo junto a app/build.gradle.kts. Sincroniza y vuelve a instalar la app en el teléfono.
3. En Firebase Console comprueba Authentication → Sign-in method → Email/Password y la base Firestore (default). Las reglas móviles están en firestore.rules. Si las reglas ya están desplegadas, verifica su contenido antes de cambiar nada. En el teléfono inicia sesión con el usuario del dispensador.
4. Para probar solamente la interfaz, selecciona Probar en simulación. Para la prueba física inicia sesión y abre Ajustes.

## EMQX Cloud y MQTTX

Usa el despliegue existente dispensador-instituto. En Deployment Overview copia Address y descarga CA Certificate. El firmware configura el puerto 8883 y valida TLS. Usa el mismo identificador de equipo en las placas y en la app.

| Cliente | Publica | Recibe |
|---|---|---|
| App Android | cmd | state, ack, jpeg |
| ESP32 principal | state, ack, camera/request | cmd |
| ESP32-CAM | jpeg | camera/request |
| MQTTX para observar | — | state, ack; jpeg si se concede |

Anteponer dispensador/equipo01/ a cada sufijo de la tabla. En Access Control → Authentication verifica las cuentas y en Authorization sus permisos. Usa una contraseña privada por cliente. Si una contraseña ya fue compartida en una captura o chat, cámbiala antes de grabarla.

1. Activa los datos móviles y la zona Wi-Fi del celular en 2,4 GHz y WPA2 Personal. Anota el SSID exacto y la clave de esa red. El firmware necesita acceso a DNS, NTP y al broker por internet.
2. En MQTTX crea o abre la conexión TLS con Address, puerto 8883, usuario observador y validación del certificado activa. Usa un Client ID exclusivo. Suscríbete a dispensador/equipo01/state y dispensador/equipo01/ack con QoS 0.
3. En Ajustes de Android introduce Address sin protocolo ni puerto, equipo01 y la cuenta MQTT de la app. Pulsa Conectar equipo. Enciende el controlador y comprueba la aparición de lecturas nuevas.
4. Abre Cámara y acerca un blanco al sensor del plato. Al detectar una entrada, el ESP32 solicita una foto por camera/request y la cámara entrega un JPEG. La app conectada muestra la última imagen y permite guardarla. El broker transmite las fotos durante la sesión; las fotos de visitas anteriores no se descargan al reconectar.
5. En Tolva pulsa Dispensar. El botón pasa a Detener. Púlsalo mientras el equipo dispensa y observa que el servo cierra y no inicia otro ciclo. Verifica el mismo ID en ack y el resultado en el historial. El botón funciona con o sin presencia en el plato.
6. Para actualizar un montaje existente instala la app nueva y graba ambos sketches. En Firestore Database → Rules publica las reglas actualizadas de firestore.rules en el proyecto del dispensador. Admiten Detenido y cero ciclos para pedidos rechazados o sin confirmación, y conservan la lectura del historial antiguo. Las credenciales, los pines y el puerto del broker siguen vigentes.

### Protocolo manual

Todos los tópicos llevan el prefijo dispensador/equipo01/. En cmd la app envía action=start, id y el nonce reciente; el segundo toque envía action=stop. Publica comandos sin retención. state incluye active y present. Al finalizar, ack incluye el id de inicio, ok y cycles (aperturas iniciadas, incluida una apertura interrumpida). El controlador conserva la cuota diaria al reiniciar. Si el teléfono pierde conexión, el límite autónomo de tres aperturas por activación sigue vigente. Un pedido sin ACK se registra como Sin confirmación; no se reenvía automáticamente.
