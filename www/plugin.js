var exec = require('cordova/exec');
var PLUGIN_NAME = 'BluePrinter';

var BluePrinter = function () {
    this.channels = {
        watchinPrinter: cordova.addWindowEventHandler("printerstatus")
    };
    for (var key in this.channels) {
        this.channels[key].onHasSubscribersChange = BluePrinter.onHasSubscribersChange;
    }
}
function handlers() {
    return BluePrinter.channels.watchinPrinter.numHandlers;
}
BluePrinter.onHasSubscribersChange = function () {
    console.log('onHasSubscribersChange', this.numHandlers);
    // If we just registered the first handler, make sure native listener is started.
    if (this.numHandlers === 1 && handlers() === 1) {
        exec(BluePrinter._status, BluePrinter._error, PLUGIN_NAME, "start", []);
    } else if (handlers() === 0) {
        exec(null, null, PLUGIN_NAME, "stop", []);
    }
};
BluePrinter.prototype._status = function (info) {
    console.log('_status', info);
    if (info) {
        cordova.fireWindowEvent("printerstatus", info);
    }
};
BluePrinter.prototype._error = function(e) {
    console.log("Error initializing Printer: ", e);
};
BluePrinter.prototype.connect = function(onDisconnect, onDisconnectError) {
    exec(BluePrinter._status, BluePrinter._error, PLUGIN_NAME, "connect", []);
    //exec(onDisconnect, onDisconnectError, PLUGIN_NAME, "start", []);
};
BluePrinter.prototype.disconnect = function(onDisconnect, onDisconnectError) {
    exec(onDisconnect, onDisconnectError, PLUGIN_NAME, "disconnect", []);
};
BluePrinter.prototype.print = function(onDisconnect, onDisconnectError, phrase) {
    exec(onDisconnect, onDisconnectError, PLUGIN_NAME, "print", [phrase]);
};

var BluePrinter = new BluePrinter(); // jshint ignore:line

module.exports = BluePrinter;