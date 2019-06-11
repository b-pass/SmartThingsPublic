#include <Adafruit_TSL2561_U.h>

#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <ESP8266HTTPClient.h>
#include <ESP8266SSDP.h>

#define WEB_PORT 80

Adafruit_TSL2561_Unified luxSensor(TSL2561_ADDR_FLOAT);
ESP8266WebServer webServer(WEB_PORT);

#define NUM_IMPORTANT_HEADERS 3
char const *importantHeaders[NUM_IMPORTANT_HEADERS] = {
  "CALLBACK",
  "TIMEOUT",
  "SID"
};

void ReqStatus();
void ReqLux();
void ReqDebug();
void ReqSubscribe();
void Notify(int sidx);

struct subscription_t
{
  subscription_t() 
  : expire_time(0), seq(0)
  {}
  
  String sid;
  String callback;
  uint64_t expire_time;
  uint32_t seq;
};

#define MAX_SUBSCRIPTIONS 10
subscription_t subscriptions[MAX_SUBSCRIPTIONS];
int num_subscriptions = 0;

#define SAMPLE_INTERVAL 2
#define SAMPLES_PER_MIN (60/SAMPLE_INTERVAL)
#define ROLLING_HISTORY 10 // must be <= SAMPLES_PER_MIN
#define SQUELCH_INTERVAL (ROLLING_HISTORY/2)
#define MULTI_MINUTES 10
uint64_t lastRead = 0;
int omhi = 0, mmhi = 0, squelch = 0;
float currentLuxValue = 0, lastNotify = 0;
float one_minute_history[SAMPLES_PER_MIN] = {0,};
float multi_minute_history[MULTI_MINUTES] = {0,};

void setup(void) {
  pinMode(0, OUTPUT); // red LED
  digitalWrite(0, LOW); // red LED on
  Serial.begin(115200);
  delay(25);
  Serial.println(F("Setup..."));
  
  Wire.begin(5, 4);
  
  luxSensor.enableAutoRange(true);
  luxSensor.setPowerSave(false);
  luxSensor.begin();
  
  WiFi.begin("SSID", "PASSWORD");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.print(F("  Connected, IP: "));
  Serial.println(WiFi.localIP());
  
  int seed = 0;
  for (int i = 0; i < 32; ++i)
    seed = (analogRead(A0)&1) | (seed << 1);
  randomSeed(seed ? seed : millis());
  
  SSDP.setHTTPPort(WEB_PORT);
  SSDP.setSchemaURL("description.xml");
  SSDP.setURL("status");
  SSDP.setName("Huzzah +Lux");
  SSDP.setManufacturer("Adafruit");
  SSDP.setManufacturerURL("http://adafruit.com");
  SSDP.setModelNumber("lux-1.0");
  SSDP.setModelURL("http://github.io/b-pass");
  SSDP.setSerialNumber(ESP.getChipId());
  SSDP.setDeviceType("urn:schemas-upnp-org:device:esp8266_huzzah:1");
  SSDP.begin();
  
  webServer.on("/description.xml", [](){ Serial.println(F("ssdp description requested")); SSDP.schema(webServer.client()); });
  webServer.on("/", [](){ Serial.println(F("root requested")); webServer.send(200, "text/plain", "Welcome!"); });
  webServer.on("/status", &ReqStatus);
  webServer.on("/lux", &ReqLux);
  webServer.on("/debug", &ReqDebug);
  webServer.on("/subscribe", &ReqSubscribe);
  webServer.collectHeaders(importantHeaders, NUM_IMPORTANT_HEADERS);
  webServer.begin();
  
  for (int i = 0; i < MAX_SUBSCRIPTIONS; ++i)
    subscriptions[i].expire_time = 0;

  Serial.println(F("Setup finished"));
  digitalWrite(0, HIGH); // red LED off
}

