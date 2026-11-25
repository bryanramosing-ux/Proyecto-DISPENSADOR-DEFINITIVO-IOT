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
| App Android | cmd, photo | state, ack, jpeg |
| ESP32 principal | state, ack, camera/request | cmd, photo |
| ESP32-CAM | jpeg | camera/request |
| MQTTX para observar | — | state, ack; jpeg si se concede |

Anteponer dispensador/equipo01/ a cada sufijo de la tabla. En Access Control → Authentication verifica las cuentas y en Authorization sus permisos. Usa una contraseña privada por cliente. Si una contraseña ya fue compartida en una captura o chat, cámbiala antes de grabarla.

1. Activa los datos móviles y la zona Wi-Fi del celular en 2,4 GHz y WPA2 Personal. Anota el SSID exacto y la clave de esa red. El firmware necesita acceso a DNS, NTP y al broker por internet.
2. En MQTTX crea o abre la conexión TLS con Address, puerto 8883, usuario observador y validación del certificado activa. Usa un Client ID exclusivo. Suscríbete a dispensador/equipo01/state y dispensador/equipo01/ack con QoS 0.
3. En Ajustes de Android introduce Address sin protocolo ni puerto, equipo01 y la cuenta MQTT de la app. Pulsa Conectar equipo. Enciende el controlador y comprueba la aparición de lecturas nuevas.
4. La cámara entrega JPEG por MQTT. La opción en vivo solicita una ráfaga de imágenes. El celular puede recibirlas a través del broker sin abrir puertos en la zona Wi-Fi.
5. Tras el ensayo físico revisa en Firestore users/{uid}/feedings/{id}. Contrasta la hora, porciones y resultado con el movimiento observado. Un ACK confirma la decisión del controlador; la masa de alimento se comprueba con una balanza.
