/**
 *  Hue Dimmer Switch Link
 *
 *  Copyright 2016 Bryan Pass
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
    name: "Hue Dimmer Switch Link",
    namespace: "b-pass",
    author: "Bryan Pass",
    description: "Allows a 4-button (On/+/-/Off) Hue Dimmer Switch/Remote device to control other devices with those buttons.",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/unknown/zwave/remote-controller.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/unknown/zwave/remote-controller@2x.png"
)

preferences {
  section ("Select your button controller: ") {
  	input "buttons", "capability.button", title: "Remote", description:"The Hue Dimmer Remote device(s) to link", multiple:true, required:true
    input "switches", "capability.switch", title: "Switch(es)", description:"Switch(es) to send the on/off button presses", multiple: true, required: false
    input "dimmers", "capability.switchLevel", title: "Dimmer(s)", description:"Dimmer(s) to send the +/- button presses", multiple: true, required: false
    input "dimmerStep", "number", title:"Dimmer Step Amount", defaultValue: 5
  }
}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def initialize() {
  // Subscribe to the button events
  buttons.each {
  	subscribe(it, "button", buttonEvent)
  }
}

def uninstalled() {
  unsubscribe()
}

def buttonEvent(evt) {
  log.debug "buttonEvent: $evt.name $evt.value ($evt.data)"
  def buttonNumber = evt.jsonData.buttonNumber
  
  switch (buttonNumber)
  {
  case 1:
  	switches*.on()
    break
    
  case 2:
  	dimmers.each {
    	it.setLevel(it.currentLevel + dimmerStep)
    }
    break;
  
  case 3:
  	dimmers.each {
    	it.setLevel(it.currentLevel - dimmerStep)
    }
    break;
  
  case 4:
  	switches*.off()
    break
  }
}
