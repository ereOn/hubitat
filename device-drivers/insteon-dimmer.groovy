import groovy.json.JsonBuilder

metadata {
	definition (name: "Insteon Dimmer", namespace: "insteon", author: "Julien Kauffmann") {
		capability "Actuator";
		capability "Light";
		capability "Refresh";
		capability "Polling";
		capability "Sensor";
		capability "Switch";
		capability "Switch Level";
		capability "Health Check";
	}
}

void installed() {
	log.debug "Installed with settings: ${settings}";
	initialize();
}

def updated() {
	log.debug "Updated with settings: ${settings}";
	initialize();
}

def initialize() {
	state.initialized = true;
}

def on() {
	log.debug "On";
	parent.setLevel(device.deviceNetworkId, 100, 0);
}

def off() {
	log.debug "Off";
	parent.setLevel(device.deviceNetworkId, 0, 0);
}

def refresh() {
	log.debug "Refresh";
	parent.refresh(device.deviceNetworkId);
}

def poll() {
	log.debug "Poll";
	parent.refresh(device.deviceNetworkId);
}

def ping() {
	log.debug "ping";
}

def setLevel(level, duration = null) {
	log.debug "setLevel";
	parent.setLevel(device.deviceNetworkId, level, duration);
}

def writeOnOff(onOff) {
	def currentOnOff = device.currentValue("switch") as String;

	if (currentOnOff == null || onOff != (currentOnOff == "on")) {
		sendEvent(name: "switch", value: onOff ? "on" : "off", displayed: true, isStateChange: true);
	}
}

def writeLevel(level) {
	log.debug "writeLevel ${level}";

	if (level < 0) { level = 0 }
	if (level > 100) { level = 100 }

	def currentLevel = device.currentValue("level") as Integer;

	if (currentLevel == null || level != currentLevel) {
		sendEvent(name: "level", value: level, displayed: true, isStateChange: true);
	}

	writeOnOff(level > 0);
}
