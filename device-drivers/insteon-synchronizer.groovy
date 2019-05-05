import groovy.json.JsonBuilder

metadata {
	definition (name: "Insteon Synchronizer", namespace: "insteon", author: "Julien Kauffmann") {
		capability "Actuator";
	}
}

void installed() {
	initialize();
}

def updated() {
	initialize();
}

def initialize() {
	state.initialized = true
}

def parse(msg) {
	// Messages apparently can be sent to the Hub via cURL, like so:
	//
	// curl -X PUT http://192.168.0.8:39501/event -d 'some data'
	//
	// Both the headers and the body are accessible.
	//
	// For a device to actually receive such events, it's deviceNetworkId
	// must be set to the IP address or MAC address of the HTTP request
	// emitter, in hexadecimal and uppercase.
	//
	// The expected body is like:
	//
	// {"id": "hall", "state": {"level": 0.8}}
	values = parseLanMessage(msg);
	evt = new groovy.json.JsonSlurper().parseText(values.body);
	log.debug "Received state update ${values.body}";

	if (evt.state.level == null) {
		evt.state.level = 0;
	}

	def device = parent.getChildDevice(evt.id);
	device.writeLevel(evt.state.level * 100);
}
