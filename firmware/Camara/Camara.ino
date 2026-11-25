#include <algorithm>
#include "MareaMqtt.h"
#include "esp_camera.h"

// Mapa AI Thinker ESP32-CAM con OV2640. Verificar antes de cargar.
bool cameraReady=false, requested=false;
unsigned long liveSince=0, lastFrame=0;
bool live=false;
void command(char *name, uint8_t *payload, unsigned int len) {
  if(topic("camera/request")!=name) return;
  requested=true;
  if(len==4 && memcmp(payload,"live",4)==0){ live=true; liveSince=millis(); }
}
bool initCamera(){
  camera_config_t c={};
  c.ledc_channel=LEDC_CHANNEL_0; c.ledc_timer=LEDC_TIMER_0;
  c.pin_d0=5; c.pin_d1=18; c.pin_d2=19; c.pin_d3=21;
  c.pin_d4=36; c.pin_d5=39; c.pin_d6=34; c.pin_d7=35;
  c.pin_xclk=0; c.pin_pclk=22; c.pin_vsync=25; c.pin_href=23;
  c.pin_sccb_sda=26; c.pin_sccb_scl=27; c.pin_pwdn=32; c.pin_reset=-1;
  c.xclk_freq_hz=20000000; c.pixel_format=PIXFORMAT_JPEG;
  c.frame_size=FRAMESIZE_QVGA; c.jpeg_quality=14; c.fb_count=1;
  c.fb_location=CAMERA_FB_IN_DRAM;
  return esp_camera_init(&c)==ESP_OK;
}
void setup(){
  Serial.begin(115200); cameraReady=initCamera();
  Serial.println(cameraReady?"Camara lista":"Error al iniciar OV2640");
  beginNetwork(command);
}
void loop(){
  if(maintain("camera")) mqtt.subscribe(topic("camera/request").c_str());
  if(live && millis()-liveSince>4500) live=false;
  if((requested || (live && millis()-lastFrame>=550)) && mqtt.connected()){
    lastFrame=millis();
    requested=false;
    camera_fb_t *frame=cameraReady?esp_camera_fb_get():nullptr;
    if(frame){
      if(frame->len<=60000 && mqtt.beginPublish(topic("jpeg").c_str(),frame->len,false)){
        size_t offset=0;
        while(offset<frame->len){
          size_t count=std::min(size_t(1024),frame->len-offset);
          if(mqtt.write(frame->buf+offset,count)!=count){transport.stop();break;}
          offset+=count;
        }
        if(offset==frame->len) mqtt.endPublish();
      }
      esp_camera_fb_return(frame);
    }
  }
  delay(2);
}
