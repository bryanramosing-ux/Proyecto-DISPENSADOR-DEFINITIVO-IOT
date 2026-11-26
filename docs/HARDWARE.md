# Montaje del dispensador, de la placa suelta a la primera porción

Usa las etiquetas impresas en **tu placa**. Los números siguientes proceden de [Control.ino](../firmware/Control/Control.ino). El sensor del plato mide distancia frontal en centímetros. El de la tolva mira hacia el alimento y estima el porcentaje de llenado. **La fuente fotografiada dice MW 122A, 2 AMP y ofrece 3, 4,5, 6, 7,5, 9 y 12 V. No tiene posición de 5 V. No conectes su salida directamente al carril de este montaje.**

## 1. Preparar el banco sin tensión

1. Desenchufa ambos USB y apaga la fuente. Si ya está cableado el servo en D5, etiqueta los tres cables y desconecta su alimentación antes de comenzar. Trabaja sin alimento y con el brazo del servo desmontado.
2. Reúne ESP32 con micro-USB, ESP32-CAM, base programadora compatible, dos sensores y MG996R. Añade multímetro, cable USB de **datos**, protoboard, puentes, bornes para potencia y cuatro resistencias: dos de 1 kΩ y dos de 1,8 kΩ (preferiblemente 1 %). Si ya tienes parejas 1 kΩ y 2 kΩ, véase el paso 5.
3. Para el montaje completo prepara una **salida regulada de 5,0 V** con margen para ambas placas, sensores y picos del servo; una fuente de banco de 5 V y 5 A es una opción de diseño. Pide al laboratorio una salida adecuada si solo tienes la fuente de la foto. Un convertidor requiere dimensionar su entrada, salida y disipación; ponerlo tras la MW 122A por sí solo no acredita la corriente disponible.
4. Con todo desconectado usa continuidad para identificar qué orificios comparten cada carril. Algunas protoboards tienen un corte a mitad del carril. Etiqueta +5 V y GND y comprueba cualquier puente entre tramos. Usa la protoboard para señales y sensores, y bornes con ramas propias para placas y servo.

## 2. Grabar el controlador y comprobar internet

1. En Arduino IDE instala el paquete esp32 de Espressif Systems **3.3.7** (la versión con la que compilaste), PubSubClient 2.8, ArduinoJson 7.x y ESP32Servo compatible con el core 3.x. El índice estable es https://espressif.github.io/arduino-esp32/package_esp32_index.json.
2. Dentro de firmware/Control copia secrets.example.h a secrets.h. Escribe el nombre exacto y la clave de tu zona Wi-Fi, BROKER_HOST del despliegue, la cuenta MQTT del controlador y DEVICE_ID equipo01. En ROOT_CA pega el PEM del despliegue completo, incluyendo BEGIN y END CERTIFICATE. Conserva los delimitadores C++ exteriores.
3. Conecta únicamente el controlador al computador por micro-USB. Abre Control.ino y selecciona ESP32 Dev Module y el puerto que aparece al conectar la placa. Pulsa Verify y después Upload. Verify por sí solo compila.
4. Enciende la zona Wi-Fi con datos móviles. En MQTTX busca un mensaje **nuevo** en dispensador/equipo01/state. El firmware envía cada medio segundo aproximadamente; con sensores desconectados no valides las lecturas. Desconecta el USB antes de cablear.

## 3. Grabar y probar la ESP32-CAM por separado

