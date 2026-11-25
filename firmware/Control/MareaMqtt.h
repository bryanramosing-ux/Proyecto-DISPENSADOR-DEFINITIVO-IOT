#pragma once
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <time.h>
#include "secrets.h"

WiFiClientSecure transport;
PubSubClient mqtt(transport);
String root = String("dispensador/") + DEVICE_ID + "/";
String topic(const char *leaf) { return root + leaf; }

void beginNetwork(void (*callback)(char*, uint8_t*, unsigned int)) {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  transport.setCACert(ROOT_CA);
  mqtt.setServer(BROKER_HOST, 8883);
  mqtt.setCallback(callback);
  mqtt.setBufferSize(1024);
}
bool maintain(const char *role) {
  static unsigned long previous = 0;
  if (WiFi.status() != WL_CONNECTED || time(nullptr) < 1700000000) return false;
  if (mqtt.connected()) { mqtt.loop(); return false; }
  if (millis() - previous < 4000) return false;
  previous = millis();
  String client = String(role) + "-" + DEVICE_ID;
  return mqtt.connect(client.c_str(), BROKER_USER, BROKER_PASSWORD);
}
