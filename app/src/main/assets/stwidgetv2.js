//version 1.01
class StWidget {
    static widgetNum = 0;
    static dhiveClient = null;
    constructor(url) {
        this.element = null;
        this.iframe = null;
        this.user = null;
        this.properties = null;
        this.initialized = false;
        this.enableKeychainPassthrough = true;
        this.postingKey = null;
        this.allowedCustomJson = ["community", "follow"];
        this.messageListener = null;
        this.onLastRead = null;
        this.frameOrigin = '*';
        this.widgetNum = StWidget.widgetNum++;
        this.messageName = 'stlib'+this.widgetNum;
        this.url = url.indexOf('?')===-1?(url+'?embed='+this.widgetNum):(url+'&embed='+this.widgetNum);
        this.dhive = null;
        this.hivejs = null;
    }
    createElement(width=450, height=556, overlay=true, resizable=true) {
        this.initialize();
        var div = document.createElement('div');
        this.element = div;
        var style = { border: '1px solid gray' };
        if(overlay) {
            style['position'] = 'absolute';
            style['z-index'] = '10000';
        }
        if(resizable) {
            style['resize'] = 'both';
            style['overflow'] = 'hidden';
        }

        this.setStyle(style);
        this.resize(width, height);

        var iframe = document.createElement('iframe');
        this.iframe = iframe;
        iframe.src = this.url;
        iframe.style.width = "100%";
        iframe.style.height = "100%";
        div.appendChild(iframe);

        return div;
    }
    resize(width=450, height=556) {
        this.setStyle({
            width: typeof width === 'string'?width:(width+'px'),
            height: typeof height === 'string'?height:(height+'px'),
        });
    }
    setStyle(style) {
        for(var name in style) this.element.style.setProperty(name, style[name]);
    }
    setLastReadCallback(fn) {
        this.onLastRead = fn;
    }
    postMessage(message) {
        if(this.initialized) {
            var _this = this;
            if(this.iframe.contentWindow != null)
                this.iframe.contentWindow.postMessage(message, this.frameOrigin);
            else this.iframe.addEventListener( "load", ()=>{
                _this.iframe.contentWindow.postMessage(message, this.frameOrigin);
            });
            return true;
        }
        return false;
    }
    setProperties(properties) {
        this.properties = properties;
        this.postMessage([this.messageName, "setProperties", JSON.stringify(this.properties)]);
    }
    pause(value) { return this.postMessage([this.messageName, "pause", JSON.stringify(value)]); }
    setUser(user) {
        this.user = user;
        this.postMessage([this.messageName, "setUser", JSON.stringify(user)])
        this.reload();
    }
    setPostingKey(key, dhiveOrHivejs) {
        if(dhiveOrHivejs.PrivateKey != null) {
            this.postingKey = (typeof key === 'string')?dhiveOrHivejs.PrivateKey.fromString(key):key;
            this.dhive = dhiveOrHivejs;
        }
        else {
            this.postingKey = key;
            this.hivejs = dhiveOrHivejs;
        }
    }
    getDhiveClient() {
        if (StWidget.dhiveClient === null) {
            var dhiveClient = this.dhive.Client;
            StWidget.dhiveClient = new dhiveClient(["https://api.hive.blog", "https://anyx.io", "https://api.openhive.network", "https://rpc.ecency.com"]);
        }
        return StWidget.dhiveClient;
    }
    reload() {
        this.postMessage([this.messageName, "reload", JSON.stringify("")])
    }
    navigate(url) {
        this.url = url.indexOf('?')===-1?(url+'?embed='+this.widgetNum):(url+'&embed='+this.widgetNum);
        this.postMessage([this.messageName, "navigate", JSON.stringify(this.url)]);
    }

