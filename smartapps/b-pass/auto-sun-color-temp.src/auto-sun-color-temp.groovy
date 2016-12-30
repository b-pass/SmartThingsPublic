/**
 *  Auto-sun color temp
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
    name: "Auto-sun color temp",
    namespace: "b-pass",
    author: "Bryan Pass",
    description: "Automatically change color temperature based on time of day.",
    category: "convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Weather/weather14-icn.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Weather/weather14-icn@2x.png",
    iconX3Url: "http://cdn.device-icons.smartthings.com/Weather/weather14-icn@2x.png"
)

preferences {
	section() {
    	input "bulbs", "capability.colorTemperature", title:"Bulb(s)", description:"The bulb(s) to control", multiple:true, required:true
		input "sunriseOffset", "number", title:"Sunrise Offset", description:"Minutes difference from true sunrise", required:true, defaultValue:-30
		input "sunsetOffset", "number", title:"Sunset Offset", description:"Minutes difference from true sunset", required:true, defaultValue:30
        input "sunriseOverride", "time", title:"Fixed Sunrise", description:"Use this time instead of true sunrise", required:false
        input "sunsetOverride", "time", title:"Fixed Sunset", description:"Use this time instead of true sunset", required:false
        input "minTemp", "number", title:"Min Color Temp", description:"Minimum color temperature", required:true, defaultValue:2200
        input "maxTemp", "number", title:"Max Color Temp", description:"Maximum color temperature", required:true, defaultValue:6000
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def getSunrise() {
	if (sunriseOverride)
    	return timeToday(sunriseOverride, location.timeZone).getTime() + sunriseOffset*60*1000
    return getSunriseAndSunset().sunrise.getTime() + sunriseOffset*60*1000
}

def getSunset() {
	if (sunsetOverride)
    	return timeToday(sunsetOverride, location.timeZone).getTime() + sunsetOffset*60*1000
    return getSunriseAndSunset().sunset.getTime() + sunsetOffset*60*1000
}

def calcColorTemperature() {
    def sunriseTime = getSunrise()
    def sunsetTime = getSunset()
    def nowTime = now()
    //log.debug "wtf? $nowTime < $sunriseTime || $nowTime > $sunsetTime"
    if (nowTime < sunriseTime || nowTime > sunsetTime) 
    	return minTemp
    
    def dayLength = sunsetTime - sunriseTime

    //Generate color temperature parabola from points
    //Specify double type or calculations fail
    double x1 = sunriseTime
    double y1 = minTemp
    double x2 = sunriseTime+(dayLength/2)
    double y2 = maxTemp
    double x3 = sunsetTime
    double y3 = minTemp
    double a1 = -x1**2+x2**2
    double b1 = -x1+x2
    double d1 = -y1+y2
    double a2 = -x2**2+x3**2
    double b2 = -x2+x3
    double d2 = -y2+y3
    double bm = -(b2/b1)
    double a3 = bm*a1+a2
    double d3 = bm*d1+d2
    double a = d3/a3
    double b = (d1-a1*a)/b1
    double c = y1-a*x1**2-b*x1
    double colorTemperature = a*nowTime**2+b*nowTime+c
    double mirekCT = Math.round(1000000 / colorTemperature) as Integer //Round to mireks because thats what Hue uses
    def finalCT = Math.round(colorTemperature) as Integer
    log.debug "Color Temperature is ${finalCT} (${mirekCT} mirek)"
    return finalCT
}

def checkTemp() {
	def ct = calcColorTemperature()
	bulbs.each {
    	//if (it.currentSwitch != "off")
        if (it.currentColorTemperature != ct)
        	it.setColorTemperature(ct)
    }
	runIn(30, checkTemp)
}

def initialize() {
	checkTemp()
}