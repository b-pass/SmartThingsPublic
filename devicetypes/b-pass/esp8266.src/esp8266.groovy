/*
ESP8266 Device, talks to an ESP8266 running "smartthings_lux/smartthings_lux.ino"
The device is Discovered/Added to SmartThings via the SmartApp device discovery at: https://github.com/b-pass/SmartThingsPublic/smartapps/b-pass/esp8266-discovery.src/esp8266-discovery.groovy
*/
metadata {
	definition (name: "esp8266", 
              namespace: "b-pass",
              author: "B.Pass") {
		capability "Illuminance Measurement"
		capability "Sensor"
		capability "Refresh"
	}

	simulator {
	}

	tiles {
		valueTile("illuminance", "device.illuminance", decoration: "flat") {
			state "luminosity", label:'${currentValue} lux'
		}
        standardTile("refresh", "null", inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "illuminance"
		details "illuminance", "refresh"
	}
}

def installed() {
    log.trace "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.trace "Updated with settings: ${settings}"
    //unsubscribe()
    initialize()
}

def initialize() {
    log.trace "initialize"
    doSubscribe()
}

def refresh() {
  log.debug "Refreshing!"
  doSubscribe
}

def updateValueNow() {
  sendHubCommand(new physicalgraph.device.HubAction(
      method: "GET",
      path: "/lux",
      headers: [
          Host: getHostAddress()
      ]
  ))
}

def parse(String description) {
  // NOTE: We could tell a response from a NOTIFY by checking json vs. xml or checking status code (200 vs. null) or checking for SEQ/NT/SID header
  
  def msg = parseLanMessage(description)
  //log.debug "msg: ${msg}"
  if (!msg)
  {
    log.warn "Failed to parse $description as LanMessage"
    return
  }
  
  if (!state.sid || (msg?.headers?.seq != null && msg.headers.seq?.toInteger() == 0))
  {
    log.debug "Updated subscription from ${state.sid} to ${msg?.headers?.sid}"
    state.sid = msg?.headers?.sid
  }
  
  if (state.sid != msg?.headers?.sid)
  {
    log.debug "Expected sid ${state.sid} doesn't match supplied sid ${msg?.headers?.sid}"
    return
  }
  
  log.debug "Got value ${msg?.xml} on sid ${state.sid}"
  
  //log.debug "json is ${msg?.json}"
  //log.debug "xml is ${msg?.xml}"
  
  // status is null if it's an http request (such as NOTIFY)
  if (msg.status && msg.status != 200)
  {
    log.warn "HTTP status code ${msg.status}"
    return
  }
  
  if (!msg?.xml)
    return // nothing to do
  
  return [createEvent([
    name:"illuminance",
    value:msg.xml,
    unit:"lux"
  ])]
}

def sync(ip, port) {
log.trace "sync ${ip}, ${port}"
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

def doSubscribe(callbackPath="") {
    log.trace "doSubscribe($callbackPath)"
    
    state.sid = null;
    sendHubCommand(new physicalgraph.device.HubAction(
        method: "SUBSCRIBE",
        path: "/subscribe",
        headers: [
            Host: getHostAddress(),
            CALLBACK: "<http://${getCallBackAddress()}/notify${callbackPath}>",
            NT: "upnp:event",
            TIMEOUT: "Second-3630"
        ]
    ))

    log.trace "SUBSCRIBE $path"
    runIn(3600, doSubscribe)
}

// gets the address of the hub
def getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
def getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    //log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
