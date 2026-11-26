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
Presence presence;
String sessionId;
unsigned cycles=0;
bool opened=false;
unsigned long pulseAt=0;
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
  j["active"]=sessionId.length()>0; j["present"]=presence.near;
  String body; serializeJson(j,body);
  mqtt.publish(topic("state").c_str(),body.c_str());
}
void reply(const String &id, bool ok, unsigned count) {
  JsonDocument ack;
  ack["id"]=id; ack["ok"]=ok; ack["cycles"]=count;
  String body; serializeJson(ack,body);
  mqtt.publish(topic("ack").c_str(),body.c_str());
}
void stopFeed() {
  gate.write(CLOSED); opened=false;
  if(sessionId.length()) {
    String finished=sessionId; sessionId=""; lastFeed=millis();
    reply(finished,true,cycles);
  }
  renew(); sendState();
}
bool nextCycle() {
  const unsigned today=time(nullptr)/86400;
  const uint32_t stored=nv.getUInt("quota",0);
  unsigned used=stored/16==today ? stored%16 : 0;
  level=binLevel();
  if(!storageOK || !canCycle(level,used,cycles)) return false;
  if(nv.putUInt("quota",today*16+used+1)!=sizeof(uint32_t)) return false;
  cycles++; pulseAt=millis(); opened=true; gate.write(OPEN);
  return true;
}
void command(char *name, uint8_t *payload, unsigned int len) {
  if(topic("cmd")!=name || len>320) return;
  JsonDocument j;
  if(deserializeJson(j,payload,len)) return;
  String action=j["action"] | "";
  if(action=="stop"){ stopFeed(); return; }
  String id=j["id"] | "";
  if(action!="start" || !id.length() || id.length()>64 || id==sessionId) return;
  bool fresh=nonce==j["nonce"].as<String>() && millis()-issued<20000;
  if(sessionId.length() || !fresh || millis()-lastFeed<10000){ reply(id,false,0); return; }
  cycles=0;
  if(!nextCycle()){ reply(id,false,0); return; }
  sessionId=id; renew(); sendState();
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
  if(sessionId.length() && (WiFi.status()!=WL_CONNECTED || !mqtt.connected())) stopFeed();
  if(maintain("control")){
    renew(); mqtt.subscribe(topic("cmd").c_str());
  }
  if(sessionId.length()){
    if(opened && millis()-pulseAt>=350){
      gate.write(CLOSED); opened=false;
      if(cycles>=3) stopFeed();
    } else if(!opened && millis()-pulseAt>=3000 && !nextCycle()) stopFeed();
  }
  if(mqtt.connected() && millis()-sampled>=500){
    sampled=millis();
    plate=measure(TRIG_PLATE,ECHO_PLATE);
    if(presence.sample(plate)) mqtt.publish(topic("camera/request").c_str(),"capture");
    delay(65); level=binLevel();
    if(sessionId.length() && level<4) stopFeed();
    if(millis()-issued>15000) renew();
    sendState();
  }
  delay(2);
}
