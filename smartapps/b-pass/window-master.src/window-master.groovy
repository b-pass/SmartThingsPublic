/**
 *  Window Master
 *
 *  Copyright 2018 Bryan Pass
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
    name: "Window Master",
    namespace: "b-pass",
    author: "Bryan Pass",
    description: "Masterfully coordinate window actuators and thermostats.",
    category: "Green Living",
    iconUrl: "http://cdn.device-icons.smartthings.com/Home/home9-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home9-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Home/home9-icn@3x.png",
)

preferences {
	section("Things") {
		input "windows", "capability.doorControl", title:"Windows", required: true, multiple: true
		input "therm", "capability.thermostat", title:"Inside Thermostat", required: false
		input "outTemp", "capability.temperatureMeasurement", title:"Outside Temperature", required: false
        input "outRelHum", "capability.relativeHumidityMeasurement", title:"Outside Humidity", required: false
	}
    /*section("Desired accuracy:") {
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
    }*/
}

def installed() {
	log.debug "installed"
    
    state.oldOutTemp = 0
    state.shouldBeOpen = false
    
    if (outTemp)
      	subscribe(outTemp, "temperature", outTempHandler)
    
    checkWebWeather()
    runEvery15Minutes(checkWebWeather)
}

def updated() {
	log.debug "updated"
	unsubscribe()
    installed()
}

def checkWebWeather() {
	def nowWx = getWeatherFeature("conditions")?.current_observation
	//log.trace nowWx
    //log.trace getWeatherFeature("hourly")
    
    def weatherObsStr = nowWx?.weather
    if (weatherObsStr =~ /(?:rain|snow|mist|ice|sleet|fog|thunder)/)
    {
    	log.warn "DANGER DANGER ${weatherObsStr}"
    	// emergency close
    }
    
    log.trace "${thermostat.heatingSetpoint?.properties}"
    log.debug "temp=${nowWx?.temp_f}, feelslike=${nowWx?.feelslike_f}, rh=${nowWx?.relative_humidity}"
    
    
    /*
    In the summer we want to open the windows any time it feels cooler than the cooling setpoint. (With a sane hard min inside temp)
    In the winter we want to open the windows any time it feels warmer than the heat setpoint.  (With a sane hard max inside temp)
    
    If it has been > 5 days with no windows open we can flex the winter rules a little cooler.
    Summer rules need to be minus a couple degrees as we really don't want warm inside.  Also have to watch the humidity in summer, "feels like" may not be good enough.
    
    Summer = tomorrow's high >= hard max (78-ish)
    Winter = tomorrow's high <= hard min (65-ish)
    
    Perfect zone (68-74) -> always open windows in this zone because it feels nice
    Target zone (65-75) -> move temp toward one end of this zone based on if tomorrow will be colder or hotter. if tomorrow will be in the zone jthen just keep it perfect
    
    T=80, I=70, O=50 -> windows open until I hits 65
    T=60, I=70, O=50 -> windows not open because we want it to be 75
    T=60, I=70, O=70 -> windows open (perfect zone)
    T=60, I=70, O=75 -> windows open because we want it to be 75
    T=80, I=78, O=76 -> winodws open because we want it to be 65
    
    Remember not to change window state more than once per hour
    
    Ideally heat/cool coming on would shut the winodws if we can figure out the thermostat API.
    */
}

/*
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
            dimmer.setLevel(15)
            ctrlSwitch?.on()
            dimmer.on()
            dimmer.setLevel(15)
            state.squelchUntil = now() + 60*1000
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
        		state.squelchUntil = now() + 90*1000
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
    	state.squelchUntil = now() + 90*1000
    	dimmer.off()
    }
    else
    {
    	log.debug "Turning dimmer(s) on"
        state.shouldBeOn = true
    	state.squelchUntil = now() + 15*1000
        dimmer.setLevel(15)
    	dimmer.on()
        dimmer.setLevel(15)
    }
}
*/