/**
ESP8266 Discovery App, talks to an ESP8266 running: https://github.com/b-pass/SmartThingsPublic/smartapps/b-pass/ESP8266.src/smartthings_lux.ino
Adds these type devices: https://github.com/b-pass/SmartThingsPublic/smartapps/b-pass/ESP8266.src/ESP8266.groovy
*/
definition(
		name: "ESP8266 UPnP Service Manager",
		namespace: "b-pass",
		author: "B. Pass",
		description: "Discovers ESP8266-based things",
		category: "My Apps",
		iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
		iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
		iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	page(name: "deviceDiscovery", title: "UPnP Device Setup", content: "deviceDiscovery")
}

def deviceDiscovery() {
	log.debug "inside deviceDiscovery"
    
	def options = [:]
	def devices = getVerifiedDevices()
	devices.each {
		def value = it.value.name ?: "UPnP Device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		options["${key}"] = value
	}

	ssdpSubscribe()
	ssdpDiscover()
	verifyDevices()

	return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!", nextPage: "", refreshInterval: 5, install: true, uninstall: true) {
		section("Please wait while we discover your UPnP Device. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
			input "selectedDevices", "enum", required: false, title: "Select Devices (${options.size() ?: 0} found)", multiple: true, options: options
		}
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "inside initialize"
    
	unsubscribe()
	unschedule()

	ssdpSubscribe()

	if (selectedDevices) {
		addDevices()
	}

	runEvery5Minutes("ssdpDiscover")
}

void ssdpDiscover() {
	log.debug "inside ssdpDiscover"
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:esp8266_huzzah", physicalgraph.device.Protocol.LAN))
}

void ssdpSubscribe() {
	log.debug "inside ssdpSubscribe"
	subscribe(location, "ssdpTerm.urn:schemas-upnp-org:device:esp8266_huzzah", ssdpHandler)
}

Map verifiedDevices() {
	log.debug "inside verifiedDevices"
	def devices = getVerifiedDevices()
	def map = [:]
	devices.each {
		def value = it.value.name ?: "UPnP Device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map["${key}"] = value
	}
	map
}

void verifyDevices() {
	log.debug "inside verifyDevices"
	def devices = getDevices().findAll { it?.value?.verified != true }
	devices.each {
		int port = convertHexToInt(it.value.deviceAddress)
		String ip = convertHexToIP(it.value.networkAddress)
		String host = "${ip}:${port}"
        log.debug "GET ${it.value.ssdpPath} from ${host}"
		sendHubCommand(new physicalgraph.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", physicalgraph.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
	}
}

def getVerifiedDevices() {
	log.debug "inside getVerifiedDevices"
	getDevices().findAll{ it.value.verified == true }
}

def getDevices() {
	log.debug "inside getDevices"
	if (!state.devices) {
		state.devices = [:]
	}
	state.devices
}

def addDevices() {
	log.debug "inside addDevices"
    
	def devices = getDevices()

	selectedDevices.each { dni ->
		def selectedDevice = devices.find { it.value.mac == dni }
		def d
		if (selectedDevice) {
			d = getChildDevices()?.find {
				it.deviceNetworkId == selectedDevice.value.mac
			}
		}

		if (!d) {
			log.debug "Creating Device with dni: ${selectedDevice.value.mac}"
			addChildDevice("b-pass", "ESP8266 UPnP Device", selectedDevice.value.mac, selectedDevice.value.hub, [
				"label": selectedDevice.value.name ?: "UPnP Device",
				"data": [
					"mac": selectedDevice.value.mac,
					"ip": selectedDevice.value.networkAddress,
					"port": selectedDevice.value.deviceAddress
				]
			])
		}
	}
}

def ssdpHandler(evt) {
	log.debug "inside ssdpHandler"
    
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseLanMessage(description)
	parsedEvent << ["hub":hub]

	def devices = getDevices()
	String ssdpUSN = parsedEvent.ssdpUSN.toString()
	if (devices."${ssdpUSN}") {
		def d = devices."${ssdpUSN}"
		if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
			d.networkAddress = parsedEvent.networkAddress
			d.deviceAddress = parsedEvent.deviceAddress
			def child = getChildDevice(parsedEvent.mac)
			if (child) {
				child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
			}
		}
	} else {
		devices << ["${ssdpUSN}": parsedEvent]
	}
}

void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
	log.debug "inside deviceDescriptionHandler"
    
	def body = hubResponse.xml
	def devices = getDevices()
	def device = devices.find {
    	it?.key?.contains(body?.device?.UDN?.text())
    }
	if (device) {
        def name = body?.device?.friendlyName ?: "UPnP Device"
        name = "${name} @${device?.value?.mac}"
		device.value << [name: name, model:body?.device?.modelNumber?.text(), serialNumber:body?.device?.serialNumber?.text(), verified: true]
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