void loop(void) {
  delay(1);
  
  webServer.handleClient();
  
  uint64_t now = millis();
  if (lastRead + SAMPLE_INTERVAL*1000 <= now)
  {
    lastRead = now;
    uint16_t b, i;
    luxSensor.getLuminosity(&b,&i);
    
    one_minute_history[omhi++] = luxSensor.calculateLux(b,i);
    if (omhi == SAMPLES_PER_MIN)
    {
      omhi = 0;
      
      float avg = 0;
      for (int i = 0; i < SAMPLES_PER_MIN; ++i)
        avg += one_minute_history[i];
      avg /= SAMPLES_PER_MIN;
      
      float old = multi_minute_history[mmhi];
      multi_minute_history[mmhi++%MULTI_MINUTES] = avg;
    }
    
    float prevValue = currentLuxValue;
    currentLuxValue = 0;
    for (int i = 0; i < ROLLING_HISTORY; ++i)
      currentLuxValue += one_minute_history[(omhi - 1 - i + SAMPLES_PER_MIN) % SAMPLES_PER_MIN];
    currentLuxValue /= ROLLING_HISTORY;
    
    ++squelch;
    if (squelch >= SQUELCH_INTERVAL && int(currentLuxValue+0.5) != int(lastNotify + 0.5))
    {
      Serial.println(F("Lux changed enough to notify someone"));
      lastNotify = currentLuxValue;
      squelch = 0;
      for (int sidx = 0; sidx < MAX_SUBSCRIPTIONS; ++sidx)
      {
        if (subscriptions[sidx].expire_time > now)
          Notify(sidx);
      }
    }
  }
}

void ReqStatus()
{
  Serial.println(F("/status requested"));
  
  String doc = F("{ \"version\":1");
  doc += F(", \"uptime_millis\":");
  doc += millis();
  doc += F(" }\n");
  webServer.send(200, "application/json", doc);
}

void ReqLux()
{
  Serial.println(F("/lux requested"));
  
  String doc;
  doc += F("{ \"current\":");
  doc += currentLuxValue;
  doc += F(", \"this_minute\":[");
  for (int i = 0; i < SAMPLES_PER_MIN; ++i)
  {
    if (i)
      doc += F(", ");
    doc += one_minute_history[(omhi - 1 - i + SAMPLES_PER_MIN) % SAMPLES_PER_MIN];
  }
  doc += F("], \"previous_minutes\":[");
  for (int i = 0; i < MULTI_MINUTES; ++i)
  {
    if (i)
      doc += F(", ");
    doc += multi_minute_history[(mmhi - 1 + i + MULTI_MINUTES) % MULTI_MINUTES];
  }
  doc += F("] }\n");
  
  webServer.send(200, "application/json", doc);
}

void ReqDebug()
{
  Serial.println(F("/debug requested"));
  
  String doc;
  
  doc += F("Now:"); doc += millis(); doc += F("\n");
  doc += F("\nCurrent Average:"); doc += currentLuxValue; doc += F("\n");
  
  doc += F("\nOMIdx: ");
  doc += omhi;
  doc += F("\n");
  for (int i = 0; i < SAMPLES_PER_MIN; i++)
  {
    doc += F("Sample ");
    doc += (i+1);
    doc += F(": ");
    doc += one_minute_history[i];
    doc += F("\n");
  }
  
  doc += F("\nMMIdx: ");
  doc += mmhi;
  doc += F("\n");
  for (int i = 0; i < MULTI_MINUTES; i++)
  {
    doc += F("Minute ");
    doc += (i+1);
    doc += F(": ");
    doc += multi_minute_history[i];
    doc += F("\n");
  }
  
  doc += F("\n\n");
  
  webServer.send(200, "text/plain", doc);
}

String generateUUID()
{
  char value[37] = "00000000-0000-0000-0000-000000000000";
  
  for (int i = 0; i < 36; ++i)
  {
    if (value[i] != '-')
    {
      int x = random(0, 16);
      if (x < 10)
        value[i] = '0'+x;
      else
        value[i] = 'a'+x-10;
    }
  }
  
  return String(value);
}