1. Comprueba que tu revisión corresponde al mapa **AI Thinker ESP32-CAM con OV2640** usado en [Camara.ino](../firmware/Camara/Camara.ino). La foto frontal identifica las etiquetas de conexión, pero no certifica el modelo del sensor. Comprueba la ficha del kit y el flex. Manipula el flex con la placa apagada.
2. Copia firmware/Camara/secrets.example.h a secrets.h en esa misma carpeta. Usa el mismo Wi-Fi, host, certificado y equipo01. Introduce la **cuenta MQTT de la cámara**. Los sketches crean automáticamente IDs de cliente distintos.
3. Con la base USB compatible desconectada, asienta la cámara haciendo coincidir etiquetas y orientación. Conecta el USB de datos a la base como única alimentación. Abre Camara.ino, selecciona AI Thinker ESP32-CAM y el puerto de la base; verifica y sube. Reinicia en modo normal y abre el monitor serie a 115200. Busca **Camara lista**. Si aparece **Error al iniciar OV2640**, revisa modelo, asiento y orientación del flex antes de continuar.
4. Si solo dispones de USB-UART, usa señales TTL de **3,3 V** y alimentación regulada de 5 V con capacidad para la cámara. Con todo apagado conecta TX del adaptador → U0R; RX → U0T; GND → GND común. El positivo de la fuente va al pin **5V** de la cámara. Deja el positivo del adaptador libre. Une IO0 a GND y reinicia para cargar; después de Upload apaga, quita el puente IO0–GND y reinicia para ejecutar. Verifica el nivel lógico del adaptador antes de usarlo.
5. En operación la cámara solo necesita **5V y GND** externos. Usa el pin rotulado 5V y un GND claramente identificado. Deja libres VCC, 3V3, GND/R, U0R, U0T e IO0. El controlador solicita una fotografía al detectar presencia por MQTT y la cámara publica JPEG. No hay cable TX/RX entre las placas ni una URL de vídeo que debas abrir.

## 4. Distribuir los 5 V y el retorno

1. Sin cargas conectadas, ajusta **la fuente adecuada** a 5,0 V CC. En el multímetro selecciona tensión continua, negro en COM y rojo en V. Mide entre los bornes y en el extremo de cada rama; el signo debe ser positivo con la punta roja sobre +5 V. Apaga la salida.
2. Lleva el negativo a un borne común. Desde ahí salen los retornos del controlador, cámara, servo y carril negativo de sensores. Desde el borne positivo salen cuatro ramas: controlador, cámara, servo y carril positivo de sensores. Mantén sin conectar las ramas que aún no se prueban.
3. En la placa principal de la foto, con micro-USB abajo, se leen **VIN y GND** abajo a la izquierda y **3V3** abajo a la derecha. Conecta +5 V → VIN **solo si la ficha de esa revisión confirma que VIN admite 5 V regulados**; GND → GND. La ficha DevKitC de referencia no identifica automáticamente este clon. Si falta la confirmación, prueba el controlador por USB con su VIN libre y masa común para las señales. Nunca inyectes 5 V en 3V3 ni uses simultáneamente USB y VIN para alimentar el controlador.
4. Para probar cada nueva rama apaga, conecta, inspecciona y vuelve a encender. En la cámara usa una sola alimentación a la vez. Desconecta la base USB al pasarla a la fuente.

## 5. Cablear los dos sensores uno por uno

En la parte posterior del sensor de la foto se distinguen **Vcc, Trig, Echo y Gnd**. Comprueba los rótulos en cada unidad. La tabla supone funcionamiento en modo TRIG/ECHO de cuatro hilos. Si la segunda unidad difiere o tiene puentes de selección de modo, identifica su revisión antes de conectarla.

| Pin del sensor | Sensor del plato → destino | Sensor de la tolva → destino |
|---|---|---|
| Vcc | Carril +5 V | Carril +5 V |
| Gnd | Carril GND | Carril GND |
| Trig | D19 / GPIO19 del ESP32 | D18 / GPIO18 del ESP32 |
| Echo | Resistencia superior → nodo A → D21 / GPIO21 | Resistencia superior → nodo B → D22 / GPIO22 |

1. **Plato.** Con la alimentación apagada lleva Vcc y Gnd a sus carriles y D19 a Trig. Conecta Echo a un extremo de una resistencia de 1 kΩ. Coloca el otro extremo en una fila libre: será el nodo A. En esa fila conecta el cable a D21 y un extremo de una resistencia de 1,8 kΩ. El otro extremo de la de 1,8 kΩ va a GND. Así, un pulso de 5 V en Echo equivale a unos 3,21 V en D21. Nunca puentear la resistencia entre Echo y el GPIO.
2. Enciende. Coloca una superficie plana frente al sensor y muévela entre dos posiciones medidas con una regla. En MQTTX, cm debe cambiar en el mismo sentido que la distancia. Un valor de −1 indica medición inválida. Apaga.
3. **Tolva.** Repite con el segundo sensor y otras dos resistencias. D18 va a Trig; Echo pasa por 1 kΩ al nodo B; el nodo B va a D22 y por 1,8 kΩ a GND. Los nodos A y B quedan en filas distintas. Si usas 2 kΩ en la rama inferior en lugar de 1,8 kΩ, 5,00 V nominales producen 3,33 V; verifica tensión real y tolerancias. Para el montaje nuevo se eligió 1,8 kΩ para dar más margen.
4. Orienta el sensor hacia abajo y evita que mida una pared lateral. Enciende y acerca una superficie plana. El campo level debe subir. El firmware parte de EMPTY_CM=26 y FULL_CM=5. Mide en tu tolva vacía y en el máximo de llenado, ajusta las dos constantes en Control.ino y vuelve a grabar si las distancias reales difieren. La distancia de vacío debe ser mayor que la de lleno.

