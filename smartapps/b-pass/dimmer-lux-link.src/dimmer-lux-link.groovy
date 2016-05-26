/**
 *  Dimmer Lux Link
 *
 *  Copyright 2016 B Pass
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
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	section("Things") {
		input "luxSensor", "capability.illuminanceMeasurement", title:"Light Sensor", required: true
		input "dimmer", "capability.switchLevel", title:"Dimmer Switch", required: true
	}
    section("Desired accuracy:") {
    	input "luxAccuracy", "decimal", default:1.0, required: false, title:"Lux"
    }
	section("Target levels:") {
		input "nightLux", "decimal", default:20, required: true, title:"Night"
		input "dayLux", "decimal", default:40, required: true, title:"Day"
	}
    section("Special Times") {
    	input "noOnAt", "time", required: false, title:"Stop turning on"
    	//input "forceOffTime", "time", required: false, title:"Force off"
    }
}

def installed() {
	log.debug "setup subscriptions"
	subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(dimmer, "switch", switchHandler)
    subscribe(dimmer, "switchLevel", dimmerHandler)
}

def updated() {
	log.debug "updated"
	unsubscribe()
    installed()
}

def getLuxTarget() {
	def cal = getSunriseAndSunset()
	if (cal.sunrise.getTime() >= now() || cal.sunset.getTime() < now())
    	return nightLux
    else
    	return dayLux
}

def luxHandler(evt) {
	log.trace "lux val: ${evt.value}, ${dimmer.currentSwitch}, ${dimmer.currentLevel}"
    
    def oldLux = state.oldLux
    if (oldLux == null)
    	oldLux = 0
    state.oldLux = evt.value
    
    if (state.squelchUntil > now())
    	return
    
    def targetLux = getLuxTarget()
    
    if (dimmer.currentSwitch == "off" && 
    	oldLux >= targetLux && evt.value < targetLux)
   	{
    	if (Date.parse("yyy-MM-dd'T'HH:mm:ss.SSSZ", noOnAt).getTime() >= now)
        {
        	log.debug "Not turning the light on because it's after ${noOnAt}"
        }
        else
        {
            log.info "It's getting dark in here (${evt.value}) so turning the light on"
            dimmer.setLevel(10)
            dimmer.on()
            state.squelchUntil = now() + 60*1000
        }
        return
    }
    
    if (dimmer.currentSwitch == "on")
    {
        def level = dimmer.currentLevel
    	if (evt.value < (targetLux - luxAccuracy) && level < 100)
        {
        	level += 1
            if (evt.value < (targetLux - luxAccuracy*2) && level < 95)
            	level += 1
            log.info "It's getting dark in here (${evt.value}) so I'm increasing the dimmer to ${level}"
            dimmer.setLevel(level)
            return
        }
        
        if (evt.value > (targetLux + luxAccuracy))
        {
        	if (level < 10)
            {
            	log.info "It's plenty bright in here (${evt.value}) so I'm turning the light off"
                dimmer.off()
        		state.squelchUntil = now() + 90*1000
                return
            }
            
            level -= 1
            if (evt.value > (targetLux + luxAccuracy*2))
            	level -= 1
                
           	log.info "It's bright in here (${evt.value}) so I'm decreasing the dimmer to ${level}"
            dimmer.setLevel(level)
            return
    	}
    }
}

def switchHandler(evt) {
    state.squelchUntil = now() + 60*1000
}

def dimmerHandler(evt) {
    state.squelchUntil = now() + 120*1000
}
