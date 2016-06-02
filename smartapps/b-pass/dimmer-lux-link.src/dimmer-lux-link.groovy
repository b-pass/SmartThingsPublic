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
	log.debug "installed"
    
    state.squelchUntil = 0
    state.oldLux = null
    
	subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(dimmer, "switch", switchHandler)
}

def updated() {
	unsubscribe()
    
    state.squelchUntil = 0
    state.oldLux = null
    
	subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(dimmer, "switch", switchHandler)
    subscribe(dimmer, "switchLevel", dimmerHandler)
	log.debug "updated"
}

def getLuxTarget() {
	def cal = getSunriseAndSunset()
	if (now() < (cal.sunrise.getTime()+30*60*1000) || now() >= (cal.sunset.getTime() - 30*60*1000))
    	return nightLux
    else
    	return dayLux
}

def luxHandler(evt) {
    if (state.squelchUntil > now())
    {
    	log.debug "squlech ${state.squelchUntil} ... ${now()}"
    	return
    }
    
    def oldLux = state.oldLux
    def currentLux = state.oldLux = evt.doubleValue
    
    if (oldLux == null)
    	return // first run
    
    def targetLux = getLuxTarget()
        
    if (dimmer.currentSwitch == "off" && 
    	oldLux >= (targetLux-luxAccuracy) && currentLux < (targetLux-luxAccuracy))
   	{
    	if (now() >= timeToday(noOnAt, location.timeZone).getTime())
        {
        	log.info "Not turning the light on because it's late."
        }
        else
        {
            log.info "It's getting dark in here (${currentLux}) so turning the light on"
            dimmer.setLevel(15)
            dimmer.on()
            dimmer.setLevel(15)
            state.squelchUntil = now() + 60*1000
        }
        return
    }
    
    if (dimmer.currentSwitch == "on")
    {
        def level = dimmer.currentLevel
    	if (currentLux < (targetLux - luxAccuracy) && level < 100)
        {
        	level += 1
            if (currentLux < (targetLux - luxAccuracy*2) && level < 100)
            	level += 1
            log.info "It's getting dark in here (${currentLux}) so I'm increasing the dimmer to ${level}"
            dimmer.setLevel(level)
            return
        }
        
        if (currentLux > (targetLux + luxAccuracy))
        {
        	if (level <= 10)
            {
            	log.info "It's plenty bright in here (${currentLux}) so I'm turning the light off"
                dimmer.off()
        		state.squelchUntil = now() + 90*1000
                return
            }
            
            level -= 1
            if (currentLux > (targetLux + luxAccuracy*2) && level > 10)
            	level -= 1
            
           	log.info "It's bright in here (${currentLux}) so I'm decreasing the dimmer to ${level}"
            dimmer.setLevel(level)
            return
    	}
    }
}

def switchHandler(evt) {
    state.squelchUntil = now() + 60*1000
}