## 6. Cablear y calibrar el Tower Pro MG996R

| Cable típico del MG996R | Conexión |
|---|---|
| Marrón, retorno | Borne GND común |
| Rojo, alimentación | Borne +5 V regulado, por rama de potencia propia |
| Naranja, señal | D5 / GPIO5 del controlador |

1. Verifica la identificación de los hilos en la unidad real. Apaga todo. Conecta primero retorno y alimentación al distribuidor y después señal a D5. Evita que la corriente del motor pase por el ESP32, el USB o los contactos de la protoboard. D5 transporta únicamente la señal de posición.
2. Deja el brazo desmontado al primer encendido. El controlador solicita **85° al arrancar**. Espera el posicionamiento, corta alimentación y monta el brazo con el disco en cerrado, sin forzar el eje.
3. Con mecanismo descargado, comprueba que hay recorrido libre hasta la posición abierta programada (**105°**). Aparta manos y cables. Enciende y pulsa Dispensar y luego Detener antes de la siguiente apertura cuando haya nivel válido y telemetría reciente. El programa ordena abrir durante 350 ms y regresar a cerrado. Si golpea un tope, zumba continuamente o reinicia el controlador, corta alimentación y corrige la mecánica o la distribución antes de repetir.
4. Con alimento, recoge y pesa varias porciones. Ajusta posición y tiempo según el mecanismo. El ACK confirma la orden ejecutada por el firmware; el dispensador carece de célula de carga para certificar gramos.

## 7. Comprobar el conjunto

| Prueba | Evidencia esperada |
|---|---|
| Alimentación | Medición de la rama de 5 V en las cargas, también mientras arranca el servo; ambas placas siguen conectadas. |
| Plato y tolva | cm cambia al mover el blanco del plato y level al cambiar el nivel de la tolva. El sensor del plato informa distancia; la política actual evalúa el nivel de la tolva. |
| Foto | Orienta la cámara hacia el plato. Acerca un blanco a 25 cm o menos durante dos lecturas. Debe llegar una foto a Android y el servo debe seguir cerrado. Mantén el blanco: no se solicita otra foto. Retíralo más allá de 35 cm durante tres lecturas y vuelve a acercarlo para provocar otra captura. |
| Porción | Pulsa Dispensar y luego Detener. Comprueba cierre y ausencia de aperturas posteriores. Repite y deja actuar el límite automático. Contrasta las aperturas iniciadas con ack e historial. |
| Rechazo | Con la tolva vacía confirmada por el sensor, el pedido debe rechazarse sin mover el servo. |
| Reinicio | Tras apagar y encender con USB desconectado, ambas placas vuelven al Wi-Fi y llega un state nuevo. |

La política del controlador inicia aperturas cada 3 s hasta recibir Detener, con un máximo de tres aperturas por activación. Exige al menos 4 % de nivel antes de cada apertura, espera de 10 s y máximo diario de 12 porciones. Son valores del prototipo. El montaje queda validado al registrar mediciones, movimiento y foto en el equipo físico.

### Referencias técnicas

- [Tower Pro MG996R y tensión de operación](https://towerpro.com.tw/product/mg996r/)
- [HC-SR04 y niveles lógicos de Echo](https://learn.adafruit.com/ultrasonic-sonar-distance-sensors/pinouts)
- [Espressif, alternativas de alimentación del DevKitC de referencia](https://docs.espressif.com/projects/esp-dev-kits/en/latest/esp32/esp32-devkitc/user_guide.html)
- [Espressif, IO0 y modo de carga](https://docs.espressif.com/projects/esptool/en/latest/esp32/advanced-topics/boot-mode-selection.html)
- [MCI Electronics, kit de cámara con base USB](https://mcielectronics.cl/shop/product/tarjeta-de-desarrollo-esp32-cam-base-de-programacion/)
