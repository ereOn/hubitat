definition(
		name: "Insteon Web Service",
		namespace: "insteon",
		author: "julien.kauffmann@freelan.org",
		description: "Insteon Devices integration",
		category: "My Apps",
		iconUrl: "https://static1.squarespace.com/static/53fce470e4b0374adfdd30bc/573f623c4d088e1eb6128630/57b1f7cd6b8f5b17d7c3d395/1471281130922/Insteon+Logo+%28Blue%29.png?format=2500w",
		iconX2Url: "https://static1.squarespace.com/static/53fce470e4b0374adfdd30bc/573f623c4d088e1eb6128630/57b1f7cd6b8f5b17d7c3d395/1471281130922/Insteon+Logo+%28Blue%29.png?format=2500w",
		iconX3Url: "https://static1.squarespace.com/static/53fce470e4b0374adfdd30bc/573f623c4d088e1eb6128630/57b1f7cd6b8f5b17d7c3d395/1471281130922/Insteon+Logo+%28Blue%29.png?format=2500w",
		singleInstance: false
		);

preferences {
	page(name: "configureWebService");
	page(name: "chooseDevices", content: "chooseDevices");
}

def configureWebService() {
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: true) {
		section("Web Service") {
			input "host", "text", title: "Host", description: "(ie. 192.168.1.10)", required: true;
			input "port", "text", title: "Port", description: "(ie. 7660)", required: true, default: "7660";
		}

		section("Manage devices") {
			href(name:"Choose devices", page:"chooseDevices", description:"", title: "Choose devices", params: [:]);
		}
	}
}

def chooseDevices(params) {
	def addedDevices = [:];
	def availableDevices = [:];

	state.all_devices.each { id, device ->
		def dev = getChildDevice(id);

		if (dev) {
			addedDevices[id] = device;
		} else {
			availableDevices[id] = device;
		}
	}

	if (params.add) {
		def deviceId = params.add;
		params.add = null;
		def device = state.all_devices[deviceId];

		try {
			//def deviceNetworkId = convertIPToHex(host)
			def d = addChildDevice("insteon", "Insteon Dimmer", deviceId, null, [:]);
			d.setLabel(device.description);
			d.refresh();

			addedDevices[deviceId] = device;
			availableDevices.remove(deviceId);
		} catch (Exception e) {
			log.warn "Device ${deviceId} was already created (${e}).";
		}
	}

	if (params.remove) {
		log.info "Removing device ${params.remove}";

		def deviceId = params.remove;
		params.remove = null;

		try {
			deleteChildDevice(deviceId);
			addedDevices.remove(deviceId);
			availableDevices[deviceId] = state.all_devices[deviceId];
		} catch (hubitat.exception.NotFoundException e) {
			log.warn "Device ${devId} was already deleted (${e}).";
			addedDevices.remove(deviceId);
			availableDevices[deviceId] = state.all_devices[deviceId];
		} catch (hubitat.exception.ConflictException e) {
			log.error "Device ${deviceId} is still in use (${e}).";
			errorText = "Device ${state.all_devices[deviceId].description} is still in use. Remove from any SmartApps or Dashboards, then try again.";
		}
	}

	dynamicPage(name:"chooseDevices", title: "", install: true) {
		section("Added devices") {
			addedDevices.sort{it.value.description}.each {
				def deviceId = it.key;
				def name = it.value.description;
				href(name:"${deviceId}", page:"chooseDevices", description:"", title:"Remove ${name} [${deviceId}]", params: [remove: deviceId], submitOnChange: true);
			}
		}
		section("Available devices") {
			availableDevices.sort{it.value.description}.each {
				def deviceId = it.key;
				def name = it.value.description;
				href(name:"${deviceId}", page:"chooseDevices", description:"", title:"Add ${name} [${deviceId}]", params: [add: deviceId], submitOnChange: true);
			}
		}
		section("Web Service") {
			href(name:"Configure Web Service", page:"configureWebService", description:"", title: "Configure Web Service");
		}
	}
}

private String convertIPToHex(ipAddress) {
	return ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join().toUpperCase();
}

def installed() {
	log.debug "Installed with settings: ${settings}";
	initialize();
	state.installed = true;
}

def uninstalled() {
	log.debug "Uninstalling...";

	state.all_devices.each { id, device ->
		deleteChildDevice(id);
	}

	destroySyncDevice();
	log.debug "Uninstalled.";
	state.installed = false;
}

def updated() {
	log.debug "Updated with settings: ${settings}";
	initialize();
}

def initialize() {
	log.debug "Initializing...";

	state.all_devices = [:];

	createSyncDevice();
	refreshDevices();
	log.debug "Initialization done.";
}

def createSyncDevice() {
	def deviceNetworkId = convertIPToHex(host);
	addChildDevice("insteon", "Insteon Synchronizer", deviceNetworkId, null, [:]);
}

def destroySyncDevice() {
	def deviceNetworkId = convertIPToHex(host);
	deleteChildDevice(deviceNetworkId);
}

def refreshDevices() {
	log.debug "Refreshing devices...";

	devices = [:];

	httpGet(uri: "http://${host}:${port}/api/devices") { response ->
		response.data.each { device ->
			devices[device.id] = device;
		}
	}

	log.debug "Found ${devices.size()} device(s)";

	devices.each { id, device ->
		log.debug "${id} (${device.description})";
	}

	state.all_devices = devices;
}

def refresh(deviceId) {
	log.debug "Refreshing device ${deviceId}...";

	def device = getChildDevice(deviceId);

	httpGet(uri: "http://${host}:${port}/api/device/${deviceId}/state") { response ->
		device.writeLevel(Math.round((response.data.level as Float ?: 0) * 100));
	}
}

def setLevel(deviceId, level, duration) {
	log.debug "Setting device ${deviceId} level to ${level} over a period of ${duration}s ...";

	if (level < 0) { level = 0 }
	if (level > 100) { level = 100 }

	def device = getChildDevice(deviceId);

	httpPut(
			uri: "http://${host}:${port}/api/device/${deviceId}/state",
			contentType: "application/json",
			body : ["level": level / 100.0f, "onoff": level > 0, "change": duration > 0 ? "normal" : "instant"]
		   ) { response ->
		device.writeLevel(Math.round((response.data.level as Float ?: 0) * 100))
	}
}