void ReqSubscribe()
{
  Serial.println(F("/subscribe requested"));
  
  /* re-SUBSCRBE would have a SID and no Callback, but so would UNSUBSCRIBE. 
  TIMEOUT is optional on re-SUBSCRIBE. Therefore a request with no TIMEOUT and 
  a valid SID could be either a UNSUBSCRIBE or a re-SUBSCRIBE and we can't 
  know which.  So we assume its a re-SUBSCRIBE.
  
  We should obvious be able to tell from the method, but ESP8266's WebServer 
  doesn't support that.*/
  if (!webServer.hasHeader("TIMEOUT"))
  {
    webServer.send(500, "text/plain", F("Can't tell if this was an UNSUBSCRIBE or a re-SUBSCRIBE because it is missing a TIMEOUT header"));
    return;
  }
  
  int sidx = -1;
  bool isNew;
  if (webServer.hasHeader("SID"))
  {
    String sid = webServer.header("SID").substring(5); // prefixed with "uuid:"
    for (int i = 0; i < MAX_SUBSCRIPTIONS; ++i)
    {
      if (subscriptions[i].sid == sid && subscriptions[i].expire_time < millis())
      {
        sidx = i;
        break;
      }
    }
    
    if (sidx < 0)
    {
      webServer.send(412, "text/plain", F("The SID you sent cannot be found"));
      return;
    }
    isNew = false;
  }
  else
  {
    for (int i = 0; i < MAX_SUBSCRIPTIONS; ++i)
    {
      if (subscriptions[i].expire_time < millis())
      {
        sidx = i;
        break;
      }
    }
    
    if (sidx < 0)
    {
      webServer.send(503, "text/plain", F("Server too busy"));
      return;
    }
    
    subscriptions[sidx].sid = generateUUID();
    isNew = true;
  }
  
  subscriptions[sidx].callback = webServer.header("CALLBACK");
  if (subscriptions[sidx].callback.length() < 2 || subscriptions[sidx].callback.length() > 256)
  {
    webServer.send(400, "text/plain", F("Bad/missing CALLBACK header."));
    return;
  }
  
  int lifetime = webServer.header("TIMEOUT").substring(7).toInt(); // prefixed with "Second-"
  if (lifetime < 10 || lifetime > 7200)
    lifetime = 3600;
  
  subscriptions[sidx].expire_time = millis() + lifetime * 1000;
  
  webServer.sendHeader("Server", F("Arduino/1.0 UPnP/1.1 Lux/1.0"));
  webServer.sendHeader("TIMEOUT", "Second-"+String(lifetime));
  webServer.sendHeader("SID", "uuid:"+subscriptions[sidx].sid);
  webServer.sendHeader("NT", "upnp:event");
  webServer.send(200, "text/plain", "");
  
  // MUST send seq=0 NOTIFY event after subscribing
  if (isNew)
  {
    subscriptions[sidx].seq = 0;
    Notify(sidx);
  }
}

void Notify(int sidx)
{
  String xml = F("<?xml version=\"1.0\"?>\n"
                 "<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">\n"
                 "<e:property>\n"
                 "<lux>");
  xml += currentLuxValue;
  xml += F("</lux>\n"
           "</e:property>\n"
           "</e:propertyset>\n");
  
  int cbp = 0;
  while (cbp < subscriptions[sidx].callback.length())
  {
    while (cbp < subscriptions[sidx].callback.length() && subscriptions[sidx].callback[cbp] != '<')
      ++cbp;
    ++cbp; // skip the <
    int start = cbp;
    while (cbp < subscriptions[sidx].callback.length() && subscriptions[sidx].callback[cbp] != '>')
      ++cbp;
    int end = cbp;
    ++cbp; // skip the >
    
    if (start >= end)
      continue;
    
    String callback = subscriptions[sidx].callback.substring(start, end);
    Serial.print(F("Notify cb: "));
    Serial.println(callback);
    
    HTTPClient client;
    client.begin(callback);
    client.useHTTP10(true);
    client.setTimeout(2000);
    
    client.addHeader("Content-Type", "text/xml; charset=\"utf-8\"");
    client.addHeader("Content-Length", String(xml.length()));
    client.addHeader("NT", "upnp:event");
    client.addHeader("NTS", "upnp:propchange");
    client.addHeader("SID", "uuid:"+subscriptions[sidx].sid);
    client.addHeader("SEQ", String(subscriptions[sidx].seq));
    
    if (client.sendRequest("NOTIFY", xml) > 0)
      break;
  }
  
  ++subscriptions[sidx].seq;
}

