#include "MareaMqtt.h"
#include "Policy.h"
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include <Preferences.h>

const int SERVO_PIN=5, TRIG_PLATE=19, ECHO_PLATE=21, TRIG_BIN=18, ECHO_BIN=22;
const int EMPTY_CM=26, FULL_CM=5, CLOSED=85, OPEN=105;
Servo gate;
Preferences nv;
bool storageOK=false;
int level=-1, plate=-1;
String nonce;
unsigned long issued=0, lastFeed=0, sampled=0;
void renew() {
  nonce=String(esp_random(),HEX)+"-"+String(esp_random(),HEX);
  issued=millis();
}
int measure(int trig, int echo) {
  digitalWrite(trig,LOW); delayMicroseconds(2);
  digitalWrite(trig,HIGH); delayMicroseconds(10); digitalWrite(trig,LOW);
  unsigned long duration=pulseIn(echo,HIGH,25000);
  int cm=duration/58;
  return duration && cm>=2 && cm<=400 ? cm : -1;
}
int binLevel() {
  int cm=measure(TRIG_BIN,ECHO_BIN);
  if(cm<0) return -1;
  return constrain((EMPTY_CM-cm)*100/(EMPTY_CM-FULL_CM),0,100);
}
void sendState() {
  JsonDocument j; j["cm"]=plate; j["level"]=level; j["nonce"]=nonce;
  String body; serializeJson(j,body);
  mqtt.publish(topic("state").c_str(),body.c_str());
}
void command(char *name, uint8_t *payload, unsigned int len) {
  if(topic("photo")==name) { bool live=len==4 && memcmp(payload,"live",4)==0; mqtt.publish(topic("camera/request").c_str(),live?"live":"capture"); return; }
  if(topic("cmd")!=name || len>320) return;
  JsonDocument j;
  if(deserializeJson(j,payload,len)) return;
  String id=j["id"] | "";
  if(id.length()==0 || id.length()>64) return;
  int n=j["portions"].is<int>() ? j["portions"].as<int>() : 0;
  const unsigned today=time(nullptr)/86400;
  const uint32_t stored=nv.getUInt("quota",0);
  unsigned used=stored/16==today ? stored%16 : 0;
  level=binLevel();
  bool fresh=j["nonce"].is<const char*>() && nonce==j["nonce"].as<String>() && millis()-issued<20000;
  bool ok=storageOK && allowed(n,level,used,millis()-lastFeed,fresh);
  if(ok) ok=nv.putUInt("quota",today*16+used+n)==sizeof(uint32_t);
  if(ok) {
    renew(); lastFeed=millis();
    for(int i=0;i<n;i++){
      gate.write(OPEN); delay(350); gate.write(CLOSED); delay(450);
    }
  }
  JsonDocument ack; ack["id"]=id; ack["ok"]=ok;
  String body; serializeJson(ack,body); mqtt.publish(topic("ack").c_str(),body.c_str());
}
void setup() {
  Serial.begin(115200);
  pinMode(TRIG_PLATE,OUTPUT); pinMode(ECHO_PLATE,INPUT);
  pinMode(TRIG_BIN,OUTPUT); pinMode(ECHO_BIN,INPUT);
  gate.setPeriodHertz(50); gate.attach(SERVO_PIN,1000,2000); gate.write(CLOSED);
  storageOK=nv.begin("marea",false);
  beginNetwork(command); renew();
}
void loop() {
  if(maintain("control")){
    renew(); mqtt.subscribe(topic("cmd").c_str()); mqtt.subscribe(topic("photo").c_str());
  }
  if(mqtt.connected() && millis()-sampled>=2000){
    sampled=millis();
    if(millis()-issued>15000) renew();
    plate=measure(TRIG_PLATE,ECHO_PLATE); delay(65); level=binLevel(); sendState();
  }
  delay(2);
}
