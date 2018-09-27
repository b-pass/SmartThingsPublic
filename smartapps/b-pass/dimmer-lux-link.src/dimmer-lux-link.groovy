/**
 *  Dimmer Lux Link
 *
 *  Copyright 2016 B. Pass
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Dimmer Lux Link",
    namespace: "b-pass",
    author: "B. Pass",
    description: "Link a dimmer and a lux/illuminance sensor.",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Home/home30-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home30-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home30-icn@2x.png")

preferences {
	section("Things") {
		input "luxSensor", "capability.illuminanceMeasurement", title:"Light Sensor", required: true
		input "dimmer", "capability.switchLevel", title:"Dimmer Switch", required: true, multiple: true
		input "ctrlSwitch", "capability.switch", title:"On/Off Switch", required: false
	}
    section("Desired accuracy:") {
    	input "luxAccuracy", "decimal", default:1.0, required: false, title:"Lux"
    }
	section("Target levels:") {
		input "nightLux", "decimal", default:20, required: true, title:"Night"
        input "preDawnLux", "decimal", default:10, required: true, title:"Pre-Dawn"
		input "dayLux", "decimal", default:40, required: true, title:"Day"
	}
    section("Special Times") {
    	input "noOnAt", "time", required: false, title:"Stop turning on"
    	//input "forceOffTime", "time", required: false, title:"Force off"
    }
}

def installed() {
	log.debug "installed"
    
    state.squelchUntil = 0
    state.oldLux = null
    state.shouldBeOn = false
    
	subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(dimmer, "switch", dimmerHandler)
    if (ctrlSwitch)
    	subscribe(ctrlSwitch, "switch", ctrlSwitchHandler)
}

def updated() {
	unsubscribe()
    
    state.squelchUntil = 0
    state.oldLux = null
    state.shouldBeOn = false
    
	subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(dimmer, "switch", dimmerHandler)
    if (ctrlSwitch)
    	subscribe(ctrlSwitch, "switch", ctrlSwitchHandler)
    
	log.debug "updated"
}

def getLuxTarget() {
	def cal = getSunriseAndSunset()
	if (now() < (cal.sunrise.getTime()+30*60*1000))
    	return preDawnLux
    else if (now() >= (cal.sunset.getTime() - 30*60*1000))
    	return nightLux
    else
    	return dayLux
}

def fixMe() {
     dimmer.each {
    	if (it.currentSwitch == "on" && !state.shouldBeOn)
        {
        	it.off()
        }
        if (it.currentSwitch == "off" && state.shouldBeOn)
        {
            dimmer.setLevel(15)
            dimmer.on()
            dimmer.setLevel(15)
        }
    }
    
    if (ctrlSwitch) {
        if (ctrlSwitch.currentSwitch == "on" && !state.shouldBeOn)
            ctrlSwitch.off()
   		if (ctrlSwitch.currentSwitch == "off" && state.shouldBeOn)
            ctrlSwitch.on()
    }
}

def luxHandler(evt) {
    def oldLux = state.oldLux
    def currentLux = state.oldLux = evt.doubleValue
    //log.trace "luxHandler ${evt}"
    if (oldLux == null)
    	return // first run
    
    if (state.squelchUntil > now())
    {
    	log.debug "Squleched lux change ${currentLux} (until ${state.squelchUntil})"
    	return
    }
    
    def targetLux = getLuxTarget()
    
    log.trace "currentLux = ${currentLux} targetLux = ${targetLux} acc = ${luxAccuracy} shouldBeOn = ${state.shouldBeOn}"
    fixMe()
    
    if (!state.shouldBeOn && oldLux >= (targetLux-luxAccuracy) && currentLux < (targetLux-luxAccuracy))
   	{
    	if (now() >= timeToday(noOnAt, location.timeZone).getTime())
        {
        	log.info "Not turning the light on because it's late."
        }
        else
        {
            log.info "It's getting dark in here (${currentLux}) so turning the light on"
            state.shouldBeOn = true
            state.squelchUntil = now() + 15*1000
            dimmer.setLevel(15)
            ctrlSwitch?.on()
            dimmer.on()
            dimmer.setLevel(15)
            runIn(60, checkLevels)
        }
        return
    }
    
    if (state.shouldBeOn)
    {
        def level = dimmer[0].currentLevel
    	if (currentLux < (targetLux - luxAccuracy) && level < 100)
        {
        	level += 1
            if (currentLux < (targetLux - luxAccuracy*2) && level < 95)
            	level += 4
            log.info "It's getting dark in here (${currentLux}) so I'm increasing the dimmer to ${level}"
            dimmer.setLevel(level)
            return
        }
        
        if (currentLux > (targetLux + luxAccuracy))
        {
        	if (level <= 10)
            {
            	log.info "It's plenty bright in here (${currentLux}) so I'm turning the light off"
                state.shouldBeOn = false
                ctrlSwitch?.off()
                dimmer.off()
        		state.squelchUntil = now() + 60*1000
                return
            }
            
            level -= 1
            if (currentLux > (targetLux + luxAccuracy*2) && level > 15)
            	level -= 4
            
           	log.info "It's bright in here (${currentLux}) so I'm decreasing the dimmer to ${level}"
            dimmer.setLevel(level)
            return
    	}
    }
    
	log.info "lux change did nothing"
}

def dimmerHandler(evt) {
	if (evt.value.startsWith("turning"))
    	return
    
    log.trace "Dimmer ${evt.device} changed itself to state ${evt.value}"
    if (evt.value != "on")
    {
    	if (state.shouldBeOn)
        {
          evt.device?.setLevel(15)
          dimmer.on()
          evt.device?.setLevel(15)
        }
    }
    else
    {
    	if (!state.shouldBeOn)
        {
        	dimmer.off()
    		state.squelchUntil = now() + 10*1000
        }
    }
}

def ctrlSwitchHandler(evt) {
	if (evt.value?.startsWith("turning"))
    	return
    
    log.trace "Control switch ${evt.device} is now ${evt.value}"
    
    if (evt.value != "on")
    {
    	log.debug "Turning dimmer(s) off"
        state.shouldBeOn = false
    	state.squelchUntil = now() + 60*1000
    	dimmer.off()
    }
    else
    {
    	log.debug "Turning dimmer(s) on"
        state.shouldBeOn = true
    	state.squelchUntil = now() + 5*1000
        dimmer.setLevel(15)
    	dimmer.on()
        dimmer.setLevel(15)
        runIn(15, checkLevels)
    }
}

def checkLevels() {
  if (luxSensor && luxSensor.currentValue)
    luxHandler([doubleValue: luxsensor.currentValue])
}
