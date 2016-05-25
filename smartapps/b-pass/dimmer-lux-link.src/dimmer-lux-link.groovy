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
		input "dimmer", "capability.switch level", title:"Dimmer Switch", required: true
	}
    section("Desired accuracy:") {
    	input "luxAccuracy", "decimal", default:1.0, required: false, title:"Lux"
    }
	section("Target levels:") {
		input "nightLux", "number", default:20, required: true, title:"Night"
		input "dayLux", "number", default:40, required: true, title:"Day"
	}
    section("Special Times") {
    	input "noOnAt", "time", required: false, title:"Stop turning on"
    	//input "forceOffTime", "time", required: false, title:"Force off"
    }
}

def installed() {
	subscribe(luxSensor, "illuminance", luxHandler)
    subscribe(dimmer, "switch", switchHandler)
}

def updated() {
	unsubscribe()
    installed()
}

def luxHandler(evt) {
	log.debug "lux: ${evt.value}"
    
}

def switchHandler(evt) {
	log.debug "switch: ${evt.value}"
    
}
