# Dispensador IoT · Android y ESP32

Proyecto para Android Studio Panda 1. Abre esta carpeta, donde están settings.gradle.kts y gradlew. El teléfono controla el dispensador a través de EMQX y guarda el historial por usuario en Firebase. La simulación sigue disponible para probar la interfaz.

1. [Configurar Android, Firebase, EMQX y MQTTX](docs/SOFTWARE.md).
2. [Programar las dos placas, cablear componente por componente y verificar el montaje](docs/HARDWARE.md).
3. [Firmware del controlador](firmware/Control/Control.ino) y [firmware de la cámara](firmware/Camara/Camara.ino).

Los módulos app, domain, data y hardware forman la aplicación. El directorio firmware contiene dos proyectos independientes de Arduino IDE. En mecanica están los STL seleccionados de torre, tolva, dosificador, comedero y alojamientos. Comprueba las medidas antes de imprimir. El montaje definitivo del MG996R requiere comprobar su fijación y recorrido con el disco real.

El repositorio contiene las plantillas de configuración. Cada integrante coloca su google-services.json en app/ y las credenciales locales en firmware/Control/secrets.h y firmware/Camara/secrets.h.