    initialize() {
        if(this.messageListener != null) return;
        var _this = this;
        this.messageListener = (event) => {
            try {
                if(event.data != null && Array.isArray(event.data)) {
                    var data = event.data;
                    if(data.length > 2 &&
                        (data[0] === 'stlib' || (data[0] === 'stlib'+this.widgetNum))) {
                        _this.onMessage(event, data[1], data[2], data.length > 3?data[3]:[]);
                    }
                }
            }
            catch(e) { console.log(e); }
        };
        window.addEventListener("message", this.messageListener);
    }
    onMessage(event, msgId, name, args) {
        var _this = this;
        switch(name) {
            case "initialize":
                this.initialized = true;
                this.frameOrigin = event.origin;
                if(this.properties != null)
                    event.source.postMessage([_this.messageName, "setProperties", JSON.stringify(_this.properties)], event.origin);
                if(this.user != null)
                    event.source.postMessage([_this.messageName, "setUser", JSON.stringify(_this.user)], event.origin);
                event.source.postMessage([_this.messageName, "initMain", JSON.stringify("")], event.origin);
                break;
            case "notifications":
                if(this.onLastRead) _this.onLastRead(args);
                break;
            case "requestCustomJson":
            case "requestVerifyKey":
            case "requestSignBuffer":
            case "requestEncodeMessage":
                if(_this.postingKey) {
                    try {
                        if(_this.dhive) _this.handleWithDhive(event, msgId, name, args)
                        else if(_this.hivejs) _this.handleWithHivejs(event, msgId, name, args);
                    }
                    catch(e) {
                        event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                            success: false, error: e, result: null
                        })], event.origin);
                    }
                }
                else if(_this.enableKeychainPassthrough) {
                    _this.handleWithKeychain(event, msgId, name, args);
                }
            break;
        }
    }
    handleWithKeychain(event, msgId, name, args) {
        var _this = this;
        switch(name) {
            case "requestCustomJson":
                if(this.allowedCustomJson.indexOf(args[1]) !== -1 && args[2] === 'Posting') {
                    window.hive_keychain.requestCustomJson(args[0], args[1], 'Posting', args[3], args[4], (r)=>{
                        event.source.postMessage([_this.messageName, msgId, JSON.stringify(r)], event.origin);
                    });
                }
            break;
            case "requestVerifyKey":
                if(args[2] === 'Posting')
                    window.hive_keychain.requestVerifyKey(args[0], args[1], 'Posting', (r)=>{
                        event.source.postMessage([_this.messageName, msgId, JSON.stringify(r)], event.origin);
                    });
            break;
            case "requestSignBuffer":
                if(args[2] === 'Posting')
                    window.hive_keychain.requestSignBuffer(args[0], args[1], 'Posting', (r)=>{
                        event.source.postMessage([_this.messageName, msgId, JSON.stringify(r)], event.origin);
                    });
            break;
            case "requestEncodeMessage":
                if(args[3] === 'Posting')
                    window.hive_keychain.requestEncodeMessage(args[0], args[1], args[2], 'Posting', (r)=>{
                        event.source.postMessage([_this.messageName, msgId, JSON.stringify(r)], event.origin);
                    });
            break;
        }
    }
    handleWithDhive(event, msgId, name, args) {
        var _this = this;
        switch(name) {
            case "requestCustomJson":
                if(this.allowedCustomJson.indexOf(args[1]) !== -1 && args[2] === 'Posting') {
                    _this.getDhiveClient().broadcast.json({
                            id: args[1],
                            json: args[3],
                            required_auths: [],
                            required_posting_auths: [args[0]],
                        }, _this.postingKey).then(function(result) {
                            event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                                success: true,
                                error: null,
                                result: result
                            })], event.origin);
                        }, function(error) {
                            event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                                success: false,
                                error: error,
                                result: false
                            })], event.origin);
                        });
                }
            break;
            case "requestVerifyKey":
                if(args[2] !== 'Posting') return;
                var decoded = _this.dhive.Memo.decode(_this.postingKey, args[1]);
                if(decoded.startsWith("#")) decoded = decoded.substring(1);
                event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                    success: true,
                    error: null,
                    result: decoded
                })], event.origin);
            break;
            case "requestSignBuffer":
                if(args[2] !== 'Posting') return;
                var messageHash = _this.dhive.cryptoUtils.sha256(args[1]);
                var result = _this.postingKey.sign(messageHash).toString("hex");
                event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                    success: true,
                    error: null,
                    result: result
                })], event.origin);
            break;
            case "requestEncodeMessage":
                if(args[3] !== 'Posting') return;
                if(args[0] == args[1]) {
                    var publicKey = _this.postingKey.createPublic("STM");
                    var result = _this.dhive.Memo.encode(_this.postingKey, publicKey, args[2]);
                    event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                        success: true,
                        error: null,
                        result: result
                    })], event.origin);
                }
                else {
                    _this.getDhiveClient().database
                        .getAccounts([args[1]]).then((array)=>{
                        if(array.length > 0 && array[0].name == args[1]) {
                            var publicKey = array[0].posting.key_auths[0][0];
                            var result = _this.dhive.Memo.encode(_this.postingKey, publicKey, args[2]);
                            event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                                success: true,
                                error: null,
                                result: result
                            })], event.origin);
                        }
                    });
                }
            break;
        }
    }
    handleWithHivejs(event, msgId, name, args) {
        var _this = this;
        switch(name) {
            case "requestCustomJson":
                if(this.allowedCustomJson.indexOf(args[1]) !== -1 && args[2] === 'Posting') {
                    _this.hivejs.broadcast.customJson(_this.postingKey, [], [args[0]], args[1],
                      args[3], function(error, result) {
                        if(error == null) {
                            event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                                success: true,
                                error: null,
                                result: result
                            })], event.origin);
                        }
                        else {
                            event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                                success: false,
                                error: error,
                                result: false
                            })], event.origin);
                        }
                    });
                }
            break;
            case "requestVerifyKey":
                if(args[2] !== 'Posting') return;
                var decoded = _this.hivejs.memo.decode(_this.postingKey, args[1]);
                if(decoded.startsWith("#")) decoded = decoded.substring(1);
                event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                    success: true,
                    error: null,
                    result: decoded
                })], event.origin);
            break;
            case "requestSignBuffer":
                if(args[2] !== 'Posting') return;
                var result = _this.hivejs.auth.Signature.sign(args[1], _this.postingKey).toHex();
                event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                    success: true,
                    error: null,
                    result: result
                })], event.origin);
            break;
            case "requestEncodeMessage":
                if(args[3] !== 'Posting') return;
                if(args[0] == args[1]) {
                    var publicKey = _this.hivejs.auth.wifToPublic(_this.postingKey)
                    var result = _this.hivejs.memo.encode(_this.postingKey, publicKey, args[2]);
                    event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                        success: true,
                        error: null,
                        result: result
                    })], event.origin);
                }
                else {
                    _this.hivejs.api.getAccountsAsync([args[1]]).then((array)=>{
                        if(array.length > 0 && array[0].name == args[1]) {
                            var publicKey = array[0].posting.key_auths[0][0];
                            var result = _this.hivejs.memo.encode(_this.postingKey, publicKey, args[2]);
                            event.source.postMessage([_this.messageName, msgId, JSON.stringify({
                                success: true,
                                error: null,
                                result: result
                            })], event.origin);
                        }
                    });
                }
            break;
        }
    }
    cleanup() {
        if(this.messageListener != null) {
            window.removeEventListener("message", this.messageListener);
            this.messageListener = null;
        }
    }
}